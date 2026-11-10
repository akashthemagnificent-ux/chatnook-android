// Shared empty / error / loading states, all token-styled (spec §3.21, §4).
// Empty and error cards use the spotlight surface so they never read as a void.
import type { ReactNode } from 'react'
import { AlertTriangle, Inbox } from 'lucide-react'
import { Button, Skeleton } from './ui/base'
import { SpotlightCard } from './fx/surfaces'
import { GridBackdrop } from './fx/backgrounds'
import { Reveal } from './fx/motion'
import { cn } from '../lib/utils'
import { t } from '../lib/i18n'

export function EmptyState({
  icon,
  title,
  body,
  action,
  className,
}: {
  icon?: ReactNode
  title: string
  body?: string
  action?: ReactNode
  className?: string
}) {
  return (
    <SpotlightCard className={cn('flex flex-col items-center justify-center gap-4 px-6 py-12 text-center', className)}>
      <GridBackdrop className="opacity-[0.02]" />
      <Reveal className="relative flex flex-col items-center gap-4">
        <div className="relative">
          <span aria-hidden className="absolute inset-0 rounded-2xl bg-brand/25 blur-2xl" />
          <span className="relative flex size-16 items-center justify-center rounded-2xl border border-line/10 bg-elevated/80 text-brand shadow-glow ring-1 ring-inset ring-white/5">
            {icon ?? <Inbox className="size-7" />}
          </span>
          <span aria-hidden className="absolute -right-1 -top-1 size-2.5 rounded-full bg-brand shadow-[0_0_12px_rgb(var(--brand))]" />
        </div>
        <div className="space-y-1.5">
          <h3 className="text-lg font-semibold text-ink-primary text-balance">{title}</h3>
          {body && <p className="max-w-sm text-sm leading-relaxed text-ink-secondary text-balance">{body}</p>}
        </div>
        {action}
      </Reveal>
    </SpotlightCard>
  )
}

export function ErrorState({ title, body, onRetry, className }: {
  title?: string
  body?: string
  onRetry?: () => void
  className?: string
}) {
  return (
    <SpotlightCard glow={0.1} className={cn('flex flex-col items-center justify-center gap-4 px-6 py-12 text-center', className)}>
      <GridBackdrop className="opacity-[0.02]" />
      <Reveal className="relative flex flex-col items-center gap-4">
        <div className="relative">
          <span aria-hidden className="absolute inset-0 rounded-2xl bg-danger/25 blur-2xl" />
          <span className="relative flex size-16 items-center justify-center rounded-2xl border border-danger/20 bg-danger/10 text-danger ring-1 ring-inset ring-white/5">
            <AlertTriangle className="size-7" />
          </span>
        </div>
        <div className="space-y-1.5">
          <h3 className="text-lg font-semibold text-ink-primary">{title ?? t('errors.title')}</h3>
          <p className="max-w-sm text-sm leading-relaxed text-ink-secondary">{body ?? t('errors.body')}</p>
        </div>
        {onRetry && <Button variant="outline" size="sm" onClick={onRetry}>{t('common.retry')}</Button>}
      </Reveal>
    </SpotlightCard>
  )
}

export function MessageSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="flex flex-col gap-5 px-5 py-6" aria-label={t('a11y.loadingMessages')} role="status">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="flex gap-3">
          <Skeleton className="size-10 shrink-0 rounded-full" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-3 w-28" />
            <Skeleton className="h-3.5" style={{ width: `${55 + ((i * 37) % 40)}%` }} />
            {i % 3 === 0 && <Skeleton className="h-3.5 w-2/5" />}
          </div>
        </div>
      ))}
    </div>
  )
}

export function ListSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="flex flex-col gap-1 p-2" aria-label={t('a11y.loading')} role="status">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 rounded-md px-2 py-2">
          <Skeleton className="size-9 shrink-0 rounded-full" />
          <Skeleton className="h-3.5" style={{ width: `${50 + ((i * 29) % 40)}%` }} />
        </div>
      ))}
    </div>
  )
}
