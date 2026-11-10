// Types mirror the existing Supabase schema (spec §9). No new tables or columns.

export type Profile = {
  id: string
  username: string
  display_name: string | null
  avatar_url: string | null
  banner_url: string | null
  accent_color: string | null
  theme: Record<string, unknown> | null
  custom_status: string | null
  badges: string[] | null
  avatar_decoration_url: string | null
  avatar_is_animated: boolean | null
}

export type Guild = {
  id: string
  name: string
  icon_url: string | null
  owner_id: string
}

export type ChannelType = 'text' | 'voice' | 'category' | 'dm' | 'group_dm'

export type Channel = {
  id: string
  guild_id: string | null
  name: string | null
  type: ChannelType
  topic: string | null
  position: number
  parent_id: string | null
}

export type Attachment = {
  id: string
  url: string
  filename: string | null
  content_type: string | null
  width: number | null
  height: number | null
  size_bytes: number | null
}

export type Reaction = {
  emoji: string
  user_id: string
}

export type Reply = {
  content: string | null
  author_id: string | null
}

export type Author = {
  id: string
  username: string
  display_name: string | null
  avatar_url: string | null
}

export type Message = {
  id: string
  channel_id: string
  author_id: string
  content: string | null
  type: 'default' | 'system' | 'reply'
  reply_to_id: string | null
  created_at: string
  edited_at: string | null
  deleted_at: string | null
  author: Author | null
  reply: Reply | null
  reactions: Reaction[]
  attachments: Attachment[]
  // Optimistic send state — client only, never sent to the server.
  pending?: 'sending' | 'failed'
}

export type DmSummary = {
  channel_id: string
  user_id: string
  name: string
  avatar_url: string | null
  last_message: string | null
  last_at: string | null
}

export type UnreadState = {
  unread: number
  mentions: number
}

export type Role = {
  id: string
  name: string
  color: number | null
  position: number | null
  permissions?: number
  is_everyone?: boolean
}

export type Member = {
  user_id: string
  nickname: string | null
  joined_at: string
  profile: Profile | null
  roles: Role[]
}

export type Invite = {
  code: string
  uses: number | null
  max_uses: number | null
  created_at: string
  expires_at: string | null
}

export type CustomEmoji = {
  id: string
  name: string
  url: string
  animated: boolean
}

export type BannedUser = {
  user_id: string
  reason: string | null
  created_at: string
  profile: Pick<Profile, 'username' | 'display_name' | 'avatar_url'> | null
}

export type AuditEntry = {
  id: number
  action: string
  detail: string | null
  created_at: string
  actor: Pick<Profile, 'username' | 'display_name'> | null
  target: Pick<Profile, 'username' | 'display_name'> | null
}

export type ChannelNotificationLevel = 'all' | 'mentions' | 'none'

export function displayName(profile: Pick<Profile, 'username' | 'display_name'> | null | undefined): string {
  if (!profile) return 'Unknown'
  return profile.display_name?.trim() || profile.username
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('') || '?'
}
