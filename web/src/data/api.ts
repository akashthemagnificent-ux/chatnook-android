// The single data layer: every read/write goes through here (spec §8/§9).
// Bound to the EXISTING schema — no invented tables, no ad-hoc fetch in components.
import { supabase, isSupabaseConfigured } from '../lib/supabase'
import type {
  AuditEntry, BannedUser, Channel, ChannelNotificationLevel,
  CustomEmoji, DmSummary, Guild, Invite, Member, Message, Profile, UnreadState,
} from '../lib/types'

export { isSupabaseConfigured }

function db() {
  if (!supabase) throw new Error('Supabase is not configured')
  return supabase
}

// PostgREST needs an FK hint here: messages reaches profiles directly (author_id)
// and through reactions, so a bare `profiles` embed answers 300 Multiple Choices.
const MESSAGE_COLUMNS =
  'id,channel_id,author_id,content,type,reply_to_id,created_at,edited_at,deleted_at,' +
  'author:profiles!messages_author_id_fkey(id,username,display_name,avatar_url),' +
  'reply:messages(content,author_id),' +
  'reactions(emoji,user_id),' +
  'attachments(id,url,filename,content_type,width,height,size_bytes)'

const PROFILE_COLUMNS =
  'id,username,display_name,avatar_url,banner_url,accent_color,theme,' +
  'custom_status,badges,avatar_decoration_url,avatar_is_animated'

export async function currentUserId(): Promise<string> {
  const { data } = await db().auth.getUser()
  if (!data.user) throw new Error('Not signed in')
  return data.user.id
}

// ---------------------------------------------------------------- auth profile
export async function getProfile(userId: string): Promise<Profile | null> {
  const { data, error } = await db().from('profiles').select(PROFILE_COLUMNS).eq('id', userId).maybeSingle()
  if (error) throw error
  return (data as unknown as Profile) ?? null
}

export async function updateProfile(userId: string, patch: Partial<Profile>): Promise<void> {
  const { error } = await db().from('profiles').update(patch).eq('id', userId)
  if (error) throw error
}

// ---------------------------------------------------------------- guilds
export async function getMyGuilds(): Promise<Guild[]> {
  // members_read_same_guild lets a member read every row in their guilds, so
  // without this filter the rail lists each server once per member.
  const { data, error } = await db()
    .from('guild_members')
    .select('guild:guilds(id,name,icon_url,owner_id)')
    .eq('user_id', await currentUserId())
  if (error) throw error
  return (data ?? [])
    .map((row: { guild: Guild | Guild[] | null }) =>
      Array.isArray(row.guild) ? row.guild[0] : row.guild)
    .filter((g: Guild | undefined | null): g is Guild => Boolean(g))
}

export async function createGuild(name: string): Promise<string> {
  const { data, error } = await db().rpc('create_guild', { guild_name: name })
  if (error) throw error
  return data as string
}

export async function joinGuild(code: string): Promise<string> {
  const { data, error } = await db().rpc('join_guild', { invite_code: code })
  if (error) throw error
  return data as string
}

export async function updateGuild(guildId: string, patch: Partial<Pick<Guild, 'name' | 'icon_url'>>): Promise<void> {
  const { error } = await db().from('guilds').update(patch).eq('id', guildId)
  if (error) throw error
}

// RLS members_delete_self allows deleting your own row. Scope by user_id so an
// owner (who passes the policy for every row) can never wipe the whole roster.
export async function leaveGuild(guildId: string, userId: string): Promise<void> {
  const { error } = await db().from('guild_members').delete()
    .eq('guild_id', guildId).eq('user_id', userId)
  if (error) throw error
}

export async function getGuild(guildId: string): Promise<Guild | null> {
  const { data, error } = await db().from('guilds').select('id,name,icon_url,owner_id').eq('id', guildId).maybeSingle()
  if (error) throw error
  return (data as Guild) ?? null
}

export async function myPermissions(guildId: string): Promise<number> {
  const { data, error } = await db().rpc('my_permissions', { gid: guildId })
  if (error) throw error
  return Number(data ?? 0)
}

// ---------------------------------------------------------------- channels
export async function getChannels(guildId: string): Promise<Channel[]> {
  const { data, error } = await db()
    .from('channels')
    .select('id,guild_id,name,topic,type,position,parent_id')
    .eq('guild_id', guildId)
    .order('position', { ascending: true })
  if (error) throw error
  return (data ?? []) as Channel[]
}

