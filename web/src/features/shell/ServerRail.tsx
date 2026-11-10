// Discord-led server rail (spec §3.3): home/DM button, server icons, add-server FAB.
// Active = accent pill, unread = white dot, hover = labeled tooltip.
import { LogIn, MessagesSquare, Plus } from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { initials } from '../../lib/types'
import { cn } from '../../lib/utils'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger, IconTooltip } from '../../components/ui/overlay'
import { t } from '../../lib/i18n'
import type { Guild } from '../../lib/types'

function GuildIcon({ guild, active }: { guild: Guild; active: boolean }) {
  return (
    <span className={cn(
      'flex size-12 shrink-0 items-center justify-center overflow-hidden text-base font-semibold transition-all duration-150 ease-out',
      active
        ? 'rounded-[22%] bg-brand text-ink-accent shadow-soft'
        : 'rounded-[30%] bg-elevated text-ink-secondary ring-1 ring-line/10 group-hover:rounded-[22%] group-hover:bg-brand group-hover:text-ink-accent group-hover:ring-brand',
    )}>
      {guild.icon_url
        ? <img src={guild.icon_url} alt="" className="size-full object-cover" />
        : initials(guild.name)}
    </span>
  )
}

export function ServerRail({
  orientation = 'vertical',
  onNavigate,
}: {
  orientation?: 'vertical' | 'horizontal'
  onNavigate?: () => void
}) {
  const { guilds, activeGuildId, selectGuild, guildUnread, unread, dms } = useChat()
  const setOverlay = useAppStore((s) => s.setOverlay)
  const horizontal = orientation === 'horizontal'
  const homeActive = activeGuildId === null
  const homeUnread = dms.some((d) => (unread[d.channel_id]?.unread ?? 0) > 0)

  const pill = (active: boolean) => active
    ? horizontal
      ? 'absolute -bottom-2 left-1/2 h-1 w-8 -translate-x-1/2 rounded-full bg-ink-primary'
      : 'absolute -left-3 top-1/2 h-10 w-1 -translate-y-1/2 rounded-r-full bg-ink-primary'
    : 'hidden'

  const dot = (show: boolean) => show
    ? horizontal
      ? 'absolute -top-1 right-0 size-3 rounded-full border-2 border-surface bg-ink-primary'
      : 'absolute -left-2 top-1/2 size-2.5 -translate-y-1/2 rounded-full border-2 border-surface bg-ink-primary'
    : 'hidden'

  const pick = (id: string | null) => { selectGuild(id); onNavigate?.() }

  return (
    <nav
      aria-label={t('nav.servers')}
      className={cn(
        'flex shrink-0 items-center gap-2 bg-surface',
        horizontal ? 'w-full flex-row overflow-x-auto px-3 py-2 scrollbar-thin' : 'h-full w-[72px] flex-col overflow-y-auto py-3 scrollbar-thin',
      )}
    >
      {/* Home / direct messages */}
      <IconTooltip label={t('rail.home')} side={horizontal ? 'bottom' : 'right'}>
        <button onClick={() => pick(null)} className="group relative flex size-12 shrink-0 items-center justify-center" aria-label={t('rail.home')}>
          <span className={pill(homeActive)} />
          <span className={dot(homeUnread && !homeActive)} />
          <span className={cn(
            'flex size-12 items-center justify-center rounded-[30%] transition-all duration-150 ease-out',
            homeActive
              ? 'rounded-[22%] bg-brand text-ink-accent shadow-soft'
              : 'bg-elevated text-ink-secondary ring-1 ring-line/10 group-hover:rounded-[22%] group-hover:bg-brand group-hover:text-ink-accent group-hover:ring-brand',
          )}>
            <MessagesSquare className="size-6" />
          </span>
        </button>
      </IconTooltip>

      <span className={cn('shrink-0 bg-line/10', horizontal ? 'h-8 w-px' : 'h-px w-8')} />

      {guilds.map((g) => {
        const active = g.id === activeGuildId
        const count = guildUnread[g.id] ?? 0
        return (
          <IconTooltip key={g.id} label={g.name} side={horizontal ? 'bottom' : 'right'}>
            <button onClick={() => pick(g.id)} className="group relative flex size-12 shrink-0 items-center justify-center" aria-label={g.name} aria-current={active ? 'true' : undefined}>
              <span className={pill(active)} />
              <span className={dot(count > 0 && !active)} />
              <GuildIcon guild={g} active={active} />
            </button>
          </IconTooltip>
        )
      })}

      <DropdownMenu>
        <IconTooltip label={t('rail.addServer')} side={horizontal ? 'bottom' : 'right'}>
          <DropdownMenuTrigger asChild>
            <button className="group flex size-12 shrink-0 items-center justify-center" aria-label={t('rail.addServer')}>
              <span className="flex size-12 items-center justify-center rounded-[30%] border-2 border-dashed border-line/20 text-success transition-all duration-150 ease-out group-hover:rounded-[22%] group-hover:border-brand group-hover:bg-brand/12 group-hover:text-brand">
                <Plus className="size-6" />
              </span>
            </button>
          </DropdownMenuTrigger>
        </IconTooltip>
        <DropdownMenuContent side={horizontal ? 'bottom' : 'right'} align="start" className="w-52">
          <DropdownMenuItem onSelect={() => { setOverlay('createServer'); onNavigate?.() }}>
            <Plus className="size-4" /> {t('guild.createTitle')}
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => { setOverlay('joinServer'); onNavigate?.() }}>
            <LogIn className="size-4" /> {t('guild.joinTitle')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </nav>
  )
}
