// Create a server (spec §3.15): name step, then the invite step that actually
// gets friends in. Both steps write to the existing guilds/invites tables.
import { useState } from 'react'
import { Check, Copy, Sparkles } from 'lucide-react'
import * as api from '../../data/api'
import { useChat } from '../../data/ChatContext'
import { useAppStore } from '../../store/app'
import { useSession } from '../../store/session'
import { t } from '../../lib/i18n'
import { toast } from '../../components/ui/toast'
import { Button, Input, Label } from '../../components/ui/base'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '../../components/ui/overlay'

export function CreateServerDialog() {
  const overlay = useAppStore((s) => s.overlay)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const configured = useSession((s) => s.configured)
  const { createGuild, selectGuild } = useChat()
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState<{ id: string; code: string | null } | null>(null)
  const open = overlay === 'createServer'

  const close = (v: boolean) => {
    if (v) return
    setOverlay(null)
    setName('')
    setDone(null)
  }

  const create = async () => {
    const trimmed = name.trim()
    if (!trimmed) return
    setBusy(true)
    try {
      const id = await createGuild(trimmed)
      selectGuild(id)
      const code = configured ? await api.createInvite(id).catch(() => null) : null
      setDone({ id, code })
    } catch {
      toast.error(t('toast.somethingWrong'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-lg">
        <DialogTitle>{t('guild.createTitle')}</DialogTitle>
        <DialogDescription>{t('guild.createSubtitle')}</DialogDescription>

        {done ? (
          <div className="mt-5 space-y-4">
            <div className="flex items-center gap-2 rounded-md border border-success/25 bg-success/10 px-3 py-2.5 text-sm text-ink-primary">
              <Check className="size-4 text-success" />
              {t('toast.serverCreated')}
            </div>
            {done.code ? (
              <div className="space-y-1.5">
                <Label>{t('invite.codeLabel')}</Label>
                <div className="flex gap-2">
                  <Input readOnly value={done.code} onFocus={(e) => e.currentTarget.select()} />
                  <Button variant="outline" size="icon" aria-label={t('invite.copy')}
                    onClick={() => void navigator.clipboard.writeText(done.code!).then(() => toast.success(t('invite.copied')))}>
                    <Copy className="size-4" />
                  </Button>
                </div>
                <p className="text-xs text-ink-muted">{t('invite.subtitle')}</p>
              </div>
            ) : null}
            <div className="flex justify-end">
              <Button onClick={() => close(false)}>{t('common.continue')}</Button>
            </div>
          </div>
        ) : (
          <form className="mt-5 space-y-4" onSubmit={(e) => { e.preventDefault(); void create() }}>
            <div className="space-y-1.5">
              <Label htmlFor="guild-name">{t('guild.serverName')}</Label>
              <Input id="guild-name" autoFocus maxLength={100} value={name}
                placeholder={t('guild.serverNamePlaceholder')} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="flex items-center justify-end gap-2">
              <Button type="button" variant="ghost" onClick={() => close(false)}>{t('common.cancel')}</Button>
              <Button type="submit" disabled={!name.trim() || busy}>
                <Sparkles className="size-4" />
                {busy ? t('guild.creating') : t('guild.create')}
              </Button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
