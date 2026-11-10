// Branded boot splash (spec §3.13), shown only until the session resolves.
import { BrandMark } from './Brand'
import { LoaderDots } from './LoaderDots'
import { t } from '../lib/i18n'

export function Splash() {
  return (
    <div className="flex h-full w-full flex-col items-center justify-center gap-6 bg-base">
      <BrandMark size={64} className="animate-scale-in" />
      <span className="text-display font-bold tracking-tight text-ink-primary animate-fade-up">{t('app.name')}</span>
      <LoaderDots className="text-ink-muted" label={t('splash.loading')} />
    </div>
  )
}
