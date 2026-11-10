// PKCE / magic-link / recovery redirect target. supabase-js (detectSessionInUrl)
// exchanges the code on boot; this route just holds a branded beat then goes home.
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { BrandMark } from '../../components/Brand'
import { LoaderDots } from '../../components/LoaderDots'
import { t } from '../../lib/i18n'

export function AuthCallback() {
  const navigate = useNavigate()
  useEffect(() => {
    const id = window.setTimeout(() => navigate('/', { replace: true }), 900)
    return () => window.clearTimeout(id)
  }, [navigate])

  return (
    <div className="flex h-full flex-col items-center justify-center gap-5 bg-base">
      <BrandMark size={56} className="animate-scale-in" />
      <p className="text-md font-semibold text-ink-primary">{t('auth.callbackTitle')}</p>
      <LoaderDots className="text-ink-muted" label={t('auth.callbackTitle')} />
    </div>
  )
}
