// Shared empty / error / loading states, all token-styled (spec §3.21, §4).
import type { ReactNode } from 'react'
import { AlertTriangle, Inbox } from 'lucide-react'
import { Button } from './ui/base'
import { Skeleton } from './ui/base'
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
    <div className={cn('flex flex-col items-center justify-center gap-3 px-6 py-12 text-center animate-fade-up', className)}>
      <div className="flex size-14 items-center justify-center rounded-xl bg-surface border border-line/10 text-ink-muted">
        {icon ?? <Inbox className="size-7" />}
      </div>
      <div className="space-y-1">
        <h3 className="text-md font-semibold text-ink-primary text-balance">{title}</h3>
        {body && <p className="max-w-xs text-sm text-ink-secondary text-balance">{body}</p>}
      </div>
      {action}
    </div>
  )
}

export function ErrorState({ title, body, onRetry, className }: {
  title?: string
  body?: string
  onRetry?: () => void
  className?: string
}) {
  return (
    <div className={cn('flex flex-col items-center justify-center gap-3 px-6 py-12 text-center', className)}>
      <div className="flex size-14 items-center justify-center rounded-xl bg-danger/10 text-danger">
        <AlertTriangle className="size-7" />
      </div>
      <div className="space-y-1">
        <h3 className="text-md font-semibold text-ink-primary">{title ?? t('errors.title')}</h3>
        <p className="max-w-xs text-sm text-ink-secondary">{body ?? t('errors.body')}</p>
      </div>
      {onRetry && <Button variant="outline" size="sm" onClick={onRetry}>{t('common.retry')}</Button>}
    </div>
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
