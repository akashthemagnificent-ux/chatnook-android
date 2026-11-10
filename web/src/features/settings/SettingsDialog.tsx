// User settings (spec §3.9/§3.22): two-column dialog, compact header, live preview.
// Account and appearance write through to the existing profile row.
import { useEffect, useId, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import {
  Bell, Check, LogOut, Moon, Palette, Shield, Sun, UserRound,
} from 'lucide-react'
import * as api from '../../data/api'
import { useChat } from '../../data/ChatContext'
import { useAppStore, type Accent, type Density } from '../../store/app'
import { useSession } from '../../store/session'
import { useTheme } from '../../lib/theme'
import { t } from '../../lib/i18n'
import { cn } from '../../lib/utils'
import { displayName } from '../../lib/types'
import { toast } from '../../components/ui/toast'
import { UserAvatar } from '../../components/ui/avatar'
import { Button, Input, Label, Separator } from '../../components/ui/base'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue, Switch } from '../../components/ui/controls'
import { BorderBeam, SpotlightCard } from '../../components/fx/surfaces'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '../../components/ui/overlay'

const TABS = [
  { id: 'account', label: t('settings.account'), icon: UserRound },
  { id: 'appearance', label: t('settings.appearance'), icon: Palette },
  { id: 'privacy', label: t('settings.privacy'), icon: Shield },
  { id: 'notifications', label: t('settings.notifications'), icon: Bell },
] as const

const ACCENTS: { id: Accent; label: string }[] = [
  { id: 'coral', label: t('appearance.accentCoral') },
  { id: 'violet', label: t('appearance.accentViolet') },
  { id: 'teal', label: t('appearance.accentTeal') },
  { id: 'blue', label: t('appearance.accentBlue') },
]

export function SettingsDialog() {
  const overlay = useAppStore((s) => s.overlay)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const tab = useAppStore((s) => s.settingsTab)
  const setTab = useAppStore((s) => s.setSettingsTab)
  const open = overlay === 'settings'
  const tabLayoutId = useId()

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) setOverlay(null) }}>
      <DialogContent className="h-[min(680px,88vh)] max-w-4xl overflow-hidden p-0">
        <DialogTitle className="sr-only">{t('settings.title')}</DialogTitle>
        <DialogDescription className="sr-only">{t('settings.accountSubtitle')}</DialogDescription>
        <div className="flex h-full flex-col sm:flex-row">
          <nav aria-label={t('settings.title')}
            className="flex shrink-0 gap-1 overflow-x-auto border-b border-line/10 p-3 sm:w-52 sm:flex-col sm:border-b-0 sm:border-r sm:p-4">
            <p className="hidden px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-ink-muted sm:block">
              {t('settings.title')}
            </p>
            {TABS.map(({ id, label, icon: Icon }) => (
              <button key={id} onClick={() => setTab(id)}
                aria-current={tab === id ? 'page' : undefined}
                className={cn(
                  'relative flex min-h-10 shrink-0 items-center gap-2.5 rounded-lg px-3 text-sm font-medium transition-colors',
                  tab === id ? 'text-ink-primary' : 'text-ink-secondary hover:bg-hover hover:text-ink-primary',
                )}>
                {tab === id && (
                  <motion.span
                    layoutId={`settings-tab-${tabLayoutId}`}
                    transition={{ type: 'spring', stiffness: 480, damping: 40 }}
                    className="absolute inset-0 rounded-lg bg-elevated shadow-[inset_0_1px_0_rgb(var(--line)/0.06)]"
                  />
                )}
                <Icon className="relative size-4" />
                <span className="relative">{label}</span>
              </button>
            ))}
            <div className="sm:mt-auto">
              <SignOutButton />
            </div>
          </nav>

          <div className="min-w-0 flex-1 overflow-y-auto scrollbar-thin p-5 sm:p-7">
            <div key={tab} className="animate-fade-up">
              {tab === 'account' && <AccountTab />}
              {tab === 'appearance' && <AppearanceTab />}
              {tab === 'privacy' && <PrivacyTab />}
              {tab === 'notifications' && <NotificationsTab />}
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

