// Chat view (spec §3.5): header, message list, composer. Wires the lightbox to
// every image in the open conversation and the reply bar to the composer.
import { useMemo, useState } from 'react'
import { Bell, Hash, Menu, Search, Users } from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useMediaQuery } from '../../lib/useMediaQuery'
import { MessageList } from './MessageList'
import { Composer } from './Composer'
import { Button } from '../../components/ui/base'
import { IconTooltip } from '../../components/ui/overlay'
import { EmptyState } from '../../components/States'
import { t } from '../../lib/i18n'
import type { Attachment, Message } from '../../lib/types'

export function ChatView({ onOpenDrawer }: { onOpenDrawer?: () => void }) {
  const { activeChannel, activeDm, messages } = useChat()
  const setCommandOpen = useAppStore((s) => s.setCommandOpen)
  const openSettings = useAppStore((s) => s.openSettings)
  const toggleMemberPanel = useAppStore((s) => s.toggleMemberPanel)
  const memberPanelOpen = useAppStore((s) => s.memberPanelOpen)
  const openLightbox = useAppStore((s) => s.openLightbox)
  const isDesktop = useMediaQuery('(min-width: 1024px)')
  const [replyTo, setReplyTo] = useState<Message | null>(null)

  const imageItems = useMemo(
    () => messages.flatMap((m) => m.attachments).filter((a) => a.content_type?.startsWith('image/')),
    [messages],
  )
  const openAttachment = (a: Attachment) => {
    const idx = imageItems.findIndex((x) => x.id === a.id || x.url === a.url)
    openLightbox(imageItems, Math.max(0, idx))
  }

  const isDm = Boolean(activeDm)
  const name = isDm ? activeDm!.name : activeChannel?.name ?? ''
  const topic = activeChannel?.topic

  if (!activeChannel && !activeDm) {
    return (
      <div className="flex h-full min-h-0 flex-col bg-base">
        <MobileHeader onOpenDrawer={onOpenDrawer} title={t('nav.chats')} />
        <EmptyState title={t('chat.pickTitle')} body={t('chat.pickBody')} className="h-full"
          action={!isDesktop && onOpenDrawer ? <Button variant="outline" onClick={onOpenDrawer}>{t('nav.chats')}</Button> : undefined} />
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-base">
      <header className="z-10 flex h-14 shrink-0 items-center gap-3 border-b border-line/10 bg-surface px-3 sm:px-4">
        {onOpenDrawer && (
          <button onClick={onOpenDrawer} aria-label={t('nav.chats')}
            className="flex size-10 items-center justify-center rounded-md text-ink-secondary hover:bg-hover hover:text-ink-primary lg:hidden">
            <Menu className="size-5" />
          </button>
        )}
        <span className="flex size-6 shrink-0 items-center justify-center text-ink-muted">
          {isDm ? <span className="text-lg font-semibold">@</span> : <Hash className="size-5" />}
        </span>
        <h1 className="truncate text-md font-semibold text-ink-primary">{name}</h1>
        {topic && <span className="hidden truncate border-l border-line/10 pl-3 text-sm text-ink-muted md:block">{topic}</span>}

        <div className="ml-auto flex shrink-0 items-center gap-0.5">
          <IconTooltip label={t('chat.searchPlaceholder')} side="bottom">
            <Button variant="ghost" size="icon-sm" aria-label={t('chat.searchPlaceholder')} onClick={() => setCommandOpen(true)}><Search /></Button>
          </IconTooltip>
          <IconTooltip label={t('notifications.title')} side="bottom">
            <Button variant="ghost" size="icon-sm" aria-label={t('notifications.title')} onClick={() => openSettings('notifications')}><Bell /></Button>
          </IconTooltip>
          {!isDm && (
            <IconTooltip label={t('chat.membersToggle')} side="bottom">
              <Button variant="ghost" size="icon-sm" aria-label={t('chat.membersToggle')}
                onClick={toggleMemberPanel} className={memberPanelOpen ? 'bg-hover text-ink-primary' : ''}><Users /></Button>
            </IconTooltip>
          )}
        </div>
      </header>

      <MessageList onReply={setReplyTo} onOpenAttachment={openAttachment} />
      <Composer replyTo={replyTo} onCancelReply={() => setReplyTo(null)} />
    </div>
  )
}

function MobileHeader({ onOpenDrawer, title }: { onOpenDrawer?: () => void; title: string }) {
  if (!onOpenDrawer) return null
  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b border-line/10 px-3 lg:hidden">
      <button onClick={onOpenDrawer} aria-label={title}
        className="flex size-10 items-center justify-center rounded-md text-ink-secondary hover:bg-hover"><Menu className="size-5" /></button>
      <span className="text-md font-semibold text-ink-primary">{title}</span>
    </header>
  )
}
