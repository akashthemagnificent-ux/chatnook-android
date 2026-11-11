// Message composer (spec §3.6): auto-grow textarea, Enter to send, emoji and
// mention insert at the caret, attachments, reply preview, char cap, typing.
import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Paperclip, Send, Smile, X } from 'lucide-react'
import { useChat, previewPeople } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { Button } from '../../components/ui/base'
import { Popover, PopoverTrigger, PopoverContent, IconTooltip } from '../../components/ui/overlay'
import { EmojiPicker } from '../../components/EmojiPicker'
import { LoaderDots } from '../../components/LoaderDots'
import { toast } from '../../components/ui/toast'
import * as api from '../../data/api'
import { displayName } from '../../lib/types'
import { cn } from '../../lib/utils'
import { t } from '../../lib/i18n'
import type { AttachmentPayload } from '../../data/api'
import type { Message } from '../../lib/types'

const MAX = 2000
// Mirrors the attachments bucket's allowed_mime_types.
const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp']

export function Composer({ replyTo, onCancelReply }: { replyTo: Message | null; onCancelReply: () => void }) {
  const { activeChannel, activeDm, send, configured, members, notifyTyping } = useChat()
  const density = useAppStore((s) => s.density)
  const [value, setValue] = useState('')
  const [uploading, setUploading] = useState(false)
  const taRef = useRef<HTMLTextAreaElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const typingThrottle = useRef(0)

  const channelName = activeDm ? activeDm.name : activeChannel?.name ?? ''
  // DMs have no guild channel row; they are selected by activeChannelId only.
  const disabled = !activeChannel && !activeDm

  // Keep a per-conversation draft so switching channels does not lose typing.
  const draftKey = `chatnook.draft.${activeChannel?.id ?? 'none'}`
  useEffect(() => {
    setValue(window.localStorage.getItem(draftKey) ?? '')
  }, [draftKey])
  useEffect(() => {
    const el = taRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`
  }, [value])

  const persist = (next: string) => {
    setValue(next)
    window.localStorage.setItem(draftKey, next)
    const now = Date.now()
    if (now - typingThrottle.current > 1500) { typingThrottle.current = now; notifyTyping() }
  }

  const doSend = async () => {
    const text = value.trim()
    if (!text || disabled) return
    const replyId = replyTo?.id ?? null
    setValue('')
    window.localStorage.removeItem(draftKey)
    onCancelReply()
    if (taRef.current) taRef.current.style.height = 'auto'
    await send(text, replyId, null)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void doSend() }
  }

  const insertAtCaret = (text: string) => {
    const el = taRef.current
    if (!el) { persist(value + text); return }
    const start = el.selectionStart ?? value.length
    const end = el.selectionEnd ?? value.length
    const next = value.slice(0, start) + text + value.slice(end)
    persist(next)
    window.requestAnimationFrame(() => { el.focus(); el.selectionStart = el.selectionEnd = start + text.length })
  }

  const onFile = async (file: File | undefined) => {
    if (!file || disabled) return
    // The attachments bucket only accepts these types; fail with a real reason
    // instead of a generic error from storage.
    if (!IMAGE_TYPES.includes(file.type)) {
      toast.error(t('toast.unsupportedFile'))
      if (fileRef.current) fileRef.current.value = ''
      return
    }
    setUploading(true)
    try {
      let payload: AttachmentPayload
      if (configured) {
        payload = await api.uploadAttachment(file)
      } else {
        payload = {
          url: URL.createObjectURL(file), filename: file.name, content_type: file.type,
          size_bytes: file.size, width: null, height: null,
        }
      }
      await send('', replyTo?.id ?? null, payload)
      onCancelReply()
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  // @mention autocomplete, derived from the tail of the draft.
  const mentionQuery = /@([\w.-]*)$/.exec(value)?.[1] ?? null
  const candidates = useMemo(() => {
    const people = configured
      ? members.map((m) => ({ id: m.user_id, name: displayName(m.profile), username: m.profile?.username ?? '' }))
      : Object.values(previewPeople).filter((p) => p.id !== 'preview')
          .map((p) => ({ id: p.id, name: displayName(p), username: p.username }))
    if (mentionQuery === null) return []
    return people.filter((p) => `${p.username} ${p.name}`.toLowerCase().includes(mentionQuery.toLowerCase())).slice(0, 6)
  }, [configured, members, mentionQuery])

  const pickMention = (username: string) => persist(value.replace(/@([\w.-]*)$/, `@${username} `))

  const placeholder = disabled ? t('composer.placeholderNone')
    : activeDm ? t('composer.placeholderDm', { name: channelName })
      : t('composer.placeholder', { channel: channelName })

  return (
    <div className={cn('relative shrink-0 bg-base px-4 pb-4', density === 'compact' ? 'pt-1' : 'pt-2')}>
      {replyTo && (
        <div className="mb-2 flex items-center gap-2 rounded-md bg-surface px-3 py-2 text-sm">
          <span className="min-w-0 flex-1 truncate text-ink-secondary">
            <span className="font-medium text-ink-primary">{t('message.replyTo')} </span>
            {displayName(replyTo.author)}: {replyTo.content ?? t('message.imageAlt')}
          </span>
          <button onClick={onCancelReply} aria-label={t('composer.cancelReply')} className="rounded-sm p-1 text-ink-muted hover:text-ink-primary"><X className="size-4" /></button>
        </div>
      )}

      {mentionQuery !== null && candidates.length > 0 && (
        <div className="absolute bottom-full left-4 right-4 z-20 mb-2 overflow-hidden rounded-lg border border-line/12 bg-elevated shadow-pop">
          {candidates.map((p) => (
            <button key={p.id} onClick={() => pickMention(p.username)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-hover">
              <span className="font-medium text-ink-primary">{p.name}</span>
              <span className="text-ink-muted">@{p.username}</span>
            </button>
          ))}
        </div>
      )}

      <div className={cn(
        'flex items-end gap-1 rounded-lg border border-line/12 bg-elevated px-2 py-1.5 transition-colors',
        'focus-within:border-brand/50 focus-within:ring-2 focus-within:ring-brand/20',
        disabled && 'opacity-60',
      )}>
        <IconTooltip label={t('composer.attach')} side="top">
          <button onClick={() => fileRef.current?.click()} disabled={disabled || uploading}
            aria-label={t('composer.attach')} className="flex size-9 shrink-0 items-center justify-center rounded-md text-ink-secondary hover:bg-hover hover:text-ink-primary disabled:opacity-50">
            {uploading ? <LoaderDots /> : <Paperclip className="size-5" />}
          </button>
        </IconTooltip>
        <input ref={fileRef} type="file" accept={IMAGE_TYPES.join(',')} className="hidden"
          onChange={(e) => void onFile(e.target.files?.[0])} />

        <textarea ref={taRef} value={value} onChange={(e) => {
          const next = e.target.value
          if (next.length <= MAX) persist(next)
        }} onKeyDown={onKeyDown} rows={1} disabled={disabled} placeholder={placeholder}
          aria-label={placeholder}
          className="max-h-[200px] min-h-[36px] flex-1 resize-none bg-transparent px-1 py-2 text-base leading-relaxed text-ink-primary outline-none placeholder:text-ink-muted disabled:cursor-not-allowed scrollbar-thin" />

        <Popover>
          <PopoverTrigger asChild>
            <button disabled={disabled} aria-label={t('composer.emoji')}
              className="flex size-9 shrink-0 items-center justify-center rounded-md text-ink-secondary hover:bg-hover hover:text-ink-primary disabled:opacity-50">
              <Smile className="size-5" />
            </button>
          </PopoverTrigger>
          <PopoverContent className="w-auto p-2" side="top" align="end">
            <EmojiPicker onSelect={insertAtCaret} />
          </PopoverContent>
        </Popover>

        <Button variant="primary" size="sm" onClick={() => void doSend()} disabled={disabled || uploading || (!value.trim() && !uploading)}
          className="mb-0.5 shrink-0" aria-label={t('composer.send')}>
          <Send className="size-4" /> <span className="hidden md:inline">{t('composer.send')}</span>
        </Button>
      </div>

      <div className="mt-1.5 flex items-center justify-between px-1">
        <span className="hidden text-xs text-ink-muted sm:inline">{t('composer.shiftEnter')}</span>
        {value.length > MAX - 200 && <span className={cn('text-xs tabular-nums', value.length >= MAX ? 'text-danger' : 'text-ink-muted')}>{t('composer.charCount', { n: value.length })}</span>}
      </div>
    </div>
  )
}
