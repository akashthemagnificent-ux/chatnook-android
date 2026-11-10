// Root: session gate, routing, and the global overlay/toast layer (spec §3).
import { useEffect } from 'react'
import { Route, Routes } from 'react-router-dom'
import { useSession } from './store/session'
import { useAppStore } from './store/app'
import { ChatProvider, useChat } from './data/ChatContext'
import { AppShell } from './features/shell/AppShell'
import { AuthScreen } from './features/auth/AuthScreen'
import { AuthCallback } from './features/auth/AuthCallback'
import { RecoveryScreen } from './features/auth/RecoveryScreen'
import { CommandPalette } from './features/command/CommandPalette'
import { OverlayHost } from './components/OverlayHost'
import { ErrorBoundary, NotFound } from './components/RouteStates'
import { Splash } from './components/Splash'
import { Toaster } from './components/ui/toast'
import { initDeepLinks } from './lib/deeplink'

export default function App() {
  const status = useSession((s) => s.status)
  const init = useSession((s) => s.init)
  const accent = useAppStore((s) => s.accent)

  useEffect(() => init(), [init])
  useEffect(() => { initDeepLinks() }, [])

  // Accent lives in the store; paint it on <html> like the theme boot script does.
  useEffect(() => {
    if (accent === 'coral') document.documentElement.removeAttribute('data-accent')
    else document.documentElement.setAttribute('data-accent', accent)
  }, [accent])

  if (status === 'loading') return <Splash />
  if (status === 'recovery') return <RecoveryScreen />

  return (
    <ErrorBoundary>
      <Routes>
        <Route path="/auth/callback" element={<AuthCallback />} />
        <Route path="/" element={status === 'signed-in' ? <SignedIn /> : <AuthScreen />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
      <Toaster />
    </ErrorBoundary>
  )
}

function SignedIn() {
  return (
    <ChatProvider>
      <AppShell />
      <CommandPalette />
      <OverlayHost />
      <FirstRun />
    </ChatProvider>
  )
}

/** Opens the welcome tour once per device for a brand-new account. */
function FirstRun() {
  const { shellLoading, guilds, dms } = useChat()
  const setOverlay = useAppStore((s) => s.setOverlay)

  useEffect(() => {
    if (shellLoading) return
    let seen = '1'
    try { seen = localStorage.getItem('chatnook.onboarded') ?? '' } catch { /* storage blocked */ }
    if (!seen && guilds.length === 0 && dms.length === 0) setOverlay('onboarding')
  }, [shellLoading, guilds.length, dms.length, setOverlay])

  return null
}
