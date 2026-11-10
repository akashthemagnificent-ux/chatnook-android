// Slim offline/reconnecting banner (spec §3.14). Never blocks the UI.
import { WifiOff, Loader2 } from 'lucide-react'
import { t } from '../lib/i18n'

export type ConnectionStatus = 'online' | 'connecting' | 'reconnecting' | 'offline'

export function ConnectionBanner({ status }: { status: ConnectionStatus }) {
  if (status === 'online') return null
  const config = {
    connecting: { icon: <Loader2 className="size-3.5 animate-spin" />, text: t('connection.connecting'), cls: 'bg-info/10 text-info' },
    reconnecting: { icon: <Loader2 className="size-3.5 animate-spin" />, text: t('connection.reconnecting'), cls: 'bg-warning/10 text-warning' },
    offline: { icon: <WifiOff className="size-3.5" />, text: t('connection.offline'), cls: 'bg-warning/10 text-warning' },
  }[status]
  return (
    <div
      className={`relative flex h-8 shrink-0 animate-fade-in items-center justify-center gap-2 border-b border-line/10 text-xs font-medium backdrop-blur ${config.cls}`}
      role="status"
    >
      {config.icon}{config.text}
      <span aria-hidden className="absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-current to-transparent opacity-30" />
    </div>
  )
}
