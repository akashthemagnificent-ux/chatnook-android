# ChatNook web client

The React client for ChatNook. Same Supabase project, same tables and RPCs as the
Android app — no separate backend.

```bash
npm install
npm run dev        # http://127.0.0.1:5173
npm run build      # typecheck + production bundle in dist/
npm run lint
```

## Supabase

Copy `.env.example` to `.env` and fill both values from `local.properties`
(`SUPABASE_URL`, `SUPABASE_ANON_KEY`):

```
VITE_SUPABASE_URL=...
VITE_SUPABASE_ANON_KEY=...
```

Without them the build runs in **design-preview mode**: the full interface with
sample content and no network calls. That is what `npm run dev` shows on a fresh
clone, and it is how the UI is reviewed. Never ship a build without the env vars —
it would have no accounts.

## Layout

- `src/data/api.ts` — every Supabase read/write, one function per screen need.
- `src/data/ChatContext.tsx` — the single live store: boot, realtime, optimistic
  sends, presence, typing, unread.
- `src/features/*` — one folder per surface (shell, chat, guild, dm, settings, auth).
- `src/components/ui/*` — hand-built primitives over Radix; see `COMPONENTS.md`.

Storage writes must live under `<bucket>/<uid>/<file>`; that is the only path the
bucket policies accept.
