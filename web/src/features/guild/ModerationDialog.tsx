// Moderation (spec §3.19): the live ban list and the audit trail.
// There is no blocked-users table in the schema, so bans are the real surface.
import { useEffect, useState } from 'react'
import { ShieldCheck } from 'lucide-react'
import * as api from '../../data/api'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useSession } from '../../store/session'
import { t } from '../../lib/i18n'
import { displayName, type AuditEntry, type BannedUser } from '../../lib/types'
import { fullTime } from '../../lib/format'
import { toast } from '../../components/ui/toast'
import { UserAvatar } from '../../components/ui/avatar'
import { Button, Separator } from '../../components/ui/base'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '../../components/ui/overlay'

export function ModerationDialog() {
  const overlay = useAppStore((s) => s.overlay)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const configured = useSession((s) => s.configured)
  const { activeGuild, moderation } = useChat()
  const [bans, setBans] = useState<BannedUser[]>([])
  const [audit, setAudit] = useState<AuditEntry[]>([])
  const [loading, setLoading] = useState(false)
  const open = overlay === 'moderation' && Boolean(activeGuild)

  useEffect(() => {
    if (!open || !configured || !activeGuild) return
    let alive = true
    setLoading(true)
    void Promise.all([api.getBans(activeGuild.id), api.getAuditLog(activeGuild.id)])
      .then(([nextBans, nextAudit]) => {
        if (!alive) return
        setBans(nextBans)
        setAudit(nextAudit)
      })
      .catch(() => {})
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [open, configured, activeGuild])

  const unban = async (memberId: string) => {
    if (!activeGuild) return
    try {
      await moderation.unban(activeGuild.id, memberId)
      setBans((prev) => prev.filter((b) => b.user_id !== memberId))
      toast.success(t('toast.memberUnbanned'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) setOverlay(null) }}>
      <DialogContent className="max-h-[min(640px,88vh)] max-w-2xl overflow-y-auto scrollbar-thin">
        <DialogTitle className="flex items-center gap-2">
          <ShieldCheck className="size-5 text-brand" />
          {t('moderation.title')}
        </DialogTitle>
        <DialogDescription>{t('moderation.subtitle')}</DialogDescription>

        <section className="mt-5 space-y-2">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{t('guild.bannedList')}</h3>
          {bans.length === 0 ? (
            <p className="text-sm text-ink-muted">{loading ? t('common.loading') : t('guild.noBans')}</p>
          ) : (
            <ul className="divide-y divide-line/8 rounded-lg border border-line/10">
              {bans.map((ban) => (
                <li key={ban.user_id} className="flex items-center gap-3 px-3 py-2.5">
                  <UserAvatar name={displayName(ban.profile)} src={ban.profile?.avatar_url} size={32} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{displayName(ban.profile)}</p>
                    <p className="truncate text-xs text-ink-muted">{ban.reason || t('guild.noBans')}</p>
                  </div>
                  <Button variant="outline" size="sm" onClick={() => void unban(ban.user_id)}>
                    {t('guild.unban')}
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <Separator className="my-5" />

        <section className="space-y-2">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{t('guild.auditLog')}</h3>
          {audit.length === 0 ? (
            <p className="text-sm text-ink-muted">{loading ? t('common.loading') : t('guild.noAudit')}</p>
          ) : (
            <ul className="space-y-1">
              {audit.map((entry) => (
                <li key={entry.id} className="rounded-md px-2 py-1.5 text-sm hover:bg-hover">
                  <span className="font-medium">{displayName(entry.actor)}</span>{' '}
                  <span className="text-ink-secondary">
                    {entry.action === 'kick' ? t('guild.actionKick') : entry.action === 'ban' ? t('guild.actionBan') : t('guild.actionUnban')}
                  </span>{' '}
                  <span className="font-medium">{displayName(entry.target)}</span>
                  <span className="ml-2 text-xs text-ink-muted">{fullTime(entry.created_at)}</span>
                  {entry.detail ? <p className="mt-0.5 text-xs text-ink-muted">{entry.detail}</p> : null}
                </li>
              ))}
            </ul>
          )}
        </section>
      </DialogContent>
    </Dialog>
  )
}