function SignOutButton() {
  const signOut = useSession((s) => s.signOut)
  const configured = useSession((s) => s.configured)
  const setOverlay = useAppStore((s) => s.setOverlay)
  if (!configured) return null
  return (
    <Button variant="ghost" className="w-full justify-start gap-2.5 text-danger hover:bg-danger/10 hover:text-danger"
      onClick={() => { setOverlay(null); void signOut() }}>
      <LogOut className="size-4" />
      {t('auth.signOut')}
    </Button>
  )
}

// ---------------------------------------------------------------- account
function AccountTab() {
  const profile = useSession((s) => s.profile)
  const userId = useSession((s) => s.userId)
  const configured = useSession((s) => s.configured)
  const refreshProfile = useSession((s) => s.refreshProfile)
  const [name, setName] = useState(profile?.display_name ?? '')
  const [status, setStatus] = useState(profile?.custom_status ?? '')
  const [saving, setSaving] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setName(profile?.display_name ?? '')
    setStatus(profile?.custom_status ?? '')
  }, [profile])

  const save = async () => {
    if (!userId) return
    setSaving(true)
    try {
      await api.updateProfile(userId, { display_name: name.trim() || null, custom_status: status.trim() || null })
      await refreshProfile()
      toast.success(t('toast.profileSaved'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setSaving(false)
    }
  }

  const uploadAvatar = async (file: File) => {
    if (!userId) return
    try {
      const url = await api.uploadCosmetic('avatars', file)
      await api.updateProfile(userId, { avatar_url: url })
      await refreshProfile()
      toast.success(t('toast.avatarUpdated'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    }
  }

  const dirty = (profile?.display_name ?? '') !== name || (profile?.custom_status ?? '') !== status

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <h2 className="text-title font-semibold">{t('settings.accountTitle')}</h2>
        <p className="mt-1 text-sm text-ink-secondary">{t('settings.accountSubtitle')}</p>
      </header>

      <div className="flex items-start gap-4">
        <UserAvatar name={displayName(profile)} src={profile?.avatar_url} size={64} />
        <div className="min-w-0 pt-0.5">
          <p className="truncate text-md font-semibold">{displayName(profile)}</p>
          <p className="truncate text-sm text-ink-muted">@{profile?.username ?? 'you'}</p>
          <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/gif,image/webp" className="hidden"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) void uploadAvatar(f) }} />
          <Button variant="outline" size="sm" className="mt-2" disabled={!configured}
            onClick={() => fileRef.current?.click()}>
            {t('settings.changeAvatar')}
          </Button>
        </div>
      </div>

      <Separator />

      <div className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="settings-name">{t('settings.displayName')}</Label>
          <Input id="settings-name" value={name} maxLength={32}
            placeholder={t('settings.displayNamePlaceholder')} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label>{t('settings.username')}</Label>
          <Input value={profile?.username ?? ''} readOnly disabled />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="settings-status">{t('settings.customStatus')}</Label>
          <Input id="settings-status" value={status} maxLength={80}
            placeholder={t('settings.customStatusPlaceholder')} onChange={(e) => setStatus(e.target.value)} />
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Button onClick={() => void save()} disabled={!dirty || saving || !configured}>
          {saving ? t('common.loading') : t('common.save')}
        </Button>
        <Button variant="ghost" disabled={!dirty}
          onClick={() => { setName(profile?.display_name ?? ''); setStatus(profile?.custom_status ?? '') }}>
          {t('common.reset')}
        </Button>
      </div>
    </section>
  )
}

// ---------------------------------------------------------------- appearance
function AppearanceTab() {
  const { theme, setTheme } = useTheme()
  const profile = useSession((s) => s.profile)
  const density = useAppStore((s) => s.density)
  const setDensity = useAppStore((s) => s.setDensity)
  const accent = useAppStore((s) => s.accent)
  const setAccent = useAppStore((s) => s.setAccent)

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <h2 className="text-title font-semibold">{t('appearance.title')}</h2>
        <p className="mt-1 text-sm text-ink-secondary">{t('appearance.subtitle')}</p>
      </header>

      <div className="space-y-2">
        <Label>{t('appearance.theme')}</Label>
        <div className="grid grid-cols-2 gap-2">
          {([
            { id: 'dark' as const, label: t('appearance.themeDark'), icon: Moon },
            { id: 'light' as const, label: t('appearance.themeLight'), icon: Sun },
          ]).map(({ id, label, icon: Icon }) => (
            <button key={id} onClick={() => setTheme(id)} aria-pressed={theme === id}
              className={cn(
                'flex min-h-11 items-center justify-center gap-2 rounded-md border px-3 text-sm font-medium transition-colors',
                theme === id
                  ? 'border-brand/60 bg-brand/10 text-ink-primary'
                  : 'border-line/12 text-ink-secondary hover:bg-hover hover:text-ink-primary',
              )}>
              <Icon className="size-4" />
              {label}
              {theme === id && <Check className="size-3.5 text-brand" />}
            </button>
          ))}
        </div>
      </div>

      <div className="space-y-2">
        <Label>{t('appearance.density')}</Label>
        <Select value={density} onValueChange={(v) => setDensity(v as Density)}>
          <SelectTrigger aria-label={t('appearance.density')}><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="comfortable">{t('appearance.densityComfortable')}</SelectItem>
            <SelectItem value="compact">{t('appearance.densityCompact')}</SelectItem>
          </SelectContent>
        </Select>
        <p className="text-xs text-ink-muted">{t('appearance.densityHint')}</p>
      </div>

      <div className="space-y-2">
        <Label>{t('appearance.accent')}</Label>
        <div className="flex flex-wrap gap-2">
          {ACCENTS.map((a) => (
            <button key={a.id} onClick={() => setAccent(a.id)} aria-pressed={accent === a.id}
              className={cn(
                'flex min-h-11 items-center gap-2 rounded-md border px-3 text-sm font-medium transition-colors',
                accent === a.id ? 'border-brand/60 bg-brand/10' : 'border-line/12 hover:bg-hover',
              )}>
              <span data-accent={a.id} className={cn(
                'size-4 rounded-full bg-brand ring-1 ring-inset ring-black/20 transition-shadow',
                accent === a.id && 'shadow-[0_0_0_3px_rgb(var(--brand)/0.2)]',
              )} />
              {a.label}
              {accent === a.id && <Check className="size-3.5 text-brand" />}
            </button>
          ))}
        </div>
        <p className="text-xs text-ink-muted">{t('appearance.accentHint')}</p>
      </div>

      <Separator />

      <div className="space-y-2">
        <Label>{t('appearance.preview')}</Label>
        <SpotlightCard className="rounded-xl">
          <BorderBeam duration={8} />
          <div className="rounded-xl border border-line/12 bg-surface/80 p-4 backdrop-blur">
            <div className="flex items-start gap-3">
              <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-sheen text-xs font-bold text-ink-accent shadow-glow" aria-hidden>
                {displayName(profile).slice(0, 1).toUpperCase()}
              </span>
              <div>
                <p className="text-sm font-semibold">
                  {displayName(profile)}
                  <span className="ml-2 text-xs font-normal tabular-nums text-ink-muted">12:04</span>
                </p>
                <p className={cn('text-sm text-ink-secondary', density === 'compact' ? 'mt-0.5' : 'mt-1')}>
                  {t('appearance.previewBody')}
                </p>
              </div>
            </div>
            <div className="mt-3 flex justify-end">
              <span className="bubble-own rounded-2xl rounded-tr-md border border-brand/25 px-3 py-1.5 text-sm font-medium text-ink-primary">
                {t('appearance.previewSend')}
              </span>
            </div>
          </div>
        </SpotlightCard>
      </div>
    </section>
  )
}

// ---------------------------------------------------------------- privacy
function PrivacyTab() {
  const privacy = useAppStore((s) => s.privacy)
  const setPrivacy = useAppStore((s) => s.setPrivacy)
  const rows = [
    { key: 'readReceipts' as const, label: t('settings.readReceipts'), hint: t('settings.readReceiptsHint') },
    { key: 'onlineStatus' as const, label: t('settings.onlineStatus'), hint: t('settings.onlineStatusHint') },
    { key: 'allowDms' as const, label: t('settings.allowDms'), hint: t('settings.allowDmsHint') },
  ]
  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <h2 className="text-title font-semibold">{t('settings.privacy')}</h2>
        <p className="mt-1 text-sm text-ink-secondary">{t('settings.readReceiptsHint')}</p>
      </header>
      <div className="space-y-5">
        {rows.map((row) => (
          <div key={row.key} className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              <p className="text-sm font-medium">{row.label}</p>
              <p className="mt-0.5 text-xs text-ink-muted">{row.hint}</p>
            </div>
            <Switch checked={privacy[row.key]} aria-label={row.label}
              onCheckedChange={(v) => setPrivacy({ [row.key]: v })} />
          </div>
        ))}
      </div>
    </section>
  )
}