export async function createChannel(guildId: string, name: string, topic?: string): Promise<Channel> {
  const { data: existing } = await db().from('channels').select('position').eq('guild_id', guildId)
  const nextPosition = (existing ?? []).reduce((max, r: { position: number }) => Math.max(max, r.position), -1) + 1
  const { data, error } = await db()
    .from('channels')
    .insert({ guild_id: guildId, name: name.trim(), type: 'text', topic: topic?.trim() || null, position: nextPosition })
    .select('id,guild_id,name,topic,type,position,parent_id')
    .single()
  if (error) throw error
  return data as Channel
}

export async function getChannel(channelId: string): Promise<Channel | null> {
  const { data, error } = await db()
    .from('channels').select('id,guild_id,name,topic,type,position,parent_id')
    .eq('id', channelId).maybeSingle()
  if (error) throw error
  return (data as Channel) ?? null
}

// messages.reply_to_id has no FK, so PostgREST answers the self-embed with an
// array (empty when the row is not a reply) instead of null.
function normalizeMessage(row: Message): Message {
  const reply = row.reply as unknown
  return { ...row, reply: Array.isArray(reply) ? (reply[0] as Message['reply']) ?? null : row.reply }
}

// ---------------------------------------------------------------- messages
export async function getMessages(channelId: string, limit = 50, before?: string): Promise<Message[]> {
  let query = db()
    .from('messages')
    .select(MESSAGE_COLUMNS)
    .eq('channel_id', channelId)
    .order('created_at', { ascending: false })
    .limit(limit)
  if (before) query = query.lt('created_at', before)
  const { data, error } = await query
  if (error) throw error
  return ((data ?? []) as unknown as Message[]).map(normalizeMessage).reverse()
}

export async function getMessage(id: string): Promise<Message | null> {
  const { data, error } = await db().from('messages').select(MESSAGE_COLUMNS).eq('id', id).maybeSingle()
  if (error) throw error
  return data ? normalizeMessage(data as unknown as Message) : null
}

export async function sendMessage(params: {
  channelId: string
  content: string
  replyToId?: string | null
  attachment?: AttachmentPayload | null
}): Promise<string> {
  const { data, error } = await db().rpc('send_message', {
    p_channel_id: params.channelId,
    p_content: params.content,
    p_reply_to_id: params.replyToId ?? null,
    p_attachment: params.attachment ?? null,
  })
  if (error) throw error
  return data as string
}

export type AttachmentPayload = {
  url: string
  filename: string
  content_type: string
  size_bytes: number
  width: number | null
  height: number | null
}

export async function editMessage(id: string, content: string): Promise<void> {
  const { error } = await db().from('messages').update({ content, edited_at: new Date().toISOString() }).eq('id', id)
  if (error) throw error
}

export async function deleteMessage(id: string): Promise<void> {
  const { error } = await db().from('messages').delete().eq('id', id)
  if (error) throw error
}

export async function toggleReaction(messageId: string, emoji: string, userId: string): Promise<void> {
  const { data: existing } = await db()
    .from('reactions').select('emoji')
    .eq('message_id', messageId).eq('user_id', userId).eq('emoji', emoji).maybeSingle()
  if (existing) {
    const { error } = await db().from('reactions').delete()
      .eq('message_id', messageId).eq('user_id', userId).eq('emoji', emoji)
    if (error) throw error
  } else {
    const { error } = await db().from('reactions').insert({ message_id: messageId, user_id: userId, emoji })
    if (error) throw error
  }
}

export async function uploadAttachment(file: File): Promise<AttachmentPayload> {
  const ext = file.name.split('.').pop()?.toLowerCase() || 'bin'
  // Storage RLS only accepts writes under the caller's own uid prefix.
  const path = `${await currentUserId()}/${crypto.randomUUID()}.${ext}`
  const { error } = await db().storage.from('attachments').upload(path, file, { contentType: file.type })
  if (error) throw error
  const { data } = db().storage.from('attachments').getPublicUrl(path)
  let width: number | null = null
  let height: number | null = null
  if (file.type.startsWith('image/')) {
    const dims = await imageDimensions(file)
    width = dims.width
    height = dims.height
  }
  return {
    url: data.publicUrl,
    filename: file.name,
    content_type: file.type,
    size_bytes: file.size,
    width,
    height,
  }
}

/** Upload an avatar/banner/emoji; the bucket policy also wants the uid prefix. */
export async function uploadCosmetic(
  bucket: 'avatars' | 'banners' | 'emojis',
  file: File,
): Promise<string> {
  const ext = file.name.split('.').pop()?.toLowerCase() || 'png'
  const path = `${await currentUserId()}/${Date.now()}.${ext}`
  const { error } = await db().storage.from(bucket).upload(path, file, { contentType: file.type, upsert: true })
  if (error) throw error
  return db().storage.from(bucket).getPublicUrl(path).data.publicUrl
}

