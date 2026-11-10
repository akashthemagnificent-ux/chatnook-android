// ⌘K command palette built on cmdk (spec §3.10).
import * as React from 'react'
import { Command as CommandPrimitive } from 'cmdk'
import { Search } from 'lucide-react'
import { cn } from '../../lib/utils'
import { t } from '../../lib/i18n'
import { Dialog, DialogContent, DialogTitle, DialogDescription } from './overlay'
import { Kbd } from './base'

export const Command = React.forwardRef<
  React.ElementRef<typeof CommandPrimitive>,
  React.ComponentPropsWithoutRef<typeof CommandPrimitive>
>(({ className, ...props }, ref) => (
  <CommandPrimitive
    ref={ref}
    className={cn('flex h-full w-full flex-col overflow-hidden rounded-lg bg-elevated text-ink-primary', className)}
    {...props}
  />
))
Command.displayName = 'Command'

export const CommandInput = React.forwardRef<
  React.ElementRef<typeof CommandPrimitive.Input>,
  React.ComponentPropsWithoutRef<typeof CommandPrimitive.Input>
>(({ className, ...props }, ref) => (
  <div className="flex items-center gap-2.5 border-b border-line/10 px-4">
    <Search className="size-4 shrink-0 text-ink-muted" />
    <CommandPrimitive.Input
      ref={ref}
      className={cn(
        'flex h-12 w-full rounded-md bg-transparent py-3 text-base outline-none placeholder:text-ink-muted disabled:opacity-50',
        className,
      )}
      {...props}
    />
    <Kbd>Esc</Kbd>
  </div>
))
CommandInput.displayName = 'CommandInput'

export const CommandList = React.forwardRef<
  React.ElementRef<typeof CommandPrimitive.List>,
  React.ComponentPropsWithoutRef<typeof CommandPrimitive.List>
>(({ className, ...props }, ref) => (
  <CommandPrimitive.List ref={ref} className={cn('max-h-[360px] overflow-y-auto overflow-x-hidden p-2 scrollbar-thin', className)} {...props} />
))
CommandList.displayName = 'CommandList'

export const CommandEmpty = React.forwardRef<
  React.ElementRef<typeof CommandPrimitive.Empty>,
  React.ComponentPropsWithoutRef<typeof CommandPrimitive.Empty>
>((props, ref) => (
  <CommandPrimitive.Empty ref={ref} className="py-10 text-center text-sm text-ink-secondary" {...props} />
))
CommandEmpty.displayName = 'CommandEmpty'

export const CommandGroup = React.forwardRef<
  React.ElementRef<typeof CommandPrimitive.Group>,
  React.ComponentPropsWithoutRef<typeof CommandPrimitive.Group>
>(({ className, ...props }, ref) => (
  <CommandPrimitive.Group
    ref={ref}
    className={cn(
      'overflow-hidden p-1 text-ink-primary [&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:uppercase [&_[cmdk-group-heading]]:tracking-wide [&_[cmdk-group-heading]]:text-ink-muted',
      className,
    )}
    {...props}
  />
))
CommandGroup.displayName = 'CommandGroup'

export const CommandItem = React.forwardRef<
  React.ElementRef<typeof CommandPrimitive.Item>,
  React.ComponentPropsWithoutRef<typeof CommandPrimitive.Item>
>(({ className, ...props }, ref) => (
  <CommandPrimitive.Item
    ref={ref}
    className={cn(
      'relative flex cursor-pointer select-none items-center gap-2.5 rounded-md px-2.5 py-2.5 text-sm outline-none',
      'data-[selected=true]:bg-hover data-[selected=true]:text-ink-primary data-[disabled=true]:pointer-events-none data-[disabled=true]:opacity-50',
      className,
    )}
    {...props}
  />
))
CommandItem.displayName = 'CommandItem'

export function CommandSeparator({ className }: { className?: string }) {
  return <CommandPrimitive.Separator className={cn('my-1.5 h-px bg-line/10', className)} />
}

/** Dialog wrapper so the palette overlays everything. */
export function CommandDialog({ open, onOpenChange, label, children }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  label: string
  children: React.ReactNode
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent hideClose className="max-w-xl p-0 top-[18%] translate-y-0">
        <DialogTitle className="sr-only">{label}</DialogTitle>
        <DialogDescription className="sr-only">{t('a11y.searchDialog')}</DialogDescription>
        <Command loop>{children}</Command>
      </DialogContent>
    </Dialog>
  )
}
