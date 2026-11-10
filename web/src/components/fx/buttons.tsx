// magicui shimmer-button, rebuilt on §1 tokens: gradient fill, travelling
// sheen, warm glow, spring press.
import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/utils'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  size?: 'sm' | 'md' | 'lg'
  full?: boolean
}

export const ShimmerButton = forwardRef<HTMLButtonElement, Props>(
  ({ className, children, size = 'md', full, ...props }, ref) => (
    <button
      ref={ref}
      {...props}
      className={cn(
        'group/shimmer relative inline-flex items-center justify-center gap-2 overflow-hidden rounded-full font-semibold',
        'bg-brand-sheen bg-[length:200%_100%] text-ink-accent shadow-glow',
        'transition-[transform,box-shadow,background-position] duration-300 ease-out',
        'hover:bg-right hover:shadow-[0_0_0_1px_rgb(var(--brand)/0.4),0_16px_44px_-12px_rgb(var(--brand)/0.65)]',
        // A faded gradient reads as muddy brown on dark, so disabled goes neutral.
        'active:scale-[0.985] disabled:pointer-events-none',
        'disabled:bg-none disabled:bg-elevated disabled:!text-ink-muted disabled:shadow-none',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2 focus-visible:ring-offset-base',
        size === 'lg' ? 'h-12 px-6 text-md' : size === 'sm' ? 'h-9 px-3.5 text-sm' : 'h-11 px-5 text-base',
        full && 'w-full',
        className,
      )}
    >
      <span aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden rounded-full">
        <span className="absolute inset-y-0 -left-1/3 w-1/3 -skew-x-12 animate-shine bg-sheen opacity-35 blur-[1px] group-disabled/shimmer:opacity-0" />
      </span>
      <span className="relative z-10 inline-flex items-center gap-2">{children}</span>
    </button>
  ),
)
ShimmerButton.displayName = 'ShimmerButton'
