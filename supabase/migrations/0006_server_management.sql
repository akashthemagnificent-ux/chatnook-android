-- Phase 5a: server and channel management. Owner writes are already allowed by RLS on
-- guilds and channels; these functions cover the two operations that are not expressible
-- as a single REST call.

drop function if exists public.create_invite(uuid);

-- Same code generation as before, now with an optional expiry and use limit.
create or replace function public.create_invite(
  gid          uuid,
  p_expires_at timestamptz default null,
  p_max_uses   integer default null
)
returns text
language plpgsql
security definer
set search_path = public
as $$
-- shortcut: any member may invite (Discord gates this on a permission bit), tighten to a
-- role check when the permission resolver lands in phase 5b.
declare
  me uuid := auth.uid();
  new_code text;
begin
  if me is null then raise exception 'not authenticated'; end if;
  if not public.is_guild_member(gid, me) then raise exception 'not a guild member'; end if;

  loop
    new_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
    exit when not exists (select 1 from public.invites i where i.code = new_code);
  end loop;

  insert into public.invites (code, guild_id, created_by, expires_at, max_uses)
  values (new_code, gid, me, p_expires_at, p_max_uses);
  return new_code;
end;
$$;

grant execute on function public.create_invite(uuid, timestamptz, integer) to authenticated;

-- Members can read invites but have no delete policy; this is the only way to revoke.
create or replace function public.revoke_invite(p_code text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  invite record;
begin
  select guild_id, created_by into invite from public.invites where code = p_code;
  if invite is null then
    raise exception 'invite not found';
  end if;
  if not (public.is_guild_owner(invite.guild_id)
          or invite.created_by = auth.uid()) then
    raise exception 'not allowed';
  end if;
  delete from public.invites where code = p_code;
end;
$$;

grant execute on function public.revoke_invite(text) to authenticated;

-- Positions are reassigned as 0..n-1 in one statement, so a swap can never leave two
-- channels sharing a position (which made the old count-based ordering ambiguous).
create or replace function public.reorder_channels(p_guild_id uuid, p_ids uuid[])
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_guild_owner(p_guild_id) then
    raise exception 'not allowed';
  end if;
  update public.channels c
     set position = t.ord - 1
    from unnest(p_ids) with ordinality as t(id, ord)
   where c.id = t.id
     and c.guild_id = p_guild_id;
end;
$$;

grant execute on function public.reorder_channels(uuid, uuid[]) to authenticated;

-- The invite list refreshes on create and revoke without a manual reload.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'invites'
  ) then
    alter publication supabase_realtime add table public.invites;
  end if;
end $$;

-- Realtime filters on DELETE only see the primary key unless the table keeps the
-- whole old row. Without this, revoking an invite (or deleting a channel) never
-- reaches the client that filtered by guild_id.
alter table public.invites replica identity full;
alter table public.channels replica identity full;
alter table public.guild_members replica identity full;
alter table public.messages replica identity full;

-- A channel's own SELECT policy cannot look the channel up by id: when PostgREST
-- inserts with return=representation, the new row is not visible to that
-- subquery yet and the insert fails with "new row violates row-level security
-- policy". Check the guild directly instead.
drop policy if exists channels_read on public.channels;
create policy channels_read on public.channels
  for select to authenticated using (
    (guild_id is not null and public.is_guild_member(guild_id))
    or (guild_id is null and public.can_access_channel(id))
  );
