// Invite flow (spec §3.16): generate, copy, revoke. Backed by invites + RPCs.
import { useEffect, useState } from 'react'
import { Copy, Link2, Trash2 } from 'lucide-react'
import * as api from '../../data/api'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useSession } from '../../store/session'
import { t } from '../../lib/i18n'
import { relativeTime } from '../../lib/format'
import { toast } from '../../components/ui/toast'
import { Button, Input, Label, Separator } from '../../components/ui/base'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '../../components/ui/overlay'
import type { Invite } from '../../lib/types'

export function InviteDialog() {
  const overlay = useAppStore((s) => s.overlay)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const configured = useSession((s) => s.configured)
  const { activeGuild } = useChat()
  const [invites, setInvites] = useState<Invite[]>([])
  const [code, setCode] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const open = overlay === 'invite'

  useEffect(() => {
    if (!open || !configured || !activeGuild) return
    let alive = true
    void api.getInvites(activeGuild.id).then((rows) => {
      if (!alive) return
      setInvites(rows)
      setCode(rows[0]?.code ?? null)
    }).catch(() => {})
    return () => { alive = false }
  }, [open, configured, activeGuild])

  const generate = async () => {
    if (!activeGuild) return
    setBusy(true)
    try {
      const next = await api.createInvite(activeGuild.id)
      setCode(next)
      setInvites(await api.getInvites(activeGuild.id))
      toast.success(t('toast.inviteCreated'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setBusy(false)
    }
  }

  const revoke = async (target: string) => {
    try {
      await api.revokeInvite(target)
      setInvites((prev) => prev.filter((i) => i.code !== target))
      if (code === target) setCode(null)
      toast.success(t('toast.inviteRevoked'))
    } catch {
      toast.error(t('toast.somethingWrong'))
    }
  }

  const copy = (value: string) => {
    void navigator.clipboard.writeText(value).then(() => toast.success(t('invite.copied')))
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) setOverlay(null) }}>
      <DialogContent className="max-w-lg">
        <DialogTitle>{t('invite.title')}</DialogTitle>
        <DialogDescription>{t('invite.subtitle')}</DialogDescription>

        <div className="mt-5 space-y-2">
          <Label htmlFor="invite-current">{t('invite.codeLabel')}</Label>
          <div className="flex gap-2">
            <Input id="invite-current" readOnly value={code ?? ''}
              placeholder={t('invite.noInvites')}
              onFocus={(e) => e.currentTarget.select()} />
            <Button variant="outline" size="icon" aria-label={t('invite.copy')} disabled={!code}
              onClick={() => code && copy(code)}>
              <Copy className="size-4" />
            </Button>
          </div>
          <Button className="w-full" onClick={() => void generate()} disabled={!configured || !activeGuild || busy}>
            <Link2 className="size-4" />
            {t('invite.generate')}
          </Button>
        </div>

        <Separator className="my-5" />

        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{t('invite.activeInvites')}</p>
          {invites.length === 0 ? (
            <p className="text-sm text-ink-muted">{t('invite.noInvites')}</p>
          ) : (
            <ul className="max-h-48 space-y-1 overflow-y-auto scrollbar-thin">
              {invites.map((invite) => (
                <li key={invite.code} className="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-hover">
                  <code className="min-w-0 flex-1 truncate text-sm">{invite.code}</code>
                  <span className="shrink-0 text-xs text-ink-muted">
                    {invite.max_uses == null ? t('invite.unlimited') : t('invite.uses', { uses: invite.uses ?? 0 })}
                    {' · '}
                    {relativeTime(invite.created_at)}
                  </span>
                  <Button variant="ghost" size="icon-sm" aria-label={t('invite.revoke')}
                    onClick={() => void revoke(invite.code)}>
                    <Trash2 className="size-4" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
