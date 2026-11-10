// Keyboard shortcut help (spec §3.23). Every shortcut also has a visible path.
import { Dialog, DialogContent, DialogTitle, DialogDescription } from './ui/overlay'
import { Kbd } from './ui/base'
import { t } from '../lib/i18n'

const SHORTCUTS: { keys: string[]; label: string }[] = [
  { keys: ['⌘', 'K'], label: t('shortcuts.palette') },
  { keys: ['Ctrl', 'K'], label: t('shortcuts.paletteAlt') },
  { keys: ['Esc'], label: t('shortcuts.close') },
  { keys: ['↑'], label: t('shortcuts.editLast') },
  { keys: ['Ctrl', 'F'], label: t('shortcuts.channelSearch') },
  { keys: ['Alt', '↑'], label: t('shortcuts.prevChannel') },
  { keys: ['Alt', '↓'], label: t('shortcuts.nextChannel') },
  { keys: ['Enter'], label: t('shortcuts.send') },
  { keys: ['Shift', 'Enter'], label: t('shortcuts.newLine') },
]

export function ShortcutsDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (v: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogTitle>{t('shortcuts.title')}</DialogTitle>
        <DialogDescription>{t('shortcuts.subtitle')}</DialogDescription>
        <div className="mt-5 flex flex-col divide-y divide-line/10">
          {SHORTCUTS.map((s) => (
            <div key={s.label} className="flex items-center justify-between gap-4 py-2.5">
              <span className="text-sm text-ink-secondary">{s.label}</span>
              <span className="flex shrink-0 gap-1">
                {s.keys.map((k) => <Kbd key={k}>{k}</Kbd>)}
              </span>
            </div>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
