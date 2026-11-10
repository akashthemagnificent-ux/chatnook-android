// Server settings (spec §3.17): nav + content card. Overview, members, roles,
// emojis, danger. Every write goes through the existing RPCs/policies.
import { useEffect, useId, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import {
  AlertTriangle, Check, ImagePlus, Shield, Smile, Trash2, Users, Wrench,
} from 'lucide-react'
import * as api from '../../data/api'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useSession } from '../../store/session'
import { t } from '../../lib/i18n'
import { cn } from '../../lib/utils'
import { displayName, type CustomEmoji, type Member, type Role } from '../../lib/types'
import { roleColor } from '../../lib/format'
import { toast } from '../../components/ui/toast'
import { UserAvatar } from '../../components/ui/avatar'
import { Badge, Button, Input, Label, Separator } from '../../components/ui/base'
import { Switch } from '../../components/ui/controls'
import {
  Dialog, DialogContent, DialogDescription, DialogTitle,
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '../../components/ui/overlay'

const MANAGE_MESSAGES = 1 << 2
const MANAGE_CHANNELS = 1 << 3
const MANAGE_ROLES = 1 << 4
const CREATE_INVITE = 1 << 5
const KICK_MEMBERS = 1 << 6
const ADMINISTRATOR = 1 << 7
const MANAGE_GUILD = 1 << 11

type Section = 'overview' | 'members' | 'roles' | 'emojis' | 'danger'

export function GuildSettingsDialog() {
  const overlay = useAppStore((s) => s.overlay)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const [section, setSection] = useState<Section>('overview')
  const { activeGuild, permissions } = useChat()
  const userId = useSession((s) => s.userId)
  const open = overlay === 'guildSettings' && Boolean(activeGuild)
  const navLayoutId = useId()

  const isOwner = Boolean(activeGuild && userId === activeGuild.owner_id)
  const canManage = isOwner || (permissions & MANAGE_GUILD) !== 0

  const nav: { id: Section; label: string; icon: typeof Wrench; ownerOnly?: boolean }[] = [
    { id: 'overview', label: t('guild.overview'), icon: Wrench, ownerOnly: true },
    { id: 'members', label: t('guild.members'), icon: Users },
    { id: 'roles', label: t('guild.roles'), icon: Shield, ownerOnly: true },
    { id: 'emojis', label: t('guild.emojis'), icon: Smile, ownerOnly: true },
    { id: 'danger', label: t('guild.danger'), icon: AlertTriangle },
  ]

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) setOverlay(null) }}>
      <DialogContent className="h-[min(680px,88vh)] max-w-4xl overflow-hidden p-0">
        <DialogTitle className="sr-only">{t('guild.settings')}</DialogTitle>
        <DialogDescription className="sr-only">{t('guild.overviewHint')}</DialogDescription>
        <div className="flex h-full flex-col sm:flex-row">
          <nav aria-label={t('guild.settings')}
            className="flex shrink-0 gap-1 overflow-x-auto border-b border-line/10 p-3 sm:w-52 sm:flex-col sm:border-b-0 sm:border-r sm:p-4">
            <p className="hidden truncate px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-ink-muted sm:block">
              {activeGuild?.name}
            </p>
            {nav.map(({ id, label, icon: Icon, ownerOnly }) => {
              const disabled = ownerOnly && !canManage
              return (
                <button key={id} onClick={() => setSection(id)} disabled={disabled}
                  aria-current={section === id ? 'page' : undefined}
                  className={cn(
                    'relative flex min-h-10 shrink-0 items-center gap-2.5 rounded-lg px-3 text-sm font-medium transition-colors',
                    section === id ? 'text-ink-primary' : 'text-ink-secondary hover:bg-hover hover:text-ink-primary',
                    id === 'danger' && 'text-danger hover:bg-danger/10 hover:text-danger',
                    disabled && 'pointer-events-none opacity-40',
                  )}>
                  {section === id && id !== 'danger' && (
                    <motion.span
                      layoutId={`guild-nav-${navLayoutId}`}
                      transition={{ type: 'spring', stiffness: 480, damping: 40 }}
                      className="absolute inset-0 rounded-lg bg-elevated shadow-[inset_0_1px_0_rgb(var(--line)/0.06)]"
                    />
                  )}
                  {section === id && id === 'danger' && (
                    <motion.span
                      layoutId={`guild-nav-${navLayoutId}`}
                      transition={{ type: 'spring', stiffness: 480, damping: 40 }}
                      className="absolute inset-0 rounded-lg bg-danger/10"
                    />
                  )}
                  <Icon className="relative size-4" />
                  <span className="relative">{label}</span>
                </button>
              )
            })}
          </nav>

          <div className="min-w-0 flex-1 overflow-y-auto scrollbar-thin p-5 sm:p-7">
            <div key={section} className="animate-fade-up">
              {activeGuild && section === 'overview' && <Overview guildId={activeGuild.id} name={activeGuild.name} iconUrl={activeGuild.icon_url} />}
              {activeGuild && section === 'members' && <MembersTab />}
              {activeGuild && section === 'roles' && <RolesTab guildId={activeGuild.id} />}
              {activeGuild && section === 'emojis' && <EmojisTab guildId={activeGuild.id} />}
              {activeGuild && section === 'danger' && <DangerTab />}
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

