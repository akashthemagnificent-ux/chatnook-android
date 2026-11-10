-- Mutual servers for the profile card.
--
-- SECURITY DEFINER because a member cannot read another member's guild_members rows:
-- the policy only exposes the caller's own memberships.

create or replace function public.mutual_guilds(other uuid)
returns table (id uuid, name text, icon_url text)
language sql
security definer
set search_path = public
stable
as $$
  select g.id, g.name, g.icon_url
    from public.guild_members mine
    join public.guild_members theirs on theirs.guild_id = mine.guild_id
    join public.guilds g on g.id = mine.guild_id
   where mine.user_id = auth.uid()
     and theirs.user_id = other
   order by g.name;
$$;

revoke all on function public.mutual_guilds(uuid) from public;
grant execute on function public.mutual_guilds(uuid) to authenticated;
