// Library-style surfaces: aceternity spotlight card, magicui border beam and
// bento grid. All colour comes from §1 tokens.
import { useRef, type ReactNode } from 'react'
import { motion, useMotionTemplate, useMotionValue } from 'framer-motion'
import { cn } from '../../lib/utils'

/** A card whose surface lights up under the pointer (aceternity card-spotlight). */
export function SpotlightCard({
  children,
  className,
  glow = 0.14,
}: {
  children: ReactNode
  className?: string
  glow?: number
}) {
  const x = useMotionValue(-400)
  const y = useMotionValue(-400)
  const background = useMotionTemplate`radial-gradient(340px circle at ${x}px ${y}px, rgb(var(--brand) / ${glow}), transparent 72%)`
  const ref = useRef<HTMLDivElement>(null)

  return (
    <div
      ref={ref}
      onMouseMove={(e) => {
        const rect = e.currentTarget.getBoundingClientRect()
        x.set(e.clientX - rect.left)
        y.set(e.clientY - rect.top)
      }}
      className={cn('group/spot relative overflow-hidden', className)}
    >
      <motion.div
        aria-hidden
        style={{ background }}
        className="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-300 group-hover/spot:opacity-100"
      />
      {children}
    </div>
  )
}

/** A light that travels around the border (magicui border-beam). */
export function BorderBeam({
  className,
  duration = 7,
  delay = 0,
}: {
  className?: string
  duration?: number
  delay?: number
}) {
  return (
    <div
      aria-hidden
      className={cn('pointer-events-none absolute inset-0 overflow-hidden rounded-[inherit]', className)}
      style={{
        padding: 1,
        WebkitMask: 'linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)',
        WebkitMaskComposite: 'xor',
        maskComposite: 'exclude',
      }}
    >
      <div
        className="absolute inset-[-100%] animate-border-spin"
        style={{
          animationDuration: `${duration}s`,
          animationDelay: `${delay}s`,
          background:
            'conic-gradient(from 0deg, transparent 0deg, transparent 288deg, rgb(var(--brand) / 0.15) 310deg, rgb(var(--brand) / 0.95) 345deg, transparent 360deg)',
        }}
      />
    </div>
  )
}

export function BentoGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('grid gap-3 sm:grid-cols-3', className)}>{children}</div>
}

export function BentoCard({
  icon,
  title,
  body,
  active = false,
  className,
  onClick,
}: {
  icon: ReactNode
  title: string
  body: string
  active?: boolean
  className?: string
  onClick?: () => void
}) {
  const Tag = onClick ? 'button' : 'div'
  return (
    <Tag
      onClick={onClick}
      className={cn(
        'group/bento relative overflow-hidden rounded-xl border p-4 text-left transition-all duration-300 ease-out',
        active
          ? 'border-brand/35 bg-brand/[0.07] shadow-glow'
          : 'border-line/10 bg-elevated/50 hover:-translate-y-0.5 hover:border-line/20 hover:bg-elevated/80 hover:shadow-lift',
        className,
      )}
    >
      <div
        aria-hidden
        className={cn(
          'pointer-events-none absolute -right-10 -top-10 size-28 rounded-full bg-brand/20 blur-2xl transition-opacity duration-300',
          active ? 'opacity-100' : 'opacity-0 group-hover/bento:opacity-100',
        )}
      />
      <span
        className={cn(
          'relative flex size-10 items-center justify-center rounded-lg border transition-colors duration-300',
          active
            ? 'border-brand/30 bg-brand/15 text-brand'
            : 'border-line/10 bg-surface text-ink-secondary group-hover/bento:text-brand',
        )}
      >
        {icon}
      </span>
      <p className="relative mt-3 text-sm font-semibold text-ink-primary">{title}</p>
      <p className="relative mt-1 text-xs leading-relaxed text-ink-secondary">{body}</p>
    </Tag>
  )
}
