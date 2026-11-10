// Message list (spec §3.5/§3.6a): grouping, date dividers, sticky-to-bottom,
// jump-to-present, upward history paging with scroll anchoring, and all states.
// History is bounded to PAGE_SIZE (50) per fetch, so the DOM stays small.
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { ArrowDown, Loader2 } from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useSession } from '../../store/session'
import { MessageRow } from './Message'
import { MessageSkeleton, EmptyState, ErrorState } from '../../components/States'
import { Button } from '../../components/ui/base'
import { LoaderDots } from '../../components/LoaderDots'
import { dayDivider } from '../../lib/format'
import { cn } from '../../lib/utils'
import { t } from '../../lib/i18n'
import type { Attachment, Message } from '../../lib/types'

type Props = {
  onReply: (message: Message) => void
  onOpenAttachment: (attachment: Attachment) => void
}

const FIVE_MIN = 5 * 60 * 1000

export function MessageList({ onReply, onOpenAttachment }: Props) {
  const { messages, messagesLoading, messagesError, hasMore, loadOlder, reloadMessages, activeChannelId, typing } = useChat()
  const density = useAppStore((s) => s.density)
  const myId = useSession((s) => s.userId) ?? 'preview'
  const myUsername = useSession((s) => s.profile?.username) ?? 'you'
  const scrollRef = useRef<HTMLDivElement>(null)
  const atBottomRef = useRef(true)
  const [atBottom, setAtBottom] = useState(true)
  const anchorRef = useRef<{ height: number; top: number } | null>(null)
  const loadingOlderRef = useRef(false)
  const [loadingOlder, setLoadingOlder] = useState(false)

  const scrollToBottom = () => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }

  // Landing on a new conversation always starts at the present.
  useEffect(() => {
    atBottomRef.current = true
    setAtBottom(true)
    scrollToBottom()
  }, [activeChannelId])

  // Stick to the bottom on new messages when the user is already there.
  useEffect(() => {
    if (atBottomRef.current) scrollToBottom()
  }, [messages.length])

  // After older history is prepended, keep the same content under the viewport.
  useLayoutEffect(() => {
    if (anchorRef.current && scrollRef.current) {
      const el = scrollRef.current
      const { height, top } = anchorRef.current
      el.scrollTop = top + (el.scrollHeight - height)
      anchorRef.current = null
      loadingOlderRef.current = false
      setLoadingOlder(false)
    }
  }, [messages])

  const onScroll = () => {
    const el = scrollRef.current
    if (!el) return
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    atBottomRef.current = nearBottom
    setAtBottom(nearBottom)
    if (el.scrollTop < 60 && hasMore && !messagesLoading && !loadingOlderRef.current) {
      loadingOlderRef.current = true
      setLoadingOlder(true)
      anchorRef.current = { height: el.scrollHeight, top: el.scrollTop }
      void loadOlder().catch(() => { loadingOlderRef.current = false; setLoadingOlder(false); anchorRef.current = null })
    }
  }

  const rows: ReactNode[] = []
  let lastDay = ''
  messages.forEach((m, i) => {
    const day = dayDivider(m.created_at)
    if (day !== lastDay) {
      rows.push(
        <div key={`day-${m.id}`} className="flex items-center gap-3 px-4 py-3">
          <span className="h-px flex-1 bg-line/10" />
          <span className="text-xs font-semibold text-ink-muted">{day}</span>
          <span className="h-px flex-1 bg-line/10" />
        </div>,
      )
      lastDay = day
    }
    const prev = messages[i - 1]
    const grouped = Boolean(prev && prev.author_id === m.author_id && prev.type !== 'system' && m.type !== 'system'
      && !prev.deleted_at && dayDivider(prev.created_at) === day
      && new Date(m.created_at).getTime() - new Date(prev.created_at).getTime() < FIVE_MIN)
    rows.push(
      <MessageRow key={m.id} message={m} grouped={grouped} mine={m.author_id === myId}
        compact={density === 'compact'} mentionName={myUsername} onReply={onReply} onOpenAttachment={onOpenAttachment} />,
    )
  })

  const showSkeleton = messagesLoading && messages.length === 0
  const showEmpty = !messagesLoading && !messagesError && messages.length === 0

  return (
    <div className="relative min-h-0 flex-1">
      <div ref={scrollRef} onScroll={onScroll} className="h-full overflow-y-auto scrollbar-thin">
        {messagesError && messages.length > 0 && (
          <div className="mx-4 mt-2 flex items-center justify-between gap-3 rounded-md bg-danger/10 px-3 py-2 text-sm text-danger">
            <span>{t('chat.loadFailedBody')}</span>
            <Button variant="ghost" size="sm" onClick={reloadMessages}>{t('common.retry')}</Button>
          </div>
        )}
        {hasMore && !messagesLoading && messages.length > 0 && (
          <div className="flex justify-center py-2">
            <Button variant="ghost" size="sm" onClick={() => {
              const el = scrollRef.current
              if (el) anchorRef.current = { height: el.scrollHeight, top: el.scrollTop }
              loadingOlderRef.current = true
              setLoadingOlder(true)
              void loadOlder().catch(() => { loadingOlderRef.current = false; setLoadingOlder(false); anchorRef.current = null })
            }}>{t('chat.loadOlder')}</Button>
          </div>
        )}
        {showSkeleton ? <MessageSkeleton /> : messagesError && messages.length === 0 ? (
          <ErrorState title={t('chat.loadFailedTitle')} body={t('chat.loadFailedBody')} onRetry={reloadMessages} />
        ) : showEmpty ? (
          <EmptyState title={t('chat.noMessagesTitle')} body={t('chat.noMessagesBody')} className="h-full" />
        ) : (
          <div className={cn('flex flex-col pb-2', density === 'compact' ? 'pt-1' : 'pt-3')}>{rows}</div>
        )}
        {typing.length > 0 && (
          <div className="flex items-center gap-2 px-4 pb-2 text-xs text-ink-muted">
            <LoaderDots />
            <span>{typing.length === 1 ? t('chat.typing', { name: typing[0] }) : t('chat.typingMany', { n: typing.length })}</span>
          </div>
        )}
      </div>

      {!atBottom && !showSkeleton && (
        <button onClick={() => { atBottomRef.current = true; setAtBottom(true); scrollToBottom() }}
          className="absolute bottom-4 right-5 z-10 inline-flex items-center gap-1.5 rounded-full border border-line/15 bg-elevated px-3.5 py-2 text-sm font-medium text-ink-primary shadow-pop hover:bg-hover">
          <ArrowDown className="size-4" /> {t('chat.jumpToPresent')}
        </button>
      )}
      {loadingOlder && (
        <div className="pointer-events-none absolute left-1/2 top-2 -translate-x-1/2 rounded-full bg-elevated px-3 py-1.5 text-xs text-ink-secondary shadow-soft">
          <Loader2 className="mr-1 inline size-3 animate-spin" /> {t('common.loading')}
        </div>
      )}
    </div>
  )
}
