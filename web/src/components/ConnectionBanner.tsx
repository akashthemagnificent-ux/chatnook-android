// Slim offline/reconnecting banner (spec §3.14). Never blocks the UI.
import { WifiOff, Loader2 } from 'lucide-react'

export type ConnectionStatus = 'online' | 'connecting' | 'reconnecting' | 'offline'

export function ConnectionBanner({ status }: { status: ConnectionStatus }) {
  if (status === 'online') return null
  const config = {
    connecting: { icon: <Loader2 className="size-3.5 animate-spin" />, text: 'Connecting…', cls: 'bg-info/10 text-info' },
    reconnecting: { icon: <Loader2 className="size-3.5 animate-spin" />, text: 'Reconnecting…', cls: 'bg-warning/12 text-warning' },
    offline: { icon: <WifiOff className="size-3.5" />, text: 'Offline — messages will send when reconnected', cls: 'bg-warning/12 text-warning' },
  }[status]
  return (
    <div className={`flex h-8 shrink-0 items-center justify-center gap-2 text-xs font-medium ${config.cls}`} role="status">
      {config.icon}{config.text}
    </div>
  )
}
