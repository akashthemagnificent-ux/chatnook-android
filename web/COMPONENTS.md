# ChatNook component sources

Recorded per spec §2.3 step 4. The **master component → source mapping in §2.3 is
authoritative**; this file records the final choice for every shipped primitive and
the browser-use inspection that set the quality bar (§2.1/§2.2).

## Library inspection (browser-use, live rendering)

The app is a Capacitor bundle (offline `file://`, no runtime CDN), so §2's
copy-paste libraries are consumed as **source patterns**, not runtime deps. Every
primitive below is a hand-built, token-driven React component using the installed
Radix primitives + `@/lib` tokens — this is exactly what `shadcn/ui add` emits, and
is the "custom component to the same production bar" path explicitly allowed by
spec §0.1 and §2.3 step 3.

Live sites opened in browser-use (headless Chromium over CDP) to set the visual bar
(hover/focus polish, spacing, radius, motion restraint, empty/loading/error states):

| Site | Inspected | What set the bar |
|---|---|---|
| `ui.shadcn.com/docs/components/button` | button variants, docs chrome, rounded panels, focus rings | button/label/input/card structure, 2px accent focus ring, muted secondary text |
| `magicui.design/docs/components/shimmer-button` | shimmer button + installation panels | restrained primary CTA motion, pastel accent used sparingly |
| `ui.aceternity.com/components/aurora-background` | aurora background | subtle, low-opacity login backdrop that never dominates the card |

Other §2 pool members (originui, 21st.dev, hyperui, preline, hover.dev free set,
etc.) are alternative copy-paste sources for the same primitives; none supplies a
primitive the mapping lacks, so no additional pick was needed. The mapping table,
not a beauty contest between identical Radix wrappers, decides the source. Reactbits
(owner's original pick) is a motion-effects source; its restrained hover/entrance
ideas are reproduced locally with CSS (`fade-up`, `scale-in`) so the bundle stays
dependency-light and honors `prefers-reduced-motion`.

## Shipped primitives → source

| Component | Source (per §2.3 mapping) | Implementation |
|---|---|---|
| `Button` (primary/ghost/destructive/outline) | shadcn `button`; primary = magicui `shimmer-button` idea | `ui/base.tsx` — cva variants, coral accent, shimmer on hover for primary |
| `Input`, `Textarea` (auto-grow), `Label` | shadcn `input` / `textarea` / `label` | `ui/base.tsx` |
| `Card` (+ Header/Title/Content/Footer) | shadcn `card` | `ui/base.tsx` |
| `Badge` (unread/mention/role pill) | shadcn `badge` | `ui/base.tsx` |
| `Separator`, `Skeleton`, `Kbd` | shadcn `separator` / `skeleton` | `ui/base.tsx` (separator uses token border, not solid gray) |
| `Avatar` + status dot | shadcn `avatar`; status overlay custom | `ui/avatar.tsx` (overlay token-driven) |
| `Dialog`, `AlertDialog` | shadcn `dialog` | `ui/overlay.tsx` (Radix dialog) |
| `Sheet` (mobile drawers, profile popout) | shadcn `sheet` | `ui/overlay.tsx` (Radix dialog, side variants) |
| `DropdownMenu`, `ContextMenu` (message actions) | shadcn `dropdown-menu` / `context-menu` | `ui/overlay.tsx` (Radix) |
| `Tooltip` (icon labels) | shadcn `tooltip` | `ui/overlay.tsx` (Radix) |
| `Popover` (emoji/mention picker) | shadcn `popover` | `ui/overlay.tsx` (Radix) |
| `HoverCard` (user popout) | shadcn `hover-card` | `ui/overlay.tsx` (Radix) |
| `Tabs` (settings/profile sections) | shadcn `tabs` | `ui/controls.tsx` (Radix) |
| `Switch`, `Slider`, `Select`, `Collapsible`, `ScrollArea` | shadcn same | `ui/controls.tsx` (Radix) |
| `Command` (⌘K palette) | shadcn `command` (cmdk) | `ui/command.tsx` (cmdk) |
| Toast / notifications | `sonner` | `ui/toast.tsx` (token-styled Toaster) |
| `LoaderDots` (typing/sending) | indie-starter loader-dots | `components/LoaderDots.tsx` (CSS dot-pulse) |
| `AuroraBackground` (login) | aceternity / magicui aurora | `components/AuroraBackground.tsx` (CSS, lazy-loaded, subtle) |
| Empty-state card | magicui bento / reactbits card + lucide | `components/States.tsx` |

## Custom components (no library match, built to §2.2 bar, all token-driven)

| Component | Why custom | File |
|---|---|---|
| `ServerRail` | Discord-led IA, no generic library match | `features/shell/ServerRail.tsx` |
| `ChannelSidebar` | collapsible categories + unread badges | `features/shell/ChannelSidebar.tsx` |
| `MessageList` / `Message` | grouped variants, reactions, reply preview, delivery ticks | `features/chat/MessageList.tsx`, `Message.tsx` |
| `Composer` | auto-grow, emoji/mention insert, optimistic send | `features/chat/Composer.tsx` |
| `MemberPanel` | online/offline grouping, status dots | `features/shell/MemberPanel.tsx` |
| `UserPopout` | banner + avatar + roles + bio + moderation actions | `components/UserPopout.tsx` |
| `Lightbox` | image overlay with zoom and download | `components/Lightbox.tsx` |
| `ConfirmDialog` | one global destructive-action confirm, driven by `requestConfirm` | `components/ConfirmDialog.tsx` |
| `OverlayHost` | mounts every app-level overlay once at the root | `components/OverlayHost.tsx` |
| `RouteStates` | `NotFound` + `ErrorBoundary`, token-styled | `components/RouteStates.tsx` |
| `SettingsDialog` | Account / Appearance / Privacy / Notifications + sign out | `features/settings/SettingsDialog.tsx` |
| `GuildSettingsDialog` | Overview / Members / Roles / Emojis / Danger, permission switches | `features/guild/GuildSettingsDialog.tsx` |
| `CreateServerDialog`, `JoinServerDialog`, `InviteDialog` | server lifecycle from the rail menu and the guild menu | `features/guild/*.tsx` |
| `ModerationDialog` | ban list with unban + audit log | `features/guild/ModerationDialog.tsx` |
| `OnboardingDialog` | three-step first-run tour, remembered locally | `features/onboarding/OnboardingDialog.tsx` |
| `DmList`, `CommandPalette`, `ConnectionBanner`, `Splash`, `ShortcutsDialog`, `EmojiPicker` | app-specific surfaces | `features/**`, `components/**` |

All icons: `lucide-react`. All colors/sizes/radii/fonts come from §1 tokens in
`src/styles/globals.css` + `tailwind.config.ts`; no component hardcodes a hex.
