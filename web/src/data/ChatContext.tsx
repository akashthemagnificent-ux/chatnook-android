// Central data provider: binds the shell to the EXISTING Supabase schema and keeps it
// live via Realtime (spec §9). One place for optimistic UI, presence and typing.
// In a design-only build (no credentials) it serves clearly-marked preview content.
import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState,
  type ReactNode,
} from 'react'
import type { RealtimeChannel } from '@supabase/supabase-js'
import { supabase } from '../lib/supabase'
import { isSupabaseConfigured } from '../lib/supabase'
import type {
  AttachmentPayload,
} from './api'
import * as api from './api'
import {
  previewChannels, previewDms, previewGuilds, previewMembers, previewMessages,
  previewPeople, PREVIEW_SELF_ID,
} from './preview'
import type { Channel, DmSummary, Guild, Member, Message, UnreadState } from '../lib/types'
import { useAppStore } from '../store/app'
import { useSession } from '../store/session'
import type { Presence } from '../components/ui/avatar'
import type { ConnectionStatus } from '../components/ConnectionBanner'

const PAGE_SIZE = 50
const MAX_MESSAGE_LENGTH = 2000

type ChatContextValue = {
  configured: boolean
  shellLoading: boolean
  activeGuildId: string | null
  activeChannelId: string | null
  guilds: Guild[]
  channels: Channel[]
  dms: DmSummary[]
  members: Member[]
  unread: Record<string, UnreadState>
  guildUnread: Record<string, number>
  permissions: number
  activeGuild: Guild | null
  activeChannel: Channel | null
  activeDm: DmSummary | null
  messages: Message[]
  messagesLoading: boolean
  messagesError: string | null
  hasMore: boolean
  presence: Record<string, Presence>
  typing: string[]
  connection: ConnectionStatus
  selectGuild: (id: string | null) => void
  selectChannel: (id: string) => void
  openDm: (userId: string) => Promise<void>
  send: (content: string, replyToId?: string | null, attachment?: AttachmentPayload | null) => Promise<void>
  notifyTyping: () => void
  retryMessage: (id: string) => Promise<void>
  toggleReaction: (messageId: string, emoji: string) => Promise<void>
  editMessage: (id: string, content: string) => Promise<void>
  deleteMessage: (id: string) => Promise<void>
  loadOlder: () => Promise<void>
  reloadMessages: () => void
  createGuild: (name: string) => Promise<string>
  joinGuild: (code: string) => Promise<string>
  createChannel: (name: string, topic?: string) => Promise<void>
  refreshGuilds: () => void
  refreshUnread: () => void
  moderation: ModerationApi
}

export type ModerationApi = {
  kick: (guildId: string, memberId: string) => Promise<void>
  ban: (guildId: string, memberId: string, reason: string | null) => Promise<void>
  unban: (guildId: string, memberId: string) => Promise<void>
  refreshMembers: () => void
}

const ChatContext = createContext<ChatContextValue | null>(null)

