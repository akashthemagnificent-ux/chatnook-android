# ChatNook — Discord Parity Build Plan (v1)

Status: in progress
Owner: Forge
Started: 2026-10-25

This file is the single source of truth for this phase. Nothing ships until every
box here is ticked and verified. When in doubt, re-read this file, not memory.

---

## 0. Locked constraints (from user, non-negotiable)

- Android only. No iOS, no PWA. minSdk 24, targetSdk 36 (local SDK ceiling).
- $0 budget. Free tiers only.
- AI-agent built and maintained. Human cannot debug; code must be readable and verified.
- Release APK built in GitHub Actions only, not locally. Batch builds, never per-push.
- Reuse libraries. Do not rebuild what exists.
- UI must use copy-paste component libraries (ReactBits/MagicUI/Aceternity equivalents),
  not hand-rolled squares. Nitro colours must be rich/textured gradients, not flat fills.

### Hard blockers requiring the human (cannot be done from code)

| # | Blocker | Why | Action |
|---|---------|-----|--------|
| B1 | `anonymous_users: false` | Dashboard toggle. Anon sign-in returns 422 until enabled. | Auth → Providers → Anonymous sign-ins → ON |
| B2 | `mailer_autoconfirm: false` | Email+password signup sends a confirmation mail. Supabase's shared mailer rejects arbitrary recipients ("Email address is invalid"), so friends never receive it. | Auth → Providers → Email → turn OFF "Confirm email" |
| B3 | Magic link needs real SMTP | OTP/magic-link always sends mail. Free = needs a domain for Resend/Brevo. No domain = cannot work. | Either (a) supply SMTP creds + a domain, or (b) magic link ships disabled with an in-UI note |
| B4 | No `google-services.json` | FCM push requires a Firebase project linked to the Google account. | Create Firebase project, add Android app `com.chatnook.app`, download `google-services.json` |
| B5 | Release APK signed with debug key | Anyone can sign an update over it. Not safe to distribute. | Generate upload keystore, store as CI secrets (I can generate, you must keep the file) |

> B2 is the important one: with confirm-email OFF, email+password works with **zero**
> SMTP and zero cost. Without it, login method 2 is dead.

---

## 1. Data model (Supabase Postgres + RLS)

Reverse-engineered from Discord's public data model (guilds → categories → channels,
members, roles, permission bitfields, DM channels as a guild-less channel of 2 recipients).

### Core identity
- [ ] `profiles` — id (auth.uid), username, display_name, avatar_url, banner_url,
      accent_color, theme_json, status, custom_status, badges[], decoration_url,
      created_at, updated_at
- [ ] `invites` — code (pk), created_by, max_uses, uses, expires_at
- [ ] trigger: new auth user → auto-create `profiles` row (username derived, editable)

### Guilds (servers)
- [ ] `guilds` — id, name, icon_url, banner_url, owner_id, created_at
- [ ] `guild_members` — guild_id, user_id, nickname, joined_at, role_ids[]
- [ ] `guild_roles` — id, guild_id, name, color, position, permissions (bigint bitfield),
      is_default, hoist, mentionable
- [ ] `guild_categories` — id, guild_id, name, position
- [ ] `channels` — id, guild_id (nullable for DM), category_id, name, type
      (text|voice-later), topic, position, is_nsfw, slowmode_seconds
- [ ] `channel_overwrites` — channel_id, target_id (role|user), allow, deny

### Messages
- [ ] `messages` — id (bigint identity for stable ordering), channel_id, author_id,
      content, type (text|system|join), reply_to_id, attachments jsonb, embeds jsonb,
      edited_at, created_at, deleted_at
- [ ] `message_reactions` — message_id, user_id, emoji, created_at
- [ ] `read_states` — user_id, channel_id, last_read_message_id, mention_count

### DMs
- [ ] `dm_channels` — id, user_a, user_b (ordered), created_at
- [ ] `dm_messages` — reuse `messages` with channel_id → dm_channels.id (unified table)

### Nitro / cosmetics
- [ ] `custom_emojis` — id, guild_id, name, storage_path, uploaded_by
- [ ] `user_badges` — user_id, badge_key, granted_at
- [ ] storage buckets: `avatars`, `banners`, `emojis`, `decorations` (public read,
      owner write, size + MIME limits enforced by policy)

