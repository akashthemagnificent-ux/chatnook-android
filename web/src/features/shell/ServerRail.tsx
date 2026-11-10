// Discord-led server rail (spec §3.3): home/DM button, server icons, add-server FAB.
// Active = accent pill, unread = white dot, hover = labeled tooltip.
import { useId } from 'react'
import { motion } from 'framer-motion'
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
      'flex size-12 shrink-0 items-center justify-center overflow-hidden text-base font-semibold transition-all duration-200 ease-out',
      active
        ? 'rounded-[30%] bg-brand-sheen text-ink-accent shadow-glow ring-1 ring-white/15'
        : 'rounded-[34%] bg-elevated/80 text-ink-secondary ring-1 ring-line/10 group-hover:-translate-y-0.5 group-hover:rounded-[30%] group-hover:bg-brand-sheen group-hover:text-ink-accent group-hover:shadow-glow group-hover:ring-white/15',
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

  const uid = useId()
  const pill = (active: boolean) => active ? (
    <motion.span
      layoutId={`rail-pill-${uid}`}
      transition={{ type: 'spring', stiffness: 520, damping: 38 }}
      className={horizontal
        ? 'absolute -bottom-2 left-[calc(50%-16px)] h-1 w-8 rounded-full bg-brand shadow-[0_0_14px_rgb(var(--brand)/0.8)]'
        : 'absolute -left-3 top-[calc(50%-20px)] h-10 w-1 rounded-r-full bg-brand shadow-[0_0_14px_rgb(var(--brand)/0.8)]'}
    />
  ) : null

  const dot = (show: boolean) => show ? (
    <span className={horizontal
      ? 'absolute right-0 top-0 size-3 rounded-full border-2 border-surface bg-brand shadow-[0_0_10px_rgb(var(--brand)/0.6)]'
      : 'absolute -left-2 top-[calc(50%-5px)] size-2.5 rounded-full border-2 border-surface bg-brand shadow-[0_0_10px_rgb(var(--brand)/0.6)]'} />
  ) : null

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
          {pill(homeActive)}
          {dot(homeUnread && !homeActive)}
          <span className={cn(
            'flex size-12 items-center justify-center rounded-[34%] transition-all duration-200 ease-out',
            homeActive
              ? 'rounded-[30%] bg-brand-sheen text-ink-accent shadow-glow ring-1 ring-white/15'
              : 'bg-elevated/80 text-ink-secondary ring-1 ring-line/10 group-hover:-translate-y-0.5 group-hover:rounded-[30%] group-hover:bg-brand-sheen group-hover:text-ink-accent group-hover:shadow-glow group-hover:ring-white/15',
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
              {pill(active)}
              {dot(count > 0 && !active)}
              <GuildIcon guild={g} active={active} />
            </button>
          </IconTooltip>
        )
      })}

      <DropdownMenu>
        <IconTooltip label={t('rail.addServer')} side={horizontal ? 'bottom' : 'right'}>
          <DropdownMenuTrigger asChild>
            <button className="group flex size-12 shrink-0 items-center justify-center" aria-label={t('rail.addServer')}>
              <span className="flex size-12 items-center justify-center rounded-[34%] border-2 border-dashed border-line/20 text-ink-secondary transition-all duration-200 ease-out group-hover:-translate-y-0.5 group-hover:rounded-[30%] group-hover:border-brand/60 group-hover:bg-brand/10 group-hover:text-brand group-hover:shadow-glow">
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
