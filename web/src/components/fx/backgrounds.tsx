// Library-style backdrops (magicui aurora + aceternity grid), CSS-only.
import { cn } from '../../lib/utils'

export function GridBackdrop({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={cn('pointer-events-none absolute inset-0', className)}
      style={{
        backgroundImage:
          'linear-gradient(rgb(var(--line)) 1px, transparent 1px), linear-gradient(90deg, rgb(var(--line)) 1px, transparent 1px)',
        backgroundSize: '56px 56px',
        maskImage: 'radial-gradient(ellipse at center, black, transparent 72%)',
        WebkitMaskImage: 'radial-gradient(ellipse at center, black, transparent 72%)',
      }}
    />
  )
}

/** Slow drifting colour field. Kept behind a dark vignette so it never competes. */
export function Aurora({ className, dim = false }: { className?: string; dim?: boolean }) {
  return (
    <div aria-hidden className={cn('pointer-events-none absolute inset-0 overflow-hidden', className)}>
      <div className={cn('absolute -left-[18%] -top-[28%] h-[75vh] w-[75vw] rounded-full bg-brand/25 blur-[130px] animate-aurora', dim && 'opacity-50')} />
      <div className={cn('absolute -right-[22%] top-[6%] h-[60vh] w-[60vw] rounded-full bg-info/20 blur-[140px] animate-aurora-alt', dim && 'opacity-50')} />
      <div
        className={cn('absolute -bottom-[34%] left-[18%] h-[65vh] w-[65vw] rounded-full bg-success/15 blur-[150px] animate-aurora', dim && 'opacity-40')}
        style={{ animationDelay: '-8s' }}
      />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,transparent_18%,rgb(var(--bg-base))_76%)]" />
      <GridBackdrop className="opacity-[0.035]" />
    </div>
  )
}
