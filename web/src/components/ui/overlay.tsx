// Radix-backed overlays: dialog, sheet, dropdown/context menu, tooltip, popover, hover-card.
import * as React from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import * as DropdownMenuPrimitive from '@radix-ui/react-dropdown-menu'
import * as ContextMenuPrimitive from '@radix-ui/react-context-menu'
import * as TooltipPrimitive from '@radix-ui/react-tooltip'
import * as PopoverPrimitive from '@radix-ui/react-popover'
import * as HoverCardPrimitive from '@radix-ui/react-hover-card'
import { X } from 'lucide-react'
import { cn } from '../../lib/utils'
import { t } from '../../lib/i18n'
import { useMediaQuery } from '../../lib/useMediaQuery'

export const Dialog = DialogPrimitive.Root
export const DialogTrigger = DialogPrimitive.Trigger
export const DialogClose = DialogPrimitive.Close
export const DialogPortal = DialogPrimitive.Portal

const overlaySurface =
  'fixed z-50 border border-line/12 bg-elevated shadow-pop text-ink-primary ' +
  'data-[state=open]:animate-scale-in'

export const DialogOverlay = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn('fixed inset-0 z-40 bg-black/60 backdrop-blur-sm data-[state=open]:animate-fade-in', className)}
    {...props}
  />
))
DialogOverlay.displayName = 'DialogOverlay'

export const DialogContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> & { hideClose?: boolean }
>(({ className, children, hideClose, ...props }, ref) => (
  <DialogPortal>
    <DialogOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(
        overlaySurface,
        'left-1/2 top-1/2 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-lg p-6',
        className,
      )}
      {...props}
    >
      {children}
      {!hideClose && (
        <DialogPrimitive.Close
          className="absolute right-4 top-4 rounded-sm p-1 text-ink-secondary hover:bg-hover hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
          aria-label={t('a11y.close')}
        >
          <X className="size-4" />
        </DialogPrimitive.Close>
      )}
    </DialogPrimitive.Content>
  </DialogPortal>
))
DialogContent.displayName = 'DialogContent'

export const DialogTitle = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Title ref={ref} className={cn('text-title font-semibold tracking-tight', className)} {...props} />
))
DialogTitle.displayName = 'DialogTitle'

export const DialogDescription = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Description ref={ref} className={cn('text-sm text-ink-secondary mt-1.5', className)} {...props} />
))
DialogDescription.displayName = 'DialogDescription'

// ---------------------------------------------------------------- sheet (mobile drawers)
const sheetSides = {
  left: 'inset-y-0 left-0 h-full w-[86%] max-w-[320px] border-r rounded-none data-[state=open]:animate-slide-from-left',
  right: 'inset-y-0 right-0 h-full w-[86%] max-w-[360px] border-l rounded-none data-[state=open]:animate-slide-from-right',
  bottom: 'inset-x-0 bottom-0 w-full max-h-[85vh] rounded-t-xl border-t data-[state=open]:animate-slide-from-bottom',
} as const

export const Sheet = DialogPrimitive.Root
export const SheetTrigger = DialogPrimitive.Trigger
export const SheetClose = DialogPrimitive.Close

export const SheetContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> & { side?: keyof typeof sheetSides; hideClose?: boolean }
>(({ className, children, side = 'left', hideClose, ...props }, ref) => (
  <DialogPortal>
    <DialogOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(overlaySurface, 'fixed p-0 flex flex-col', sheetSides[side], className)}
      {...props}
    >
      {children}
      {!hideClose && (
        <DialogPrimitive.Close
          className="absolute right-3 top-3 z-10 rounded-sm p-1.5 text-ink-secondary hover:bg-hover hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
          aria-label={t('a11y.close')}
        >
          <X className="size-4" />
        </DialogPrimitive.Close>
      )}
    </DialogPrimitive.Content>
  </DialogPortal>
))
SheetContent.displayName = 'SheetContent'

export const SheetTitle = DialogTitle
export const SheetDescription = DialogDescription

// ---------------------------------------------------------------- dropdown menu
export const DropdownMenu = DropdownMenuPrimitive.Root
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger
export const DropdownMenuGroup = DropdownMenuPrimitive.Group

const menuContentCls =
  'z-50 min-w-[200px] overflow-hidden rounded-lg border border-line/12 bg-elevated p-1.5 shadow-pop text-ink-primary ' +
  'data-[state=open]:animate-scale-in'

export const DropdownMenuContent = React.forwardRef<
  React.ElementRef<typeof DropdownMenuPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Content>
>(({ className, sideOffset = 6, ...props }, ref) => (
  <DropdownMenuPrimitive.Portal>
    <DropdownMenuPrimitive.Content ref={ref} sideOffset={sideOffset} className={cn(menuContentCls, className)} {...props} />
  </DropdownMenuPrimitive.Portal>
))
DropdownMenuContent.displayName = 'DropdownMenuContent'

const itemCls =
  'relative flex cursor-pointer select-none items-center gap-2.5 rounded-sm px-2.5 py-2 text-sm outline-none ' +
  'transition-colors focus:bg-hover data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&_svg]:size-4'

