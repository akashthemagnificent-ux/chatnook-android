// shadcn-style primitives, hand-built on §1 tokens (no runtime library dep).
import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { Slot } from '@radix-ui/react-slot'
import * as LabelPrimitive from '@radix-ui/react-label'
import * as SeparatorPrimitive from '@radix-ui/react-separator'
import { cn } from '../../lib/utils'

// ---------------------------------------------------------------- button
const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md font-medium ' +
    'transition-all duration-150 ease-out select-none active:scale-[0.98] ' +
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2 focus-visible:ring-offset-base ' +
    'disabled:pointer-events-none disabled:opacity-45 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        primary:
          'bg-brand text-ink-accent shadow-soft hover:bg-brand-strong hover:shadow-pop ' +
          'relative overflow-hidden',
        default: 'bg-elevated text-ink-primary border border-line/10 hover:bg-hover',
        ghost: 'text-ink-secondary hover:bg-hover hover:text-ink-primary',
        destructive: 'bg-danger/12 text-danger hover:bg-danger/20 border border-danger/20',
        outline: 'border border-line/15 text-ink-primary hover:bg-hover hover:border-line/25',
        link: 'text-brand underline-offset-4 hover:underline p-0 h-auto min-h-0',
      },
      size: {
        sm: 'h-9 min-h-[44px] sm:min-h-0 px-3 text-sm [&_svg]:size-4 rounded-sm',
        md: 'h-11 px-4 text-base [&_svg]:size-[18px]',
        lg: 'h-12 px-6 text-md [&_svg]:size-5',
        icon: 'size-11 p-0 [&_svg]:size-5',
        'icon-sm': 'size-9 min-h-0 p-0 [&_svg]:size-4 rounded-sm',
      },
    },
    defaultVariants: { variant: 'default', size: 'md' },
  },
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, children, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button'
    return (
      <Comp className={cn(buttonVariants({ variant, size }), className)} ref={ref} {...props}>
        {variant === 'primary' ? (
          <>
            {/* magicui shimmer-button idea: a restrained sheen on hover only. */}
            <span
              aria-hidden
              className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/20 to-transparent transition-transform duration-500 ease-out group-hover:translate-x-full"
            />
            {children}
          </>
        ) : (
          children
        )}
      </Comp>
    )
  },
)
Button.displayName = 'Button'

// Primary buttons are wrapped in a `group` by callers; this wrapper guarantees it.
export function PrimaryButton(props: ButtonProps) {
  return (
    <span className="group inline-flex">
      <Button variant="primary" {...props} />
    </span>
  )
}

// ---------------------------------------------------------------- input
export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type, ...props }, ref) => (
    <input
      type={type}
      ref={ref}
      className={cn(
        'flex h-11 w-full rounded-md border border-line/12 bg-base/40 px-3.5 py-2 text-base text-ink-primary',
        'placeholder:text-ink-muted transition-colors duration-150',
        'focus-visible:outline-none focus-visible:border-brand/60 focus-visible:ring-2 focus-visible:ring-brand/30',
        'disabled:cursor-not-allowed disabled:opacity-50',
        'aria-[invalid=true]:border-danger aria-[invalid=true]:focus-visible:ring-danger/30',
        className,
      )}
      {...props}
    />
  ),
)
Input.displayName = 'Input'

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea
      ref={ref}
      className={cn(
        'flex w-full rounded-md border border-line/12 bg-base/40 px-3.5 py-2.5 text-base text-ink-primary',
        'placeholder:text-ink-muted transition-colors duration-150 resize-none',
        'focus-visible:outline-none focus-visible:border-brand/60 focus-visible:ring-2 focus-visible:ring-brand/30',
        'disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    />
  ),
)
Textarea.displayName = 'Textarea'

export const Label = React.forwardRef<
  React.ElementRef<typeof LabelPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof LabelPrimitive.Root>
>(({ className, ...props }, ref) => (
  <LabelPrimitive.Root
    ref={ref}
    className={cn('text-sm font-medium text-ink-primary leading-none peer-disabled:opacity-60', className)}
    {...props}
  />
))
Label.displayName = 'Label'

// ---------------------------------------------------------------- card
export const Card = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('rounded-lg border border-line/10 bg-surface', className)} {...props} />
  ),
)
Card.displayName = 'Card'

export const CardHeader = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('flex flex-col gap-1.5 p-5 pb-3', className)} {...props} />
  ),
)
CardHeader.displayName = 'CardHeader'

export const CardTitle = React.forwardRef<HTMLHeadingElement, React.HTMLAttributes<HTMLHeadingElement>>(
  ({ className, ...props }, ref) => (
    <h3 ref={ref} className={cn('text-md font-semibold text-ink-primary tracking-tight', className)} {...props} />
  ),
)
CardTitle.displayName = 'CardTitle'

export const CardDescription = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(
  ({ className, ...props }, ref) => (
    <p ref={ref} className={cn('text-sm text-ink-secondary', className)} {...props} />
  ),
)
CardDescription.displayName = 'CardDescription'

export const CardContent = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => <div ref={ref} className={cn('p-5 pt-0', className)} {...props} />,
)
CardContent.displayName = 'CardContent'

export const CardFooter = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('flex items-center gap-2 p-5 pt-0', className)} {...props} />
  ),
)
CardFooter.displayName = 'CardFooter'

// ---------------------------------------------------------------- badge
const badgeVariants = cva(
  'inline-flex items-center justify-center gap-1 rounded-full font-semibold tabular-nums leading-none',
  {
    variants: {
      variant: {
        default: 'bg-elevated text-ink-secondary border border-line/10',
        accent: 'bg-brand text-ink-accent',
        danger: 'bg-danger text-white',
        outline: 'border border-line/20 text-ink-secondary',
        dot: 'bg-ink-secondary/15 text-ink-secondary',
      },
      size: {
        sm: 'min-h-[18px] px-1.5 text-[0.6875rem]',
        md: 'min-h-[20px] px-2 text-xs',
      },
    },
    defaultVariants: { variant: 'default', size: 'md' },
  },
)
export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}
export function Badge({ className, variant, size, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant, size }), className)} {...props} />
}

// ---------------------------------------------------------------- separator / skeleton / kbd
export const Separator = React.forwardRef<
  React.ElementRef<typeof SeparatorPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof SeparatorPrimitive.Root>
>(({ className, orientation = 'horizontal', decorative = true, ...props }, ref) => (
  <SeparatorPrimitive.Root
    ref={ref}
    decorative={decorative}
    orientation={orientation}
    className={cn(
      'shrink-0 bg-line/10',
      orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px',
      className,
    )}
    {...props}
  />
))
Separator.displayName = 'Separator'

export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('animate-pulse rounded-md bg-elevated', className)} {...props} />
}

export function Kbd({ className, ...props }: React.HTMLAttributes<HTMLElement>) {
  return (
    <kbd
      className={cn(
        'inline-flex h-5 min-w-5 items-center justify-center rounded-sm border border-line/15 bg-elevated px-1.5',
        'font-mono text-[0.6875rem] font-medium text-ink-secondary',
        className,
      )}
      {...props}
    />
  )
}
