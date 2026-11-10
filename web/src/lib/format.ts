// en-US formatting helpers (spec §10): relative times, file sizes, numbers.
import { t } from './i18n'

export function relativeTime(iso: string, now = Date.now()): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const diff = Math.max(0, now - then)
  const min = 60_000
  const hour = 60 * min
  const day = 24 * hour
  if (diff < min) return t('common.now')
  if (diff < hour) return `${Math.floor(diff / min)}m`
  if (diff < day) return `${Math.floor(diff / hour)}h`
  if (diff < 7 * day) return `${Math.floor(diff / day)}d`
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

export function dayDivider(iso: string, now = Date.now()): string {
  const date = new Date(iso)
  const today = new Date(now)
  const sameDay = (a: Date, b: Date) =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  const yesterday = new Date(now - 86_400_000)
  if (sameDay(date, today)) return t('chat.today')
  if (sameDay(date, yesterday)) return t('chat.yesterday')
  return date.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })
}

export function messageTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

export function fullTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit',
  })
}

export function fileSize(bytes: number | null | undefined): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function numberWithSeparator(value: number): string {
  return value.toLocaleString('en-US')
}

export function roleColor(color: number | null | undefined): string | undefined {
  if (!color) return undefined
  return `#${(color & 0xffffff).toString(16).padStart(6, '0')}`
}
