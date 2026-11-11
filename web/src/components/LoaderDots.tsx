// indie-starter loader-dots idea, CSS-only (spec §3.6 typing/sending).
import { cn } from '../lib/utils'
import { t } from '../lib/i18n'

export function LoaderDots({ className, label }: { className?: string; label?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1', className)} role="status" aria-label={label ?? t('a11y.loading')}>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="size-1.5 rounded-full bg-current animate-dot-pulse"
          style={{ animationDelay: `${i * 160}ms` }}
        />
      ))}
    </span>
  )
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      className={cn('inline-block size-4 animate-spin rounded-full border-2 border-line/20 border-t-brand', className)}
      role="status"
      aria-label={t('a11y.loading')}
    />
  )
}
