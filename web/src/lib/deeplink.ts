// Auth links that come back through the app's own scheme (chatnook://auth).
// The webview's document URL never changes, so supabase-js cannot pick the code
// up on its own the way it does on the web build.
import { App as CapApp } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'
import { supabase } from './supabase'
import { useSession } from '../store/session'

export const AUTH_REDIRECT = 'chatnook://auth'

/** The scheme the email links should come back to, or the page itself on the web. */
export function authRedirect(type?: 'recovery'): string {
  if (!Capacitor.isNativePlatform()) {
    return `${window.location.origin}/#/auth/callback${type ? `?type=${type}` : ''}`
  }
  return type ? `${AUTH_REDIRECT}?type=${type}` : AUTH_REDIRECT
}

export function initDeepLinks(): void {
  if (!Capacitor.isNativePlatform() || !supabase) return
  void CapApp.addListener('appUrlOpen', async ({ url }) => {
    if (!url.startsWith('chatnook://')) return
    let parsed: URL
    try { parsed = new URL(url) } catch { return }
    const code = parsed.searchParams.get('code')
    if (!code) return
    const { data, error } = await supabase!.auth.exchangeCodeForSession(code)
    if (error) return
    if (parsed.searchParams.get('type') === 'recovery') {
      useSession.setState({ status: 'recovery', userId: data.session?.user.id ?? null })
    }
    window.location.hash = '#/auth/callback'
  })
}
