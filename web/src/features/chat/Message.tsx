// One Message component with variants (spec §3.6a): own/other, grouped/first,
// compact/cozy. Bubbles are the core surface — own messages are right-aligned
// and accent-tinted, others sit left on a surface bubble.
import { useMemo, useState, type ReactNode } from 'react'
import {
  CheckCheck, Copy, CornerUpLeft, Download, File as FileIcon, Image as ImageIcon,
  MoreHorizontal, Pencil, Reply, Smile, SmilePlus, Trash2, X,
} from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useSession } from '../../store/session'
import { useAppStore } from '../../store/app'
import { UserAvatar } from '../../components/ui/avatar'
import { Badge, Button, Textarea } from '../../components/ui/base'
import {
  Popover, PopoverTrigger, PopoverContent,
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuSeparator, ContextMenu, ContextMenuTrigger, ContextMenuContent, ContextMenuItem,
} from '../../components/ui/overlay'
import { EmojiPicker } from '../../components/EmojiPicker'
import { MessageContent } from '../../components/MessageContent'
import { LoaderDots } from '../../components/LoaderDots'
import { toast } from '../../components/ui/toast'
import { displayName } from '../../lib/types'
import { messageTime, fullTime, fileSize } from '../../lib/format'
import { cn } from '../../lib/utils'
import { t } from '../../lib/i18n'
import type { Attachment, Message as MessageType } from '../../lib/types'

type Props = {
  message: MessageType
  grouped: boolean
  mine: boolean
  compact: boolean
  mentionName: string
  onReply: (message: MessageType) => void
  onOpenAttachment: (attachment: Attachment) => void
}

