// Login / sign-up / password reset (spec §3.1). Centered card on a subtle aurora
// backdrop, react-hook-form validation, inline errors, and clear loading states.
import { lazy, Suspense, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { useForm } from 'react-hook-form'
import { ArrowLeft, Eye, EyeOff, Lock, Mail, Sparkles } from 'lucide-react'
import { Wordmark } from '../../components/Brand'
import { LoaderDots } from '../../components/LoaderDots'
import { ShimmerButton } from '../../components/fx/buttons'
import { BorderBeam, SpotlightCard } from '../../components/fx/surfaces'
import {
  Button, Input, Label, Separator, Card, CardContent,
} from '../../components/ui/base'
import { toast } from '../../components/ui/toast'
import { useSession } from '../../store/session'
import { t } from '../../lib/i18n'

const AuroraBackground = lazy(() => import('../../components/AuroraBackground'))

type Mode = 'sign-in' | 'sign-up' | 'reset'
type Fields = { email: string; password: string; confirm: string }

const emailRule = {
  required: t('auth.invalidEmail'),
  pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: t('auth.invalidEmail') },
}
const passwordRule = { required: t('auth.passwordShort'), minLength: { value: 6, message: t('auth.passwordShort') } }

export function AuthScreen() {
  const [mode, setMode] = useState<Mode>('sign-in')
  const [submitting, setSubmitting] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const signIn = useSession((s) => s.signIn)
  const signUp = useSession((s) => s.signUp)
  const signInWithMagicLink = useSession((s) => s.signInWithMagicLink)
  const signInAsGuest = useSession((s) => s.signInAsGuest)
  const sendReset = useSession((s) => s.sendReset)

  const { register, handleSubmit, getValues, formState: { errors }, reset: resetForm } = useForm<Fields>({
    defaultValues: { email: '', password: '', confirm: '' },
  })

  const switchMode = (next: Mode) => {
    setMode(next)
    resetForm({ email: getValues('email'), password: '', confirm: '' })
  }

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true)
    try {
      if (mode === 'sign-in') await signIn(values.email, values.password)
      else if (mode === 'sign-up') await signUp(values.email, values.password)
      else {
        await sendReset(values.email)
        toast.success(t('auth.resetSent'))
        switchMode('sign-in')
      }
    } catch {
      toast.error(t('auth.error'))
    } finally {
      setSubmitting(false)
    }
  })

  const onMagicLink = async () => {
    const email = getValues('email')
    if (!emailRule.pattern.value.test(email)) {
      toast.error(t('auth.invalidEmail'))
      return
    }
    setSubmitting(true)
    try {
      await signInWithMagicLink(email)
      toast.success(t('auth.magicLinkSent'))
    } catch {
      toast.error(t('auth.error'))
    } finally {
      setSubmitting(false)
    }
  }

  const onGuest = async () => {
    setSubmitting(true)
    try { await signInAsGuest() } catch { toast.error(t('auth.error')) } finally { setSubmitting(false) }
  }

  const title = mode === 'sign-in' ? t('auth.welcome') : mode === 'sign-up' ? t('auth.createTitle') : t('auth.resetTitle')
  const subtitle = mode === 'sign-in' ? t('auth.subtitle') : mode === 'sign-up' ? t('auth.createSubtitle') : t('auth.resetSubtitle')
  const cta = mode === 'sign-in' ? t('auth.signIn') : mode === 'sign-up' ? t('auth.create') : t('auth.resetSend')
  const busyLabel = mode === 'sign-up' ? t('auth.creating') : t('auth.signingIn')

  return (
    <div className="grain relative flex min-h-full w-full items-center justify-center overflow-y-auto bg-base px-4 py-10">
      <Suspense fallback={null}><AuroraBackground /></Suspense>

      <div className="relative z-10 w-full max-w-[420px] animate-fade-up">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <Wordmark shine />
          <p className="text-sm text-ink-secondary">{t('app.tagline')}</p>
        </div>

        <SpotlightCard className="rounded-2xl">
          <BorderBeam duration={9} />
          <Card className="relative rounded-2xl border-line/10 bg-surface/85 shadow-pop backdrop-blur-xl">
            <CardContent className="p-6 sm:p-7">
            {mode === 'reset' && (
              <button onClick={() => switchMode('sign-in')}
                className="mb-4 inline-flex items-center gap-1.5 text-sm text-ink-secondary transition-colors hover:text-ink-primary">
                <ArrowLeft className="size-4" /> {t('common.back')}
              </button>
            )}
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={mode}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.18, ease: [0.22, 1, 0.36, 1] }}
              >
                <h1 className="text-title font-bold tracking-tight text-ink-primary">{title}</h1>
                <p className="mt-1 text-sm text-ink-secondary">{subtitle}</p>
              </motion.div>
            </AnimatePresence>

            <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="email">{t('common.email')}</Label>
                <div className="relative">
                  <Mail className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
                  <Input id="email" type="email" autoComplete="email" placeholder={t('auth.emailPlaceholder')}
                    className="pl-9" aria-invalid={Boolean(errors.email)}
                    {...register('email', emailRule)} />
                </div>
                {errors.email && <p className="text-xs text-danger">{errors.email.message}</p>}
              </div>

              {mode !== 'reset' && (
                <>
                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="password">{t('common.password')}</Label>
                      {mode === 'sign-in' && (
                        <button type="button" onClick={() => switchMode('reset')}
                          className="text-xs font-medium text-brand hover:underline">{t('auth.forgot')}</button>
                      )}
                    </div>
                    <div className="relative">
                      <Lock className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
                      <Input id="password" type={showPassword ? 'text' : 'password'}
                        autoComplete={mode === 'sign-in' ? 'current-password' : 'new-password'}
                        placeholder={t('auth.passwordPlaceholder')} className="pl-9 pr-10"
                        aria-invalid={Boolean(errors.password)} {...register('password', passwordRule)} />
                      <button type="button" onClick={() => setShowPassword((v) => !v)}
                        aria-label={showPassword ? t('common.hidePassword') : t('common.showPassword')}
                        className="absolute right-2 top-1/2 -translate-y-1/2 rounded-sm p-1.5 text-ink-secondary hover:text-ink-primary">
                        {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                      </button>
                    </div>
                    {errors.password && <p className="text-xs text-danger">{errors.password.message}</p>}
                  </div>

                  {mode === 'sign-up' && (
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="confirm">{t('auth.confirmPassword')}</Label>
                      <Input id="confirm" type={showPassword ? 'text' : 'password'} autoComplete="new-password"
                        aria-invalid={Boolean(errors.confirm)}
                        {...register('confirm', {
                          required: t('auth.passwordsDiffer'),
                          validate: (v) => v === getValues('password') || t('auth.passwordsDiffer'),
                        })} />
                      {errors.confirm && <p className="text-xs text-danger">{errors.confirm.message}</p>}
                    </div>
                  )}
                </>
              )}

              <ShimmerButton type="submit" size="lg" full disabled={submitting} className="mt-1">
                {submitting ? <><LoaderDots /> <span>{busyLabel}</span></> : cta}
              </ShimmerButton>
            </form>

            {mode === 'sign-in' && (
              <>
                <div className="my-5 flex items-center gap-3">
                  <Separator className="flex-1" />
                  <span className="text-xs uppercase tracking-wide text-ink-muted">{t('common.or')}</span>
                  <Separator className="flex-1" />
                </div>
                <Button variant="outline" className="w-full" disabled={submitting} onClick={onMagicLink}>
                  <Sparkles className="size-4" /> {t('auth.magicLink')}
                </Button>
                <Button variant="ghost" className="mt-2 w-full" disabled={submitting} onClick={onGuest}>
                  {t('auth.guest')}
                </Button>
                <p className="mt-2 text-center text-xs text-ink-muted">{t('auth.guestWarning')}</p>
              </>
            )}

            <div className="mt-6 text-center text-sm text-ink-secondary">
              {mode === 'sign-in' ? (
                <button className="font-medium text-brand transition-colors hover:text-brand-glow" onClick={() => switchMode('sign-up')}>
                  {t('auth.noAccount')}
                </button>
              ) : (
                <button className="font-medium text-brand transition-colors hover:text-brand-glow" onClick={() => switchMode('sign-in')}>
                  {t('auth.haveAccount')}
                </button>
              )}
            </div>
            </CardContent>
          </Card>
        </SpotlightCard>
      </div>
    </div>
  )
}
