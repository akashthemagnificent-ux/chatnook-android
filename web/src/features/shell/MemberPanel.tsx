// Member list (spec §3.8): online/offline grouping, status dots, role color,
// click-through profile popout. Virtualized with @tanstack/react-virtual.
import { useRef, useState } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { Shield, Users } from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useSession } from '../../store/session'
import { UserAvatar, type Presence } from '../../components/ui/avatar'
import { Badge } from '../../components/ui/base'
import { Dialog, DialogContent, DialogTitle } from '../../components/ui/overlay'
import { EmptyState } from '../../components/States'
import { UserPopoutContent } from '../../components/UserPopout'
import { toast } from '../../components/ui/toast'
import { displayName } from '../../lib/types'
import { roleColor } from '../../lib/format'
import { t } from '../../lib/i18n'
import type { Member } from '../../lib/types'

const KICK_MEMBERS = 1 << 6
const ADMINISTRATOR = 1 << 7

type Item = { kind: 'header'; label: string; count: number } | { kind: 'member'; member: Member }

export function MemberPanel({ onNavigate }: { onNavigate?: () => void }) {
  const { members, presence, activeGuild, permissions, moderation, openDm } = useChat()
  const userId = useSession((s) => s.userId)
  const requestConfirm = useAppStore((s) => s.requestConfirm)
  const [selected, setSelected] = useState<Member | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  // You are always online to yourself, even before presence syncs.
  const isOnline = (id: string) => id === userId || presence[id] === 'online' || presence[id] === 'idle' || presence[id] === 'dnd'
  const online = members.filter((m) => isOnline(m.user_id))
  const offline = members.filter((m) => !isOnline(m.user_id))

  const items: Item[] = []
  if (online.length) { items.push({ kind: 'header', label: t('members.online'), count: online.length }); online.forEach((m) => items.push({ kind: 'member', member: m })) }
  if (offline.length) { items.push({ kind: 'header', label: t('members.offline'), count: offline.length }); offline.forEach((m) => items.push({ kind: 'member', member: m })) }

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: (i) => items[i]?.kind === 'header' ? 32 : 48,
    overscan: 6,
  })

  const isOwner = Boolean(activeGuild && activeGuild.owner_id === userId)
  const canModerate = (permissions & KICK_MEMBERS) !== 0 || (permissions & ADMINISTRATOR) !== 0 || isOwner

  const kick = (m: Member) => requestConfirm({
    title: t('guild.kickConfirmTitle'),
    body: t('guild.kickConfirmBody'),
    confirmLabel: t('members.kick'), destructive: true,
    onConfirm: async () => { try { await moderation.kick(activeGuild!.id, m.user_id); toast.success(t('toast.memberKicked')) } catch { toast.error(t('toast.somethingWrong')) } },
  })
  const ban = (m: Member) => requestConfirm({
    title: t('guild.banConfirmTitle'),
    body: t('guild.banConfirmBody'),
    confirmLabel: t('members.ban'), destructive: true,
    onConfirm: async () => { try { await moderation.ban(activeGuild!.id, m.user_id, null); toast.success(t('toast.memberBanned')) } catch { toast.error(t('toast.somethingWrong')) } },
  })

  return (
    <aside className="flex h-full min-h-0 w-full flex-col bg-surface" aria-label={t('members.title')}>
      <div className="flex h-14 shrink-0 items-center gap-2 border-b border-line/10 bg-gradient-to-b from-elevated/25 to-transparent px-4">
        <Users className="size-4 text-brand" />
        <h2 className="text-sm font-semibold text-ink-primary">{t('members.title')}</h2>
        <span className="ml-auto rounded-full border border-line/10 bg-elevated/60 px-2 py-0.5 text-xs tabular-nums text-ink-secondary">{members.length}</span>
      </div>

      {members.length === 0 ? (
        <EmptyState icon={<Users className="size-7" />} title={t('members.noMembers')} />
      ) : (
        <div ref={scrollRef} className="min-h-0 flex-1 overflow-y-auto scrollbar-thin">
          <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
            {virtualizer.getVirtualItems().map((row) => {
              const item = items[row.index]
              return (
                <div key={row.key} ref={virtualizer.measureElement} data-index={row.index}
                  style={{ position: 'absolute', top: 0, left: 0, width: '100%', transform: `translateY(${row.start}px)` }}>
                  {item.kind === 'header' ? (
                    <div className="flex h-8 items-center gap-2 px-4 pt-1 text-xs font-semibold uppercase tracking-wide text-ink-muted">
                      {item.label} <span className="text-ink-muted/70">— {item.count}</span>
                    </div>
                  ) : (
                    <MemberRow member={item.member} presence={item.member.user_id === userId ? 'online' : presence[item.member.user_id]} onClick={() => setSelected(item.member)} />
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      <Dialog open={Boolean(selected)} onOpenChange={(v) => !v && setSelected(null)}>
        <DialogContent hideClose className="max-w-sm overflow-hidden p-0">
          <DialogTitle className="sr-only">{t('members.title')}</DialogTitle>
          {selected && (
            <UserPopoutContent
              profile={selected.profile}
              roles={selected.roles}
              canModerate={canModerate}
              onMessage={() => { void openDm(selected.user_id); setSelected(null); onNavigate?.() }}
              onKick={() => { setSelected(null); kick(selected) }}
              onBan={() => { setSelected(null); ban(selected) }}
            />
          )}
        </DialogContent>
      </Dialog>
    </aside>
  )
}

function MemberRow({ member, presence, onClick }: { member: Member; presence?: Presence; onClick: () => void }) {
  const name = displayName(member.profile)
  const topRole = member.roles.reduce<{ color: number | null; position: number | null } | null>((best, r) => {
    if (!best || (r.position ?? 0) >= (best.position ?? 0)) return { color: r.color, position: r.position }
    return best
  }, null)
  const color = roleColor(topRole?.color)
  return (
    <button onClick={onClick} className="group relative flex h-12 w-full items-center gap-3 px-3 text-left transition-colors hover:bg-hover/70">
      <span aria-hidden className="absolute left-0 top-1/2 h-5 w-0.5 -translate-y-1/2 rounded-r-full bg-brand opacity-0 transition-opacity duration-200 group-hover:opacity-100" />
      <UserAvatar name={name} src={member.profile?.avatar_url} size={32} presence={presence ?? 'offline'} />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium" style={{ color: color ?? 'rgb(var(--text-primary))' }}>{name}</span>
        <span className="block truncate text-xs text-ink-muted">{member.profile?.custom_status || `@${member.profile?.username ?? 'unknown'}`}</span>
      </span>
      {topRole && topRole.color && (
        <Badge variant="outline" size="sm" className="shrink-0 gap-1">
          <Shield className="size-3" /> {member.roles[0]?.name}
        </Badge>
      )}
    </button>
  )
}
