// Mounts every global overlay from the single store slot (spec §3).
import { useAppStore } from '../store/app'
import { SettingsDialog } from '../features/settings/SettingsDialog'
import { CreateServerDialog } from '../features/guild/CreateServerDialog'
import { JoinServerDialog } from '../features/guild/JoinServerDialog'
import { InviteDialog } from '../features/guild/InviteDialog'
import { GuildSettingsDialog } from '../features/guild/GuildSettingsDialog'
import { ModerationDialog } from '../features/guild/ModerationDialog'
import { OnboardingDialog } from '../features/onboarding/OnboardingDialog'
import { ShortcutsDialog } from './ShortcutsDialog'
import { Lightbox } from './Lightbox'
import { ConfirmDialog } from './ConfirmDialog'

export function OverlayHost() {
  const overlay = useAppStore((s) => s.overlay)
  const setOverlay = useAppStore((s) => s.setOverlay)
  const lightbox = useAppStore((s) => s.lightbox)
  const setLightboxIndex = useAppStore((s) => s.setLightboxIndex)
  const closeLightbox = useAppStore((s) => s.closeLightbox)

  return (
    <>
      <SettingsDialog />
      <GuildSettingsDialog />
      <CreateServerDialog />
      <JoinServerDialog />
      <InviteDialog />
      <ModerationDialog />
      <OnboardingDialog />
      <ShortcutsDialog open={overlay === 'shortcuts'} onOpenChange={(v) => setOverlay(v ? 'shortcuts' : null)} />
      {lightbox ? (
        <Lightbox
          items={lightbox.items}
          index={lightbox.index}
          onIndexChange={setLightboxIndex}
          onClose={closeLightbox}
        />
      ) : null}
      <ConfirmDialog />
    </>
  )
}
