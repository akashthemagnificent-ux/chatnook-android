// Original ChatNook mark (spec §1.6): a token-styled monogram, no Discord likeness.
import { cn } from '../lib/utils'

export function BrandMark({ size = 36, className }: { size?: number; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-[30%] bg-brand text-ink-accent font-bold shadow-soft',
        className,
      )}
      style={{ width: size, height: size, fontSize: size * 0.5 }}
      aria-hidden
    >
      C
    </span>
  )
}

export function Wordmark({ className, showMark = true }: { className?: string; showMark?: boolean }) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      {showMark && <BrandMark size={30} />}
      <span className="text-display font-bold tracking-tight text-ink-primary leading-none">ChatNook</span>
    </span>
  )
}