// ---------------------------------------------------------------- overview
function Overview({ guildId, name, iconUrl }: { guildId: string; name: string; iconUrl: string | null }) {
  const configured = useSession((s) => s.configured)
  const { refreshGuilds } = useChat()
  const [nextName, setNextName] = useState(name)
  const [icon, setIcon] = useState(iconUrl)
  const [saving, setSaving] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => { setNextName(name); setIcon(iconUrl) }, [name, iconUrl])

  const save = async () => {
    setSaving(true)
    try {
      await api.updateGuild(guildId, { name: nextName.trim(), icon_url: icon })
      refreshGuilds()
      toast.success(t('common.saved'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setSaving(false)
    }
  }

  const uploadIcon = async (file: File) => {
    try {
      const url = await api.uploadCosmetic('avatars', file)
      setIcon(url)
      await api.updateGuild(guildId, { name, icon_url: url })
      refreshGuilds()
      toast.success(t('common.saved'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    }
  }

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <h2 className="text-title font-semibold">{t('guild.overview')}</h2>
        <p className="mt-1 text-sm text-ink-secondary">{t('guild.overviewHint')}</p>
      </header>

      <div className="flex items-center gap-4">
        <span className="grid size-16 shrink-0 place-items-center overflow-hidden rounded-lg bg-base/60 text-2xl font-semibold text-ink-secondary ring-1 ring-line/10">
          {icon ? <img src={icon} alt="" className="size-full object-cover" /> : name.slice(0, 1).toUpperCase()}
        </span>
        <div>
          <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/gif,image/webp" className="hidden"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) void uploadIcon(f) }} />
          <Button variant="outline" size="sm" disabled={!configured} onClick={() => fileRef.current?.click()}>
            {t('guild.changeIcon')}
          </Button>
        </div>
      </div>

      <Separator />

      <div className="space-y-1.5">
        <Label htmlFor="guild-name-input">{t('guild.serverNameLabel')}</Label>
        <Input id="guild-name-input" value={nextName} maxLength={100} onChange={(e) => setNextName(e.target.value)} />
      </div>

      <div className="flex items-center gap-2">
        <Button onClick={() => void save()} disabled={!configured || saving || !nextName.trim() || nextName === name}>
          {saving ? t('common.loading') : t('common.save')}
        </Button>
      </div>
    </section>
  )
}

// ---------------------------------------------------------------- members
function MembersTab() {
  const { members, moderation, activeGuild, permissions } = useChat()
  const userId = useSession((s) => s.userId)
  const requestConfirm = useAppStore((s) => s.requestConfirm)
  const isOwner = Boolean(activeGuild && userId === activeGuild.owner_id)
  const canKick = isOwner || (permissions & KICK_MEMBERS) !== 0

  return (
    <section className="space-y-5">
      <header>
        <h2 className="text-title font-semibold">{t('guild.members')}</h2>
        <p className="mt-1 text-sm text-ink-secondary">{t('guild.membersHint')}</p>
      </header>
      <ul className="divide-y divide-line/8 rounded-lg border border-line/10">
        {members.map((m) => (
          <MemberRow key={m.user_id} member={m} canKick={canKick} isOwner={isOwner}
            isSelf={m.user_id === userId}
            onKick={() => activeGuild && requestConfirm({
              title: t('guild.kickConfirmTitle'), body: t('guild.kickConfirmBody'), confirmLabel: t('guild.kick'), destructive: true,
              onConfirm: () => { void moderation.kick(activeGuild.id, m.user_id).then(() => toast.success(t('toast.memberKicked'))).catch(() => toast.error(t('toast.somethingWrong'))) },
            })}
            onBan={() => activeGuild && requestConfirm({
              title: t('guild.banConfirmTitle'), body: t('guild.banConfirmBody'), confirmLabel: t('guild.ban'), destructive: true,
              onConfirm: () => { void moderation.ban(activeGuild.id, m.user_id, null).then(() => toast.success(t('toast.memberBanned'))).catch(() => toast.error(t('toast.somethingWrong'))) },
            })} />
        ))}
      </ul>
    </section>
  )
}

function MemberRow({ member, canKick, isOwner, isSelf, onKick, onBan }: {
  member: Member; canKick: boolean; isOwner: boolean; isSelf: boolean
  onKick: () => void; onBan: () => void
}) {
  const name = member.nickname || displayName(member.profile)
  return (
    <li className="flex items-center gap-3 px-3 py-2.5">
      <UserAvatar name={name} src={member.profile?.avatar_url} size={36} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{name}</p>
        <div className="mt-0.5 flex flex-wrap gap-1">
          {member.roles.map((r) => (
            <Badge key={r.id} style={r.color ? { color: roleColor(r.color) } : undefined}>{r.name}</Badge>
          ))}
        </div>
      </div>
      {canKick && !isSelf && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label={t('common.more')}>•••</Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onSelect={onKick}>{t('guild.kick')}</DropdownMenuItem>
            <DropdownMenuItem destructive onSelect={onBan} disabled={isOwner}>{t('guild.ban')}</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      )}
    </li>
  )
}

// ---------------------------------------------------------------- roles
function RolesTab({ guildId }: { guildId: string }) {
  const configured = useSession((s) => s.configured)
  const [roles, setRoles] = useState<Role[]>([])
  const [selected, setSelected] = useState<Role | null>(null)
  const [creating, setCreating] = useState(false)
  const [newName, setNewName] = useState('')

  const load = async () => setRoles(await api.getRoles(guildId))

  useEffect(() => {
    if (!configured) return
    void load().catch(() => {})
  }, [configured, guildId])

  const create = async () => {
    const name = newName.trim()
    if (!name) return
    try {
      await api.createRole(guildId, name, null, 0)
      setNewName('')
      setCreating(false)
      await load()
      toast.success(t('common.saved'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    }
  }

  return (
    <section className="space-y-5">
      <header className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-title font-semibold">{t('guild.roles')}</h2>
          <p className="mt-1 text-sm text-ink-secondary">{t('guild.rolesHint')}</p>
        </div>
        <Button size="sm" onClick={() => setCreating((v) => !v)} disabled={!configured}>{t('guild.newRole')}</Button>
      </header>

      {creating && (
        <div className="flex gap-2">
          <Input autoFocus value={newName} maxLength={40} placeholder={t('guild.roleNamePlaceholder')}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') void create() }} />
          <Button onClick={() => void create()} disabled={!newName.trim()}>{t('guild.createRole')}</Button>
        </div>
      )}

      {roles.length === 0 ? (
        <p className="text-sm text-ink-muted">{t('guild.noRoles')}</p>
      ) : (
        <ul className="divide-y divide-line/8 rounded-lg border border-line/10">
          {roles.map((role) => (
            <li key={role.id}>
              <button onClick={() => setSelected(role)} className="flex w-full items-center gap-3 px-3 py-2.5 text-left hover:bg-hover">
                <span className="size-3 shrink-0 rounded-full bg-elevated ring-1 ring-inset ring-black/20"
                  style={{ background: roleColor(role.color) }} />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{role.name}</span>
                {role.is_everyone ? <Badge>{t('common.all')}</Badge> : null}
              </button>
            </li>
          ))}
        </ul>
      )}

      {selected && (
        <RoleEditor role={selected} onClose={() => setSelected(null)}
          onSaved={async () => { setSelected(null); await load() }} />
      )}
    </section>
  )
}

const PERMISSIONS: { bit: number; label: string; hint: string }[] = [
  { bit: MANAGE_MESSAGES, label: t('roles.manageMessages'), hint: t('roles.manageMessagesHint') },
  { bit: MANAGE_CHANNELS, label: t('roles.manageChannels'), hint: t('roles.manageChannelsHint') },
  { bit: MANAGE_ROLES, label: t('roles.manageRoles'), hint: t('roles.manageRolesHint') },
  { bit: CREATE_INVITE, label: t('roles.createInvite'), hint: t('roles.createInviteHint') },
  { bit: KICK_MEMBERS, label: t('roles.kickMembers'), hint: t('roles.kickMembersHint') },
  { bit: ADMINISTRATOR, label: t('roles.administrator'), hint: t('roles.administratorHint') },
  { bit: MANAGE_GUILD, label: t('roles.manageServer'), hint: t('roles.manageServerHint') },
]

function RoleEditor({ role, onClose, onSaved }: { role: Role; onClose: () => void; onSaved: () => Promise<void> }) {
  const [name, setName] = useState(role.name)
  const [color, setColor] = useState(roleColor(role.color) ?? '#8b8f98')
  const [bits, setBits] = useState(role.permissions ?? 0)
  const [busy, setBusy] = useState(false)

  const toggle = (bit: number, on: boolean) =>
    setBits((prev) => (on ? prev | bit : prev & ~bit))

  const save = async () => {
    setBusy(true)
    try {
      await api.updateRole(role.id, name.trim(), parseInt(color.slice(1), 16), bits)
      toast.success(t('guild.roleSaved'))
      await onSaved()
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    setBusy(true)
    try {
      await api.deleteRole(role.id)
      toast.success(t('common.saved'))
      await onSaved()
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4 rounded-lg border border-line/12 bg-surface p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-semibold">{role.name}</h3>
        <Button variant="ghost" size="sm" onClick={onClose}>{t('common.close')}</Button>
      </div>
      <div className="flex gap-2">
        <Input value={name} maxLength={40} aria-label={t('guild.roleName')} onChange={(e) => setName(e.target.value)} />
        <input type="color" value={color} aria-label={t('appearance.accent')}
          onChange={(e) => setColor(e.target.value)}
          className="h-11 w-12 shrink-0 cursor-pointer rounded-md border border-line/12 bg-base/40 p-1" />
      </div>
      <fieldset className="space-y-3">
        <legend className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{t('guild.permissions')}</legend>
        {PERMISSIONS.map((p) => (
          <label key={p.bit} className="flex items-start justify-between gap-4">
            <span className="min-w-0">
              <span className="block text-sm font-medium">{p.label}</span>
              <span className="mt-0.5 block text-xs text-ink-muted">{p.hint}</span>
            </span>
            <Switch checked={(bits & p.bit) !== 0} aria-label={p.label}
              disabled={Boolean(role.is_everyone) && p.bit === ADMINISTRATOR}
              onCheckedChange={(v) => toggle(p.bit, v)} />
          </label>
        ))}
      </fieldset>
      <div className="flex items-center gap-2">
        <Button onClick={() => void save()} disabled={busy || !name.trim()}>{t('common.save')}</Button>
        {!role.is_everyone && (
          <Button variant="destructive" onClick={() => void remove()} disabled={busy}>
            <Trash2 className="size-4" /> {t('guild.deleteRole')}
          </Button>
        )}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------- emojis
function EmojisTab({ guildId }: { guildId: string }) {
  const configured = useSession((s) => s.configured)
  const [emojis, setEmojis] = useState<CustomEmoji[]>([])
  const [busy, setBusy] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const load = async () => setEmojis(await api.getCustomEmojis(guildId))
  useEffect(() => { if (configured) void load().catch(() => {}) }, [configured, guildId])

  const upload = async (file: File) => {
    const name = file.name.replace(/\.[^.]+$/, '').replace(/[^a-zA-Z0-9_]/g, '_').slice(0, 32) || 'emoji'
    setBusy(true)
    try {
      const url = await api.uploadCosmetic('emojis', file)
      await api.addCustomEmoji(guildId, name, url, file.type === 'image/gif')
      await load()
      toast.success(t('common.saved'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setBusy(false)
    }
  }

  const remove = async (id: string) => {
    try {
      await api.deleteCustomEmoji(id)
      setEmojis((prev) => prev.filter((e) => e.id !== id))
    } catch {
      toast.error(t('toast.somethingWrong'))
    }
  }

  return (
    <section className="space-y-5">
      <header>
        <h2 className="text-title font-semibold">{t('guild.emojis')}</h2>
        <p className="mt-1 text-sm text-ink-secondary">{t('guild.emojisHint')}</p>
      </header>

      <input ref={fileRef} type="file" accept="image/png,image/gif,image/webp" className="hidden"
        onChange={(e) => { const f = e.target.files?.[0]; if (f) void upload(f) }} />
      <Button variant="outline" size="sm" disabled={!configured || busy} onClick={() => fileRef.current?.click()}>
        <ImagePlus className="size-4" /> {t('guild.uploadEmoji')}
      </Button>
      <p className="text-xs text-ink-muted">{t('guild.emojiHint')}</p>

      {emojis.length === 0 ? (
        <p className="text-sm text-ink-muted">{t('guild.noEmojis')}</p>
      ) : (
        <ul className="grid grid-cols-3 gap-2 sm:grid-cols-4">
          {emojis.map((emoji) => (
            <li key={emoji.id} className="group relative rounded-md border border-line/10 p-2">
              <img src={emoji.url} alt={`:${emoji.name}:`} className="mx-auto size-12 object-contain" />
              <p className="mt-1 truncate text-center text-xs text-ink-muted">:{emoji.name}:</p>
              <button onClick={() => void remove(emoji.id)} aria-label={t('guild.deleteEmoji')}
                className="absolute right-1 top-1 rounded-sm bg-base/80 p-1 text-danger opacity-0 transition-opacity focus-visible:opacity-100 group-hover:opacity-100">
                <Trash2 className="size-3.5" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

// ---------------------------------------------------------------- danger
function DangerTab() {
  const { activeGuild, refreshGuilds, selectGuild } = useChat()
  const userId = useSession((s) => s.userId)
  const configured = useSession((s) => s.configured)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const requestConfirm = useAppStore((s) => s.requestConfirm)
  const isOwner = Boolean(activeGuild && userId === activeGuild.owner_id)

  if (!activeGuild) return null
  return (
    <section className="mx-auto max-w-xl space-y-5">
      <header>
        <h2 className="text-title font-semibold text-danger">{t('guild.danger')}</h2>
        <p className="mt-1 text-sm text-ink-secondary">{t('guild.leaveConfirmBody')}</p>
      </header>
      {isOwner ? (
        <p className="rounded-md border border-warning/25 bg-warning/10 px-3 py-2.5 text-sm">{t('guild.ownerCannotLeave')}</p>
      ) : (
        <Button variant="destructive" disabled={!configured || !userId}
          onClick={() => requestConfirm({
            title: t('guild.leaveConfirmTitle'), body: t('guild.leaveConfirmBody'),
            confirmLabel: t('guild.leave'), destructive: true,
            onConfirm: () => {
              void api.leaveGuild(activeGuild.id, userId!).then(() => {
                setOverlay(null)
                selectGuild(null)
                refreshGuilds()
              }).catch(() => toast.error(t('toast.somethingWrong')))
            },
          })}>
          {t('guild.leave')}
        </Button>
      )}
      <Separator />
      <p className="flex items-center gap-2 text-xs text-ink-muted"><Check className="size-3.5" /> {t('guild.membersHint')}</p>
    </section>
  )
}