### RLS invariants (must all hold)
- [ ] User can only read messages in channels of guilds they are a member of, or their own DMs
- [ ] User can only insert messages as themselves (`author_id = auth.uid()`)
- [ ] User can only edit/delete own messages (or guild owner/admin with MANAGE_MESSAGES)
- [ ] Guild creation allowed for any authenticated user; creator becomes owner + admin role
- [ ] Invite code required for guild join unless guild is marked public
- [ ] Nobody can read another user's `read_states`, DM membership, or session rows
- [ ] Storage: user writes only under their own `{user_id}/` prefix

---

## 2. Milestone M1 — Auth (3 methods, one clean screen)

- [ ] Auth screen: single clean surface, 3 clearly separated paths, no clutter
      - [ ] "Continue with invite code" (guest/anonymous path)
      - [ ] "Email & password" (sign up / sign in toggle)
      - [ ] "Email me a link" (magic link) — hidden behind a feature flag if B3 unresolved
- [ ] `AuthRepository`: `signInAnonymously`, `signUpWithPassword`, `signInWithPassword`,
      `sendMagicLink`, `signOut`, `currentUser`, `sessionStatus` flow
- [ ] Session persistence + auto-restore on cold start (supabase-kt does this natively;
      must verify it survives process death)
- [ ] Profile bootstrap: after first auth, prompt for username + display name
- [ ] Invite code gate: anonymous users must redeem a code before entering a guild
- [ ] Auth state drives navigation (no manual nav flags)
- [ ] Unit tests: invite validation, username validation, auth-state reducer

## 3. Milestone M2 — Guild + channel shell (Discord layout)

- [ ] Left rail: guild icons (server list), DM button, create-server button
- [ ] Guild screen: category list → channels, collapsible categories
- [ ] Channel header: name, topic, member list toggle
- [ ] Member sidebar grouped by role with role colours
- [ ] Create server flow (name + icon upload)
- [ ] Create channel / create category (owner/admin only)
- [ ] Invite flow: generate code, share sheet, redeem code
- [ ] Role editor: create role, colour, permission checkboxes
- [ ] Permission resolver: compute effective permissions from roles + overwrites
      (Discord algorithm: base @everyone → role OR → overwrite deny → overwrite allow)
- [ ] Unit tests: permission resolver truth table (this is the one place a bug is a security bug)

## 4. Milestone M3 — Messaging

- [ ] Message list with Paging 3 (`paging-compose` 3.4.1), page size 25, prefetch 25
- [ ] Bidirectional paging (newest first, scroll up = older, scroll down = newer)
- [ ] In-memory cache keyed by channel so switching channels is instant and not janky
- [ ] Realtime INSERT/UPDATE/DELETE via Supabase Realtime on the open channel only
- [ ] Optimistic send: message appears immediately, reconciled by id on server ack
- [ ] Message grouping (consecutive same-author messages collapse header)
- [ ] Reply-to with inline quoted preview
- [ ] Edit / delete own messages
- [ ] Reactions with emoji picker (stock + guild custom)
- [ ] Attachments: image upload to storage, inline preview, tap to fullscreen
- [ ] Mention detection + highlight, unread badges per channel
- [ ] Markdown-lite rendering: bold, italic, strike, code, code block, links
- [ ] Typing indicator (Realtime broadcast, throttle to 1 per 3s)
- [ ] Tests: message grouping logic, mention parser, optimistic reconciliation

## 5. Milestone M4 — DMs

- [ ] DM list in left rail (avatar + name + last message + unread)
- [ ] Start DM from member profile
- [ ] DM thread reuses the chat screen (same pager, same composer)
- [ ] DM realtime + unread parity with channels
- [ ] Tests: dm channel pair ordering (user_a < user_b always)

## 6. Milestone M5 — Push notifications (FCM)

- [ ] Add `google-services.json`, Firebase BOM, `firebase-messaging`
- [ ] Device token registration → `device_tokens` table (user_id, token, platform)
- [ ] Supabase Edge Function (Deno) on `messages` INSERT → send FCM to channel members
      who are not the author and have no read_state covering the message
