// First-run welcome (spec §3.2). Three short beats, then straight into the app.
import { useState } from 'react'
import { Hash, MessageCircle, Server } from 'lucide-react'
import { useAppStore } from '../../store/app'
import { t } from '../../lib/i18n'
import { cn } from '../../lib/utils'
import { Button } from '../../components/ui/base'
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
  const open = overlay === 'onboarding'
  const current = STEPS[step]

  const finish = () => {
    try { localStorage.setItem('chatnook.onboarded', '1') } catch { /* storage blocked */ }
    setStep(0)
    setOverlay(null)
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) finish() }}>
      <DialogContent className="max-w-md text-center">
        <div className="mx-auto grid size-12 place-items-center rounded-xl bg-brand/12 text-brand">
          <current.icon className="size-6" />
        </div>
        <DialogTitle className="mt-4">{current.title}</DialogTitle>
        <DialogDescription>{current.body}</DialogDescription>

        <div className="mt-5 flex items-center justify-center gap-1.5" aria-hidden>
          {STEPS.map((_, i) => (
            <span key={i} className={cn('h-1.5 rounded-full transition-all', i === step ? 'w-6 bg-brand' : 'w-1.5 bg-line/25')} />
          ))}
        </div>

        <div className="mt-5 flex items-center justify-between gap-2">
          <Button variant="ghost" onClick={finish}>{t('onboarding.skip')}</Button>
          {step < STEPS.length - 1 ? (
            <Button onClick={() => setStep((s) => s + 1)}>{t('onboarding.next')}</Button>
          ) : (
            <Button onClick={finish}>{t('onboarding.done')}</Button>
          )}
        </div>
        <p className="sr-only">{t('onboarding.subtitle')}</p>
      </DialogContent>
    </Dialog>
  )
}