function imageDimensions(file: File): Promise<{ width: number; height: number }> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      resolve({ width: img.naturalWidth, height: img.naturalHeight })
      URL.revokeObjectURL(url)
    }
    img.onerror = () => {
      resolve({ width: 0, height: 0 })
      URL.revokeObjectURL(url)
    }
    img.src = url
  })
}

// ---------------------------------------------------------------- read state
export async function markRead(channelId: string, messageId: string, userId: string): Promise<void> {
  const { error } = await db().from('read_states').upsert({
    user_id: userId,
    channel_id: channelId,
    last_read_message_id: messageId,
    last_read_at: new Date().toISOString(),
  }, { onConflict: 'user_id,channel_id' })
  if (error) throw error
}

export async function getUnread(guildId: string | null, mention: string): Promise<Record<string, UnreadState>> {
  const { data, error } = await db().rpc('channel_unread_counts', {
    p_guild_id: guildId,
    p_mention: mention,
  })
  if (error) throw error
  const out: Record<string, UnreadState> = {}
  for (const row of (data ?? []) as { channel_id: string; unread: number; mentions: number }[]) {
    out[row.channel_id] = { unread: Number(row.unread), mentions: Number(row.mentions) }
  }
  return out
}

// ---------------------------------------------------------------- DMs
export async function getDmSummaries(): Promise<DmSummary[]> {
  const { data, error } = await db().rpc('dm_summaries')
  if (error) throw error
  return (data ?? []) as DmSummary[]
}

export async function findOrCreateDm(otherUser: string): Promise<string> {
  const { data, error } = await db().rpc('find_or_create_dm', { other_user: otherUser })
  if (error) throw error
  return data as string
}

export async function searchProfiles(query: string): Promise<Profile[]> {
  const { data, error } = await db().from('profiles').select(PROFILE_COLUMNS)
    .or(`username.ilike.%${query}%,display_name.ilike.%${query}%`).limit(20)
  if (error) throw error
  return (data ?? []) as unknown as Profile[]
}

// ---------------------------------------------------------------- members / roles
export async function getMembers(guildId: string): Promise<Member[]> {
  const { data, error } = await db()
    .from('guild_members')
    .select(
      'user_id,nickname,joined_at,' +
      `profile:profiles(${PROFILE_COLUMNS}),` +
      'member_roles(role:roles(id,name,color,position))',
    )
    .eq('guild_id', guildId)
  if (error) throw error
  return ((data ?? []) as unknown as Record<string, unknown>[]).map((row) => {
    const profile = row.profile as Profile | Profile[] | null
    const mr = (row.member_roles ?? []) as { role: Role | Role[] | null }[]
    return {
      user_id: row.user_id as string,
      nickname: row.nickname as string | null,
      joined_at: row.joined_at as string,
      profile: (Array.isArray(profile) ? profile[0] : profile) ?? null,
      roles: mr.map((m) => (Array.isArray(m.role) ? m.role[0] : m.role)).filter(Boolean) as Role[],
    }
  })
}

type Role = {
  id: string; name: string; color: number | null; position: number | null
  permissions?: number; is_everyone?: boolean
}

// ---------------------------------------------------------------- invites
export async function createInvite(guildId: string): Promise<string> {
  const { data, error } = await db().rpc('create_invite', { gid: guildId })
  if (error) throw error
  return data as string
}

export async function getInvites(guildId: string): Promise<Invite[]> {
  const { data, error } = await db().from('invites')
    .select('code,uses,max_uses,created_at,expires_at')
    .eq('guild_id', guildId)
  if (error) throw error
  return (data ?? []) as Invite[]
}

export async function revokeInvite(code: string): Promise<void> {
  const { error } = await db().rpc('revoke_invite', { p_code: code })
  if (error) throw error
}

// ---------------------------------------------------------------- emojis
// ---------------------------------------------------------------- roles
export async function getRoles(guildId: string): Promise<Role[]> {
  const { data, error } = await db().from('roles')
    .select('id,name,color,position,permissions,is_everyone')
    .eq('guild_id', guildId).order('position')
  if (error) throw error
  return (data ?? []) as unknown as Role[]
}

export async function createRole(
  guildId: string, name: string, color: number | null, permissions: number,
): Promise<string> {
  const { data, error } = await db().rpc('create_role', {
    gid: guildId, p_name: name, p_color: color, p_permissions: permissions,
  })
  if (error) throw error
  return data as string
}

export async function updateRole(
  roleId: string, name: string, color: number | null, permissions: number,
): Promise<void> {
  const { error } = await db().rpc('update_role', {
    p_role_id: roleId, p_name: name, p_color: color, p_permissions: permissions,
  })
  if (error) throw error
}

