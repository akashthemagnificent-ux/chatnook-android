// Branded boot splash (spec §3.13): aurora, pulsing mark, wordmark, loader.
import { BrandMark } from './Brand'
import { LoaderDots } from './LoaderDots'
import { Aurora } from './fx/backgrounds'
import { GradientText } from './fx/text'
import { t } from '../lib/i18n'

export function Splash() {
  return (
    <div className="grain relative flex h-full w-full flex-col items-center justify-center gap-7 overflow-hidden bg-base">
      <Aurora dim />
      <div className="relative">
        <span aria-hidden className="absolute inset-0 animate-pulse-ring rounded-[30%]" />
        <BrandMark size={72} className="animate-scale-in" />
      </div>
      <div className="relative flex animate-fade-up flex-col items-center gap-1.5" style={{ animationDelay: '120ms' }}>
        <span className="text-display font-bold leading-none tracking-tight"><GradientText>ChatNook</GradientText></span>
        <p className="text-sm text-ink-secondary">{t('app.tagline')}</p>
      </div>
      <LoaderDots className="relative text-brand" label={t('splash.loading')} />
    </div>
  )
}
