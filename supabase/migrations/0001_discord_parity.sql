-- ChatNook schema (Discord parity, v1)
-- Apply via Supabase SQL editor. Idempotent: safe to re-run.
--
-- Design notes:
--  * RLS never queries a table it protects directly; it calls SECURITY DEFINER
--    helpers instead, which avoids Postgres "infinite recursion detected in
--    policy for relation" (the classic Supabase membership-policy trap).
--  * Channels with guild_id = null are DMs; membership lives in dm_participants.
--  * Roles use a bigint bitfield, same shape as Discord's permission bits.
--  * DM pair-uniqueness is enforced in find_or_create_dm(), not by an index:
--    Postgres forbids subqueries in index expressions.

-- No pgcrypto: gen_random_uuid() is built into Postgres 13+ (Supabase runs 15+).

-- ---------------------------------------------------------------- helpers

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ---------------------------------------------------------------- profiles

create table if not exists public.profiles (
  id                     uuid primary key references auth.users (id) on delete cascade,
  username               text not null unique,
  display_name           text,
  avatar_url             text,
  banner_url             text,
  accent_color           text,
  theme                  jsonb not null default '{}'::jsonb,
  custom_status          text,
  badges                 text[] not null default '{}',
  avatar_decoration_url  text,
  avatar_is_animated     boolean not null default false,
  created_at             timestamptz not null default now(),
  updated_at             timestamptz not null default now(),
  constraint profiles_username_shape check (username ~ '^[a-z0-9._-]{2,32}$')
);

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles
  for each row execute function public.touch_updated_at();

-- Every auth user gets a profile row, so the app never sees a null author.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  base text;
  candidate text;
  n int := 0;
begin
  base := coalesce(
    nullif(new.raw_user_meta_data ->> 'username', ''),
    'user_' || substr(replace(new.id::text, '-', ''), 1, 8)
  );
  base := lower(regexp_replace(base, '[^a-z0-9._-]', '', 'g'));
  if length(base) < 2 then
    base := 'user_' || substr(replace(new.id::text, '-', ''), 1, 8);
  end if;
  candidate := left(base, 32);

  while exists (select 1 from public.profiles p where p.username = candidate) loop
    n := n + 1;
    candidate := left(base, 26) || '_' || n::text;
  end loop;

  insert into public.profiles (id, username, display_name)
  values (new.id, candidate, coalesce(new.raw_user_meta_data ->> 'display_name', candidate))
  on conflict (id) do nothing;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------- guilds

create table if not exists public.guilds (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  icon_url    text,
  owner_id    uuid not null references public.profiles (id) on delete cascade,
  created_at  timestamptz not null default now(),
  constraint guilds_name_len check (char_length(name) between 1 and 100)
);

