// Recovery-link handoff (spec §3.1 / §9.4): the reset link signs the user in with
// an event type of PASSWORD_RECOVERY, then we ask for the new password here.
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Lock } from 'lucide-react'
import { Wordmark } from '../../components/Brand'
import { LoaderDots } from '../../components/LoaderDots'
import { Button, Input, Label, Card, CardContent } from '../../components/ui/base'
import { toast } from '../../components/ui/toast'
import { useSession } from '../../store/session'
import { t } from '../../lib/i18n'

type Fields = { password: string; confirm: string }
const passwordRule = { required: t('auth.passwordShort'), minLength: { value: 6, message: t('auth.passwordShort') } }

export function RecoveryScreen() {
  const [submitting, setSubmitting] = useState(false)
  const updatePassword = useSession((s) => s.updatePassword)
  const clearRecovery = useSession((s) => s.clearRecovery)
  const { register, handleSubmit, getValues, formState: { errors } } = useForm<Fields>({
    defaultValues: { password: '', confirm: '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true)
    try {
      await updatePassword(values.password)
      toast.success(t('auth.updatePassword'))
      clearRecovery()
    } catch {
      toast.error(t('auth.error'))
    } finally {
      setSubmitting(false)
    }
  })

  return (
    <div className="relative flex min-h-full items-center justify-center bg-base px-4 py-10">
      <div className="w-full max-w-[400px] animate-fade-up">
        <div className="mb-8 flex justify-center"><Wordmark /></div>
        <Card className="shadow-pop">
          <CardContent className="p-6">
            <h1 className="text-title font-bold tracking-tight">{t('auth.updatePasswordTitle')}</h1>
            <p className="mt-1 text-sm text-ink-secondary">{t('auth.updatePasswordSubtitle')}</p>
            <form onSubmit={onSubmit} className="mt-6 flex flex-col gap-4" noValidate>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="new-password">{t('common.password')}</Label>
                <div className="relative">
                  <Lock className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
                  <Input id="new-password" type="password" autoComplete="new-password" className="pl-9"
                    aria-invalid={Boolean(errors.password)} {...register('password', passwordRule)} />
                </div>
                {errors.password && <p className="text-xs text-danger">{errors.password.message}</p>}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="confirm-password">{t('auth.confirmPassword')}</Label>
                <Input id="confirm-password" type="password" autoComplete="new-password"
                  aria-invalid={Boolean(errors.confirm)}
                  {...register('confirm', {
                    required: t('auth.passwordsDiffer'),
                    validate: (v) => v === getValues('password') || t('auth.passwordsDiffer'),
                  })} />
                {errors.confirm && <p className="text-xs text-danger">{errors.confirm.message}</p>}
              </div>
              <Button type="submit" variant="primary" size="lg" disabled={submitting} className="mt-1 w-full">
                {submitting ? <><LoaderDots /> {t('common.loading')}</> : t('auth.updatePassword')}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
