-- ChatNook schema, part 2: guild bootstrap, storage, realtime plumbing.
-- Apply after 0001_discord_parity.sql. Idempotent.
--
-- 1. create_guild() RPC: a guild insert followed by SELECT ... RETURNING would be
--    checked against guilds_read_member (is_guild_member), which is false until the
--    guilds_add_owner trigger has run. Doing both steps inside one SECURITY DEFINER
--    function sidesteps that ordering problem and also seeds the default channel.
-- 2. Storage buckets for avatars/banners/emoji, writable only under the caller's
--    own folder prefix.
-- 3. Realtime: every table the client watches joins the publication, and replica
--    identity is set to FULL so DELETE payloads carry the columns the client filters
--    or matches on (Realtime only sends the primary key otherwise).

-- ---------------------------------------------------------------- guild bootstrap

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

  -- Every server gets an open text channel, like Discord's #general.
  insert into public.channels (guild_id, name, type, position)
  values (gid, 'general', 'text', 0);

  return gid;
end;
$$;

-- ---------------------------------------------------------------- storage

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('avatars', 'avatars', true, 8388608,
   array['image/png', 'image/jpeg', 'image/gif', 'image/webp']),
  ('banners', 'banners', true, 8388608,
   array['image/png', 'image/jpeg', 'image/gif', 'image/webp']),
  ('emojis',  'emojis',  true, 524288,
   array['image/png', 'image/gif', 'image/webp'])
on conflict (id) do update
  set public             = excluded.public,
      file_size_limit    = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- Anyone may read cosmetics (they show up in every message header).
drop policy if exists chatnook_cosmetics_read on storage.objects;
create policy chatnook_cosmetics_read on storage.objects
  for select to public using (
    bucket_id in ('avatars', 'banners', 'emojis')
  );

-- Writes are confined to <bucket>/<auth.uid()>/<file>.
drop policy if exists chatnook_cosmetics_insert on storage.objects;
create policy chatnook_cosmetics_insert on storage.objects
  for insert to authenticated with check (
    bucket_id in ('avatars', 'banners', 'emojis')
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists chatnook_cosmetics_update on storage.objects;
create policy chatnook_cosmetics_update on storage.objects
  for update to authenticated using (
    bucket_id in ('avatars', 'banners', 'emojis')
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists chatnook_cosmetics_delete on storage.objects;
create policy chatnook_cosmetics_delete on storage.objects
  for delete to authenticated using (
    bucket_id in ('avatars', 'banners', 'emojis')
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- ---------------------------------------------------------------- realtime

alter table public.messages       replica identity full;
alter table public.channels       replica identity full;
alter table public.reactions      replica identity full;
alter table public.guild_members  replica identity full;
alter table public.profiles       replica identity full;
alter table public.custom_emojis  replica identity full;

do $$
declare
  t text;
begin
  foreach t in array array[
    'guilds', 'guild_members', 'channels', 'messages', 'reactions', 'profiles', 'custom_emojis'
  ] loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end;
$$;