export function MessageRow({ message, grouped, mine, compact, mentionName, onReply, onOpenAttachment }: Props) {
  const myId = useSession((s) => s.userId) ?? 'preview'
  const { toggleReaction, editMessage, deleteMessage, retryMessage } = useChat()
  const requestConfirm = useAppStore((s) => s.requestConfirm)
  const [editing, setEditing] = useState(false)
  const [editValue, setEditValue] = useState(message.content ?? '')
  const [menuOpen, setMenuOpen] = useState(false)

  const name = displayName(message.author)
  const isDeleted = message.deleted_at != null || (message.type !== 'system' && !message.content && message.attachments.length === 0)
  const isSystem = message.type === 'system'

  const reactionGroups = useMemo(() => {
    const map = new Map<string, { emoji: string; count: number; mine: boolean }>()
    for (const r of message.reactions) {
      const prev = map.get(r.emoji)
      if (prev) { prev.count += 1; prev.mine = prev.mine || r.user_id === myId }
      else map.set(r.emoji, { emoji: r.emoji, count: 1, mine: r.user_id === myId })
    }
    return [...map.values()]
  }, [message.reactions, myId])

  const copyText = () => {
    if (message.content) void navigator.clipboard?.writeText(message.content)
    toast.success(t('common.copied'))
  }
  const copyLink = () => {
    void navigator.clipboard?.writeText(`${window.location.origin}${window.location.pathname}#/?message=${message.id}`)
    toast.success(t('toast.linkCopied'))
  }
  const saveEdit = () => {
    const value = editValue.trim()
    if (value && value !== message.content) void editMessage(message.id, value)
    setEditing(false)
  }
  const confirmDelete = () => requestConfirm({
    title: t('message.deleteTitle'),
    body: t('message.deleteBody'),
    confirmLabel: t('common.delete'), destructive: true,
    onConfirm: () => void deleteMessage(message.id),
  })

  const menuItems = (
    <>
      <DropdownMenuItem onSelect={copyText}><Copy /> {t('message.copyText')}</DropdownMenuItem>
      <DropdownMenuItem onSelect={copyLink}><Copy /> {t('message.copyLink')}</DropdownMenuItem>
      {mine && !isDeleted && (
        <>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => setEditing(true)}><Pencil /> {t('message.edit')}</DropdownMenuItem>
          <DropdownMenuItem destructive onSelect={confirmDelete}><Trash2 /> {t('message.delete')}</DropdownMenuItem>
        </>
      )}
    </>
  )

  // System rows are centered, bubble-less notices.
  if (isSystem) {
    return (
      <div className="flex justify-center px-4 py-1.5">
        <p className="max-w-md text-center text-xs text-ink-muted">{message.content}</p>
      </div>
    )
  }

  return (
    <ContextMenu>
      <ContextMenuTrigger asChild>
        <div className={cn(
          'group relative flex w-full items-end gap-2 px-4 transition-colors duration-150',
          mine ? 'justify-end' : 'justify-start',
          compact ? 'py-0.5' : 'py-1',
        )}>
          {!mine && (
            <div className="w-9 shrink-0 self-start pt-5">
              {!grouped && <UserAvatar name={name} src={message.author?.avatar_url} size={36} />}
            </div>
          )}

          <div className={cn('relative flex min-w-0 max-w-[min(78%,40rem)] flex-col', mine ? 'items-end' : 'items-start')}>
            {!grouped && (
              <div className="mb-1 flex items-baseline gap-2 px-1">
                {!mine && (
                  <>
                    <span className="text-sm font-semibold text-ink-primary">{name}</span>
                    <span className="text-[0.6875rem] text-ink-muted" title={fullTime(message.created_at)}>
                      {messageTime(message.created_at)}
                    </span>
                  </>
                )}
                {mine && (
                  <>
                    <Badge variant="dot" size="sm">{t('message.you')}</Badge>
                    <span className="text-[0.6875rem] text-ink-muted" title={fullTime(message.created_at)}>
                      {messageTime(message.created_at)}
                    </span>
                    {!message.pending && (
                      <CheckCheck className="size-3 text-ink-muted" aria-label={t('message.delivered')} />
                    )}
                  </>
                )}
              </div>
            )}

            <div className={cn(
              'relative max-w-full border px-3 py-1.5 text-left shadow-soft',
              mine
                ? 'rounded-lg rounded-tr-sm border-brand/35 bg-brand/30'
                : 'rounded-lg rounded-tl-sm border-line/10 bg-surface',
              grouped && 'rounded-lg',
              isDeleted && 'border-line/10 bg-surface/60',
            )}>
              {isDeleted ? (
                <p className="text-sm italic text-ink-muted">{t('message.deleted')}</p>
              ) : editing ? (
                <div className="flex w-[min(70vw,32rem)] flex-col gap-2">
                  <Textarea value={editValue} onChange={(e) => setEditValue(e.target.value)} autoFocus rows={2}
                    onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); saveEdit() } if (e.key === 'Escape') setEditing(false) }} />
                  <div className="flex gap-2">
                    <Button size="sm" variant="primary" onClick={saveEdit}>{t('message.save')}</Button>
                    <Button size="sm" variant="ghost" onClick={() => setEditing(false)}>{t('message.cancel')}</Button>
                  </div>
                </div>
              ) : (
                <>
                  {message.reply && (
                    <div className="mb-1.5 flex items-center gap-1.5 rounded-sm border-l-2 border-brand/60 bg-base/40 px-2 py-1 text-xs text-ink-secondary">
                      <CornerUpLeft className="size-3 shrink-0 text-ink-muted" />
                      <span className="font-medium">{t('message.replyTo')}</span>
                      <span className="truncate">{message.reply.content ?? t('message.imageAlt')}</span>
                    </div>
                  )}
                  {message.content && (
                    <MessageContent content={message.content} mentionName={mentionName} isMine={mine} />
                  )}
                  {message.edited_at && !message.pending && (
                    <span className="ml-1 align-baseline text-[0.6875rem] text-ink-muted">({t('message.edited')})</span>
                  )}
                  {message.attachments.length > 0 && (
                    <AttachmentRow items={message.attachments} onOpen={onOpenAttachment} />
                  )}
                </>
              )}
            </div>

            {/* reactions */}
            {!isDeleted && reactionGroups.length > 0 && (
              <div className={cn('mt-1 flex flex-wrap items-center gap-1', mine ? 'justify-end' : 'justify-start')}>
                {reactionGroups.map((g) => (
                  <button key={g.emoji} onClick={() => void toggleReaction(message.id, g.emoji)}
                    className={cn(
                      'inline-flex h-7 items-center gap-1 rounded-full border px-2 text-sm transition-colors',
                      g.mine ? 'border-brand/50 bg-brand/12 text-ink-primary' : 'border-line/12 bg-elevated text-ink-secondary hover:border-line/25',
                    )}
                    aria-label={`${g.emoji} ${g.count}`}>
                    <span>{g.emoji}</span><span className="text-xs font-medium tabular-nums">{g.count}</span>
                  </button>
                ))}
                <EmojiPopover onPick={(e) => void toggleReaction(message.id, e)}>
                  <button className="inline-flex h-7 items-center gap-1 rounded-full border border-line/12 bg-elevated px-2 text-ink-muted hover:text-ink-primary" aria-label={t('message.addReaction')}>
                    <SmilePlus className="size-4" />
                  </button>
                </EmojiPopover>
              </div>
            )}

            {/* optimistic send / failed / delivered state */}
            {message.pending === 'sending' && (
              <span className="mt-1 inline-flex items-center gap-2 text-xs text-ink-muted"><LoaderDots /> {t('message.sending')}</span>
            )}
            {message.pending === 'failed' && (
              <button onClick={() => void retryMessage(message.id)} className="mt-1 inline-flex items-center gap-1.5 text-xs text-danger hover:underline">
                <X className="size-3.5" /> {t('message.failed')}
              </button>
            )}
          </div>

          {mine && (
            <div className="w-9 shrink-0 self-start pt-5">
              {!grouped && <UserAvatar name={name} src={message.author?.avatar_url} size={36} />}
            </div>
          )}

          {/* hover toolbar */}
          <div className={cn(
            'absolute top-0 z-10 flex -translate-y-1/2 items-center gap-0.5 rounded-lg border border-line/12 bg-elevated p-0.5 shadow-soft transition-opacity duration-150',
            'opacity-0 group-hover:opacity-100 focus-within:opacity-100',
            (menuOpen || editing) && 'opacity-100',
            mine ? 'right-12' : 'left-12',
          )}>
            <EmojiPopover onPick={(e) => void toggleReaction(message.id, e)}>
              <button className="flex size-8 items-center justify-center rounded-md text-ink-secondary hover:bg-hover hover:text-ink-primary" aria-label={t('message.react')}>
                <Smile className="size-[18px]" />
              </button>
            </EmojiPopover>
            <button onClick={() => onReply(message)} className="flex size-8 items-center justify-center rounded-md text-ink-secondary hover:bg-hover hover:text-ink-primary" aria-label={t('message.reply')}>
              <Reply className="size-[18px]" />
            </button>
            <DropdownMenu open={menuOpen} onOpenChange={setMenuOpen}>
              <DropdownMenuTrigger asChild>
                <button className="flex size-8 items-center justify-center rounded-md text-ink-secondary hover:bg-hover hover:text-ink-primary" aria-label={t('message.more')}>
                  <MoreHorizontal className="size-[18px]" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">{menuItems}</DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </ContextMenuTrigger>
      <ContextMenuContent>
        <ContextMenuItem onSelect={() => onReply(message)}><Reply /> {t('message.reply')}</ContextMenuItem>
        <ContextMenuItem onSelect={copyText}><Copy /> {t('message.copyText')}</ContextMenuItem>
        {mine && !isDeleted && (
          <>
            <ContextMenuItem onSelect={() => setEditing(true)}><Pencil /> {t('message.edit')}</ContextMenuItem>
            <ContextMenuItem destructive onSelect={confirmDelete}><Trash2 /> {t('message.delete')}</ContextMenuItem>
          </>
        )}
      </ContextMenuContent>
    </ContextMenu>
  )
}

