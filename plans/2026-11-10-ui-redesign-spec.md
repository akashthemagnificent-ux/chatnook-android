# ChatNook — Full UI Redesign Instruction Specification

**Status:** Mandatory redesign brief for the post-prototype phase.
**Audience:** The redesign agent (and any human reviewer).
**Goal:** Replace the current prototype UI (see §0.3 "as-is" screenshots) with a
professional, polished, modern chat interface inspired by Discord and other major
chat apps (Telegram, Slack, WhatsApp, iMessage). Every screen, every component,
every state must meet a production-ready quality bar — no "prototype" look.

---

## 0. Critical decisions & scope (read before building)

### 0.1 Target stack — RESOLVE THIS FIRST
The current prototype is native **Android (Jetpack Compose)**. The mandated UI
libraries (§2) are all **React + Tailwind CSS web component libraries** and cannot
be imported into a Compose app. The redesign target reconciles the "Android APK"
rule with the library-first preference (PROJECT_RULES §4/§8), with the owner's
clarification that **custom components of equal production quality are explicitly
allowed and expected** (see §2):

> **React + Tailwind SPA, packaged for Android with Capacitor (free, no money).**
> - Single React + Tailwind codebase using the §2 libraries directly.
> - `npx @capacitor/create-app` + Android platform → builds a real `.apk`/`.aab`.
> - APK build still runs only in GitHub Actions (PROJECT_RULES §1), never locally.
>
> **Custom components are explicitly allowed and expected** (owner clarification):
> prefer a library primitive when one fits, but build a custom component to the same
> production-quality bar whenever the library has no match (§2). **If the Capacitor
> path fails for any reason**, the agent may fall back to another build (native
> Compose or plain web) — the design system (§1) and component specs (§3) still
> apply unchanged, and custom components simply recreate the library look. Capacitor
> stays the recommended default because it uses the libraries directly.

### 0.2 Inspiration hierarchy
1. **Discord (primary)** — information architecture: server rail → channel sidebar
   → message view → member panel; dark-first surface layering; single-accent
   discipline; hover-rich, context-menu-driven interactions.
2. **Telegram** — clean message list, readable bubbles, inline reply/preview.
3. **Slack** — workspace/channel model, command palette, threading.
4. **WhatsApp / iMessage** — compositional simplicity, bubble clarity, typing &
   presence feedback.

Note: *inspired by* means adopt patterns and quality — **do not copy Discord's
exact colors, logos, or assets.** Use an original palette (§1.1).

### 0.3 As-is problems this redesign must eliminate
Derived from the four attached screenshots:
- Tiny, low-contrast secondary text; no typographic hierarchy.
- Mismatched saturated accents (purple/blue/pink/green) with no system.
- Cramped, edge-hugging toolbars; huge empty black regions.
- No message bubbles, timestamps, grouping, reactions, or unread cues.
- Decorative gradient banner dominating the profile screen; weak sectioning.
- Default-looking form fields and thin-outline buttons; unclear selection/active
  states; no focus/hover/disabled states; no empty/loading/error states.
- Icon-only controls with no labels; low-res, dated icon shapes.

Every item above is a hard failure in the new design.

---

## 1. Design system foundation (build this first, everything references it)

### 1.1 Color — dark-first, layered, single accent
Define as CSS variables (Tailwind theme extension). Never hardcode.
- **Backgrounds (3 surface depths):** `--bg-base` (app), `--bg-surface`
  (sidebars/cards), `--bg-elevated` (popovers/menus). Each step noticeably
  lighter than the last; pick an original cool-neutral scale, not pure black.
