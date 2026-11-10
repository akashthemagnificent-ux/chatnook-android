// DM / conversation list (spec §3.7): search, unread badges, active state,
// and a "start a DM" dialog over the existing profiles table.
import { useMemo, useState } from 'react'
import { MessageSquarePlus, Search } from 'lucide-react'
import { useChat, previewPeople } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { UserAvatar } from '../../components/ui/avatar'
import { Badge, Button, Input, Label } from '../../components/ui/base'
import { ScrollArea } from '../../components/ui/controls'
import { Dialog, DialogContent, DialogTitle, DialogDescription, DialogClose } from '../../components/ui/overlay'
import { EmptyState, ListSkeleton } from '../../components/States'
import { relativeTime } from '../../lib/format'
import { displayName } from '../../lib/types'
import * as api from '../../data/api'
import { t } from '../../lib/i18n'
import { cn } from '../../lib/utils'
import { toast } from '../../components/ui/toast'

export function DmList({ onNavigate }: { onNavigate?: () => void }) {
  const { dms, unread, activeChannelId, openDm, configured, shellLoading } = useChat()
  const setActiveChannelId = useAppStore((s) => s.setActiveChannelId)
  const [query, setQuery] = useState('')
  const [startOpen, setStartOpen] = useState(false)

  const filtered = useMemo(
    () => dms.filter((d) => d.name.toLowerCase().includes(query.toLowerCase())),
    [dms, query],
  )

  const pick = (channelId: string) => {
    setActiveChannelId(channelId)
    onNavigate?.()
  }

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-col bg-surface">
      <div className="flex h-14 items-center justify-between gap-2 border-b border-line/10 bg-gradient-to-b from-elevated/25 to-transparent px-3">
        <h2 className="truncate px-1 text-md font-semibold text-ink-primary">{t('dms.title')}</h2>
        <Button variant="ghost" size="icon-sm" aria-label={t('sidebar.newDm')} onClick={() => setStartOpen(true)}>
          <MessageSquarePlus />
        </Button>
      </div>

      <div className="px-3 py-2">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
          <Input value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder={t('sidebar.searchPlaceholder')} className="h-9 pl-9 text-sm" aria-label={t('sidebar.searchPlaceholder')} />
        </div>
      </div>

      <ScrollArea className="min-h-0 flex-1">
        {shellLoading ? <ListSkeleton /> : filtered.length === 0 ? (
          <EmptyState icon={<MessageSquarePlus className="size-7" />} title={t('dms.noConversations')} body={t('dms.noConversationsBody')}
            action={<Button variant="outline" size="sm" onClick={() => setStartOpen(true)}>{t('dms.startDm')}</Button>} />
        ) : (
          <div className="flex flex-col gap-0.5 p-2">
            {filtered.map((d) => {
              const state = unread[d.channel_id]
              const active = d.channel_id === activeChannelId
              return (
                <button key={d.channel_id} onClick={() => pick(d.channel_id)}
                  className={cn(
                    'group relative flex w-full items-center gap-3 overflow-hidden rounded-lg px-2.5 py-2 text-left transition-colors duration-150',
                    active ? 'bg-gradient-to-r from-brand/12 to-transparent' : 'hover:bg-hover',
                  )}>
                  {active && (
                    <span aria-hidden className="absolute inset-y-2 left-0 w-0.5 rounded-r-full bg-brand" />
                  )}
                  <UserAvatar name={d.name} src={d.avatar_url} size={36} />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center justify-between gap-2">
                      <span className={cn('truncate text-sm', state?.unread ? 'font-semibold text-ink-primary' : 'font-medium text-ink-secondary')}>{d.name}</span>
                      {d.last_at && <span className="shrink-0 text-xs text-ink-muted">{relativeTime(d.last_at)}</span>}
                    </span>
                    <span className="mt-0.5 flex items-center justify-between gap-2">
                      <span className="truncate text-sm text-ink-muted">{d.last_message ?? t('chat.noMessagesBody')}</span>
                      {state?.unread && !active ? <Badge variant="accent" size="sm">{state.unread}</Badge> : null}
                    </span>
                  </span>
                </button>
              )
            })}
          </div>
        )}
      </ScrollArea>

      <StartDmDialog open={startOpen} onOpenChange={setStartOpen} configured={configured} onPick={(userId) => { void openDm(userId); onNavigate?.() }} />
    </div>
  )
}

function StartDmDialog({ open, onOpenChange, configured, onPick }: {
  open: boolean
  onOpenChange: (v: boolean) => void
  configured: boolean
  onPick: (userId: string) => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<{ id: string; name: string; username: string; avatar_url: string | null }[]>([])
  const [searching, setSearching] = useState(false)

  const run = async (value: string) => {
    setQuery(value)
    if (!value.trim()) { setResults([]); return }
    if (!configured) {
      setResults(Object.values(previewPeople)
        .filter((p) => p.id !== 'preview')
        .filter((p) => `${p.username} ${p.display_name}`.toLowerCase().includes(value.toLowerCase()))
        .map((p) => ({ id: p.id, name: displayName(p), username: p.username, avatar_url: p.avatar_url })))
      return
    }
    setSearching(true)
    try {
      const profiles = await api.searchProfiles(value)
      setResults(profiles.map((p) => ({ id: p.id, name: displayName(p), username: p.username, avatar_url: p.avatar_url })))
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setSearching(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogTitle>{t('sidebar.findPeople')}</DialogTitle>
        <DialogDescription>{t('sidebar.findPeoplePlaceholder')}</DialogDescription>
        <div className="mt-4 flex flex-col gap-3">
          <div>
            <Label htmlFor="people-search" className="sr-only">{t('sidebar.findPeople')}</Label>
            <Input id="people-search" value={query} onChange={(e) => void run(e.target.value)}
              placeholder={t('sidebar.findPeoplePlaceholder')} autoFocus />
          </div>
          <ScrollArea className="max-h-72">
            {results.length === 0 ? (
              <p className="py-8 text-center text-sm text-ink-muted">
                {searching ? t('common.loading') : query.trim() ? t('sidebar.noPeople') : t('sidebar.findPeoplePlaceholder')}
              </p>
            ) : (
              <div className="flex flex-col gap-1">
                {results.map((r) => (
                  <DialogClose asChild key={r.id}>
                    <button onClick={() => onPick(r.id)}
                      className="flex items-center gap-3 rounded-md px-2 py-2 text-left hover:bg-hover">
                      <UserAvatar name={r.name} src={r.avatar_url} size={36} />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-ink-primary">{r.name}</span>
                        <span className="block truncate text-xs text-ink-muted">@{r.username}</span>
                      </span>
                    </button>
                  </DialogClose>
                ))}
              </div>
            )}
          </ScrollArea>
        </div>
      </DialogContent>
    </Dialog>
  )
}