function AttachmentRow({ items, onOpen }: { items: Attachment[]; onOpen: (a: Attachment) => void }) {
  return (
    <div className="mt-1.5 flex flex-wrap gap-2">
      {items.map((a) => {
        const isImage = a.content_type?.startsWith('image/')
        if (isImage) {
          return (
            <button key={a.id} onClick={() => onOpen(a)}
              className="group/img relative block max-h-64 max-w-sm overflow-hidden rounded-md border border-line/10 bg-base/40">
              <img src={a.url} alt={a.filename ?? t('message.imageAlt')} loading="lazy" className="max-h-64 max-w-full object-cover" />
              <span className="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-opacity group-hover/img:bg-black/30 group-hover/img:opacity-100">
                <ImageIcon className="size-6 text-white" />
              </span>
            </button>
          )
        }
        return (
          <a key={a.id} href={a.url} download={a.filename ?? undefined} target="_blank" rel="noopener noreferrer"
            className="flex max-w-xs items-center gap-3 rounded-md border border-line/10 bg-elevated px-3 py-2 hover:border-line/25">
            <FileIcon className="size-8 shrink-0 text-ink-muted" />
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium text-ink-primary">{a.filename ?? t('message.file')}</span>
              <span className="block text-xs text-ink-muted">{fileSize(a.size_bytes)}</span>
            </span>
            <Download className="size-4 shrink-0 text-ink-muted" />
          </a>
        )
      })}
    </div>
  )
}

function EmojiPopover({ onPick, children }: { onPick: (emoji: string) => void; children: ReactNode }) {
  const [open, setOpen] = useState(false)
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent className="w-auto p-2">
        <EmojiPicker onSelect={(e) => { onPick(e); setOpen(false) }} />
      </PopoverContent>
    </Popover>
  )
}
