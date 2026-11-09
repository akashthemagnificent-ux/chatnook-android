-- Phase 5b: roles and the permission resolver's data.
--
-- Bits are shared with PermissionBits.kt and must never be renumbered:
--   0 view channel, 1 send messages, 2 manage messages, 3 manage channels,
--   4 manage roles, 5 create invite, 6 kick members, 7 administrator,
--   8 mention everyone, 9 attach files, 10 add reactions, 11 manage guild.

-- Only one @everyone role per guild.
create unique index if not exists roles_one_everyone_idx
  on public.roles (guild_id) where is_everyone;

-- Guilds created before this migration need theirs.
insert into public.roles (guild_id, name, color, position, permissions, is_everyone)
select g.id, '@everyone', null, 0,
       (1::bigint << 0) | (1::bigint << 1) | (1::bigint << 5)
       | (1::bigint << 8) | (1::bigint << 9) | (1::bigint << 10),
       true
from public.guilds g
where not exists (
  select 1 from public.roles r where r.guild_id = g.id and r.is_everyone
);

-- New guilds get one alongside #general.
create or replace function public.create_guild(guild_name text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
  gid uuid;
begin
  if me is null then raise exception 'not authenticated'; end if;
  if coalesce(trim(guild_name), '') = '' then raise exception 'guild name required'; end if;

  insert into public.guilds (name, owner_id)
  values (left(trim(guild_name), 100), me)
  returning id into gid;

  insert into public.channels (guild_id, name, type, position)
  values (gid, 'general', 'text', 0);

  insert into public.roles (guild_id, name, position, permissions, is_everyone)
  values (gid, '@everyone', 0,
          (1::bigint << 0) | (1::bigint << 1) | (1::bigint << 5)
          | (1::bigint << 8) | (1::bigint << 9) | (1::bigint << 10),
          true);

  return gid;
end;
$$;

-- The mask returned for owners and administrators; mirrors PermissionBits.ALL.
create or replace function public.all_permissions()
returns bigint
language sql
immutable
as $$ select (1::bigint << 12) - 1 $$;

-- What the caller may do in a guild. The UI uses this to hide buttons, while RLS
-- remains the real gate. Non-members get 0 so nothing leaks.
create or replace function public.my_permissions(gid uuid)
returns bigint
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
  bits bigint;
begin
  if me is null then raise exception 'not authenticated'; end if;
  if public.is_guild_owner(gid, me) then
    return public.all_permissions();
  end if;
  if not public.is_guild_member(gid, me) then
    return 0;
  end if;

  select coalesce(bit_or(r.permissions), 0) into bits
    from public.member_roles mr
    join public.roles r on r.id = mr.role_id
   where mr.guild_id = gid and mr.user_id = me;

  select bits | coalesce(max(r.permissions), 0) into bits
    from public.roles r
   where r.guild_id = gid and r.is_everyone;

  if (bits & (1::bigint << 7)) <> 0 then
    return public.all_permissions();
  end if;
  return bits;
end;
$$;

create or replace function public.create_role(
  gid           uuid,
  p_name        text,
  p_color       integer default null,
  p_permissions bigint default 0
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
  rid uuid;
  next_pos integer;
begin
  if me is null then raise exception 'not authenticated'; end if;
  if not public.is_guild_owner(gid, me) then raise exception 'not allowed'; end if;
  if coalesce(trim(p_name), '') = '' then raise exception 'role name required'; end if;

  select coalesce(max(position), 0) + 1 into next_pos
    from public.roles where guild_id = gid;

  insert into public.roles (guild_id, name, color, position, permissions)
  values (gid, left(trim(p_name), 100), p_color, next_pos, coalesce(p_permissions, 0))
  returning id into rid;

  return rid;
end;
$$;

-- @everyone keeps its name: it is the base every member holds, not an assignable role.
create or replace function public.update_role(
  p_role_id     uuid,
  p_name        text,
  p_color       integer default null,
  p_permissions bigint default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  target record;
begin
  select guild_id, is_everyone into target from public.roles where id = p_role_id;
  if not found then raise exception 'role not found'; end if;
  if not public.is_guild_owner(target.guild_id) then raise exception 'not allowed'; end if;

  update public.roles
     set name        = case when is_everyone then name else left(trim(coalesce(p_name, name)), 100) end,
         color       = p_color,
         permissions = coalesce(p_permissions, permissions)
   where id = p_role_id;
end;
$$;

create or replace function public.delete_role(p_role_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  target record;
begin
  select guild_id, is_everyone into target from public.roles where id = p_role_id;
  if not found then raise exception 'role not found'; end if;
  if not public.is_guild_owner(target.guild_id) then raise exception 'not allowed'; end if;
  if target.is_everyone then raise exception 'the everyone role cannot be deleted'; end if;
  delete from public.roles where id = p_role_id;
end;
$$;

-- Replaces the member's roles in one call so the UI cannot leave a half-applied set.
create or replace function public.set_member_roles(
  gid       uuid,
  member_id uuid,
  role_ids  uuid[]
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_guild_owner(gid) then raise exception 'not allowed'; end if;
  if not exists (
    select 1 from public.guild_members m where m.guild_id = gid and m.user_id = member_id
  ) then
    raise exception 'not a member';
  end if;

  delete from public.member_roles where guild_id = gid and user_id = member_id;

  insert into public.member_roles (guild_id, user_id, role_id)
  select gid, member_id, r.id
    from public.roles r
   where r.id = any(coalesce(role_ids, '{}'))
     and r.guild_id = gid
     and not r.is_everyone;
end;
$$;

grant execute on function public.all_permissions() to authenticated;
grant execute on function public.my_permissions(uuid) to authenticated;
grant execute on function public.create_role(uuid, text, integer, bigint) to authenticated;
grant execute on function public.update_role(uuid, text, integer, bigint) to authenticated;
grant execute on function public.delete_role(uuid) to authenticated;
grant execute on function public.set_member_roles(uuid, uuid, uuid[]) to authenticated;

-- Role edits must reach open member sheets and role lists.
alter table public.roles replica identity full;
alter table public.member_roles replica identity full;

-- Role edits and assignments must reach open screens without a reload.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'roles'
  ) then
    alter publication supabase_realtime add table public.roles;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'member_roles'
  ) then
    alter publication supabase_realtime add table public.member_roles;
  end if;
end $$;
