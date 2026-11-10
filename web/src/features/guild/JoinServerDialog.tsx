// Join a server (spec §3.15): paste a code or a full invite link.
// The invite preview card is not possible without a new RPC (invites are
// member-readable only), so the dialog goes straight to the join.
import { useState } from 'react'
import { LogIn } from 'lucide-react'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { t } from '../../lib/i18n'
import { toast } from '../../components/ui/toast'
import { Button, Input, Label } from '../../components/ui/base'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '../../components/ui/overlay'

/** Accepts a bare code or any URL whose last path segment is the code. */
export function parseInviteCode(input: string): string {
  const trimmed = input.trim()
  if (!trimmed) return ''
  const tail = trimmed.replace(/\/+$/, '').split('/').pop() ?? ''
  return tail.split('?')[0]
}

export function JoinServerDialog() {
  const overlay = useAppStore((s) => s.overlay)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const { joinGuild, selectGuild } = useChat()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const open = overlay === 'joinServer'

  const close = (v: boolean) => {
    if (v) return
    setOverlay(null)
    setCode('')
    setError(null)
  }

  const join = async () => {
    const parsed = parseInviteCode(code)
    if (!parsed) return
    setBusy(true)
    setError(null)
    try {
      const id = await joinGuild(parsed)
      selectGuild(id)
      toast.success(t('toast.serverJoined'))
      close(false)
    } catch {
      setError(t('guild.joinSubtitle'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-lg">
        <DialogTitle>{t('guild.joinTitle')}</DialogTitle>
        <DialogDescription>{t('guild.joinSubtitle')}</DialogDescription>
        <form className="mt-5 space-y-4" onSubmit={(e) => { e.preventDefault(); void join() }}>
          <div className="space-y-1.5">
            <Label htmlFor="invite-code">{t('guild.inviteCode')}</Label>
            <Input id="invite-code" autoFocus value={code}
              aria-invalid={Boolean(error)} aria-describedby={error ? 'invite-error' : undefined}
              placeholder={t('guild.inviteCodePlaceholder')} onChange={(e) => setCode(e.target.value)} />
            {error && <p id="invite-error" className="text-xs text-danger">{error}</p>}
          </div>
          <div className="flex items-center justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => close(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={!parseInviteCode(code) || busy}>
              <LogIn className="size-4" />
              {busy ? t('guild.joining') : t('guild.join')}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