- [ ] Android notification channel + tap-to-open-deep-link into the right channel
- [ ] Suppress notification when the app is foregrounded on that exact channel
- [ ] Notification permission request (Android 13+ POST_NOTIFICATIONS)
- [ ] Tests: payload builder, suppression logic
- [ ] Edge function deployed + verified by hand with a real device

## 7. Milestone M6 — Nitro cosmetics

Order chosen by cost: DB field + client render first, upload pipelines later.

- [ ] Custom status + tag (text, emoji, expiry)
- [ ] Accent colour + **rich gradient themes** (multi-stop gradients with texture,
      like Discord Nitro profile themes — not flat colour). Render with Compose
      `Brush.linearGradient`/`radialGradient` + optional noise overlay
- [ ] Profile banner (image + animated GIF via coil-gif), cropped, uploaded
- [ ] Animated avatar (GIF/WebP via coil-gif)
- [ ] Custom emoji upload per guild + usage in messages and reactions
- [ ] Badges (static + animated variants)
- [ ] Avatar decoration (overlay ring rendered around avatar)
- [ ] Profile card redesign: banner header, gradient wash, avatar with decoration,
      badges row, status, mutual guilds
- [ ] Tests: theme JSON parse/serialize, gradient stop validation, upload size guard

## 8. Milestone M7 — UI system upgrade

Replace hand-built surfaces with a consistent component layer. Compose-native
equivalents of the reference sites (those are React, so direct copy is impossible;
port the *design*, not the code):

- [ ] Design tokens: spacing scale, radii, elevation, motion durations
- [ ] Textured gradient primitives (animated shimmer on nitro cards)
- [ ] `shimmer` 1.5.0 for loading skeletons
- [ ] `lottie-compose` 6.7.1 for empty states / send animation
- [ ] Motion: shared-element transitions between list → chat, springy press states
- [ ] Dark theme first-class (Discord-like), light theme as secondary
- [ ] Haptics on send/react/copy
- [ ] Accessibility: content descriptions, 48dp targets, dynamic type, contrast check

## 9. Milestone M8 — Verification (do not skip)

- [ ] Every box above is ticked, or explicitly deferred with a reason in this file
- [ ] `./gradlew assembleRelease` green in CI
- [ ] `./gradlew testReleaseUnitTest` green in CI
- [ ] Manual pass on a real Android device against the live Supabase project:
      - [ ] anonymous sign-in → onboarding → create server → send message
      - [ ] email+password signup → same flow
      - [ ] invite code gates a second account into the server
      - [ ] two devices see each other's messages live
      - [ ] DM between the two accounts works
      - [ ] push notification arrives when app is backgrounded
      - [ ] banner + GIF avatar + gradient theme render correctly
      - [ ] RLS negative test: account C cannot read server A's messages
- [ ] APK artifact downloaded from the CI run and installed fresh

---

## 10. Schema migration history

- [x] v0 `supabase/schema.sql` — profiles, channels, messages (placeholder, superseded)
- [ ] v1 `supabase/migrations/0001_discord_parity.sql` — full model above
- [ ] v2 storage buckets + policies
- [ ] v3 realtime publication for messages/message_reactions/guild_members

---

## 11. Known risks

| Risk | Impact | Mitigation |
|---|---|---|
| Supabase free tier pauses after 7 days idle | App dead until manual wake | Cron ping every 3 days (GitHub Actions schedule) |
| 500MB DB / 1GB storage | Media fills fast | Enforce upload limits; document when to migrate to R2 |
| Realtime 200 concurrent | Fine for 50 users, no headroom | Acceptable; revisit if group grows |
| REA tool unhealthy (`hopper: missing_analysis_engine`) | Cannot binary-reverse Discord | Use Discord's documented data model + public API shape instead; design is well known |
| Paging 3 + Compose on 2-core CI | Slow builds | Batch builds only |
| Debug-keystore release APK | Insecure distribution | B5 |

---

## 12. Verification log (append as work lands)

| Date | Milestone | Evidence |
|---|---|---|
| — | — | — |