export const DropdownMenuItem = React.forwardRef<
  React.ElementRef<typeof DropdownMenuPrimitive.Item>,
  React.ComponentPropsWithoutRef<typeof DropdownMenuPrimitive.Item> & { destructive?: boolean }
>(({ className, destructive, ...props }, ref) => (
  <DropdownMenuPrimitive.Item
    ref={ref}
    className={cn(itemCls, destructive && 'text-danger focus:bg-danger/12 focus:text-danger', className)}
    {...props}
  />
))
DropdownMenuItem.displayName = 'DropdownMenuItem'

export function DropdownMenuSeparator({ className }: { className?: string }) {
  return <DropdownMenuPrimitive.Separator className={cn('my-1.5 h-px bg-line/10', className)} />
}

export function DropdownMenuLabel({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-2.5 py-1.5 text-xs font-semibold uppercase tracking-wide text-ink-muted', className)} {...props} />
}

// ---------------------------------------------------------------- context menu
export const ContextMenu = ContextMenuPrimitive.Root
export const ContextMenuTrigger = ContextMenuPrimitive.Trigger
export const ContextMenuContent = React.forwardRef<
  React.ElementRef<typeof ContextMenuPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof ContextMenuPrimitive.Content>
>(({ className, ...props }, ref) => (
  <ContextMenuPrimitive.Portal>
    <ContextMenuPrimitive.Content ref={ref} className={cn(menuContentCls, className)} {...props} />
  </ContextMenuPrimitive.Portal>
))
ContextMenuContent.displayName = 'ContextMenuContent'
export const ContextMenuItem = React.forwardRef<
  React.ElementRef<typeof ContextMenuPrimitive.Item>,
  React.ComponentPropsWithoutRef<typeof ContextMenuPrimitive.Item> & { destructive?: boolean }
>(({ className, destructive, ...props }, ref) => (
  <ContextMenuPrimitive.Item
    ref={ref}
    className={cn(itemCls, destructive && 'text-danger focus:bg-danger/12 focus:text-danger', className)}
    {...props}
  />
))
ContextMenuItem.displayName = 'ContextMenuItem'

// ---------------------------------------------------------------- tooltip
export const TooltipProvider = TooltipPrimitive.Provider
export const Tooltip = TooltipPrimitive.Root
export const TooltipTrigger = TooltipPrimitive.Trigger
export const TooltipContent = React.forwardRef<
  React.ElementRef<typeof TooltipPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof TooltipPrimitive.Content>
>(({ className, sideOffset = 8, ...props }, ref) => (
  <TooltipPrimitive.Portal>
    <TooltipPrimitive.Content
      ref={ref}
      sideOffset={sideOffset}
      className={cn(
        'z-[60] rounded-md bg-elevated border border-line/12 px-2.5 py-1.5 text-xs font-medium text-ink-primary shadow-pop',
        'data-[state=delayed-open]:animate-scale-in',
        className,
      )}
      {...props}
    />
  </TooltipPrimitive.Portal>
))
TooltipContent.displayName = 'TooltipContent'

/** Convenience: an icon button wrapped with a labeled tooltip (a11y §4). */
export function IconTooltip({ label, children, side = 'right' }: { label: string; children: React.ReactNode; side?: 'top' | 'right' | 'bottom' | 'left' }) {
  // Touch devices have no hover: a tap would leave the tooltip stuck open.
  const canHover = useMediaQuery('(hover: hover)')
  if (!canHover) return <>{children}</>
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent side={side}>{label}</TooltipContent>
    </Tooltip>
  )
}

// ---------------------------------------------------------------- popover / hover card
export const Popover = PopoverPrimitive.Root
export const PopoverTrigger = PopoverPrimitive.Trigger
export const PopoverAnchor = PopoverPrimitive.Anchor
export const PopoverContent = React.forwardRef<
  React.ElementRef<typeof PopoverPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof PopoverPrimitive.Content>
>(({ className, align = 'start', sideOffset = 8, ...props }, ref) => (
  <PopoverPrimitive.Portal>
    <PopoverPrimitive.Content
      ref={ref}
      align={align}
      sideOffset={sideOffset}
      className={cn('z-50 rounded-lg border border-line/12 bg-elevated p-2 shadow-pop outline-none data-[state=open]:animate-scale-in', className)}
      {...props}
    />
  </PopoverPrimitive.Portal>
))
PopoverContent.displayName = 'PopoverContent'

export const HoverCard = HoverCardPrimitive.Root
export const HoverCardTrigger = HoverCardPrimitive.Trigger
export const HoverCardContent = React.forwardRef<
  React.ElementRef<typeof HoverCardPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof HoverCardPrimitive.Content>
>(({ className, align = 'start', sideOffset = 8, ...props }, ref) => (
  <HoverCardPrimitive.Portal>
    <HoverCardPrimitive.Content
      ref={ref}
      align={align}
      sideOffset={sideOffset}
      className={cn('z-50 w-80 rounded-lg border border-line/12 bg-elevated shadow-pop outline-none data-[state=open]:animate-scale-in', className)}
      {...props}
    />
  </HoverCardPrimitive.Portal>
))
HoverCardContent.displayName = 'HoverCardContent'
