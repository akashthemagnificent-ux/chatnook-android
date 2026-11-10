-- Phase 8: enforce the permission bits the UI already gates on.
--
-- The channel menu, the role editor and the server settings screen are all shown
-- according to my_permissions(), but every write behind them was owner-only. A
-- moderator holding MANAGE_CHANNELS therefore saw a "new channel" action whose
-- insert RLS refused. This migration makes the server agree with the client.
--
-- Bit numbers mirror PermissionBits.kt: 2 = MANAGE_MESSAGES, 3 = MANAGE_CHANNELS,
-- 4 = MANAGE_ROLES, 11 = MANAGE_GUILD.

create or replace function public.has_permission(gid uuid, bit_index int)
returns boolean
language sql
stable
security definer
set search_path = public
as $$ select (public.my_permissions(gid) & (1::bigint << bit_index)) <> 0 $$;

revoke all on function public.has_permission(uuid, int) from public;
grant execute on function public.has_permission(uuid, int) to authenticated;

-- ---------------------------------------------------------------- channels

drop policy if exists channels_insert_guild_owner on public.channels;
drop policy if exists channels_insert_manage on public.channels;
create policy channels_insert_manage on public.channels
  for insert to authenticated with check (
    guild_id is not null and public.has_permission(guild_id, 3)
  );

drop policy if exists channels_write_guild_owner on public.channels;
drop policy if exists channels_write_manage on public.channels;
create policy channels_write_manage on public.channels
  for update to authenticated
  using (public.has_permission(guild_id, 3))
  with check (public.has_permission(guild_id, 3));

drop policy if exists channels_delete_guild_owner on public.channels;
drop policy if exists channels_delete_manage on public.channels;
create policy channels_delete_manage on public.channels
  for delete to authenticated using (public.has_permission(guild_id, 3));

-- ---------------------------------------------------------------- guild settings
-- Deleting a server stays with the owner; renaming and its settings do not.

drop policy if exists guilds_update_owner on public.guilds;
drop policy if exists guilds_update_manage on public.guilds;
create policy guilds_update_manage on public.guilds
  for update to authenticated
  using (public.has_permission(id, 11))
  with check (public.has_permission(id, 11));

-- ---------------------------------------------------------------- roles

drop policy if exists roles_write_owner on public.roles;
drop policy if exists roles_write_manage on public.roles;
create policy roles_write_manage on public.roles
  for all to authenticated
  using (public.has_permission(guild_id, 4))
  with check (public.has_permission(guild_id, 4));

drop policy if exists member_roles_write_owner on public.member_roles;
drop policy if exists member_roles_write_manage on public.member_roles;
create policy member_roles_write_manage on public.member_roles
  for all to authenticated
  using (public.has_permission(guild_id, 4))
  with check (public.has_permission(guild_id, 4));

-- ---------------------------------------------------------------- role RPCs
-- A holder of MANAGE_ROLES may manage roles, but may not hand out a permission
-- they do not hold themselves, and may not edit their own roles. Without both
-- guards the bit would be a one-step path to ADMINISTRATOR.

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
  if not public.has_permission(gid, 4) then raise exception 'not allowed'; end if;
  if coalesce(trim(p_name), '') = '' then raise exception 'role name required'; end if;
  if (coalesce(p_permissions, 0) & ~public.my_permissions(gid)) <> 0 then
    raise exception 'cannot grant permissions you do not have';
  end if;

  select coalesce(max(position), 0) + 1 into next_pos
    from public.roles where guild_id = gid;

  insert into public.roles (guild_id, name, color, position, permissions)
  values (gid, left(trim(p_name), 100), p_color, next_pos, coalesce(p_permissions, 0))
  returning id into rid;

  return rid;
end;
$$;

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
  wanted bigint;
begin
  select guild_id, is_everyone, permissions into target from public.roles where id = p_role_id;
  if not found then raise exception 'role not found'; end if;
  if not public.has_permission(target.guild_id, 4) then raise exception 'not allowed'; end if;

  wanted := coalesce(p_permissions, target.permissions);
  if (wanted & ~public.my_permissions(target.guild_id)) <> 0 then
    raise exception 'cannot grant permissions you do not have';
  end if;

  update public.roles
     set name        = case when is_everyone then name else left(trim(coalesce(p_name, name)), 100) end,
         color       = p_color,
         permissions = wanted
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
  if target.is_everyone then raise exception 'everyone role cannot be deleted'; end if;
  if not public.has_permission(target.guild_id, 4) then raise exception 'not allowed'; end if;

  delete from public.roles where id = p_role_id;
end;
$$;

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
declare
  me uuid := auth.uid();
begin
  if me is null then raise exception 'not authenticated'; end if;
  if not public.has_permission(gid, 4) then raise exception 'not allowed'; end if;
  if member_id = me and not public.is_guild_owner(gid, me) then
    raise exception 'cannot change your own roles';
  end if;
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
