-- Phase 4: people. One unread query for every channel the user can reach (guild and
-- DM), plus the DM list in a single round trip.

drop function if exists public.channel_unread_counts(uuid, text);

create or replace function public.channel_unread_counts(
  p_guild_id uuid default null,
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
  -- Covers guild channels the user belongs to and DMs they participate in.
  where public.can_access_channel(c.id)
    and (p_guild_id is null or c.guild_id = p_guild_id)
  group by c.id;
$$;

revoke all on function public.channel_unread_counts(uuid, text) from public;
grant execute on function public.channel_unread_counts(uuid, text) to authenticated;

-- One row per DM: who it is with, and the newest message. Ordering the embedded
-- messages through PostgREST is not expressible in the client, so do it here.
create or replace function public.dm_summaries()
returns table (
  channel_id   uuid,
  user_id      uuid,
  name         text,
  avatar_url   text,
  last_message text,
  last_at      timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
  select c.id,
         p.id,
         coalesce(nullif(p.display_name, ''), p.username),
         p.avatar_url,
         m.content,
         m.created_at
  from public.channels c
  join public.dm_participants me
    on me.channel_id = c.id and me.user_id = auth.uid()
  join public.dm_participants other
    on other.channel_id = c.id and other.user_id <> auth.uid()
  join public.profiles p on p.id = other.user_id
  left join lateral (
    select content, created_at
    from public.messages
    where channel_id = c.id
    order by created_at desc
    limit 1
  ) m on true
  where c.type = 'dm';
$$;

revoke all on function public.dm_summaries() from public;
grant execute on function public.dm_summaries() to authenticated;