- **Single brand accent:** one saturated color used *only* for primary actions,
  active states, mentions, and focus rings. Choose an original hue (do NOT use
  Discord Blurple's exact value).
- **Semantic colors:** success / warning / danger / info, each with a dark-mode
  variant. Used sparingly (status dots, errors).
- **Text:** `--text-primary` (high contrast, ~AA+), `--text-secondary`
  (muted but still legible ≥ 4.5:1), `--text-on-accent`.
- **Borders/dividers:** low-opacity white overlay, not solid gray lines.
- Provide a **light theme** variant of the same tokens (toggle, persisted).

### 1.2 Typography
- One clean geometric/neo-grotesque sans (system-ui stack or a free Google font,
  e.g. Inter/Geist — free, no cost). No more than **two** families (UI + optional
  mono for codes/IDs).
- Scale (rem): `xs .75 · sm .8125 · base .875 · md 1 · lg 1.125 · xl 1.25 ·
  title 1.5 · display 2`. Generous line-height (1.5 body).
- Explicit weights: 400 body, 500/600 emphasis, 700 headings. Letter-spacing
  tightened on large headings.

### 1.3 Spacing, radius, elevation
- 4px base spacing scale (4/8/12/16/24/32/48). Consistent 16–24px gutters.
- Radius scale: `sm 6 · md 10 · lg 14 · xl 20 · full 9999`. Cards/bubbles use
  `md`–`lg`; pills/fabs use `full`.
- Elevation: soft, low-opacity shadows for elevated surfaces; subtle inner border
  instead of heavy drop shadows.

### 1.4 Motion (restrained, purposeful)
- Durations 150–250ms; ease-out curves. Hover/focus transitions on interactive
  elements. Respect `prefers-reduced-motion`.
- Use library motion components (§2) for hero/empty-state flair only — not on
  every control.

### 1.5 Theming & tokens delivery
- Tokens in `tailwind.config.ts` + a `globals.css` with `:root` and
  `.theme-light` variable blocks. A `ThemeProvider` toggles a class on `<html>`.

### 1.6 Brand & identity (single source of truth)
- **App name:** ChatNook — used verbatim in UI copy (no "ChatNook!", no stylized
  variants).
- **Brand accent:** the single accent defined in §1.1. Applied to logo, primary
  buttons, active states, focus rings. Define `--brand` once; never re-declare.
- **Logo / app icon:** a simple, original mark (no Discord likeness). If no final
  mark exists, use a token-styled monogram placeholder; do **not** ship the
  prototype's purple "C" as final.
- **Wordmark:** "ChatNook" in the §1.2 display weight.
- All brand values live in §1 tokens; components reference tokens, never literals.

### 1.7 Theming & density details
- **Default theme:** dark (§1.1). On first launch, follow OS preference
  (`prefers-color-scheme`); the user can override and the choice persists.
- **Light theme:** same token names, lighter surface scale; verify AA contrast in
  **both** themes before shipping.
- **Density:** two modes — Comfortable (default) and Compact — driven by the §3.9
  density `slider`; Compact reduces row height / vertical padding ~20%.
- **No theme flash:** apply the stored theme class before first paint (inline boot
  script or Capacitor native theme).

---

## 2. Component library mandate & mapping

**Rule (PROJECT_RULES §4/§8, clarified by owner) — MANDATORY library search:**
1. **Search the entire free library pool (§2 list) before building anything.** This is
   a hard rule, not a suggestion: if the needed component is not in one library, check
   *every* library in the list. Do not stop at the first miss.
2. **Prefer the best-looking fit.** When several libraries offer the same primitive
   (e.g. multiple chat-bubble components), use the **browser-use plugin** (globally
   installed) to actually *see* each candidate rendered, then pick the best-looking,
   most polished one per the §2.2 quality bar.
3. **Custom only when truly absent.** If the component exists in none of the
   libraries, invent a custom one to the same production bar (also if the Capacitor
   path fails). Custom is fully acceptable when it meets the bar and uses §1 tokens.

**Free library pool (all Tailwind/React, copy-paste components — free only):**

| Library | Role / strengths |
|---|---|
| **shadcn/ui** | Backbone primitives: button, input, card, avatar, badge, dialog, dropdown/sheet, tabs, scroll-area, tooltip, popover, command, switch, slider, select, separator, skeleton, sonner. |
| **magicui.design** | Motion/flair: shimmer-button, aurora-background, bento-grid, typing-animation, blur-fade, pulsating-button. |
| **ui.aceternity.com** | Hero/backgrounds & spotlight cards: aurora-background, spotlight, background-beams, card-spotlight, 3d-card, floating-nav. |
| **reactbits.dev** | Animated buttons/cards/backgrounds, text animations (owner's original pick). |
| **cuicui.day** | Application UI: command-menu, combobox, color-picker, date-picker. |
| **ui.indie-starter.dev** | Loaders: loader-dots, spinners (sending/fetching/typing). |
| **ui-layout.com** | Layout blocks & sections. |
| **eldoraui.site** | Broad component set (cards, nav, hero). |
| **flashui.site** | Marketing/UI blocks. |
| **fancycomponents.dev** | Animated & interactive components. |
| **ui.lndev.me** | Component collection. |
| **21st.dev** | Community React/Tailwind components (search-driven). |
| **preline.co** | Tailwind UI components & snippets. |
| **html.tailus.io** | Tailwind UI kit. |
| **ground.bossadizenith.me** | Component blocks. |
| **reactcomponents.com** | React component collection. |
| **hyperui.dev** | Free Tailwind components. |
| **ever-ui.com** | Component library. |
| **hover.dev** | Components — **use FREE components only** (a few are paid; skip those). |
| **atomix-ui.vercel.app** | Component set. |
| **kokonutui.com** | Animated React/Tailwind components. |
| **bundui.io** | UI components. |
| **hextaui.com** | Tailwind components. |
| **originui.com** | Copy-paste Tailwind components. |
| **getjustd.com** | Tailwind UI components. |

> All are free and Tailwind-based (per the daily.dev UI-libraries roundup). Use
> **only free** components; for hover.dev use its free set and skip anything paid.
> reactbits.dev (owner's original pick) is included even though not in that roundup.

**2.1 Browser-use visual inspection (MANDATORY).** The agent must use the
**browser-use plugin** (globally installed) to open each candidate library site and
*visually inspect the rendered component* before choosing. Do not pick from docs or
code snippets alone — see it live. When multiple variants exist for one need, render
and compare them side by side and select the best-looking, most polished option.

**2.2 Set the quality bar by studying the libraries.** Before implementing, the agent
must open the library sites (via browser-use), study how components look and behave —
motion, spacing, hover/focus polish, empty/loading/error states — and adopt that level
of finish as the **minimum bar for every component it ships**, whether library or
custom. The libraries are both the source of primitives *and* the reference standard
for "industry-grade." Treat them as the benchmark, not just a parts bin. If a custom
component cannot reach that bar, iterate until it does.

**2.3 Component selection procedure (follow exactly — no guessing).** For *every*
UI need, in order:
1. Using the **browser-use plugin**, search the **entire free library pool (§2)** for
   a matching component — **every** library, not just the first. Do not stop early.
2. If multiple candidates exist, render each live via browser-use and **pick the
   best-looking, most polished** per the §2.2 bar (motion, spacing, hover/focus,
   states).
3. If none fit, build a **custom** component to the same bar using §1 tokens; document
   it in the PR.
4. Record the chosen source (library + component name, or "custom") in a repo
   `COMPONENTS.md` so reviewers can verify each decision.
This procedure is mandatory for every component, including those pre-mapped in §3.

**Master component → source mapping (authoritative):**

| UI need | Use |
|---|---|
| Button (primary/ghost/destructive) | shadcn `button` (primary = shimmer-button from magicui) |
| Text input / textarea (auto-grow) | shadcn `input` / `textarea` |
| Label, form field | shadcn `label` + `input` (never default HTML fields) |
| Card / panel / surface | shadcn `card` |
| Avatar (user/server/channel) | shadcn `avatar` (status dot overlay custom, token-driven) |
| Badge / unread / mention pill | shadcn `badge` |
| Dropdown / context menu (msg actions) | shadcn `dropdown-menu` / `context-menu` |
| Modal / dialog | shadcn `dialog` (or `alert-dialog`) |
| Mobile drawer (sidebar/menus) | shadcn `sheet` |
| Tabs (profile/settings sections) | shadcn `tabs` |
| Scrollable lists | shadcn `scroll-area` |
| Tooltip (icon labels) | shadcn `tooltip` |
| Hover profile popout | shadcn `hover-card` |
| Command palette / search | shadcn `command` (cmdk) or cuicui `command-menu` |
| Toast / notifications | `sonner` (shadcn integration) |
| Switch / toggle (settings) | shadcn `switch` |
| Slider (volume/density) | shadcn `slider` |
| Select (status/language) | shadcn `select` |
| Separator / divider | shadcn `separator` (token border, not solid gray) |
| Skeleton / loading | shadcn `skeleton` + indie-starter `loader-dots` |
| Typing / sending indicator | indie-starter loader (dots variant) |
| Hero / login background | aceternity `aurora-background` or `spotlight`; magicui `aurora-background` |
| Empty-state illustration | magicui `bento-grid` / reactbits card + lucide icon |
| Resizable 3-pane (desktop) | shadcn `resizable` |

Any component not in this table: search the **full free library pool (§2)** across
**ALL** libraries first (mandatory rule); only if truly absent in every one, build a
custom component to match §1 tokens and document it in the PR.

---

## 3. Screen-by-screen redesign spec

Each screen specifies: layout, components (§2 refs), behavior, and required
states (default / hover / active / focus / disabled / loading / empty / error).

**Layout inspiration:** a **hybrid of all major chat apps, Discord-led** — use
Discord's structure for the **server rail, channel sidebar, and settings**; borrow
Telegram's message clarity, Slack's command palette/threading, and WhatsApp/iMessage
bubble simplicity elsewhere. The UI components inside those layouts come from the §2
libraries (directly, or custom-built to their bar).
**Animations:** every meaningful transition uses purposeful motion — page/sheet
transitions, list entrances, hover/focus, send/typing, reaction pops — via library
motion components (magicui/aceternity/reactbits) where available, custom otherwise.
Honor `prefers-reduced-motion` (§1.4/§4).
**Text:** one consistent typographic system (§1.2) across all copy, labels,
messages, and microcopy — no orphaned tiny text, no placeholder/value confusion.

### 3.1 Login / Authentication  *(was: cramped, mismatched colors)*
- **Layout:** Centered card on an aurora/spotlight animated background
  (aceternity `aurora-background` or magicui). Single-column, max-width ~400px,
  vertically centered with comfortable 24px padding.
- **Components:** shadcn `card` + `label` + `input` (email) + `input`
  (password, with show/hide) + primary `shimmer-button` ("Continue"),
  `separator` ("or"), secondary `button` (magic-link / continue with provider if
  configured). `sonner` toasts for errors.
- **Behavior:** client validation with inline `label` error text; Enter submits;
  loading state disables button + shows indie-starter dots. No navigation chrome.
- **States:** empty inputs (placeholder + helper), invalid (red border +
  message), submitting (disabled + dots), success (redirect), error (sonner).
- **Quality bar:** large tap targets (≥44px), high-contrast labels, clear
  primary CTA, no decorative gradient dominating — background is subtle.

### 3.2 Onboarding / Welcome (new user)
- **Layout:** Bento-style intro (magicui `bento-grid`) introducing servers,
  channels, DMs. One action per step; skip option.
- **Components:** shadcn `card` steps, `button`, `avatar` setup, `sonner`.
- Progressive, skippable, never blocks the app on first load.

### 3.3 Server / Workspace Rail  *(Discord-style left icon column)*
- **Layout:** Fixed vertical rail (~72px), surface depth `--bg-surface`. Server
  icons as rounded squares (shadcn `avatar`); active = accent pill on left edge;
  unread = white dot; hover = tooltip with name (shadcn `tooltip`).
- **Components:** `avatar` + `tooltip` + a "Add server" custom FAB (accent,
  token-driven). Home/DM button at top.
- **Behavior:** click selects server → channel sidebar updates; hover scales
  icon slightly (reactbits hover). Right-click → `dropdown-menu` (settings,
  leave).

### 3.4 Channel Sidebar  *(was: cramped top bar, confusing rows)*
- **Layout:** Column (~240px desktop) between rail and chat. Server name header
  (shadcn `card`-light), collapsible **categories** (shadcn `collapsible`),
  channel rows with `#`/voice icons (lucide), unread = bold + white pill
  (shadcn `badge`), active = accent-tinted background.
- **Components:** `scroll-area`, `collapsible`, `button` (row), `badge`,
  `avatar` (user panel at bottom), `tooltip` on icons. Bottom: persistent user
  panel (avatar + name + mic/deafen/settings `button`s).
- **Behavior:** hover reveals thread/settings icons; active channel highlighted;
  categories remember collapse state.

### 3.5 Chat / Message View  *(core — was: empty black void, no bubbles)*
- **Layout:** Header bar (channel name `#`, topic, member toggle, search icon).
  Scrollable message list (`scroll-area`) filling available height. Composer
  pinned at bottom (§3.6). Member panel slides in from right on desktop
  (shadcn `resizable` / `sheet` on mobile).
- **Message row:** avatar (left, shadcn `avatar`) + username (accent on hover/
  role color) + timestamp (muted, `--text-secondary`) + message body. Consecutive
  same-author messages **group** (avatar hidden, hover reveals timestamp).
- **Components:** `avatar`, `badge` (mention `@everyone` highlighted in accent),
  `dropdown-menu` (react on message: react/edit/delete/copy/reply), `tooltip`,
  `hover-card` (user profile popout), `card` for linked previews.
- **Required cues (all currently missing):** grouped messages, readable
  timestamps, mention highlighting, hover message actions, reactions as chips,
  thread indicator, date dividers ("Today"), unread separator.
- **Empty state:** centered magicui/reactbits card: "No messages yet — say
  hi 👋" with subtle illustration; never a blank black area.
- **Loading:** shadcn `skeleton` lines; history fetch shows indie-starter dots.

### 3.6 Message Composer  *(was: cramped, tiny send arrow)*
- **Layout:** Rounded `card`/`input` container, full width with 12px padding.
  Left: attach (+) `button` + emoji `popover` (cuicui or custom picker).
  Center: auto-growing `textarea` ("Message #channel"). Right: prominent send
  `button` (accent, icon + label on desktop).
- **Behavior:** Enter sends, Shift+Enter newline; typing shows indie-starter
  typing dots in chat; sending shows transient dots then message; emoji popover
  inserts at cursor; mentions autocomplete via shadcn `command` popover.
- **States:** empty (placeholder), focused (accent ring), disabled (no channel),
  sending (dots), error (red border + sonner).

### 3.6a Message content & bubble spec (the core surface)
Define ONE `Message` component with variants; never hardcode per-case markup.
- **Variants:** `own` (right-aligned, accent-tinted) vs `other` (left, surface);
  `grouped` (no avatar, hover reveals timestamp) vs `first` (avatar + name);
  `compact` (density) vs `cozy`.
- **Content blocks:** text (markdown: bold/italic/code/links), mentions
  (`@user` / `@everyone` highlighted in accent), emoji-only (enlarged), code block
  (mono + copy button), quoted reply preview (compact card), attachments (image
  thumbnail → lightbox; file chip with icon + size + download), link embed (title /
  description / thumbnail card).
- **Affordances:** hover toolbar (react / reply / edit / more → `dropdown-menu`);
  reactions row (chips, toggle, `+` to add); `edited` tag; hover timestamp; delivery /
  read ticks on own messages.
- **Scroll & loading:** stick to bottom on send/new message when near bottom; show a
  "jump to present" floating pill when scrolled up; older history loads above via
  skeleton + indie-starter dots (infinite scroll).
- **Emoji:** free emoji set (system emoji or a free picker from §2 pool) in a shadcn
  `popover`; insert at cursor in composer and as reactions.

### 3.7 DM / Conversation List  *(was: screenshot 1 — tiny text, empty)*
- **Layout:** Full-height `scroll-area` list. Rows: `avatar` + name + last
  message preview (truncated, `--text-secondary`) + timestamp + unread `badge`.
  Search field at top (shadcn `input` + icon).
- **Components:** `avatar`, `badge`, `separator`, `scroll-area`, `input`.
- **Empty state:** "No conversations yet" card with start-DM action.
- **States:** unread (bold + accent pill), active (accent tint), hover, hover
  reveals actions (pin/delete via `dropdown-menu`).

### 3.8 Member List / User Popout  *(was: missing discoverability)*
- **Layout:** Right panel (desktop) or `sheet` (mobile). Grouped by role/status
  (Online / Offline). Rows: `avatar` + name + status dot + activity.
- **Components:** `scroll-area`, `avatar` (status overlay), `hover-card`
  (quick profile), `badge` (roles).
- Clicking a member opens `hover-card` or `dialog` profile with roles, bio,
  message history link.

### 3.9 Profile / Settings  *(was: screenshot 4 — dense, decorative gradient)*
**This screen is currently the worst — redesign per below.**
- **Layout:** Two-column on desktop (`resizable`): left = settings nav
  (`tabs`/nav list), right = content `card`. Single scroll column on mobile
  (`scroll-area`). **Remove the oversized decorative gradient banner** — replace
  with a compact, functional header (avatar + name + edit).
- **Sections (shadcn `tabs` / `card` each):**
  - **Account:** `avatar` (Change via `dialog`), display name `input`, custom
    status `input` (clear placeholder vs value distinction), username (read-only
    display).
  - **Appearance:** theme switch (light/dark/`switch`), density `slider`, accent
    color `select` (cuicui `color-picker` optional) — **all token-driven**, live
    preview in header, not a giant banner.
  - **Privacy:** `switch`es (read receipts, online status, DMs) with helper text.
  - **Notifications:** `switch` + `select` (all/mentions/none).
- **Components:** `tabs`, `card`, `input`, `label`, `switch`, `slider`,
  `select`, `separator`, `dialog`, `avatar`, `sonner` (saved toast), `scroll-area`.
- **Required fixes:** clear section headers; adequate spacing (≥16px between
  groups); labeled, high-contrast controls; obvious selected state on theme/
  accent; explicit Save/Apply + Reset (`button`s); visible focus rings; no
  decorative-only gradient. Every change shows immediate preview + persisted on
  save.

### 3.10 Search / Command Palette  *(was: absent)*
- **Layout:** `command` dialog (⌘K / Ctrl+K) over everything. Fuzzy search
  across channels, DMs, people, messages, settings.
- **Components:** shadcn `command` (cmdk) or cuicui `command-menu`; `avatar` in
  results; `kbd` hints.

### 3.11 Notifications & Toasts
- In-app: `sonner` toasts (top-right desktop / bottom mobile), token-styled.
- System/notif settings in §3.9. Unread badges驱动 by store, not hardcoded.

### 3.12 Navigation model
- **Desktop (≥1024px):** 3-pane — server rail + channel sidebar + chat +
  optional member panel (`resizable`). Top-level global search via ⌘K.
- **Mobile (<768px):** Bottom tab bar (`navigation-menu` / custom, token-styled)
  with 4–5 items (Chats / Servers / DMs / Search / You). Channel list & member
  list as `sheet` drawers. No huge empty black gaps.

### 3.13 Splash / Launch screen
- Brief branded splash (ChatNook wordmark + accent) shown while the app boots / theme
  is applied (§1.7 no-flash). No spinner needed if boot is fast; otherwise indie-starter
  loader.

### 3.14 Connection & offline state
- A slim top **connection banner** (token-styled, not a modal) for "Connecting…",
  "Reconnecting…", "Offline — messages will send when reconnected". Auto-dismisses on
  healthy connection. Never blocks the UI.

### 3.15 Create / Join server (guild)
- **Create:** multi-step `dialog`/`card` (name, icon via `avatar` upload, optional
  template). **Join:** invite-code `input` → preview `card` → confirm. Uses existing
  `guilds` / `invites` tables (§9).

### 3.16 Invite flow
- Generate/share invite (`input` + copy button, `sonner` "copied"); accept screen
  previews the server before joining.

### 3.17 Server (guild) settings  *(Discord-led layout)*
- Left `tabs`/nav (Overview, Members, Roles, Emojis, Settings) + right content `card`.
  Overview: icon (`avatar`), name `input`, description `textarea`. Members: manageable
  list (`scroll-area`, `avatar`, role `badge`, `dropdown-menu` actions). Roles:
  draggable list with permission `switch`es. Emojis: grid uploader (`custom_emojis`).

### 3.18 Notification preferences
- Per-guild (`guild_notification_settings`) + global (`channel_notification_settings`)
  + push: `switch` + `select` (all / mentions / none / mute). Driven by existing tables.

### 3.19 Blocked users / moderation
- List of blocked profiles (`avatar` + unblock `button`); block action reachable from
  member/user popout. Keep minimal for the 20–50 friends scope.

### 3.20 Attachment viewer / lightbox
- Full-screen overlay (`dialog`) for images/PDF; prev/next, zoom, download; backdrop
  blur; closes on Esc / backdrop click. Keyboard navigable.

### 3.21 Error / not-found / route states
- Generic error `card` (icon + message + retry), 404/not-found for unknown routes,
  and per-screen error states (e.g. channel load failed). All token-styled; no raw
  stack traces to users.

### 3.22 Appearance live-preview
- The §3.9 Appearance controls update the live UI instantly (theme/density/accent)
  via the §1.5 `ThemeProvider`; a small preview chip shows the active accent.

### 3.23 Keyboard shortcuts (desktop)
- Document a shortcut set and surface it: `⌘K` command palette, `Esc` close overlays,
  `↑` edit last own message, `Ctrl/Cmd+Enter` send (optional), `Ctrl/Cmd+F` in-channel
  search, `Alt+↑/↓` switch channels. Show a shortcuts help dialog.

---

## 4. Cross-cutting requirements (apply everywhere)

- **Responsive:** mobile-first; breakpoints 640 / 768 / 1024 / 1280. Panes
  collapse to drawers below 1024px. No element smaller than 44px tap target.
- **Accessibility (WCAG AA):** contrast ≥ 4.5:1 text; visible focus rings
  (accent, 2px); all icons-in-buttons have `aria-label`; semantic HTML; keyboard
  nav for command palette and menus; `prefers-reduced-motion` honored.
- **Theming:** dark default + light toggle, persisted to store/localStorage,
  applied via class on `<html>` (§1.5). No flash of wrong theme.
- **States everywhere:** default / hover / active / focus / disabled / loading /
  empty / error — all defined with token-driven styles. No screen ships without
  its empty + error + loading states.
- **Performance:** virtualize long message/member lists; lazy-load heavy
  aceternity/magicui backgrounds; keep interactions at 60fps; no layout shift.
- **Consistency:** one icon set (lucide), one radius scale, one accent. No
  ad-hoc colors or one-off button shapes.
- **Animations (purposeful, everywhere):** transitions on navigation (sheet/page),
  list item entrances, hover/focus, send/typing indicators, reaction pops, and
  theme switches. Use magicui/aceternity/reactbits motion components; custom only
  when none fits, matching §1.4 (150–250ms, ease-out). Always honor
  `prefers-reduced-motion` (provide instant fallbacks). No jank, no layout shift
  from animation.
- **Text & microcopy:** one typographic voice (§1.2) across all UI copy, labels,
  messages, errors, and empty states. Helpful, human microcopy (e.g. empty states
  tell the user what to do next). No orphaned tiny text, no placeholder/value
  ambiguity, consistent terminology app-wide.
- **Keyboard shortcuts (desktop):** implement the §3.23 set; every shortcut has a visible
  path too (no keyboard-only features). Show a shortcuts help dialog.
- **Real-time & data binding:** UI is a view over the **existing Supabase backend**
  (§9) — messages, typing, presence, reactions, read-state update live via Supabase
  Realtime, not polling. Bind to the existing tables/types; do not invent new schema.
- **Optimistic UI:** sent messages, reactions, and status toggles apply instantly (local
  state) and reconcile on server confirm/error. Show sending state (indie-starter dots);
  on failure, mark the item and allow retry.
- **Scroll behavior:** chat sticks to bottom on new messages when near bottom; "jump to
  present" pill when scrolled up; older history paginates upward (infinite scroll).
- **PWA / offline:** installable PWA with a service worker caching static shell + recent
  data; graceful offline banner (§3.14). No paid CDN/analytics (PROJECT_RULES §8).
- **Localization:** ship **English (en) only** for now, but keep all strings in one
  i18n layer (no hardcoded copy in components) so future locales are drop-in. Use ONE
  locale convention (en-US) — no mixing en-GB/en-US spelling (the prototype's
  "colour" leak is a failure).
- **Fonts & assets:** self-host or system-font the §1.2 typeface (avoid runtime Google
  Fonts network dependency in the APK); icons = lucide only; illustrations from a free
  source (e.g. unDraw) or built from library components — no paid asset services.
- **Emoji:** use the OS/system emoji or a free emoji set (§2 pool); never a paid icon
  service.
- **Reduced motion / a11y:** all motion honors `prefers-reduced-motion`; full keyboard
  operability; focus order logical; ARIA where semantic HTML is insufficient.

---

## 5. Anti-patterns (explicitly forbidden)
- Default HTML `<input>`/`<select>`/`<button>` styling.
- Solid gray dividers, pure-black backgrounds, mismatched saturated accents.
- Tiny (<14px) secondary text; missing type hierarchy.
- Icon-only actions without tooltip/label.
- Decorative gradient banners consuming screen real estate.
- Blank/empty regions where content or an empty-state should be.
- Missing hover/active/focus/disabled/loading/empty/error states.
- Low-res or inconsistent icon shapes.
- **Mixing en-US / en-GB spelling** ("colour" vs "color", "favourite" vs
  "favorite") — pick **en-US** and use it everywhere. The prototype's "colour" leak
  is a hard failure.
- **Flash of wrong theme** on launch (dark UI then light, or vice-versa). Apply the
  stored theme class before first paint (§1.7).
- **Skipping the browser-use inspection (§2.1)** — picking a component from docs/code
  alone without rendering it live. Mandatory for every chosen component.
- **Missing COMPONENTS.md entry (§2.3 step 4)** — every shipped component must record
  its source (library + name, or "custom"). Reviewers verify choices against it.
- **Hardcoding values that bypass §1 tokens** — colors, sizes, radii, fonts written as
  literals instead of CSS variables / Tailwind theme tokens.
- **Fake/demo data standing in for the real backend** — UI must bind to the existing
  Supabase tables (§9), not invented mock arrays, except for isolated loading/empty
  state demos.
- **Polling instead of Supabase Realtime** for messages/typing/presence/reactions/
  read-state.
- **Layout shift / jank from animation** — motion must not move surrounding content,
  shift scroll position, or drop below 60fps.
- **Tap targets below 44px** on touch (mobile bottom-nav items, FABs, list rows).
- **Copying Discord assets** — exact colors, logos, "Discord" wordmark, or Clyde-like
  mascot. Original palette/identity only (§1.6).

---

## 6. Acceptance criteria (Definition of Done)
- [ ] All §3 screens implemented; library components preferred, custom components
      allowed and held to the §2.1 quality bar.
- [ ] §1 design tokens drive 100% of color/type/spacing — zero hardcoded values.
- [ ] Dark + light themes toggle and persist without flash.
- [ ] Every screen has loading / empty / error states.
- [ ] WCAG AA contrast; visible focus; ⌘K works; keyboard reachable.
- [ ] **Animations** present and consistent on key transitions; `prefers-reduced-motion`
      honored with instant fallbacks; no jank/layout shift.
- [ ] **Text/microcopy** uses one typographic system (§1.2) uniformly; no tiny
      orphaned text; empty states have helpful copy.
- [ ] Responsive: 3-pane desktop, bottom-nav mobile, no empty black gaps.
- [ ] The four as-is problems (§0.3) are demonstrably gone (screenshot proof in
      PR).
- [ ] Builds to Android APK via Capacitor in GitHub Actions (PROJECT_RULES §1).
- [ ] No console errors; 60fps interactions; lists virtualized.
- [ ] **COMPONENTS.md** exists at repo root; every shipped component records its source
      (library + name, or "custom") per §2.3 step 4; selections traceable to a
      browser-use inspection (§2.1).
- [ ] **Backend binding (§9):** UI reads/writes the existing Supabase tables; no invented
      schema; live updates use Supabase Realtime (not polling); optimistic UI reconciles
      on confirm/error.
- [ ] **i18n (§10):** all user-facing strings live in one i18n layer (en-US only), no
      hardcoded copy in components, no en-GB/en-US spelling mix.
- [ ] **No theme flash:** stored theme applied before first paint (§1.7).

---

## 7. Handoff notes for the agent
- Start with §1 (tokens) and §2 (library pool + mapping) before any screen.
- **Use the browser-use plugin** to inspect candidate components live (§2.1) and to
  pick the best-looking variant when several exist. Search the **full free library
  pool** — all 25 libraries in §2 — before falling back to custom.
- Install: `shadcn` init, add the components you select, plus the motion/flair
  libraries (`magicui`, `aceternity`, `reactbits`) and loaders (`indie-starter`) per
  their docs. Wrap with Capacitor for Android.
- Treat this file as the source of truth; if a screen need isn't listed, apply
  the §4 cross-cutting rules and the §2 mapping, then note the addition.
- Do NOT copy Discord assets/colors; original palette only.

---

## 8. Mandatory tech stack (build with exactly this)

The libraries in §2 are React + Tailwind. The build must therefore be a React web
app packaged for Android — **Capacitor is the default bridge** (§0.1). Use this
concrete stack; do not substitute without explicit owner approval.

- **Language / framework:** React 18+ with **TypeScript** (strict). No plain JS
  components.
- **Build tool:** **Vite** (fast HMR, small bundle). `npm create vite@latest
  -- --template react-ts`.
- **Styling:** **Tailwind CSS** v3 (stable) with the design tokens from §1 wired into
  `tailwind.config.ts` (colors → CSS variables, radius/type/spacing scales). A
  `globals.css` holds `:root` / `.theme-light` variable blocks (§1.5).
- **Component base:** **shadcn/ui** (`npx shadcn@latest init` then `add` the
  components mapped in §2). shadcn copies source into the repo (not a runtime dep) so
  custom edits stay in-house.
- **Motion/flair:** install `magicui`, `aceternity`, `reactbits` per their docs for the
  hero/empty/transition components; loaders from `indie-starter`.
- **State management:** **Zustand** (lightweight) for client UI state (theme, active
  server/channel, composer drafts, command-palette open). Server data via the Supabase
  client + React Query **OR** Supabase's realtime subscriptions with local optimistic
  state (§9). Pick one data layer and use it consistently — no ad-hoc `fetch` in
  components.
- **Backend client:** **@supabase/supabase-js** v2, initialized from environment
  variables (URL + anon key). Never ship the service-role key in the app bundle.
- **Routing:** **React Router v6** (or TanStack Router). All §3 screens are routes;
  unknown routes render §3.21 not-found.
- **Forms:** **react-hook-form** + **zod** for validation (login §3.1, settings §3.9,
  create-server §3.15). Inline errors use the §1 token error color.
- **i18n layer:** **i18next** (or a minimal in-house dictionary) — all strings in one
  `locales/en.json`; components import keys, never literals (§10).
- **Android packaging:** **Capacitor** (`@capacitor/core`, `@capacitor/android`).
  `npx cap add android`; build the web app, `npx cap sync`, then the APK/aab is built
  in GitHub Actions only (PROJECT_RULES §1). The app must read the OS dark/light
  preference at launch and pass it to the §1.5 `ThemeProvider` to avoid theme flash.
- **Performance:** **@tanstack/react-virtual** for message/member list virtualization
  (§4). Lazy-load heavy aceternity/magicui backgrounds via `React.lazy` + `Suspense`.
- **Quality gates:** ESLint + TypeScript `strict`; run `tsc --noEmit` and lint in CI
  before the APK build. Zero console errors is an acceptance criterion (§6).

Do **not** introduce paid services, analytics, or CDNs (PROJECT_RULES §8).

---

## 9. Backend / data binding (bind to the EXISTING Supabase schema)

ChatNook already has a Supabase backend with a Discord-parity schema. The UI is a
**view over these tables** — do NOT invent new tables or columns. Bind each §3 screen
to the existing schema below and keep it live via **Supabase Realtime** (not polling).

### 9.1 Tables (authoritative — from `supabase/migrations/`)

| Table | Key columns | Drives which UI |
|---|---|---|
| `profiles` | id (FK auth.users), username, display_name, avatar_url, banner_url, accent_color, theme (jsonb), custom_status, badges[], avatar_is_animated | Avatar/popout (§3.5/§3.8), profile/settings (§3.9), server member rows, server rail icon |
| `guilds` | id, name, icon_url, owner_id | Server rail (§3.3), guild header (§3.4), guild settings (§3.17) |
| `guild_members` | guild_id, user_id | Membership, member list, "leave server" |
| `member_roles` | guild_id, user_id, role_id (PK triplet, FK to guild_members) | Maps members→roles; drives role badges/colored names (§3.8) |
| `roles` | id, guild_id, name, color (int), gradient (jsonb), position, permissions (bigint), is_everyone | Roles UI (§3.17), colored member names, permission switches |
| `channels` | id, guild_id (null = DM), name, type ('text'\|'voice'\|'category'\|'dm'\|'group_dm'), topic, position, parent_id | Channel sidebar (§3.4), chat header, DM list (§3.7) |
| `messages` | id, channel_id, author_id, content, type ('default'\|'system'\|'reply'), reply_to_id, created_at, edited_at, deleted_at | Message view (§3.5/§3.6a), composer (§3.6) |
| `reactions` | message_id, user_id, emoji (PK triplet) | Reaction chips (§3.6a) |
| `attachments` | id, message_id, url, filename, content_type, size_bytes, width, height | Attachment chips / lightbox (§3.6a/§3.20) |
| `read_states` | user_id, channel_id, last_read_message_id, last_read_at | Unread pills/bold (§3.4/§3.7), unread divider (§3.5) |
| `dm_participants` | channel_id, user_id | DM membership (§3.7) |
| `invites` | code (PK), guild_id, created_by, expires_at, max_uses, uses | Invite create/accept (§3.16) |
| `custom_emojis` | id, guild_id, name, url, animated | Emoji picker (§3.6), guild emoji settings (§3.17) |
| `push_tokens` | user_id, token, platform | Push notifications delivery |
| `guild_notification_settings` | user_id, guild_id, muted | Per-guild mute (§3.18) |
| `channel_notification_settings` | user_id, channel_id, level ('all'\|'mentions'\|'none') | Per-channel level (§3.18) |

### 9.2 Live updates (Supabase Realtime — no polling)
- Subscribe to **`postgres_changes`** on `messages` (INSERT/UPDATE/DELETE) filtered by
  the open channel(s) to render new/edited/deleted messages instantly (§3.5/§3.6a).
- Subscribe to `reactions` changes to update reaction chips live.
- **Presence** (`supabase.channel(...).on('presence')`) for online/away/offline dots
  in the member list (§3.8) and typing indicators.
- **Typing:** a lightweight presence or a `typing` broadcast channel per channel; show
  the indie-starter dots (§3.6) when peers are typing.
- **Read state:** on opening a channel or viewing the bottom, upsert `read_states`
  (`last_read_message_id`); broadcast clears the unread pill.
- Reconnect/backoff is handled by the Supabase client; surface connection status via
  the §3.14 banner (don't block the UI).

### 9.3 Optimistic UI (§4)
- **Send message:** append locally (status `sending`, indie-starter dots) → insert via
  client → on success flip to `sent` (read ticks when peer reads); on error mark
  failed with a retry affordance (§3.6a).
- **React / unreact:** toggle the chip locally immediately; reconcile on DB confirm.
- **Settings toggles (theme/density/accent/privacy):** apply to the `ThemeProvider`
  instantly, persist to `profiles.theme` (jsonb) on save (§3.9/§3.22).

### 9.4 Auth
- Supabase **Auth** (email/password + magic link if configured) backs §3.1. On session
  change, hydrate the Zustand store from `profiles`. Guard app routes behind an auth
  check; unauthenticated → login (§3.1).

### 9.5 Rules
- No new tables/columns without owner approval — the schema above is the contract.
- Anon key only in the bundle; RLS policies already protect rows (do not weaken them).
- If a screen seems to need data not in this schema, raise it in the PR; do not silently
  fake it (anti-pattern, §5).

---

## 10. Content & copy conventions (one voice, en-US only)

All user-facing text lives in a single i18n dictionary (`locales/en.json`, §8 i18n
layer). Components reference **keys**, never raw strings. This keeps copy consistent
and makes future locales drop-in.

- **Locale:** ship **en-US only**. Spelling must be en-US throughout: *color, favorite,
  customize, behavior, dialog, organize, license, canceled, gray*. **Never** mix in
  en-GB forms (the prototype's "colour" is a hard failure — §5).
- **Voice:** friendly, concise, human. Microcopy tells the user what to do next
  (e.g. empty states: *"No messages yet — say hi 👋"*, *"No conversations yet. Start a
  DM to begin."*). Avoid robotic or error-code copy.
- **Capitalization:** Sentence case for labels, buttons, and menu items
  (*"Send message"*, not *"Send Message"*). Title Case only for screen/section titles.
- **Buttons:** verb-led, action-first (*"Create server"*, *"Join server"*, *"Copy
  invite"*). Primary CTA is a single clear action per screen.
- **Errors:** plain language, no stack traces; say what happened and the next step
  (*"Couldn't send — tap to retry."*). Map to §3.21 error states.
- **Numbers/units:** 1,000 separators; file sizes as KB/MB from `attachments.size_bytes`.
  Relative times ("2m", "Yesterday", "Today") from `created_at`/`edited_at`.
- **Placeholders vs values:** placeholders are muted helper text inside empty inputs;
  once a value exists it shows in `--text-primary` (no placeholder/value confusion —
  a §0.3 failure fixed in §3.9).
- **Brand:** "ChatNook" verbatim (§1.6) — no "ChatNook!", no stylized variants.
- **Accessibility copy:** every icon-only control gets an `aria-label` and (where
  visible) a tooltip (§4 a11y). Never ship an unlabeled icon button.
- **No lorem / no fake names:** seed demos (if any) with neutral placeholder content,
  clearly marked, and never leave them in the shipped build (anti-pattern, §5).

---

## 11. Redesign checklist (use as the build order)

1. **§1 tokens** → `tailwind.config.ts` + `globals.css` + `ThemeProvider` (dark/light,
   no-flash boot script).
2. **§8 stack** → Vite + React + TS + Tailwind + shadcn init; install motion/loader
   libs; wire Supabase client + i18n dictionary.
3. **§2.3 procedure** → for each component, browser-use inspect → pick best → record in
   `COMPONENTS.md`.
4. **Shell first:** §3.3 server rail + §3.4 channel sidebar + §3.5 chat (the 3-pane).
5. **Composer + messages:** §3.6 / §3.6a with realtime + optimistic + reactions +
   attachments + lightbox (§3.20).
6. **Lists:** §3.7 DMs, §3.8 members, presence/typing.
7. **Auth + onboarding:** §3.1 login, §3.2 welcome.
8. **Settings:** §3.9 (account/appearance/privacy/notifications), §3.17 guild settings,
   §3.18 notification prefs, §3.19 blocked.
9. **Discovery & flow:** §3.10 command palette, §3.15 create/join, §3.16 invites.
10. **Cross-cutting:** §4 (a11y, responsive, animations, PWA/offline, reduced motion),
    §3.13 splash, §3.14 connection, §3.21 errors, §3.23 shortcuts.
11. **Verify:** §6 acceptance criteria; screenshot the four §0.3 problems resolved;
    confirm `COMPONENTS.md` + §9 backend binding + §10 i18n.
12. **Package:** Capacitor sync + GitHub Actions APK build (PROJECT_RULES §1).