export function ChatProvider({ children }: { children: ReactNode }) {
  const configured = isSupabaseConfigured
  const userId = useSession((s) => s.userId)
  const profile = useSession((s) => s.profile)
  const activeGuildId = useAppStore((s) => s.activeGuildId)
  const activeChannelId = useAppStore((s) => s.activeChannelId)
  const setActiveGuildId = useAppStore((s) => s.setActiveGuildId)
  const setActiveChannelId = useAppStore((s) => s.setActiveChannelId)

  const [shellLoading, setShellLoading] = useState(true)
  const [guilds, setGuilds] = useState<Guild[]>([])
  const [channels, setChannels] = useState<Channel[]>([])
  const [dms, setDms] = useState<DmSummary[]>([])
  const [members, setMembers] = useState<Member[]>([])
  const [unread, setUnread] = useState<Record<string, UnreadState>>({})
  const [channelGuild, setChannelGuild] = useState<Record<string, string>>({})
  const [permissions, setPermissions] = useState(0)
  const [messages, setMessages] = useState<Message[]>([])
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [messagesError, setMessagesError] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [presence, setPresence] = useState<Record<string, Presence>>({})
  const [typing, setTyping] = useState<string[]>([])
  const [connection, setConnection] = useState<ConnectionStatus>('connecting')
  const [memberTick, setMemberTick] = useState(0)

  const channelSubRef = useRef<RealtimeChannel | null>(null)
  const globalSubRef = useRef<RealtimeChannel | null>(null)
  const presenceSubRef = useRef<RealtimeChannel | null>(null)

  // In preview mode the current user is 'preview'.
  const myId = userId ?? 'preview'
  const myUsername = profile?.username ?? 'you'

  const activeGuild = useMemo(
    () => guilds.find((g) => g.id === activeGuildId) ?? null,
    [guilds, activeGuildId],
  )
  const activeChannel = useMemo(() => {
    if (activeChannelId) return channels.find((c) => c.id === activeChannelId) ?? null
    return null
  }, [channels, activeChannelId])
  const activeDm = useMemo(
    () => dms.find((d) => d.channel_id === activeChannelId) ?? null,
    [dms, activeChannelId],
  )
  const guildUnread = useMemo(() => {
    const out: Record<string, number> = {}
    for (const [channelId, state] of Object.entries(unread)) {
      const gid = channelGuild[channelId]
      if (gid && state.unread > 0) out[gid] = (out[gid] ?? 0) + state.unread
    }
    return out
  }, [unread, channelGuild])

  // ---------------------------------------------------------------- boot
  const loadGuilds = useCallback(async (): Promise<Guild[]> => {
    if (!configured) { setGuilds(previewGuilds); return previewGuilds }
    try { const g = await api.getMyGuilds(); setGuilds(g); return g } catch { setGuilds([]); return [] }
  }, [configured])

  const loadDms = useCallback(async () => {
    if (!configured) { setDms(previewDms); return }
    try { setDms(await api.getDmSummaries()) } catch { setDms([]) }
  }, [configured])

  const loadUnread = useCallback(async () => {
    if (!configured) {
      setUnread({ 'c-general': { unread: 3, mentions: 1 }, 'c-media': { unread: 2, mentions: 0 }, 'dm-1': { unread: 1, mentions: 0 } })
      return
    }
    // p_guild_id = null covers every channel the user can access (all servers + DMs),
    // so the rail and DM list can show badges for servers that are not open.
    try { setUnread(await api.getUnread(null, myUsername)) } catch { /* keep last */ }
  }, [configured, myUsername])

  useEffect(() => {
    let alive = true
    ;(async () => {
      const [loadedGuilds] = await Promise.all([loadGuilds(), loadDms()])
      if (alive) { setShellLoading(false); setConnection('online') }
      // Map every channel to its guild so the rail can attribute unread counts to
      // servers that are not currently open (the unread RPC is keyed by channel).
      let map: Record<string, string> = {}
      if (!configured) {
        Object.entries(previewChannels).forEach(([gid, list]) => list.forEach((c) => { map[c.id] = gid }))
      } else {
        const lists = await Promise.all(loadedGuilds.map((g) => api.getChannels(g.id).catch(() => [] as Channel[])))
        if (!alive) return
        lists.forEach((list, i) => list.forEach((c) => { map[c.id] = loadedGuilds[i].id }))
      }
      if (alive) setChannelGuild(map)
    })()
    return () => { alive = false }
  }, [loadGuilds, loadDms, configured])

  // ---------------------------------------------------------------- global realtime (guilds, membership, profiles, read-state)
  useEffect(() => {
    if (!configured || !supabase) return
    setConnection('connecting')
    const channel = supabase.channel('chatnook-global')
    channel
      .on('postgres_changes', { event: '*', schema: 'public', table: 'guilds' }, () => { void loadGuilds() })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'guild_members' }, () => { void loadGuilds(); setMemberTick((t) => t + 1) })
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'profiles' }, () => { setMemberTick((t) => t + 1) })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'read_states' }, () => { void loadUnread() })
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') setConnection('online')
        else if (status === 'CHANNEL_ERROR' || status === 'TIMED_OUT') setConnection('reconnecting')
        else if (status === 'CLOSED') setConnection('offline')
      })
    globalSubRef.current = channel
    return () => { void supabase?.removeChannel(channel); globalSubRef.current = null }
  }, [configured, loadGuilds, loadUnread])

  // Online/offline presence + typing broadcast for the active guild.
  useEffect(() => {
    if (!configured || !supabase || !activeGuildId || !userId) return
    const room = supabase.channel(`presence-${activeGuildId}`, { config: { presence: { key: userId }, broadcast: { self: false } } })
    room
      .on('presence', { event: 'sync' }, () => {
        const state = room.presenceState<{ online_at: string }>()
        const next: Record<string, Presence> = {}
        Object.keys(state).forEach((id) => { next[id] = 'online' })
        setPresence(next)
      })
      .on('broadcast', { event: 'typing' }, ({ payload }) => {
        const name = (payload as { name?: string }).name
        if (!name) return
        setTyping((prev) => (prev.includes(name) ? prev : [...prev, name]))
        window.setTimeout(() => setTyping((prev) => prev.filter((n) => n !== name)), 3000)
      })
      .subscribe(async (status) => {
        if (status === 'SUBSCRIBED') await room.track({ online_at: new Date().toISOString() })
      })
    presenceSubRef.current = room
    return () => { void supabase?.removeChannel(room); presenceSubRef.current = null }
  }, [configured, activeGuildId, userId])

  // Broadcast "typing" (debounced by caller) — exposed through a ref-based helper.
  const broadcastTyping = useCallback((name: string) => {
    const room = presenceSubRef.current
    if (room && configured) void room.send({ type: 'broadcast', event: 'typing', payload: { name } })
  }, [configured])

  // ---------------------------------------------------------------- per-guild data
  useEffect(() => {
    if (!activeGuildId) { setChannels([]); setMembers([]); setPermissions(0); return }
    if (!configured) {
      setChannels(previewChannels[activeGuildId] ?? [])
      setMembers(activeGuildId === 'g1' ? previewMembers : previewMembers.slice(0, 3))
      setPermissions(previewGuilds.find((g) => g.id === activeGuildId)?.owner_id === PREVIEW_SELF_ID ? 4095 : 32)
      setPresence({ u2: 'online', u3: 'online', u4: 'idle', u5: 'offline' })
      return
    }
    let alive = true
    ;(async () => {
      const [chans, perms] = await Promise.all([
        api.getChannels(activeGuildId).catch(() => [] as Channel[]),
        api.myPermissions(activeGuildId).catch(() => 0),
      ])
      if (alive) { setChannels(chans); setPermissions(perms) }
    })()
    return () => { alive = false }
  }, [activeGuildId, configured])

  useEffect(() => {
    if (!activeGuildId) return
    if (!configured) return
    let alive = true
    api.getMembers(activeGuildId).then((m) => { if (alive) setMembers(m) }).catch(() => {})
    return () => { alive = false }
  }, [activeGuildId, configured, memberTick])

  useEffect(() => { void loadUnread() }, [loadUnread])

  // ---------------------------------------------------------------- messages for active channel
  const loadMessages = useCallback(async (channelId: string) => {
    setMessagesLoading(true)
    setMessagesError(null)
    if (!configured) {
      setMessages(previewMessages[channelId] ?? [])
      setHasMore(false)
      setMessagesLoading(false)
      return
    }
    try {
      const rows = await api.getMessages(channelId, PAGE_SIZE)
      setMessages(rows)
      setHasMore(rows.length === PAGE_SIZE)
    } catch {
      setMessagesError('Could not load messages.')
    } finally {
      setMessagesLoading(false)
    }
  }, [configured])

  useEffect(() => {
    if (!activeChannelId) { setMessages([]); return }
    void loadMessages(activeChannelId)
  }, [activeChannelId, loadMessages])

  // Realtime INSERT/UPDATE/DELETE for the open channel + reactions.
  useEffect(() => {
    if (!configured || !supabase || !activeChannelId) return
    const channel = supabase.channel(`messages-${activeChannelId}`)
    channel
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages', filter: `channel_id=eq.${activeChannelId}` }, async (payload) => {
        const id = (payload.new as { id: string }).id
        // A known row still needs the server's copy while it is optimistic: the
        // server owns created_at and may normalise the text.
        const known = messagesRef.current.find((m) => m.id === id)
        if (known && !known.pending) return
        const full = await api.getMessage(id).catch(() => null)
        if (!full) return
        setMessages((prev) => prev.some((m) => m.id === id)
          ? prev.map((m) => m.id === id ? { ...full, pending: undefined } : m)
          : [...prev, full])
      })
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'messages', filter: `channel_id=eq.${activeChannelId}` }, (payload) => {
        const row = payload.new as { id: string; content: string | null; edited_at: string | null; deleted_at: string | null }
        setMessages((prev) => prev.map((m) => m.id === row.id
          ? { ...m, content: row.content, edited_at: row.edited_at, deleted_at: row.deleted_at }
          : m))
      })
      .on('postgres_changes', { event: 'DELETE', schema: 'public', table: 'messages' }, (payload) => {
        const id = (payload.old as { id: string }).id
        setMessages((prev) => prev.filter((m) => m.id !== id))
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'reactions' }, async (payload) => {
        const rec = (payload.new ?? payload.old) as { message_id?: string } | null
        const messageId = rec?.message_id
        if (!messageId || !messagesRef.current.some((m) => m.id === messageId)) return
        const full = await api.getMessage(messageId).catch(() => null)
        if (full) setMessages((prev) => prev.map((m) => m.id === messageId ? { ...m, reactions: full.reactions } : m))
      })
      .subscribe()
    channelSubRef.current = channel
    return () => { void supabase?.removeChannel(channel); channelSubRef.current = null }
  }, [configured, activeChannelId])

  // Keep a ref of messages so realtime callbacks read current state without re-subscribing.
  const messagesRef = useRef<Message[]>([])
  useEffect(() => { messagesRef.current = messages }, [messages])

  // Mark read when the newest message changes (spec §9.2).
  const lastMessageId = messages.length ? messages[messages.length - 1].id : null
  useEffect(() => {
    if (!configured || !activeChannelId || !lastMessageId || !userId) return
    void api.markRead(activeChannelId, lastMessageId, userId).catch(() => {})
  }, [configured, activeChannelId, lastMessageId, userId])

  // ---------------------------------------------------------------- actions
  const broadcastTypingRef = useRef(broadcastTyping)
  useEffect(() => { broadcastTypingRef.current = broadcastTyping }, [broadcastTyping])

  const send = useCallback(async (content: string, replyToId?: string | null, attachment?: AttachmentPayload | null) => {
    if (!activeChannelId) return
    const text = content.slice(0, MAX_MESSAGE_LENGTH)
    if (!text.trim() && !attachment) return
    broadcastTypingRef.current(myUsername)
    const tempId = crypto.randomUUID()
    const optimistic: Message = {
      id: tempId, channel_id: activeChannelId, author_id: myId,
      content: text, type: 'default', reply_to_id: replyToId ?? null,
      created_at: new Date().toISOString(), edited_at: null, deleted_at: null,
      author: profile ? { id: profile.id, username: profile.username, display_name: profile.display_name, avatar_url: profile.avatar_url } : null,
      reply: null, reactions: [],
      attachments: attachment ? [{
        id: tempId, url: attachment.url, filename: attachment.filename,
        content_type: attachment.content_type, width: attachment.width, height: attachment.height,
        size_bytes: attachment.size_bytes,
      }] : [],
      pending: 'sending',
    }
    setMessages((prev) => [...prev, optimistic])

    // The server owns created_at and may normalise the text, so swap in its row.
    const reconcile = async (realId: string) => {
      const full = configured ? await api.getMessage(realId).catch(() => null) : null
      setMessages((prev) => prev.map((m) => m.id === tempId
        ? { ...(full ?? m), id: realId, pending: undefined }
        : m))
    }
    const fail = () => setMessages((prev) => prev.map((m) => m.id === tempId ? { ...m, pending: 'failed' } : m))

    if (!configured) {
      // Preview: resolve locally so the optimistic state is visible for a beat.
      window.setTimeout(() => reconcile(tempId), 450)
      return
    }
    try {
      // The RPC accepts a client-supplied message id so the realtime INSERT can
      // reconcile without a duplicate flash.
      const { error } = await supabase!.rpc('send_message', {
        p_channel_id: activeChannelId,
        p_content: text,
        p_message_id: tempId,
        p_reply_to_id: replyToId ?? null,
        p_attachment: attachment ?? null,
      })
      if (error) throw error
      reconcile(tempId)
    } catch {
      fail()
    }
  }, [activeChannelId, myId, myUsername, profile, configured])

  const retryMessage = useCallback(async (id: string) => {
    const m = messagesRef.current.find((x) => x.id === id)
    if (!m) return
    setMessages((prev) => prev.filter((x) => x.id !== id))
    await send(m.content ?? '', m.reply_to_id, null)
  }, [send])

  const toggleReaction = useCallback(async (messageId: string, emoji: string) => {
    // Optimistic toggle.
    setMessages((prev) => prev.map((m) => {
      if (m.id !== messageId) return m
      const mine = m.reactions.some((r) => r.emoji === emoji && r.user_id === myId)
      return {
        ...m,
        reactions: mine
          ? m.reactions.filter((r) => !(r.emoji === emoji && r.user_id === myId))
          : [...m.reactions, { emoji, user_id: myId }],
      }
    }))
    if (!configured) return
    try { await api.toggleReaction(messageId, emoji, myId) }
    catch { void loadMessages(messageId ? activeChannelId ?? '' : '') }
  }, [myId, configured, loadMessages, activeChannelId])

  const editMessage = useCallback(async (id: string, content: string) => {
    const text = content.slice(0, MAX_MESSAGE_LENGTH)
    setMessages((prev) => prev.map((m) => m.id === id ? { ...m, content: text, edited_at: new Date().toISOString() } : m))
    if (!configured) return
    try { await api.editMessage(id, text) } catch { void loadMessages(activeChannelId ?? '') }
  }, [configured, loadMessages, activeChannelId])

  const deleteMessage = useCallback(async (id: string) => {
    setMessages((prev) => prev.filter((m) => m.id !== id))
    if (!configured) return
    try { await api.deleteMessage(id) } catch { void loadMessages(activeChannelId ?? '') }
  }, [configured, loadMessages, activeChannelId])

  const loadOlder = useCallback(async () => {
    if (!configured || !activeChannelId || !hasMore || messages.length === 0) return
    const oldest = messages[0]
    const older = await api.getMessages(activeChannelId, PAGE_SIZE, oldest.created_at).catch(() => [] as Message[])
    setMessages((prev) => {
      const ids = new Set(prev.map((m) => m.id))
      return [...older.filter((m) => !ids.has(m.id)), ...prev]
    })
    setHasMore(older.length === PAGE_SIZE)
  }, [configured, activeChannelId, hasMore, messages])

  const selectGuild = useCallback((id: string | null) => {
    setActiveGuildId(id)
    setActiveChannelId(null)
    if (configured && id) {
      api.getChannels(id).then((chans) => {
        setChannels(chans)
        const first = chans.find((c) => c.type === 'text')
        if (first) setActiveChannelId(first.id)
      }).catch(() => {})
    } else if (!configured && id) {
      const first = (previewChannels[id] ?? []).find((c) => c.type === 'text')
      if (first) setActiveChannelId(first.id)
    }
  }, [setActiveGuildId, setActiveChannelId, configured])

  const selectChannel = useCallback((id: string) => setActiveChannelId(id), [setActiveChannelId])

  const openDm = useCallback(async (otherUserId: string) => {
    if (!configured) {
      const dm = previewDms.find((d) => d.user_id === otherUserId)
      if (dm) { setActiveGuildId(null); setActiveChannelId(dm.channel_id) }
      return
    }
    const channelId = await api.findOrCreateDm(otherUserId)
    setActiveGuildId(null)
    setActiveChannelId(channelId)
    void loadDms()
  }, [configured, setActiveGuildId, setActiveChannelId, loadDms])

  const createGuild = useCallback(async (name: string) => {
    if (!configured) {
      const id = `g${Date.now()}`
      const g: Guild = { id, name, icon_url: null, owner_id: myId }
      setGuilds((prev) => [...prev, g])
      return id
    }
    const id = await api.createGuild(name)
    await loadGuilds()
    return id
  }, [configured, myId, loadGuilds])

  const joinGuild = useCallback(async (code: string) => {
    if (!configured) { await loadGuilds(); return previewGuilds[0].id }
    const id = await api.joinGuild(code)
    await loadGuilds()
    return id
  }, [configured, loadGuilds])

  const createChannel = useCallback(async (name: string, topic?: string) => {
    if (!configured || !activeGuildId) return
    const created = await api.createChannel(activeGuildId, name, topic)
    setChannels((prev) => [...prev, created])
    setActiveChannelId(created.id)
  }, [configured, activeGuildId, setActiveChannelId])

  const moderation = useMemo<ModerationApi>(() => ({
    kick: async (guildId, memberId) => { if (configured) await api.kickMember(guildId, memberId); setMemberTick((t) => t + 1) },
    ban: async (guildId, memberId, reason) => { if (configured) await api.banMember(guildId, memberId, reason); setMemberTick((t) => t + 1) },
    unban: async (guildId, memberId) => { if (configured) await api.unbanMember(guildId, memberId); setMemberTick((t) => t + 1) },
    refreshMembers: () => setMemberTick((t) => t + 1),
  }), [configured])

  const value: ChatContextValue = {
    configured, shellLoading, activeGuildId, activeChannelId, guilds, channels, dms, members, unread, guildUnread, permissions,
    activeGuild, activeChannel, activeDm, messages, messagesLoading, messagesError,
    hasMore, presence, typing, connection,
    selectGuild, selectChannel, openDm, send, retryMessage, toggleReaction,
    notifyTyping: () => broadcastTypingRef.current(myUsername),
    editMessage, deleteMessage, loadOlder, createGuild, joinGuild, createChannel,
    refreshGuilds: () => { void loadGuilds() },
    reloadMessages: () => { if (activeChannelId) void loadMessages(activeChannelId) },
    refreshUnread: () => void loadUnread(), moderation,
  }

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

export function useChat() {
  const ctx = useContext(ChatContext)
  if (!ctx) throw new Error('useChat must be used inside ChatProvider')
  return ctx
}

export function useBroadcastTyping() {
  // The provider broadcasts typing on send; composer can call this on keystrokes.
  const ctx = useContext(ChatContext)
  return ctx?.configured ?? false
}

export { previewPeople }
