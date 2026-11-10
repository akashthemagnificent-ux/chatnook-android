// First-run welcome (spec §3.2): a bento tour with three short beats, then in.
import { useState } from 'react'
import { Hash, MessageCircle, Server } from 'lucide-react'
import { useAppStore } from '../../store/app'
import { t } from '../../lib/i18n'
import { cn } from '../../lib/utils'
import { Button } from '../../components/ui/base'
import { BrandMark } from '../../components/Brand'
import { BorderBeam, BentoCard, BentoGrid } from '../../components/fx/surfaces'
import { ShimmerButton } from '../../components/fx/buttons'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '../../components/ui/overlay'

const STEPS = [
  { icon: Server, title: t('onboarding.serversTitle'), body: t('onboarding.serversBody') },
  { icon: Hash, title: t('onboarding.channelsTitle'), body: t('onboarding.channelsBody') },
  { icon: MessageCircle, title: t('onboarding.dmsTitle'), body: t('onboarding.dmsBody') },
]

export function OnboardingDialog() {
  const overlay = useAppStore((s) => s.overlay)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const [step, setStep] = useState(0)

  const finish = () => {
    try { localStorage.setItem('chatnook.onboarded', '1') } catch { /* storage blocked */ }
    setStep(0)
    setOverlay(null)
  }

  return (
    <Dialog open={overlay === 'onboarding'} onOpenChange={(v) => { if (!v) finish() }}>
      <DialogContent
        hideClose
        className="max-w-2xl overflow-hidden p-0 outline-none"
        // Focus the panel, not "Skip tour": a ring on the dismissal link at
        // first paint reads as a selected button.
        onOpenAutoFocus={(e) => { e.preventDefault(); (e.currentTarget as HTMLElement | null)?.focus() }}
      >
        <BorderBeam duration={10} />
        <div className="p-6 sm:p-7">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <BrandMark size={28} />
              <span className="text-sm font-semibold tracking-tight text-ink-primary">ChatNook</span>
            </div>
            <button onClick={finish} className="text-sm text-ink-muted transition-colors hover:text-ink-primary">
              {t('onboarding.skip')}
            </button>
          </div>

          <DialogTitle className="mt-5 text-title font-bold">{t('onboarding.title')}</DialogTitle>
          <DialogDescription>{t('onboarding.subtitle')}</DialogDescription>

          <BentoGrid className="mt-5">
            {STEPS.map((s, i) => (
              <BentoCard
                key={s.title}
                icon={<s.icon className="size-5" />}
                title={s.title}
                body={s.body}
                active={i === step}
                onClick={() => setStep(i)}
              />
            ))}
          </BentoGrid>

          <div className="mt-6 flex items-center justify-between gap-3">
            <div className="flex items-center gap-1.5" aria-hidden>
              {STEPS.map((_, i) => (
                <span key={i} className={cn('h-1.5 rounded-full transition-all duration-300', i === step ? 'w-7 bg-brand' : 'w-1.5 bg-line/25')} />
              ))}
            </div>
            {step < STEPS.length - 1 ? (
              <Button onClick={() => setStep((s) => s + 1)}>{t('onboarding.next')}</Button>
            ) : (
              <ShimmerButton onClick={finish}>{t('onboarding.done')}</ShimmerButton>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
