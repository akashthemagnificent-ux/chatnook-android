// sonner Toaster, token-styled (spec §3.11): top-right desktop, bottom mobile.
import { Toaster as SonnerToaster } from 'sonner'
import { useTheme } from '../../lib/theme'
import { useMediaQuery } from '../../lib/useMediaQuery'

export { toast } from 'sonner'

export function Toaster() {
  const { theme } = useTheme()
  const isDesktop = useMediaQuery('(min-width: 768px)')
  return (
    <SonnerToaster
      theme={theme}
      position={isDesktop ? 'top-right' : 'bottom-center'}
      richColors
      closeButton
      toastOptions={{
        classNames: {
          toast: '!bg-elevated !border-line/12 !text-ink-primary !rounded-lg !shadow-pop',
          title: '!text-ink-primary !font-semibold',
          description: '!text-ink-secondary',
          actionButton: '!bg-brand !text-ink-accent',
          cancelButton: '!bg-hover !text-ink-secondary',
        },
      }}
    />
  )
}
