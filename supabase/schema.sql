-- ChatNook schema (Supabase / Postgres)
-- Matches the columns used by SupabaseChatRepository.
-- Run in the Supabase SQL editor of your project.

create table if not exists public.messages (
    message_id   uuid primary key default gen_random_uuid(),
    channel_id   text        not null,
    sender_id    text        not null,
    content      text        not null,
    message_type text        not null default 'text',
    created_at   timestamptz not null default now()
);

create index if not exists messages_channel_created_idx
    on public.messages (channel_id, created_at);

alter table public.messages enable row level security;

-- Prototype policy: anyone holding the anon key (i.e. the friends group) can read/write.
-- Tighten to auth.uid() = sender_id once profiles/auth land.
drop policy if exists "prototype read messages" on public.messages;
create policy "prototype read messages"
    on public.messages for select
    to anon, authenticated
    using (true);

drop policy if exists "prototype write messages" on public.messages;
create policy "prototype write messages"
    on public.messages for insert
    to anon, authenticated
    with check (true);