// ---------------------------------------------------------------- notifications
function NotificationsTab() {
  const configured = useSession((s) => s.configured)
  const userId = useSession((s) => s.userId)
  const { activeGuild, activeChannel } = useChat()
  const [muted, setMuted] = useState(false)
  const [level, setLevel] = useState<'all' | 'mentions' | 'none'>('all')

  useEffect(() => {
    if (!configured || !userId || !activeGuild) return
    let alive = true
    void api.getGuildMuted(activeGuild.id, userId).then((m) => { if (alive) setMuted(m) }).catch(() => {})
    return () => { alive = false }
  }, [configured, userId, activeGuild])

  useEffect(() => {
    if (!configured || !userId || !activeChannel) return
    let alive = true
    void api.getChannelLevels(userId).then((levels) => {
      if (alive && activeChannel) setLevel(levels[activeChannel.id] ?? 'all')
    }).catch(() => {})
    return () => { alive = false }
  }, [configured, userId, activeChannel])

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <h2 className="text-title font-semibold">{t('notifications.title')}</h2>
        <p className="mt-1 text-sm text-ink-secondary">{t('notifications.subtitle')}</p>
      </header>

      {activeGuild ? (
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-sm font-medium">{t('notifications.muteServer')}</p>
            <p className="mt-0.5 text-xs text-ink-muted">{t('notifications.muteServerHint')}</p>
          </div>
          <Switch checked={muted} aria-label={t('notifications.muteServer')}
            onCheckedChange={(v) => {
              setMuted(v)
              if (configured && userId) void api.setGuildMuted(activeGuild.id, userId, v).catch(() => setMuted(!v))
            }} />
        </div>
      ) : null}

      {activeChannel ? (
        <div className="space-y-2">
          <Label>{t('notifications.perChannel')}</Label>
          <Select value={level} onValueChange={(v) => {
            const next = v as 'all' | 'mentions' | 'none'
            setLevel(next)
            if (configured && userId) void api.setChannelLevel(activeChannel.id, userId, next).catch(() => {})
          }}>
            <SelectTrigger aria-label={t('notifications.level')}><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('notifications.levelAll')}</SelectItem>
              <SelectItem value="mentions">{t('notifications.levelMentions')}</SelectItem>
              <SelectItem value="none">{t('notifications.levelNone')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      ) : null}
    </section>
  )
}
