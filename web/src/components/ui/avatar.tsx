// shadcn avatar + token-driven status dot overlay (spec §3.8).
import * as React from 'react'
import * as AvatarPrimitive from '@radix-ui/react-avatar'
import { cn } from '../../lib/utils'
import { initials } from '../../lib/types'

export const Avatar = React.forwardRef<
  React.ElementRef<typeof AvatarPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Root>
>(({ className, ...props }, ref) => (
  <AvatarPrimitive.Root
    ref={ref}
    className={cn('relative flex shrink-0 overflow-hidden rounded-full bg-hover ring-1 ring-line/10', className)}
    {...props}
  />
))
Avatar.displayName = 'Avatar'

export const AvatarImage = React.forwardRef<
  React.ElementRef<typeof AvatarPrimitive.Image>,
  React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Image>
>(({ className, ...props }, ref) => (
  <AvatarPrimitive.Image ref={ref} className={cn('aspect-square h-full w-full object-cover', className)} {...props} />
))
AvatarImage.displayName = 'AvatarImage'

export const AvatarFallback = React.forwardRef<
  React.ElementRef<typeof AvatarPrimitive.Fallback>,
  React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Fallback>
>(({ className, ...props }, ref) => (
  <AvatarPrimitive.Fallback
    ref={ref}
    className={cn('flex h-full w-full items-center justify-center rounded-full bg-hover text-ink-secondary font-semibold', className)}
    {...props}
  />
))
AvatarFallback.displayName = 'AvatarFallback'

export type Presence = 'online' | 'idle' | 'dnd' | 'offline'

const presenceColor: Record<Presence, string> = {
  online: 'bg-success',
  idle: 'bg-warning',
  dnd: 'bg-danger',
  offline: 'bg-ink-muted',
}

/** A ready-to-use avatar with initials fallback and an optional presence ring. */
export function UserAvatar({
  name,
  src,
  size = 40,
  presence,
  className,
  ring,
}: {
  name: string
  src?: string | null
  size?: number
  presence?: Presence
  className?: string
  ring?: boolean
}) {
  return (
    <span className={cn('relative inline-flex shrink-0', className)} style={{ width: size, height: size }}>
      <Avatar className={cn('h-full w-full', ring && 'ring-2 ring-brand ring-offset-2 ring-offset-base')}>
        {src ? <AvatarImage src={src} alt="" /> : null}
        <AvatarFallback style={{ fontSize: Math.max(11, size * 0.34) }}>{initials(name)}</AvatarFallback>
      </Avatar>
      {presence && (
        <span
          className={cn(
            'absolute bottom-0 right-0 rounded-full border-[2.5px] border-surface',
            presenceColor[presence],
          )}
          style={{ width: Math.max(10, size * 0.3), height: Math.max(10, size * 0.3) }}
          aria-label={presence}
        />
      )}
    </span>
  )
}
