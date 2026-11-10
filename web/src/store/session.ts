// Auth + current profile. Mirrors the native app: email/password (auto-confirmed),
// magic link, anonymous guest, password reset, and the recovery-link handoff.
import { create } from 'zustand'
import { supabase, isSupabaseConfigured } from '../lib/supabase'
import { getProfile } from '../data/api'
import { authRedirect } from '../lib/deeplink'
import type { Profile } from '../lib/types'

export type SessionStatus = 'loading' | 'signed-out' | 'signed-in' | 'recovery'

type SessionState = {
  status: SessionStatus
  userId: string | null
  profile: Profile | null
  configured: boolean
  error: string | null
  init: () => () => void
  refreshProfile: () => Promise<void>
  signIn: (email: string, password: string) => Promise<void>
  signUp: (email: string, password: string) => Promise<void>
  signInWithMagicLink: (email: string) => Promise<void>
  signInAsGuest: () => Promise<void>
  sendReset: (email: string) => Promise<void>
  updatePassword: (password: string) => Promise<void>
  clearRecovery: () => void
  signOut: () => Promise<void>
}

export const useSession = create<SessionState>((set, get) => ({
  status: 'loading',
  userId: null,
  profile: null,
  configured: isSupabaseConfigured,
  error: null,

  init: () => {
    if (!supabase || !isSupabaseConfigured) {
      // Design-only build: show the shell with clearly-labeled preview content.
      set({ status: 'signed-in', userId: 'preview', profile: PREVIEW_PROFILE })
      return () => {}
    }
    const client = supabase
    const { data: { subscription } } = client.auth.onAuthStateChange((event, session) => {
      if (event === 'PASSWORD_RECOVERY') {
        set({ status: 'recovery', userId: session?.user.id ?? null })
        return
      }
      if (session?.user) {
        set({ status: 'signed-in', userId: session.user.id, error: null })
        void get().refreshProfile()
      } else {
        set({ status: 'signed-out', userId: null, profile: null })
      }
    })
    return () => subscription.unsubscribe()
  },

  refreshProfile: async () => {
    const id = get().userId
    if (!id || id === 'preview') return
    try {
      const profile = await getProfile(id)
      set({ profile })
    } catch {
      /* keep the session even if the profile row is slow */
    }
  },

  signIn: async (email, password) => {
    const { error } = await supabase!.auth.signInWithPassword({ email, password })
    if (error) { set({ error: error.message }); throw error }
    set({ error: null })
  },

  signUp: async (email, password) => {
    const { error } = await supabase!.auth.signUp({ email, password })
    if (error) { set({ error: error.message }); throw error }
    set({ error: null })
  },

  signInWithMagicLink: async (email) => {
    const { error } = await supabase!.auth.signInWithOtp({
      email,
      options: { emailRedirectTo: authRedirect() },
    })
    if (error) { set({ error: error.message }); throw error }
  },

  signInAsGuest: async () => {
    const { error } = await supabase!.auth.signInAnonymously()
    if (error) { set({ error: error.message }); throw error }
  },

  sendReset: async (email) => {
    const { error } = await supabase!.auth.resetPasswordForEmail(email, {
      redirectTo: authRedirect('recovery'),
    })
    if (error) { set({ error: error.message }); throw error }
  },

  updatePassword: async (password) => {
    const { error } = await supabase!.auth.updateUser({ password })
    if (error) { set({ error: error.message }); throw error }
    set({ status: 'signed-in' })
  },

  clearRecovery: () => set({ status: 'signed-in' }),

  signOut: async () => {
    await supabase?.auth.signOut()
    set({ status: 'signed-out', userId: null, profile: null })
  },
}))

// Only used to render the design preview when no credentials are injected.
const PREVIEW_PROFILE: Profile = {
  id: 'preview',
  username: 'you',
  display_name: 'Alex Rivera',
  avatar_url: null,
  banner_url: null,
  accent_color: null,
  theme: null,
  custom_status: 'Building something cozy',
  badges: [],
  avatar_decoration_url: null,
  avatar_is_animated: false,
}