create table if not exists public.guild_members (
  guild_id   uuid not null references public.guilds (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  nickname   text,
  joined_at  timestamptz not null default now(),
  primary key (guild_id, user_id)
);
create index if not exists guild_members_user_idx on public.guild_members (user_id);

-- Invite-gated joins: no direct INSERT policy exists on guild_members, so
-- membership only comes from this trigger (owner) or join_guild() (invitees).
create or replace function public.add_guild_owner()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.guild_members (guild_id, user_id)
  values (new.id, new.owner_id)
  on conflict (guild_id, user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists guilds_add_owner on public.guilds;
create trigger guilds_add_owner after insert on public.guilds
  for each row execute function public.add_guild_owner();

create table if not exists public.invites (
  code        text primary key,
  guild_id    uuid not null references public.guilds (id) on delete cascade,
  created_by  uuid references public.profiles (id) on delete set null,
  created_at  timestamptz not null default now(),
  expires_at  timestamptz,
  max_uses    integer,
  uses        integer not null default 0
);
create index if not exists invites_guild_idx on public.invites (guild_id);

create table if not exists public.roles (
  id           uuid primary key default gen_random_uuid(),
  guild_id     uuid not null references public.guilds (id) on delete cascade,
  name         text not null,
  color        integer,
  gradient     jsonb,
  position     integer not null default 0,
  permissions  bigint not null default 0,
  is_everyone  boolean not null default false,
  created_at   timestamptz not null default now()
);
create unique index if not exists roles_guild_name_idx on public.roles (guild_id, name);

create table if not exists public.member_roles (
  guild_id  uuid not null,
  user_id   uuid not null,
  role_id   uuid not null references public.roles (id) on delete cascade,
  primary key (guild_id, user_id, role_id),
  foreign key (guild_id, user_id)
    references public.guild_members (guild_id, user_id) on delete cascade
);
create index if not exists member_roles_role_idx on public.member_roles (role_id);

-- ---------------------------------------------------------------- channels

create table if not exists public.channels (
  id          uuid primary key default gen_random_uuid(),
  guild_id    uuid references public.guilds (id) on delete cascade,
  name        text,
  type        text not null default 'text',
  topic       text,
  position    integer not null default 0,
  parent_id   uuid references public.channels (id) on delete set null,
  created_at  timestamptz not null default now(),
  constraint channels_type_check check (type in ('text', 'voice', 'category', 'dm', 'group_dm')),
  constraint channels_name_shape check (
    guild_id is null or (name is not null and char_length(name) between 1 and 100)
  )
);
create index if not exists channels_guild_idx on public.channels (guild_id, position);

create table if not exists public.dm_participants (
  channel_id  uuid not null references public.channels (id) on delete cascade,
  user_id     uuid not null references public.profiles (id) on delete cascade,
  joined_at   timestamptz not null default now(),
  primary key (channel_id, user_id)
);
create index if not exists dm_participants_user_idx on public.dm_participants (user_id);

-- Returns the existing 1:1 DM with `other_user`, or creates one.
create or replace function public.find_or_create_dm(other_user uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
  existing uuid;
  new_channel uuid;
begin
  if me is null then raise exception 'not authenticated'; end if;
  if other_user = me then raise exception 'cannot DM yourself'; end if;

  select c.id into existing
  from public.channels c
  join public.dm_participants a on a.channel_id = c.id and a.user_id = me
  join public.dm_participants b on b.channel_id = c.id and b.user_id = other_user
  where c.type = 'dm'
    and (select count(*) from public.dm_participants p where p.channel_id = c.id) = 2
  limit 1;

  if existing is not null then return existing; end if;

  insert into public.channels (guild_id, type) values (null, 'dm') returning id into new_channel;
  insert into public.dm_participants (channel_id, user_id)
  values (new_channel, me), (new_channel, other_user);
  return new_channel;
end;
$$;

-- ---------------------------------------------------------------- messages

create table if not exists public.messages (
  id            uuid primary key default gen_random_uuid(),
  channel_id    uuid not null references public.channels (id) on delete cascade,
  author_id     uuid not null references public.profiles (id) on delete cascade,
  content       text,
  type          text not null default 'default',
  reply_to_id   uuid references public.messages (id) on delete set null,
  created_at    timestamptz not null default now(),
  edited_at     timestamptz,
  deleted_at    timestamptz,
  constraint messages_type_check check (type in ('default', 'system', 'reply')),
  constraint messages_body check (deleted_at is not null or content is not null or type <> 'default')
);
-- The hot path is "newest 25 in this channel", so index descending.
create index if not exists messages_channel_created_idx
  on public.messages (channel_id, created_at desc, id desc);

create table if not exists public.attachments (
  id            uuid primary key default gen_random_uuid(),
  message_id    uuid not null references public.messages (id) on delete cascade,
  url           text not null,
  filename      text,
  content_type  text,
  size_bytes    bigint,
  width         integer,
  height        integer,
  created_at    timestamptz not null default now()
);
create index if not exists attachments_message_idx on public.attachments (message_id);

create table if not exists public.reactions (
  message_id  uuid not null references public.messages (id) on delete cascade,
  user_id     uuid not null references public.profiles (id) on delete cascade,
  emoji       text not null,
  created_at  timestamptz not null default now(),
  primary key (message_id, user_id, emoji)
);
create index if not exists reactions_message_idx on public.reactions (message_id);

create table if not exists public.read_states (
  user_id              uuid not null references public.profiles (id) on delete cascade,
  channel_id           uuid not null references public.channels (id) on delete cascade,
  last_read_message_id uuid references public.messages (id) on delete set null,
  last_read_at         timestamptz not null default now(),
  primary key (user_id, channel_id)
);

create table if not exists public.custom_emojis (
  id          uuid primary key default gen_random_uuid(),
  guild_id    uuid not null references public.guilds (id) on delete cascade,
  name        text not null,
  url         text not null,
  animated    boolean not null default false,
  created_by  uuid references public.profiles (id) on delete set null,
  created_at  timestamptz not null default now(),
  unique (guild_id, name)
);

create table if not exists public.push_tokens (
  user_id     uuid not null references public.profiles (id) on delete cascade,
  token       text not null,
  platform    text not null default 'android',
  updated_at  timestamptz not null default now(),
  primary key (user_id, token)
);

-- ---------------------------------------------------------------- access checks

-- SECURITY DEFINER so policies do not re-enter guild_members' own RLS.
create or replace function public.is_guild_member(gid uuid, uid uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.guild_members m
    where m.guild_id = gid and m.user_id = uid
  );
$$;

create or replace function public.can_access_channel(cid uuid, uid uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.channels c
    where c.id = cid
      and (
        (c.guild_id is not null and public.is_guild_member(c.guild_id, uid))
        or (c.guild_id is null and exists (
              select 1 from public.dm_participants p
              where p.channel_id = c.id and p.user_id = uid
            ))
      )
  );
$$;

create or replace function public.is_guild_owner(gid uuid, uid uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.guilds g where g.id = gid and g.owner_id = uid);
$$;

-- ---------------------------------------------------------------- invites

create or replace function public.create_invite(gid uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
  -- shortcut: any member may invite (Discord gates this on a permission bit),
  -- tighten to a role check when role management lands.
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

  insert into public.invites (code, guild_id, created_by) values (new_code, gid, me);
  return new_code;
end;
$$;

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

  insert into public.guild_members (guild_id, user_id) values (inv.guild_id, me)
  on conflict (guild_id, user_id) do nothing;

  if found then
    update public.invites set uses = uses + 1 where code = inv.code;
  end if;

  return inv.guild_id;
end;
$$;

-- ---------------------------------------------------------------- RLS

alter table public.profiles        enable row level security;
alter table public.guilds          enable row level security;
alter table public.guild_members   enable row level security;
alter table public.roles           enable row level security;
alter table public.member_roles    enable row level security;
alter table public.channels        enable row level security;
alter table public.dm_participants enable row level security;
alter table public.messages        enable row level security;
alter table public.attachments     enable row level security;
alter table public.reactions       enable row level security;
alter table public.read_states     enable row level security;
alter table public.custom_emojis   enable row level security;
alter table public.invites         enable row level security;
alter table public.push_tokens     enable row level security;

-- profiles: authenticated users can read all (needed to render authors);
-- only the owner writes their row.
drop policy if exists profiles_read on public.profiles;
create policy profiles_read on public.profiles
  for select to authenticated using (true);

drop policy if exists profiles_write_own on public.profiles;
create policy profiles_write_own on public.profiles
  for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles
  for insert to authenticated with check (id = auth.uid());

-- guilds
drop policy if exists guilds_read_member on public.guilds;
create policy guilds_read_member on public.guilds
  for select to authenticated using (public.is_guild_member(id));

drop policy if exists guilds_insert_self on public.guilds;
create policy guilds_insert_self on public.guilds
  for insert to authenticated with check (owner_id = auth.uid());

drop policy if exists guilds_update_owner on public.guilds;
create policy guilds_update_owner on public.guilds
  for update to authenticated using (public.is_guild_owner(id)) with check (public.is_guild_owner(id));

drop policy if exists guilds_delete_owner on public.guilds;
create policy guilds_delete_owner on public.guilds
  for delete to authenticated using (public.is_guild_owner(id));

-- guild_members
drop policy if exists members_read_same_guild on public.guild_members;
create policy members_read_same_guild on public.guild_members
  for select to authenticated using (public.is_guild_member(guild_id));

-- No INSERT policy on purpose: direct self-join would bypass invites.
-- Membership is created by the guilds_add_owner trigger or join_guild().
drop policy if exists members_insert_self on public.guild_members;

drop policy if exists members_delete_self on public.guild_members;
create policy members_delete_self on public.guild_members
  for delete to authenticated using (
    user_id = auth.uid() or public.is_guild_owner(guild_id)
  );

-- roles / member_roles: read by members, write by owner
drop policy if exists roles_read_member on public.roles;
create policy roles_read_member on public.roles
  for select to authenticated using (public.is_guild_member(guild_id));

drop policy if exists roles_write_owner on public.roles;
create policy roles_write_owner on public.roles
  for all to authenticated using (public.is_guild_owner(guild_id)) with check (public.is_guild_owner(guild_id));

drop policy if exists member_roles_read_member on public.member_roles;
create policy member_roles_read_member on public.member_roles
  for select to authenticated using (public.is_guild_member(guild_id));

drop policy if exists member_roles_write_owner on public.member_roles;
create policy member_roles_write_owner on public.member_roles
  for all to authenticated using (public.is_guild_owner(guild_id)) with check (public.is_guild_owner(guild_id));

-- channels: visible if you can access them (guild member, or DM participant)
drop policy if exists channels_read on public.channels;
create policy channels_read on public.channels
  for select to authenticated using (public.can_access_channel(id));

drop policy if exists channels_insert_guild_owner on public.channels;
create policy channels_insert_guild_owner on public.channels
  for insert to authenticated with check (
    guild_id is not null and public.is_guild_owner(guild_id)
  );

-- DMs are created through find_or_create_dm() (SECURITY DEFINER), so no insert policy.

drop policy if exists channels_write_guild_owner on public.channels;
create policy channels_write_guild_owner on public.channels
  for update to authenticated using (public.is_guild_owner(guild_id)) with check (public.is_guild_owner(guild_id));

drop policy if exists channels_delete_guild_owner on public.channels;
create policy channels_delete_guild_owner on public.channels
  for delete to authenticated using (public.is_guild_owner(guild_id));

drop policy if exists dm_participants_read_own on public.dm_participants;
create policy dm_participants_read_own on public.dm_participants
  for select to authenticated using (public.can_access_channel(channel_id));

-- messages: readable/writable in channels you can access; author edits own.
drop policy if exists messages_read on public.messages;
create policy messages_read on public.messages
  for select to authenticated using (public.can_access_channel(channel_id));

drop policy if exists messages_insert on public.messages;
create policy messages_insert on public.messages
  for insert to authenticated with check (
    author_id = auth.uid() and public.can_access_channel(channel_id)
  );

drop policy if exists messages_update_own on public.messages;
create policy messages_update_own on public.messages
  for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());

drop policy if exists messages_delete_own on public.messages;
create policy messages_delete_own on public.messages
  for delete to authenticated using (author_id = auth.uid());

-- attachments / reactions / read_states ride on channel access
drop policy if exists attachments_read on public.attachments;
create policy attachments_read on public.attachments
  for select to authenticated using (
    exists (select 1 from public.messages m where m.id = message_id and public.can_access_channel(m.channel_id))
  );

drop policy if exists attachments_insert on public.attachments;
create policy attachments_insert on public.attachments
  for insert to authenticated with check (
    exists (
      select 1 from public.messages m
      where m.id = message_id and m.author_id = auth.uid()
    )
  );

drop policy if exists reactions_read on public.reactions;
create policy reactions_read on public.reactions
  for select to authenticated using (
    exists (select 1 from public.messages m where m.id = message_id and public.can_access_channel(m.channel_id))
  );

drop policy if exists reactions_write_own on public.reactions;
create policy reactions_write_own on public.reactions
  for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists read_states_own on public.read_states;
create policy read_states_own on public.read_states
  for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists custom_emojis_read on public.custom_emojis;
create policy custom_emojis_read on public.custom_emojis
  for select to authenticated using (public.is_guild_member(guild_id));

drop policy if exists custom_emojis_write_owner on public.custom_emojis;
create policy custom_emojis_write_owner on public.custom_emojis
  for all to authenticated using (public.is_guild_owner(guild_id)) with check (public.is_guild_owner(guild_id));

-- invites: members can list their guild's invites; writes go through RPCs only.
drop policy if exists invites_read_member on public.invites;
create policy invites_read_member on public.invites
  for select to authenticated using (public.is_guild_member(guild_id));

drop policy if exists push_tokens_own on public.push_tokens;
create policy push_tokens_own on public.push_tokens
  for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------- realtime

-- Realtime respects RLS, so only channel members receive inserts.
-- Wrapped so re-running the script does not error on "already member of publication".
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'messages'
  ) then
    alter publication supabase_realtime add table public.messages;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'reactions'
  ) then
    alter publication supabase_realtime add table public.reactions;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'channels'
  ) then
    alter publication supabase_realtime add table public.channels;
  end if;
end;
$$;
