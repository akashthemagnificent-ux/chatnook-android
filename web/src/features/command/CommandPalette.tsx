// ⌘K command palette (spec §3.10): fuzzy search over channels, DMs, people,
// servers, and settings. Built on cmdk; every action has a visible path too.
import { useMemo } from 'react'
import { Hash, MessageSquare, Plus, Settings, Users } from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import {
  CommandDialog, CommandInput, CommandList, CommandEmpty, CommandGroup, CommandItem, CommandSeparator,
} from '../../components/ui/command'
import { UserAvatar } from '../../components/ui/avatar'
import { displayName } from '../../lib/types'
import { t } from '../../lib/i18n'

export function CommandPalette() {
  const open = useAppStore((s) => s.commandOpen)
  const setOpen = useAppStore((s) => s.setCommandOpen)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const openSettings = useAppStore((s) => s.openSettings)
  const setDrawer = useAppStore((s) => s.setMobileDrawerOpen)
  const { channels, dms, guilds, members, activeGuild, selectChannel, selectGuild, openDm } = useChat()

  const textChannels = useMemo(() => channels.filter((c) => c.type === 'text'), [channels])

  const close = () => setOpen(false)
  const run = (fn: () => void) => { fn(); close(); setDrawer(false) }

  return (
    <CommandDialog open={open} onOpenChange={setOpen} label={t('command.placeholder')}>
      <CommandInput placeholder={t('command.placeholder')} />
      <CommandList>
        <CommandEmpty>{t('command.empty')}</CommandEmpty>

        {textChannels.length > 0 && (
          <CommandGroup heading={t('command.channels')}>
            {textChannels.map((c) => (
              <CommandItem key={c.id} value={`channel ${c.name}`} onSelect={() => run(() => selectChannel(c.id))}>
                <Hash className="size-4 text-ink-muted" />
                <span>{c.name}</span>
                {c.topic && <span className="ml-auto truncate text-xs text-ink-muted">{c.topic}</span>}
              </CommandItem>
            ))}
          </CommandGroup>
        )}

        {dms.length > 0 && (
          <CommandGroup heading={t('command.dms')}>
            {dms.map((d) => (
              <CommandItem key={d.channel_id} value={`dm ${d.name}`} onSelect={() => run(() => void openDm(d.user_id))}>
                <UserAvatar name={d.name} src={d.avatar_url} size={22} />
                <span>{d.name}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        )}

        {members.length > 0 && (
          <CommandGroup heading={t('command.people')}>
            {members.map((m) => (
              <CommandItem key={m.user_id} value={`person ${displayName(m.profile)} ${m.profile?.username ?? ''}`}
                onSelect={() => run(() => void openDm(m.user_id))}>
                <UserAvatar name={displayName(m.profile)} src={m.profile?.avatar_url} size={22} />
                <span>{displayName(m.profile)}</span>
                <span className="ml-auto text-xs text-ink-muted">@{m.profile?.username}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        )}

        <CommandSeparator />

        <CommandGroup heading={t('command.servers')}>
          {guilds.map((g) => (
            <CommandItem key={g.id} value={`server ${g.name}`} onSelect={() => run(() => selectGuild(g.id))}>
              <Users className="size-4 text-ink-muted" />
              <span>{g.name}</span>
              {g.id === activeGuild?.id && <span className="ml-auto text-xs text-brand">{t('common.open')}</span>}
            </CommandItem>
          ))}
          <CommandItem value="create server" onSelect={() => run(() => setOverlay('createServer'))}>
            <Plus className="size-4 text-ink-muted" /> {t('guild.create')}
          </CommandItem>
          <CommandItem value="join server" onSelect={() => run(() => setOverlay('joinServer'))}>
            <MessageSquare className="size-4 text-ink-muted" /> {t('guild.join')}
          </CommandItem>
        </CommandGroup>

        <CommandSeparator />

        <CommandGroup heading={t('command.settings')}>
          <CommandItem value="settings account" onSelect={() => run(() => openSettings('account'))}>
            <Settings className="size-4 text-ink-muted" /> {t('settings.account')}
          </CommandItem>
          <CommandItem value="settings appearance" onSelect={() => run(() => openSettings('appearance'))}>
            <Settings className="size-4 text-ink-muted" /> {t('settings.appearance')}
          </CommandItem>
          <CommandItem value="settings notifications" onSelect={() => run(() => openSettings('notifications'))}>
            <Settings className="size-4 text-ink-muted" /> {t('settings.notifications')}
          </CommandItem>
          <CommandItem value="shortcuts help" onSelect={() => run(() => setOverlay('shortcuts'))}>
            <Settings className="size-4 text-ink-muted" /> {t('shortcuts.title')}
          </CommandItem>
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  )
}
