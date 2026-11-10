// Hover/click user profile popout (spec §3.8/§3.9). Compact functional header,
// roles, bio, and optional moderation actions — no dominating gradient banner.
import { MessageSquare, UserMinus, Ban, ShieldAlert } from 'lucide-react'
import { UserAvatar } from './ui/avatar'
import { Button } from './ui/base'
import { Badge } from './ui/base'
import { Separator } from './ui/base'
import { displayName } from '../lib/types'
import { roleColor } from '../lib/format'
import { t } from '../lib/i18n'
import type { Profile, Role } from '../lib/types'

export function UserPopoutContent({
  profile,
  roles = [],
  canModerate = false,
  onMessage,
  onKick,
  onBan,
  full = true,
}: {
  profile: Profile | null
  roles?: Role[]
  canModerate?: boolean
  onMessage?: () => void
  onKick?: () => void
  onBan?: () => void
  full?: boolean
}) {
  const name = displayName(profile)
  return (
    <div className="overflow-hidden">
      {/* Compact functional header: a token-tinted accent strip, not a giant banner. */}
      <div className="h-14 bg-gradient-to-r from-brand/25 to-brand/5" />
      <div className="relative px-4 pb-4">
        <div className="-mt-7 mb-2">
          <UserAvatar name={name} src={profile?.avatar_url} size={56} ring className="ring-[3px]" />
        </div>
        <h3 className="text-md font-bold text-ink-primary">{name}</h3>
        <p className="text-sm text-ink-secondary">@{profile?.username ?? 'unknown'}</p>
        {profile?.custom_status && <p className="mt-1.5 text-sm text-ink-secondary">{profile.custom_status}</p>}

        {full && (
          <>
            <Separator className="my-3" />
            {roles.length > 0 && (
              <div className="mb-3">
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-ink-muted">{t('members.roles')}</p>
                <div className="flex flex-wrap gap-1.5">
                  {roles.map((r) => (
                    <Badge key={r.id} variant="outline" className="gap-1.5">
                      <span className="size-2 rounded-full" style={{ background: roleColor(r.color) ?? 'rgb(var(--ink-muted))' }} />
                      {r.name}
                    </Badge>
                  ))}
                </div>
              </div>
            )}
            <div className="flex gap-2">
              {onMessage && (
                <Button variant="default" size="sm" className="flex-1" onClick={onMessage}>
                  <MessageSquare /> Message
                </Button>
              )}
            </div>
            {canModerate && (
              <>
                <Separator className="my-3" />
                <p className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-ink-muted">
                  <ShieldAlert className="size-3.5" /> Moderator actions
                </p>
                <div className="flex flex-col gap-1">
                  {onKick && (
                    <Button variant="ghost" size="sm" className="justify-start text-ink-secondary" onClick={onKick}>
                      <UserMinus /> Kick
                    </Button>
                  )}
                  {onBan && (
                    <Button variant="ghost" size="sm" className="justify-start text-danger" onClick={onBan}>
                      <Ban /> Ban
                    </Button>
                  )}
                </div>
              </>
            )}
          </>
        )}
      </div>
    </div>
  )
}
