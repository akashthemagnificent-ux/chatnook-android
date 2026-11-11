import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Attachment } from '../lib/types'

export type Density = 'comfortable' | 'compact'
export type Accent = 'coral' | 'violet' | 'teal' | 'blue'
export type Privacy = { readReceipts: boolean; onlineStatus: boolean; allowDms: boolean }

// A single, transient overlay slot keeps dialogs from fighting over the screen.
export type OverlayKind =
  | 'settings' | 'guildSettings' | 'createServer' | 'joinServer'
  | 'invite' | 'moderation' | 'shortcuts' | 'onboarding' | null

export type ConfirmSpec = {
  title: string
  body?: string
  confirmLabel?: string
  destructive?: boolean
  onConfirm: () => void
}

type AppState = {
  density: Density
  setDensity: (density: Density) => void
  accent: Accent
  setAccent: (accent: Accent) => void
  commandOpen: boolean
  setCommandOpen: (open: boolean) => void
  activeGuildId: string | null
  setActiveGuildId: (id: string | null) => void
  activeChannelId: string | null
  setActiveChannelId: (id: string | null) => void
  privacy: Privacy
  setPrivacy: (patch: Partial<Privacy>) => void

  overlay: OverlayKind
  setOverlay: (overlay: OverlayKind) => void
  settingsTab: string
  setSettingsTab: (tab: string) => void
  openSettings: (tab?: string) => void

  memberPanelOpen: boolean
  setMemberPanelOpen: (open: boolean) => void
  toggleMemberPanel: () => void

  mobileDrawerOpen: boolean
  setMobileDrawerOpen: (open: boolean) => void

  lightbox: { items: Attachment[]; index: number } | null
  openLightbox: (items: Attachment[], index: number) => void
  setLightboxIndex: (index: number) => void
  closeLightbox: () => void

  confirm: ConfirmSpec | null
  requestConfirm: (spec: ConfirmSpec) => void
  clearConfirm: () => void
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      density: 'comfortable',
      setDensity: (density) => set({ density }),
      accent: 'coral',
      setAccent: (accent) => set({ accent }),
      commandOpen: false,
      setCommandOpen: (commandOpen) => set({ commandOpen }),
      activeGuildId: null,
      setActiveGuildId: (activeGuildId) => set({ activeGuildId }),
      activeChannelId: null,
      setActiveChannelId: (activeChannelId) => set({ activeChannelId }),
      privacy: { readReceipts: true, onlineStatus: true, allowDms: true },
      setPrivacy: (patch) => set((s) => ({ privacy: { ...s.privacy, ...patch } })),

      overlay: null,
      setOverlay: (overlay) => set({ overlay }),
      settingsTab: 'account',
      setSettingsTab: (settingsTab) => set({ settingsTab }),
      openSettings: (tab = 'account') => set({ overlay: 'settings', settingsTab: tab }),

      memberPanelOpen: false,
      setMemberPanelOpen: (memberPanelOpen) => set({ memberPanelOpen }),
      toggleMemberPanel: () => set((s) => ({ memberPanelOpen: !s.memberPanelOpen })),

      mobileDrawerOpen: false,
      setMobileDrawerOpen: (mobileDrawerOpen) => set({ mobileDrawerOpen }),

      lightbox: null,
      openLightbox: (items, index) => set({ lightbox: { items, index } }),
      setLightboxIndex: (index) => set((s) => (s.lightbox ? { lightbox: { ...s.lightbox, index } } : {})),
      closeLightbox: () => set({ lightbox: null }),

      confirm: null,
      requestConfirm: (confirm) => set({ confirm }),
      clearConfirm: () => set({ confirm: null }),
    }),
    {
      name: 'chatnook.ui',
      // Navigation, overlays and transient state must not survive a reload.
      partialize: (state) => ({ density: state.density, accent: state.accent }),
    },
  ),
)
