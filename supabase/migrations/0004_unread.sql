-- Phase 3: unread badges.
--
-- Counting client-side would mean downloading every unread message body, which grows
-- without bound in a busy channel. One grouped query returns a badge per channel instead.

create or replace function public.channel_unread_counts(
  p_guild_id uuid,
  p_mention  text default null
)
returns table (channel_id uuid, unread bigint, mentions bigint)
language sql
stable
security definer
set search_path = public
as $$
  select c.id,
         count(m.id) filter (where m.author_id <> auth.uid()) as unread,
         count(m.id) filter (
           where m.author_id <> auth.uid()
             and p_mention is not null
             -- usernames are [a-z0-9._-], so '.' is the only regex metacharacter to escape.
             -- '.' also ends a sentence, so it may terminate a mention even though it is
             -- legal inside a username.
             and m.content ~* ('(^|[^a-z0-9._-])@' || replace(p_mention, '.', '\.')
                               || '($|[^a-z0-9_-])')
         ) as mentions
  from public.channels c
  left join public.read_states r
         on r.channel_id = c.id and r.user_id = auth.uid()
  left join public.messages m
         on m.channel_id = c.id
        and m.created_at > coalesce(r.last_read_at, '-infinity'::timestamptz)
  where c.guild_id = p_guild_id
    and public.is_guild_member(p_guild_id)
  group by c.id;
$$;

revoke all on function public.channel_unread_counts(uuid, text) from public;
grant execute on function public.channel_unread_counts(uuid, text) to authenticated;

-- Reading a channel on one device clears its badge on the others.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'read_states'
  ) then
    alter publication supabase_realtime add table public.read_states;
  end if;
end $$;