export async function deleteRole(roleId: string): Promise<void> {
  const { error } = await db().rpc('delete_role', { p_role_id: roleId })
  if (error) throw error
}

export async function setMemberRoles(guildId: string, memberId: string, roleIds: string[]): Promise<void> {
  const { error } = await db().rpc('set_member_roles', { gid: guildId, member_id: memberId, role_ids: roleIds })
  if (error) throw error
}

export async function getCustomEmojis(guildId: string): Promise<CustomEmoji[]> {
  const { data, error } = await db().from('custom_emojis')
    .select('id,name,url,animated').eq('guild_id', guildId)
  if (error) throw error
  return (data ?? []) as CustomEmoji[]
}

// ---------------------------------------------------------------- moderation
export async function addCustomEmoji(
  guildId: string, name: string, url: string, animated: boolean,
): Promise<void> {
  const { error } = await db().from('custom_emojis')
    .insert({ guild_id: guildId, name, url, animated, created_by: await currentUserId() })
  if (error) throw error
}

export async function deleteCustomEmoji(id: string): Promise<void> {
  const { error } = await db().from('custom_emojis').delete().eq('id', id)
  if (error) throw error
}

export async function getBans(guildId: string): Promise<BannedUser[]> {
  const { data, error } = await db().from('guild_bans')
    .select('user_id,reason,created_at,profile:profiles!guild_bans_user_id_fkey(username,display_name,avatar_url)')
    .eq('guild_id', guildId)
  if (error) throw error
  return (data ?? []) as unknown as BannedUser[]
}

export async function getAuditLog(guildId: string): Promise<AuditEntry[]> {
  const { data, error } = await db().from('guild_audit_log')
    .select('id,action,detail,created_at,' +
      'actor:profiles!guild_audit_log_actor_id_fkey(username,display_name),' +
      'target:profiles!guild_audit_log_target_id_fkey(username,display_name)')
    .eq('guild_id', guildId).order('created_at', { ascending: false }).limit(50)
  if (error) throw error
  return (data ?? []) as unknown as AuditEntry[]
}

export async function kickMember(guildId: string, memberId: string): Promise<void> {
  const { error } = await db().rpc('kick_member', { gid: guildId, member: memberId })
  if (error) throw error
}

export async function banMember(guildId: string, memberId: string, reason: string | null): Promise<void> {
  const { error } = await db().rpc('ban_member', { gid: guildId, member: memberId, reason })
  if (error) throw error
}

export async function unbanMember(guildId: string, memberId: string): Promise<void> {
  const { error } = await db().rpc('unban_member', { gid: guildId, member: memberId })
  if (error) throw error
}

// ---------------------------------------------------------------- notification prefs
export async function getGuildMuted(guildId: string, userId: string): Promise<boolean> {
  const { data } = await db().from('guild_notification_settings')
    .select('muted').eq('guild_id', guildId).eq('user_id', userId).maybeSingle()
  return Boolean((data as { muted: boolean } | null)?.muted)
}

export async function setGuildMuted(guildId: string, userId: string, muted: boolean): Promise<void> {
  const { error } = await db().from('guild_notification_settings')
    .upsert({ user_id: userId, guild_id: guildId, muted }, { onConflict: 'user_id,guild_id' })
  if (error) throw error
}

export async function getChannelLevels(userId: string): Promise<Record<string, ChannelNotificationLevel>> {
  const { data } = await db().from('channel_notification_settings')
    .select('channel_id,level').eq('user_id', userId)
  const out: Record<string, ChannelNotificationLevel> = {}
  for (const row of (data ?? []) as { channel_id: string; level: ChannelNotificationLevel }[]) {
    out[row.channel_id] = row.level
  }
  return out
}

export async function setChannelLevel(channelId: string, userId: string, level: ChannelNotificationLevel): Promise<void> {
  const { error } = await db().from('channel_notification_settings')
    .upsert({ user_id: userId, channel_id: channelId, level }, { onConflict: 'user_id,channel_id' })
  if (error) throw error
}

// ---------------------------------------------------------------- stored theme
export type StoredTheme = { density?: 'comfortable' | 'compact'; accent?: string }

export async function saveStoredTheme(userId: string, patch: StoredTheme): Promise<void> {
  // Merge into the existing jsonb so other profile theme keys survive.
  const profile = await getProfile(userId)
  const merged = { ...(profile?.theme ?? {}), ...patch }
  await updateProfile(userId, { theme: merged })
}

export async function loadStoredTheme(userId: string): Promise<StoredTheme> {
  const profile = await getProfile(userId)
  return (profile?.theme ?? {}) as StoredTheme
}
