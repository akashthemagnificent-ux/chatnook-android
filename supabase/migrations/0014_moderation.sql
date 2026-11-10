-- Phase 9: moderation and a message size limit.
--
-- Until now the only way to remove someone was to delete the whole server: the
-- KICK_MEMBERS bit existed but no action used it, and there was no record of who
-- did what. This adds kick, ban and unban behind that bit, an append-only audit
-- log, and a hard cap on message length (there was none — a 1 MB paste was
-- accepted and fanned out to every client).

-- ---------------------------------------------------------------- tables

create table if not exists public.guild_bans (
  guild_id   uuid not null references public.guilds (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  banned_by  uuid references public.profiles (id) on delete set null,
  reason     text,
  created_at timestamptz not null default now(),
  primary key (guild_id, user_id)
);

create table if not exists public.guild_audit_log (
  id         bigint generated always as identity primary key,
  guild_id   uuid not null references public.guilds (id) on delete cascade,
  actor_id   uuid references public.profiles (id) on delete set null,
  action     text not null check (action in ('kick', 'ban', 'unban')),
  target_id  uuid references public.profiles (id) on delete set null,
  detail     text,
  created_at timestamptz not null default now()
);
create index if not exists guild_audit_guild_idx
  on public.guild_audit_log (guild_id, created_at desc);

alter table public.guild_bans      enable row level security;
alter table public.guild_audit_log enable row level security;

-- Only members who may kick can see who is banned or what was done.
-- Writes go through the SECURITY DEFINER functions below, never directly.
drop policy if exists guild_bans_read on public.guild_bans;
create policy guild_bans_read on public.guild_bans
  for select to authenticated using (public.has_permission(guild_id, 6));

drop policy if exists guild_audit_read on public.guild_audit_log;
create policy guild_audit_read on public.guild_audit_log
  for select to authenticated using (public.has_permission(guild_id, 6));

alter table public.guild_bans      replica identity full;
alter table public.guild_audit_log replica identity full;

do $$
declare
  t text;
begin
  foreach t in array array['guild_bans', 'guild_audit_log'] loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;

-- ---------------------------------------------------------------- actions

create or replace function public.kick_member(gid uuid, member uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
begin
  if me is null then raise exception 'not authenticated'; end if;
  if not public.has_permission(gid, 6) then raise exception 'not allowed'; end if;
  if member = me then raise exception 'cannot kick yourself'; end if;
  if public.is_guild_owner(gid, member) then raise exception 'cannot kick the owner'; end if;

  delete from public.guild_members where guild_id = gid and user_id = member;
  if not found then raise exception 'not a member'; end if;

  insert into public.guild_audit_log (guild_id, actor_id, action, target_id)
  values (gid, me, 'kick', member);
end;
$$;

create or replace function public.ban_member(gid uuid, member uuid, reason text default null)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
begin
  if me is null then raise exception 'not authenticated'; end if;
  if not public.has_permission(gid, 6) then raise exception 'not allowed'; end if;
  if member = me then raise exception 'cannot ban yourself'; end if;
  if public.is_guild_owner(gid, member) then raise exception 'cannot ban the owner'; end if;

  insert into public.guild_bans (guild_id, user_id, banned_by, reason)
  values (gid, member, me, nullif(btrim(coalesce(reason, '')), ''))
  on conflict (guild_id, user_id) do update
    set banned_by = excluded.banned_by,
        reason    = excluded.reason,
        created_at = now();

  delete from public.guild_members where guild_id = gid and user_id = member;

  insert into public.guild_audit_log (guild_id, actor_id, action, target_id, detail)
  values (gid, me, 'ban', member, nullif(btrim(coalesce(reason, '')), ''));
end;
$$;

create or replace function public.unban_member(gid uuid, member uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
begin
  if me is null then raise exception 'not authenticated'; end if;
  if not public.has_permission(gid, 6) then raise exception 'not allowed'; end if;

  delete from public.guild_bans where guild_id = gid and user_id = member;
  if not found then raise exception 'not banned'; end if;

  insert into public.guild_audit_log (guild_id, actor_id, action, target_id)
  values (gid, me, 'unban', member);
end;
$$;

revoke all on function public.kick_member(uuid, uuid) from public;
revoke all on function public.ban_member(uuid, uuid, text) from public;
revoke all on function public.unban_member(uuid, uuid) from public;
grant execute on function public.kick_member(uuid, uuid) to authenticated;
grant execute on function public.ban_member(uuid, uuid, text) to authenticated;
grant execute on function public.unban_member(uuid, uuid) to authenticated;

-- ---------------------------------------------------------------- join refuses a ban

create or replace function public.join_guild(invite_code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
  inv public.invites;
begin
  if me is null then raise exception 'not authenticated'; end if;

  select * into inv from public.invites
  where code = upper(trim(invite_code)) for update;
  if not found then raise exception 'invalid invite'; end if;
  if inv.expires_at is not null and inv.expires_at < now() then
    raise exception 'invite expired';
  end if;
  if inv.max_uses is not null and inv.uses >= inv.max_uses then
    raise exception 'invite exhausted';
  end if;
  if exists (
    select 1 from public.guild_bans b where b.guild_id = inv.guild_id and b.user_id = me
  ) then
    raise exception 'banned from this server';
  end if;

  insert into public.guild_members (guild_id, user_id) values (inv.guild_id, me)
  on conflict (guild_id, user_id) do nothing;

  if found then
    update public.invites set uses = uses + 1 where code = inv.code;
  end if;

  return inv.guild_id;
end;
$$;

-- ---------------------------------------------------------------- message length

alter table public.messages drop constraint if exists messages_len;
alter table public.messages
  add constraint messages_len check (content is null or char_length(content) <= 2000);

create or replace function public.send_message(
  p_channel_id  uuid,
  p_content     text,
  p_message_id  uuid default gen_random_uuid(),
  p_reply_to_id uuid default null,
  p_attachment  jsonb default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  me  uuid := auth.uid();
  mid uuid := coalesce(p_message_id, gen_random_uuid());
  body text := nullif(btrim(coalesce(p_content, '')), '');
begin
  if me is null then raise exception 'not authenticated'; end if;
  if not public.can_access_channel(p_channel_id) then
    raise exception 'no access to channel';
  end if;
  if body is null and p_attachment is null then
    raise exception 'message has no content';
  end if;
  if char_length(coalesce(body, '')) > 2000 then
    raise exception 'message too long';
  end if;

  -- Trust boundary: the client supplies these, so refuse anything malformed rather
  -- than writing a row the renderer cannot use.
  if p_attachment is not null then
    if coalesce(p_attachment ->> 'url', '') = '' then
      raise exception 'attachment url missing';
    end if;
    if (p_attachment ->> 'size_bytes')::bigint > 8388608 then
      raise exception 'attachment too large';
    end if;
  end if;

  insert into public.messages (id, channel_id, author_id, content, reply_to_id)
  values (mid, p_channel_id, me, coalesce(body, ''), p_reply_to_id);

  if p_attachment is not null then
    insert into public.attachments
      (message_id, url, filename, content_type, size_bytes, width, height)
    values (
      mid,
      p_attachment ->> 'url',
      p_attachment ->> 'filename',
      p_attachment ->> 'content_type',
      (p_attachment ->> 'size_bytes')::bigint,
      (p_attachment ->> 'width')::int,
      (p_attachment ->> 'height')::int
    );
  end if;

  return mid;
end;
$$;

revoke all on function public.send_message(uuid, text, uuid, uuid, jsonb) from public;
grant execute on function public.send_message(uuid, text, uuid, uuid, jsonb) to authenticated;
