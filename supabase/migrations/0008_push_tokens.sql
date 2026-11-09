-- Phase 6: push notification tokens.
--
-- One row per device. The send function reads these with the service role (which
-- bypasses RLS); a user may only see and change their own rows.

create table if not exists public.push_tokens (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  token      text not null,
  platform   text not null default 'android',
  updated_at timestamptz not null default now(),
  primary key (user_id, token)
);

create index if not exists push_tokens_user_idx on public.push_tokens (user_id);

alter table public.push_tokens enable row level security;

drop policy if exists push_tokens_own on public.push_tokens;
create policy push_tokens_own on public.push_tokens
  for all to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());
