// Original ChatNook mark (spec §1.6): a monogram "N" in a warm tile with a
// spark for the nook. No Discord likeness, no purple prototype "C".
import { cn } from '../lib/utils'

export function BrandMark({ size = 36, className }: { size?: number; className?: string }) {
  return (
    <span
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center rounded-[30%] text-ink-accent shadow-glow',
        className,
      )}
      style={{
        width: size,
        height: size,
        backgroundImage:
          'linear-gradient(145deg, rgb(var(--brand-glow)), rgb(var(--brand)) 48%, rgb(var(--brand-strong)))',
      }}
      aria-hidden
    >
      <svg viewBox="0 0 32 32" className="size-[62%]" fill="none">
        <path d="M9 22V10h3.4l6.2 7.2V10H22v12h-3.4l-6.2-7.2V22H9Z" fill="currentColor" />
        <circle cx="24.6" cy="7.6" r="2.1" fill="currentColor" opacity="0.5" />
      </svg>
      <span className="pointer-events-none absolute inset-0 rounded-[inherit] ring-1 ring-inset ring-white/20" />
    </span>
  )
}

export function Wordmark({
  className,
  showMark = true,
  shine = false,
}: {
  className?: string
  showMark?: boolean
  shine?: boolean
}) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      {showMark && <BrandMark size={30} />}
      <span
        className={cn(
          'text-display font-bold leading-none tracking-tight',
          shine ? 'text-gradient' : 'text-ink-primary',
        )}
      >
        ChatNook
      </span>
    </span>
  )
}
