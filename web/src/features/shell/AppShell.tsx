// App shell (spec §3.12): 3-pane desktop, bottom-nav mobile with sheet drawers,
// connection banner, boot auto-selection, and global keyboard shortcuts.
import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { MessageSquare, Compass, Inbox, Search, User } from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useMediaQuery } from '../../lib/useMediaQuery'
import { ServerRail } from './ServerRail'
import { ChannelSidebar } from './ChannelSidebar'
import { MemberPanel } from './MemberPanel'
import { ChatView } from '../chat/ChatView'
import { ConnectionBanner } from '../../components/ConnectionBanner'
import { Sheet, SheetContent, SheetTitle } from '../../components/ui/overlay'
import { cn } from '../../lib/utils'
import { t } from '../../lib/i18n'

type Tab = 'chats' | 'servers' | 'dms' | 'search' | 'you'

function isTypingTarget(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  if (!el) return false
  const tag = el.tagName?.toLowerCase()
  return tag === 'input' || tag === 'textarea' || el.isContentEditable
}

export function AppShell() {
  const {
    shellLoading, connection, activeGuildId, activeChannelId, activeGuild,
    guilds, dms, channels, selectGuild, selectChannel,
  } = useChat()
  const setActiveChannelId = useAppStore((s) => s.setActiveChannelId)
  const setCommandOpen = useAppStore((s) => s.setCommandOpen)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const openSettings = useAppStore((s) => s.openSettings)
  const mobileDrawerOpen = useAppStore((s) => s.mobileDrawerOpen)
  const setMobileDrawerOpen = useAppStore((s) => s.setMobileDrawerOpen)
  const memberPanelOpen = useAppStore((s) => s.memberPanelOpen)
  const setMemberPanelOpen = useAppStore((s) => s.setMemberPanelOpen)
  const isWide = useMediaQuery('(min-width: 1280px)')
  const [tab, setTab] = useState<Tab>('chats')
  const bootstrapped = useRef(false)

  // Land on a real conversation so the app never opens onto an empty void.
  useEffect(() => {
    if (shellLoading || bootstrapped.current) return
    bootstrapped.current = true
    if (activeChannelId) return
    const gid = activeGuildId ?? guilds[0]?.id ?? null
    if (gid) selectGuild(gid)
    else if (dms[0]) setActiveChannelId(dms[0].channel_id)
  }, [shellLoading, activeChannelId, activeGuildId, guilds, dms, selectGuild, setActiveChannelId])

  // Global keyboard shortcuts (spec §3.23); every one has a visible path too.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey
      if (mod && e.key.toLowerCase() === 'k') { e.preventDefault(); setCommandOpen(true); return }
      if (mod && e.key.toLowerCase() === 'f') { e.preventDefault(); setCommandOpen(true); return }
      if (isTypingTarget(e.target)) return
      if (e.key === '?') { e.preventDefault(); setOverlay('shortcuts'); return }
      if (e.altKey && (e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
        const textChannels = channels.filter((c) => c.type === 'text')
        const idx = textChannels.findIndex((c) => c.id === activeChannelId)
        if (idx === -1) return
        e.preventDefault()
        const next = e.key === 'ArrowUp' ? idx - 1 : idx + 1
        if (textChannels[next]) selectChannel(textChannels[next].id)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [channels, activeChannelId, selectChannel, setCommandOpen, setOverlay])

  const openDrawer = () => { setTab('servers'); setMobileDrawerOpen(true) }
  const openDms = () => { selectGuild(null); setTab('dms'); setMobileDrawerOpen(true) }
  const closeDrawer = () => { setMobileDrawerOpen(false); setTab('chats') }

  const tabs: { id: Tab; label: string; icon: typeof MessageSquare }[] = [
    { id: 'chats', label: t('nav.chats'), icon: MessageSquare },
    { id: 'servers', label: t('nav.servers'), icon: Compass },
    { id: 'dms', label: t('nav.dms'), icon: Inbox },
    { id: 'search', label: t('nav.search'), icon: Search },
    { id: 'you', label: t('nav.you'), icon: User },
  ]

  const onTab = (id: Tab) => {
    setTab(id)
    if (id === 'chats') { setMobileDrawerOpen(false); return }
    if (id === 'servers') { setMobileDrawerOpen(true); return }
    if (id === 'dms') { openDms(); return }
    if (id === 'search') { setCommandOpen(true); return }
    if (id === 'you') { openSettings('account') }
  }

  return (
    <div className="app-ambient grain relative flex h-full w-full flex-col overflow-hidden bg-base">
      <ConnectionBanner status={connection} />

      <div className="relative flex min-h-0 flex-1 lg:gap-2 lg:p-2">
        {/* Desktop rail + sidebar, floating as rounded panes */}
        <div className="pane hidden shrink-0 overflow-hidden bg-surface lg:flex lg:rounded-2xl">
          <ServerRail />
        </div>
        <div className="pane hidden w-60 shrink-0 overflow-hidden bg-surface lg:flex lg:rounded-2xl">
          <ChannelSidebar />
        </div>

        {/* Chat */}
        <main className="relative flex min-w-0 flex-1 flex-col overflow-hidden bg-surface lg:pane lg:rounded-2xl">
          <ChatView onOpenDrawer={openDrawer} />
        </main>

        {/* Desktop member panel */}
        {isWide && memberPanelOpen && activeGuild && (
          <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.26, ease: [0.22, 1, 0.36, 1] }}
            className="pane hidden w-60 shrink-0 overflow-hidden bg-surface xl:block xl:rounded-2xl"
          >
            <MemberPanel />
          </motion.div>
        )}
      </div>

      {/* Mobile bottom nav */}
      <nav aria-label={t('nav.chats')} className="relative flex h-16 shrink-0 items-stretch justify-around border-t border-line/10 bg-surface/80 backdrop-blur-xl lg:hidden">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button key={id} onClick={() => onTab(id)}
            className="relative flex min-w-[64px] flex-1 flex-col items-center justify-center gap-0.5 text-[0.6875rem] font-medium"
            aria-current={tab === id ? 'page' : undefined}>
            {tab === id && (
              <motion.span
                layoutId="mobile-nav-glow"
                transition={{ type: 'spring', stiffness: 420, damping: 34 }}
                className="absolute inset-x-3 top-1.5 h-8 rounded-full bg-brand/12"
              />
            )}
            <Icon className={cn(
              'relative size-5 transition-all duration-200',
              tab === id ? '-translate-y-px text-brand' : 'text-ink-muted',
            )} />
            <span className={cn('relative transition-colors duration-200', tab === id ? 'text-brand' : 'text-ink-muted')}>{label}</span>
          </button>
        ))}
      </nav>

      {/* Mobile channel/server drawer */}
      <Sheet open={mobileDrawerOpen} onOpenChange={(v) => { setMobileDrawerOpen(v); if (!v) setTab('chats') }}>
        <SheetContent side="left" className="p-0" hideClose>
          <SheetTitle className="sr-only">{t('nav.servers')}</SheetTitle>
          <div className="flex h-full flex-col">
            <ServerRail orientation="horizontal" onNavigate={closeDrawer} />
            <div className="min-h-0 flex-1 border-t border-line/10">
              <ChannelSidebar onNavigate={closeDrawer} />
            </div>
          </div>
        </SheetContent>
      </Sheet>

      {/* Mobile member sheet */}
      {!isWide && (
        <Sheet open={memberPanelOpen} onOpenChange={setMemberPanelOpen}>
          <SheetContent side="right" className="p-0">
            <SheetTitle className="sr-only">{t('members.title')}</SheetTitle>
            <MemberPanel onNavigate={() => setMemberPanelOpen(false)} />
          </SheetContent>
        </Sheet>
      )}
    </div>
  )
}
