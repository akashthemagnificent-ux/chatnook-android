// Animated gradient text (reactbits/magicui), used for the wordmark only.
import type { ReactNode } from 'react'
import { cn } from '../../lib/utils'

export function GradientText({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={cn('text-gradient', className)}>{children}</span>
}
