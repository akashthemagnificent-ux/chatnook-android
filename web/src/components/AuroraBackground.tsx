// Subtle aurora login backdrop (aceternity/magicui idea, CSS-only, lazy-loaded).
// Kept low-opacity so it never dominates the card (spec §3.1/§5).
export default function AuroraBackground() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
      <div className="absolute -top-1/3 left-1/2 h-[70vh] w-[120vw] -translate-x-1/2 rounded-full bg-brand/12 blur-[120px] animate-[fade-in_1s_ease-out_both]" />
      <div className="absolute -bottom-1/3 -left-1/4 h-[60vh] w-[70vw] rounded-full bg-info/10 blur-[130px]" />
      <div className="absolute -bottom-1/4 -right-1/4 h-[55vh] w-[60vw] rounded-full bg-brand/8 blur-[130px]" />
      {/* Faint grid gives a sense of depth without a heavy gradient. */}
      <div
        className="absolute inset-0 opacity-[0.025]"
        style={{
          backgroundImage: 'linear-gradient(rgb(var(--text-primary)) 1px, transparent 1px), linear-gradient(90deg, rgb(var(--text-primary)) 1px, transparent 1px)',
          backgroundSize: '48px 48px',
          maskImage: 'radial-gradient(ellipse at center, black, transparent 75%)',
        }}
      />
    </div>
  )
}
