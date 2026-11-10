-- Phase 6: who wants to be notified.
--
-- Two small tables instead of columns on guild_members: that table has no UPDATE policy
-- on purpose (members are only added by the invite RPC), and adding one would let a
-- member rewrite their own row, including guild_id.

create table if not exists public.guild_notification_settings (
  user_id  uuid not null references public.profiles (id) on delete cascade,
  guild_id uuid not null references public.guilds (id) on delete cascade,
  muted    boolean not null default false,
  primary key (user_id, guild_id)
);

create table if not exists public.channel_notification_settings (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  channel_id uuid not null references public.channels (id) on delete cascade,
  level      text not null check (level in ('all', 'mentions', 'none')),
  primary key (user_id, channel_id)
);

alter table public.guild_notification_settings enable row level security;
alter table public.channel_notification_settings enable row level security;

drop policy if exists guild_notifications_own on public.guild_notification_settings;
create policy guild_notifications_own on public.guild_notification_settings
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists channel_notifications_own on public.channel_notification_settings;
create policy channel_notifications_own on public.channel_notification_settings
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Real-time so a second device follows the toggle without a restart.
alter table public.guild_notification_settings replica identity full;
alter table public.channel_notification_settings replica identity full;

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
     where pubname = 'supabase_realtime'
       and tablename = 'guild_notification_settings'
  ) then
    alter publication supabase_realtime add table public.guild_notification_settings;
  end if;
  if not exists (
    select 1 from pg_publication_tables
     where pubname = 'supabase_realtime'
       and tablename = 'channel_notification_settings'
  ) then
    alter publication supabase_realtime add table public.channel_notification_settings;
  end if;
end $$;
