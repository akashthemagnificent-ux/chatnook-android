// Message body: markdown-lite (bold/italic/inline code/code block), @mentions,
// links, and emoji-only enlargement (spec §3.6a). All token-styled.
import { useState, type ReactNode } from 'react'
import { Check, Copy } from 'lucide-react'
import { cn } from '../lib/utils'
import { t } from '../lib/i18n'

const TOKEN = /(`{3}[\s\S]*?`{3}|`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*|_[^_]+_|https?:\/\/[^\s]+|@[\w.-]+|@everyone|@here)/g

function isEmojiOnly(text: string): boolean {
  const trimmed = text.trim()
  if (!trimmed || trimmed.length > 16) return false
  return /^(\p{Extended_Pictographic}|\p{Emoji_Presentation}|\s|\uFE0F|\u200D)+$/u.test(trimmed)
}

function CodeBlock({ code }: { code: string }) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    void navigator.clipboard?.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }
  return (
    <div className="group/code relative my-1.5 overflow-hidden rounded-md border border-line/10 bg-base/60">
      <button
        onClick={copy}
        aria-label={t('a11y.copyCode')}
        className="absolute right-2 top-2 rounded-sm p-1.5 text-ink-muted opacity-0 transition-opacity hover:bg-hover hover:text-ink-primary focus-visible:opacity-100 group-hover/code:opacity-100"
      >
        {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}
      </button>
      <pre className="overflow-x-auto p-3 pr-10 text-[0.8125rem] leading-relaxed scrollbar-thin"><code className="font-mono text-ink-primary">{code}</code></pre>
    </div>
  )
}

function renderInline(text: string, keyPrefix: string, mentionName: string, isMine: boolean): ReactNode[] {
  const parts = text.split(TOKEN).filter((p) => p !== '')
  return parts.map((part, i) => {
    const key = `${keyPrefix}-${i}`
    if (part.startsWith('```') && part.endsWith('```')) {
      return <CodeBlock key={key} code={part.slice(3, -3).replace(/^\n|\n$/g, '')} />
    }
    if (part.startsWith('`') && part.endsWith('`')) {
      return <code key={key} className="rounded-sm bg-base/60 px-1 py-0.5 font-mono text-[0.8125rem] text-ink-primary">{part.slice(1, -1)}</code>
    }
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={key} className="font-semibold text-ink-primary">{part.slice(2, -2)}</strong>
    }
    if ((part.startsWith('*') && part.endsWith('*')) || (part.startsWith('_') && part.endsWith('_'))) {
      return <em key={key}>{part.slice(1, -1)}</em>
    }
    if (part.startsWith('http://') || part.startsWith('https://')) {
      return (
        <a key={key} href={part} target="_blank" rel="noopener noreferrer"
          className="text-info underline decoration-info/40 underline-offset-2 hover:decoration-info break-all">
          {part}
        </a>
      )
    }
    if (part.startsWith('@')) {
      const isMention = part === '@everyone' || part === '@here' ||
        part.slice(1).toLowerCase() === mentionName.toLowerCase()
      if (isMention) {
        return (
          <span key={key} className={cn('rounded-sm px-1 font-medium', isMine ? 'bg-ink-accent/20' : 'bg-brand/20 text-brand')}>
            {part}
          </span>
        )
      }
    }
    return <span key={key}>{part}</span>
  })
}

export function MessageContent({
  content,
  mentionName,
  isMine = false,
  className,
}: {
  content: string
  mentionName: string
  isMine?: boolean
  className?: string
}) {
  if (!content) return null
  const emojiOnly = isEmojiOnly(content)
  return (
    <div className={cn('break-words text-base leading-relaxed text-ink-primary', emojiOnly && 'text-4xl leading-tight', className)}>
      {renderBlocks(content, mentionName, isMine)}
    </div>
  )
}

/**
 * Fenced code blocks span lines, so they are split out before line rendering.
 * Everything else keeps the one-line-per-block rhythm.
 */
function renderBlocks(content: string, mentionName: string, isMine: boolean): ReactNode[] {
  const FENCE = /```([a-zA-Z0-9_+-]*)\n?([\s\S]*?)```/g
  const out: ReactNode[] = []
  let last = 0
  let block = 0
  let match: RegExpExecArray | null
  const pushLines = (text: string, keyPrefix: string) => {
    const lines = text.replace(/^\n/, '').split('\n')
    lines.forEach((line, i) => {
      if (line === '' && i === lines.length - 1) return
      out.push(
        <span key={`${keyPrefix}-${i}`} className="block min-h-[1.375rem]">
          {renderInline(line, `${keyPrefix}-${i}`, mentionName, isMine)}
        </span>,
      )
    })
  }

  while ((match = FENCE.exec(content))) {
    if (match.index > last) pushLines(content.slice(last, match.index), `t${block}`)
    out.push(<CodeBlock key={`c${block}`} code={match[2].replace(/\n$/, '')} />)
    last = match.index + match[0].length
    block += 1
  }
  if (last < content.length) pushLines(content.slice(last), `t${block}`)
  return out
}
