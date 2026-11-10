// Subtle aurora + spotlight login backdrop (magicui/aceternity idea, CSS-only,
// lazy-loaded). Kept low-opacity so it never dominates the card (spec §3.1/§5).
import { Aurora } from './fx/backgrounds'

export default function AuroraBackground() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
      <Aurora />
      <div className="absolute left-1/2 top-[-12rem] h-[36rem] w-[36rem] -translate-x-1/2 animate-glow rounded-full bg-brand/12 blur-[120px]" />
    </div>
  )
}
