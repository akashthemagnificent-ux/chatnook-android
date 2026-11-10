// One confirm dialog for every destructive action (spec §4): no window.confirm.
import { useAppStore } from '../store/app'
import { t } from '../lib/i18n'
import { Button } from './ui/base'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from './ui/overlay'

export function ConfirmDialog() {
  const confirm = useAppStore((s) => s.confirm)
  const clearConfirm = useAppStore((s) => s.clearConfirm)

  const run = () => {
    const action = confirm?.onConfirm
    clearConfirm()
    action?.()
  }

  return (
    <Dialog open={Boolean(confirm)} onOpenChange={(v) => { if (!v) clearConfirm() }}>
      <DialogContent className="max-w-sm">
        <DialogTitle>{confirm?.title}</DialogTitle>
        {confirm?.body ? <DialogDescription>{confirm.body}</DialogDescription> : null}
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={clearConfirm}>{t('common.cancel')}</Button>
          <Button variant={confirm?.destructive ? 'destructive' : 'primary'} onClick={run}>
            {confirm?.confirmLabel ?? t('common.confirm')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
