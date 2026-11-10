// Channel sidebar (spec §3.4). With no active server it shows the DM list; with a
// server it shows collapsible categories, unread cues, and a persistent user panel.
import { useId, useState } from 'react'
import { motion } from 'framer-motion'
import {
  ChevronDown, Hash, Headphones, Mic, MicOff, Plus, Settings, UserPlus,
  LogOut, Shield, Bell, Volume2,
} from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useSession } from '../../store/session'
import { DmList } from '../dm/DmList'
import { UserAvatar } from '../../components/ui/avatar'
import { Badge, Button, Input, Label } from '../../components/ui/base'
import { Collapsible, CollapsibleTrigger, CollapsibleContent, ScrollArea } from '../../components/ui/controls'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuSeparator, DropdownMenuLabel, IconTooltip,
} from '../../components/ui/overlay'
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '../../components/ui/overlay'
import { EmptyState } from '../../components/States'
import { toast } from '../../components/ui/toast'
import * as api from '../../data/api'
import { displayName } from '../../lib/types'
import { cn } from '../../lib/utils'
import { t } from '../../lib/i18n'
import type { Channel } from '../../lib/types'

const MANAGE_CHANNELS = 1 << 3
const KICK_MEMBERS = 1 << 6
const ADMINISTRATOR = 1 << 7

export function ChannelSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { activeGuild } = useChat()
  if (!activeGuild) return <DmList onNavigate={onNavigate} />
  return <ServerSidebar onNavigate={onNavigate} />
}

function ServerSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const {
    activeGuild, channels, activeChannelId, selectChannel, unread, permissions,
    createChannel, configured, moderation,
  } = useChat()
  const { profile, userId } = useSession()
  const setOverlay = useAppStore((s) => s.setOverlay)
  const openSettings = useAppStore((s) => s.openSettings)
  const requestConfirm = useAppStore((s) => s.requestConfirm)
  const [muted, setMuted] = useState(false)
  const [deafened, setDeafened] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [name, setName] = useState('')
  const [topic, setTopic] = useState('')
  const [creating, setCreating] = useState(false)

  const guild = activeGuild!
  const activeLayoutId = useId()
  const isOwner = guild.owner_id === userId
  const canManageChannels = (permissions & MANAGE_CHANNELS) !== 0 || (permissions & ADMINISTRATOR) !== 0 || isOwner
  const canModerate = (permissions & KICK_MEMBERS) !== 0 || (permissions & ADMINISTRATOR) !== 0 || isOwner

  const categories = channels.filter((c) => c.type === 'category')
  const inCategory = (parent: string | null) => channels.filter(
    (c) => c.type !== 'category' && c.parent_id === parent,
  )
  const textChannels = channels.filter((c) => c.type === 'text')
  const voiceChannels = channels.filter((c) => c.type === 'voice')
  const categorizedIds = new Set(categories.flatMap((cat) => inCategory(cat.id).map((c) => c.id)))
  const looseText = textChannels.filter((c) => !categorizedIds.has(c.id) && !c.parent_id)
  const looseVoice = voiceChannels.filter((c) => !categorizedIds.has(c.id) && !c.parent_id)

  const pick = (id: string) => { selectChannel(id); onNavigate?.() }

  const submitCreate = async () => {
    if (!name.trim()) return
    setCreating(true)
    try {
      await createChannel(name.trim(), topic.trim())
      toast.success(t('toast.channelCreated'))
      setCreateOpen(false)
      setName(''); setTopic('')
      onNavigate?.()
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setCreating(false)
    }
  }

  const leave = () => requestConfirm({
    title: t('guild.leaveConfirmTitle'),
    body: t('guild.leaveConfirmBody'),
    confirmLabel: t('guild.leave'),
    destructive: true,
    onConfirm: async () => {
      try {
        if (configured && userId) await api.leaveGuild(guild.id, userId)
        toast.success(t('auth.signOut'))
        useAppStore.getState().setActiveGuildId(null)
        useAppStore.getState().setActiveChannelId(null)
        moderation.refreshMembers()
      } catch { toast.error(t('toast.somethingWrong')) }
    },
  })

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-col bg-surface">
      {/* Server header + menu */}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button className="flex h-14 shrink-0 items-center justify-between gap-2 border-b border-line/10 bg-gradient-to-b from-elevated/25 to-transparent px-4 text-left transition-colors hover:bg-hover/60">
            <span className="truncate text-md font-semibold text-ink-primary">{guild.name}</span>
            <ChevronDown className="size-4 shrink-0 text-ink-secondary" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-56">
          <DropdownMenuLabel>{guild.name}</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => setOverlay('invite')}><UserPlus /> {t('sidebar.invitePeople')}</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => setOverlay('guildSettings')}><Settings /> {t('sidebar.serverSettings')}</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => openSettings('notifications')}><Bell /> {t('sidebar.notificationSettings')}</DropdownMenuItem>
          {canModerate && <DropdownMenuItem onSelect={() => setOverlay('moderation')}><Shield /> {t('sidebar.moderation')}</DropdownMenuItem>}
          {canManageChannels && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => setCreateOpen(true)}><Plus /> {t('sidebar.createChannel')}</DropdownMenuItem>
            </>
          )}
          {!isOwner && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem destructive onSelect={leave}><LogOut /> {t('guild.leave')}</DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <ScrollArea className="min-h-0 flex-1">
        <div className="flex flex-col gap-1 p-2">
          {channels.length === 0 ? (
            <EmptyState icon={<Hash className="size-7" />} title={t('sidebar.noChannels')} body={t('sidebar.noChannelsBody')}
              action={canManageChannels ? <Button variant="outline" size="sm" onClick={() => setCreateOpen(true)}>{t('sidebar.createChannel')}</Button> : undefined} />
          ) : (
            <>
              {categories.map((cat) => (
                <CategoryGroup key={cat.id} name={cat.name ?? t('sidebar.uncategorized')} channels={inCategory(cat.id)}
                  activeChannelId={activeChannelId} unread={unread} onPick={pick} activeLayoutId={activeLayoutId} />
              ))}
              <CategoryGroup name={t('sidebar.channels')} channels={looseText}
                activeChannelId={activeChannelId} unread={unread} onPick={pick} activeLayoutId={activeLayoutId} />
              <CategoryGroup name={t('sidebar.voice')} channels={looseVoice} voice
                activeChannelId={activeChannelId} unread={unread} onPick={pick} activeLayoutId={activeLayoutId} />
            </>
          )}
          {canManageChannels && (
            <button onClick={() => setCreateOpen(true)}
              className="mt-1 flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-ink-muted hover:bg-hover hover:text-ink-primary">
              <Plus className="size-4" /> {t('sidebar.addChannel')}
            </button>
          )}
        </div>
      </ScrollArea>

      {/* Persistent user panel */}
      <div className="flex h-14 shrink-0 items-center gap-1 border-t border-line/10 bg-gradient-to-t from-base/70 to-transparent px-2">
        <button onClick={() => openSettings('account')} className="group flex min-w-0 flex-1 items-center gap-1.5 rounded-md p-1 transition-colors hover:bg-hover">
          <UserAvatar name={displayName(profile)} src={profile?.avatar_url} size={28} presence="online" />
          <span className="min-w-0 text-left">
            <span className="block truncate text-sm font-semibold text-ink-primary">{displayName(profile)}</span>
            <span className="block truncate text-xs text-ink-muted transition-colors group-hover:text-ink-secondary">@{profile?.username ?? 'you'}</span>
          </span>
        </button>
        <IconTooltip label={t('sidebar.mic')} side="top">
          <Button variant="ghost" size="icon-sm" aria-label={t('sidebar.mic')} onClick={() => setMuted((v) => !v)}>
            {muted ? <MicOff className="text-danger" /> : <Mic />}
          </Button>
        </IconTooltip>
        <IconTooltip label={t('sidebar.deafen')} side="top">
          <Button variant="ghost" size="icon-sm" aria-label={t('sidebar.deafen')} onClick={() => setDeafened((v) => !v)}>
            <Headphones className={deafened ? 'text-danger' : ''} />
          </Button>
        </IconTooltip>
        <IconTooltip label={t('common.settings')} side="top">
          <Button variant="ghost" size="icon-sm" aria-label={t('common.settings')} onClick={() => openSettings('account')}><Settings /></Button>
        </IconTooltip>
      </div>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-md">
          <DialogTitle>{t('sidebar.createChannelTitle')}</DialogTitle>
          <DialogDescription>{t('sidebar.noChannelsBody')}</DialogDescription>
          <form onSubmit={(e) => { e.preventDefault(); void submitCreate() }} className="mt-4 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="channel-name">{t('sidebar.createChannelName')}</Label>
              <div className="relative">
                <Hash className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
                <Input id="channel-name" value={name} onChange={(e) => setName(e.target.value)} autoFocus
                  placeholder={t('sidebar.createChannelNamePlaceholder')} className="pl-9" />
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="channel-topic">{t('sidebar.createChannelTopic')}</Label>
              <Input id="channel-topic" value={topic} onChange={(e) => setTopic(e.target.value)}
                placeholder={t('sidebar.createChannelTopicPlaceholder')} />
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={() => setCreateOpen(false)}>{t('common.cancel')}</Button>
              <Button type="submit" variant="primary" disabled={creating || !name.trim()}>
                {creating ? t('common.loading') : t('sidebar.createChannel')}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function CategoryGroup({ name, channels, activeChannelId, unread, onPick, voice = false, activeLayoutId }: {
  name: string
  channels: Channel[]
  activeChannelId: string | null
  unread: Record<string, { unread: number; mentions: number }>
  onPick: (id: string) => void
  voice?: boolean
  activeLayoutId: string
}) {
  const [open, setOpen] = useState(true)
  if (channels.length === 0) return null
  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="group flex w-full items-center gap-1 rounded-md px-1.5 py-1.5 text-xs font-semibold uppercase tracking-wide text-ink-muted transition-colors hover:text-ink-primary">
        <ChevronDown className={cn('size-3.5 transition-transform duration-200', !open && '-rotate-90')} />
        <span className="truncate">{name}</span>
        <Plus className="ml-auto size-3.5 opacity-0 transition-opacity group-hover:opacity-100" />
      </CollapsibleTrigger>
      <CollapsibleContent className="flex flex-col gap-0.5">
        {channels.map((c) => {
          const state = unread[c.id]
          const active = c.id === activeChannelId
          const hasUnread = (state?.unread ?? 0) > 0
          return (
            <button key={c.id} onClick={() => !voice && onPick(c.id)} disabled={voice}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'group relative flex items-center gap-2 rounded-md px-2 py-1.5 text-left transition-colors duration-150',
                voice ? 'cursor-default opacity-70' : 'hover:bg-hover',
              )}>
              {active && (
                <motion.span
                  layoutId={activeLayoutId}
                  transition={{ type: 'spring', stiffness: 480, damping: 40 }}
                  className="absolute inset-0 rounded-md bg-gradient-to-r from-brand/15 via-brand/8 to-transparent shadow-[inset_0_0_0_1px_rgb(var(--brand)/0.12)]"
                />
              )}
              {active && <span aria-hidden className="absolute left-0 top-1/2 h-4 w-0.5 -translate-y-1/2 rounded-r-full bg-brand shadow-[0_0_8px_rgb(var(--brand)/0.8)]" />}
              {voice
                ? <Volume2 className={cn('relative size-[18px] shrink-0', active ? 'text-brand' : 'text-ink-muted')} />
                : <Hash className={cn('relative size-[18px] shrink-0', active ? 'text-brand' : 'text-ink-muted')} />}
              <span className={cn('relative min-w-0 flex-1 truncate text-sm', hasUnread && !active ? 'font-semibold text-ink-primary' : 'text-ink-secondary', active && 'font-medium text-ink-primary')}>
                {c.name ?? t('common.notFound')}
              </span>
              {!active && state?.mentions ? <Badge variant="accent" size="sm" className="relative animate-pop-in">{state.mentions}</Badge>
                : hasUnread && !active ? <span className="relative size-2 animate-pop-in rounded-full bg-ink-primary shadow-[0_0_8px_rgb(var(--text-primary)/0.5)]" /> : null}
            </button>
          )
        })}
      </CollapsibleContent>
    </Collapsible>
  )
}
