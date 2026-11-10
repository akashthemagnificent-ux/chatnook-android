// Full-screen attachment viewer with prev/next, zoom, download (spec §3.20).
import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, Download, Minus, Plus, X } from 'lucide-react'
import type { Attachment } from '../lib/types'
import { fileSize } from '../lib/format'
import { t } from '../lib/i18n'
import { Button } from './ui/base'

export function Lightbox({ items, index, onIndexChange, onClose }: {
  items: Attachment[]
  index: number
  onIndexChange: (i: number) => void
  onClose: () => void
}) {
  const item = items[index]
  const [zoom, setZoom] = useState(1)

  useEffect(() => {
    setZoom(1)
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      else if (e.key === 'ArrowRight' && index < items.length - 1) onIndexChange(index + 1)
      else if (e.key === 'ArrowLeft' && index > 0) onIndexChange(index - 1)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [index, items.length, onClose, onIndexChange])

  if (!item) return null
  const isImage = item.content_type?.startsWith('image/')
  return (
    <div className="fixed inset-0 z-[70] flex flex-col bg-black/90 backdrop-blur-md animate-fade-in" role="dialog" aria-modal="true" aria-label={t('lightbox.viewer')}>
      <div className="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-white/10 bg-black/40 px-4 text-ink-primary backdrop-blur-xl">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{item.filename ?? t('message.file')}</p>
          <p className="text-xs text-white/60">{fileSize(item.size_bytes)} · {t('lightbox.position', { n: index + 1, total: items.length })}</p>
        </div>
        <div className="flex items-center gap-1">
          {isImage && (
            <>
              <Button variant="ghost" size="icon-sm" aria-label={t('lightbox.zoomOut')} onClick={() => setZoom((z) => Math.max(0.5, z - 0.25))}><Minus /></Button>
              <span className="w-10 text-center text-xs tabular-nums text-white/70">{Math.round(zoom * 100)}%</span>
              <Button variant="ghost" size="icon-sm" aria-label={t('lightbox.zoomIn')} onClick={() => setZoom((z) => Math.min(3, z + 0.25))}><Plus /></Button>
            </>
          )}
          <a href={item.url} download={item.filename ?? undefined} target="_blank" rel="noopener noreferrer"
            className="inline-flex size-9 items-center justify-center rounded-full text-white/80 transition-colors hover:bg-white/10" aria-label={t('lightbox.download')}>
            <Download className="size-4" />
          </a>
          <Button variant="ghost" size="icon-sm" aria-label={t('a11y.close')} onClick={onClose}><X /></Button>
        </div>
      </div>
      <div className="relative flex flex-1 items-center justify-center overflow-hidden p-4">
        {isImage ? (
          <img src={item.url} alt={item.filename ?? ''} className="max-h-full max-w-full animate-scale-in rounded-lg object-contain shadow-[0_30px_90px_-30px_rgba(0,0,0,0.9)] transition-transform duration-150" style={{ transform: `scale(${zoom})` }} />
        ) : (
          <div className="flex flex-col items-center gap-4 text-center text-white">
            <p className="text-lg">{item.filename}</p>
            <a href={item.url} target="_blank" rel="noopener noreferrer" className="rounded-full bg-brand px-5 py-2.5 font-medium text-ink-accent shadow-glow">{t('lightbox.openFile')}</a>
          </div>
        )}
        {items.length > 1 && (
          <>
            <button onClick={() => onIndexChange(index - 1)} disabled={index === 0} aria-label={t('lightbox.previous')}
              className="absolute left-3 flex size-11 items-center justify-center rounded-full border border-white/10 bg-black/50 text-white backdrop-blur transition-all hover:scale-105 hover:bg-black/70 disabled:opacity-30">
              <ChevronLeft className="size-5" />
            </button>
            <button onClick={() => onIndexChange(index + 1)} disabled={index === items.length - 1} aria-label={t('lightbox.next')}
              className="absolute right-3 flex size-11 items-center justify-center rounded-full border border-white/10 bg-black/50 text-white backdrop-blur transition-all hover:scale-105 hover:bg-black/70 disabled:opacity-30">
              <ChevronRight className="size-5" />
            </button>
          </>
        )}
      </div>
    </div>
  )
}
