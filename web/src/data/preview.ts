// Design-preview content. Used ONLY when VITE_SUPABASE_* is not injected, so the
// owner can see the redesign locally without touching the live backend. The shipped
// APK is built in CI with real credentials, where NONE of this is reachable (spec §5
// "never leave demo data in the shipped build"). Every string is neutral placeholder.
import type { Channel, DmSummary, Guild, Member, Message, Profile } from '../lib/types'

export const PREVIEW_SELF_ID = 'preview'

export const previewGuilds: Guild[] = [
  { id: 'g1', name: 'The Nook', icon_url: null, owner_id: PREVIEW_SELF_ID },
  { id: 'g2', name: 'Game Night', icon_url: null, owner_id: 'u2' },
  { id: 'g3', name: 'Design Lab', icon_url: null, owner_id: 'u3' },
]

export const previewChannels: Record<string, Channel[]> = {
  g1: [
    { id: 'c-general', guild_id: 'g1', name: 'general', type: 'text', topic: 'Say hi, share what you are up to.', position: 0, parent_id: null },
    { id: 'c-announce', guild_id: 'g1', name: 'announcements', type: 'text', topic: 'Release notes and updates.', position: 1, parent_id: null },
    { id: 'c-media', guild_id: 'g1', name: 'media', type: 'text', topic: null, position: 2, parent_id: null },
    { id: 'c-voice', guild_id: 'g1', name: 'Lounge', type: 'voice', topic: null, position: 3, parent_id: null },
  ],
  g2: [
    { id: 'c-g2-general', guild_id: 'g2', name: 'general', type: 'text', topic: null, position: 0, parent_id: null },
    { id: 'c-g2-lfg', guild_id: 'g2', name: 'looking-for-group', type: 'text', topic: null, position: 1, parent_id: null },
  ],
  g3: [
    { id: 'c-g3-general', guild_id: 'g3', name: 'general', type: 'text', topic: null, position: 0, parent_id: null },
    { id: 'c-g3-feedback', guild_id: 'g3', name: 'feedback', type: 'text', topic: null, position: 1, parent_id: null },
  ],
}

function author(id: string, username: string, display_name: string | null): Profile {
  return {
    id, username, display_name, avatar_url: null, banner_url: null, accent_color: null,
    theme: null, custom_status: null, badges: [], avatar_decoration_url: null, avatar_is_animated: false,
  }
}

const people: Record<string, Profile> = {
  [PREVIEW_SELF_ID]: author(PREVIEW_SELF_ID, 'you', 'Alex Rivera'),
  u2: author('u2', 'maya', 'Maya Chen'),
  u3: author('u3', 'jordan', 'Jordan Lee'),
  u4: author('u4', 'sam', 'Sam Park'),
  u5: author('u5', 'devon', 'Devon Wright'),
}

function msg(
  id: string, channel_id: string, author_id: string, content: string, minutesAgo: number,
  extra: Partial<Message> = {},
): Message {
  const created_at = new Date(Date.now() - minutesAgo * 60_000).toISOString()
  return {
    id, channel_id, author_id, content, type: 'default', reply_to_id: null,
    created_at, edited_at: null, deleted_at: null,
    author: people[author_id] ?? null, reply: null, reactions: [], attachments: [],
    ...extra,
  }
}

export const previewMessages: Record<string, Message[]> = {
  'c-general': [
    msg('m1', 'c-general', 'u2', 'Morning everyone! ☀️ Anyone up for a call later?', 240),
    msg('m2', 'c-general', 'u3', "I'm in. After 6 works for me.", 236),
    msg('m3', 'c-general', 'u2', 'Perfect. I will drop the invite here.', 235, {
      reactions: [{ emoji: '👍', user_id: 'u3' }, { emoji: '🎉', user_id: PREVIEW_SELF_ID }],
    }),
    msg('m4', 'c-general', 'u4', 'Did you see the new build? The images finally load 🎉', 120),
    msg('m5', 'c-general', PREVIEW_SELF_ID, 'Yes! That was the Coil network fetcher. Everything renders now.', 118, {
      reactions: [{ emoji: '🔥', user_id: 'u2' }, { emoji: '🔥', user_id: 'u4' }],
    }),
    msg('m6', 'c-general', 'u5', 'Here is the snippet I mentioned:\n```kotlin\nval coil = ImageLoader.Builder(context)\n  .components { add(OkHttpNetworkFetcherFactory()) }\n  .build()\n```', 60),
    msg('m7', 'c-general', 'u3', 'Nice and clean. **Ship it.**', 55),
    msg('m8', 'c-general', 'u2', '@you can you share the release notes?', 12, {
      reactions: [{ emoji: '👀', user_id: 'u3' }],
    }),
    msg('m9', 'c-general', PREVIEW_SELF_ID, 'On it — writing them up now.', 3),
  ],
  'c-announce': [
    msg('a1', 'c-announce', 'u2', '**v0.3.0 is live** — moderation, message limits, and daily backups. 🎉', 1440, {
      reactions: [{ emoji: '🎉', user_id: 'u3' }, { emoji: '❤️', user_id: 'u4' }, { emoji: '🚀', user_id: PREVIEW_SELF_ID }],
    }),
  ],
  'c-media': [
    msg('md1', 'c-media', 'u4', 'Dropping the palette we landed on for the redesign.', 300),
  ],
  'c-g2-general': [msg('g1', 'c-g2-general', 'u3', 'Game night is on for Friday 🎮', 90)],
  'c-g3-general': [msg('g3', 'c-g3-general', 'u5', 'Welcome to Design Lab. Share work in #feedback.', 500)],
  'dm-1': [
    msg('d1', 'dm-1', 'u2', 'Hey! Did you get a chance to look at the new layout?', 40),
    msg('d2', 'dm-1', PREVIEW_SELF_ID, 'I did — the channels and the member panel feel much cleaner now.', 38),
    msg('d3', 'dm-1', 'u2', 'Right? The empty states are a nice touch too.', 24, {
      reactions: [{ emoji: '💜', user_id: PREVIEW_SELF_ID }],
    }),
    msg('d4', 'dm-1', PREVIEW_SELF_ID, "I'll share release notes, sneak peeks, and a couple of screenshots later today.", 22),
    msg('d5', 'dm-1', 'u2', 'Sounds good, no rush 🙂', 20),
  ],
  'dm-2': [
    msg('d6', 'dm-2', 'u3', 'How is the new build holding up?', 200),
    msg('d7', 'dm-2', PREVIEW_SELF_ID, 'BETTER 😐', 199),
  ],
  'dm-3': [
    msg('d8', 'dm-3', 'u4', 'Do you like my pfp', 1500),
    msg('d9', 'dm-3', PREVIEW_SELF_ID, 'It looks great! 👍', 1498),
  ],
  'dm-4': [
    msg('d10', 'dm-4', 'u5', 'https://chatnook.app/invite', 13000),
  ],
}

export const previewDms: DmSummary[] = [
  { channel_id: 'dm-1', user_id: 'u2', name: 'Maya Chen', avatar_url: null, last_message: "You: I'll share release notes, sneak peeks, an…", last_at: new Date(Date.now() - 22 * 60_000).toISOString() },
  { channel_id: 'dm-2', user_id: 'u3', name: 'Jordan Lee', avatar_url: null, last_message: 'BETTER 😐', last_at: new Date(Date.now() - 21 * 60_000).toISOString() },
  { channel_id: 'dm-3', user_id: 'u4', name: 'Sam Park', avatar_url: null, last_message: 'Do you like my pfp', last_at: new Date(Date.now() - 86_400_000).toISOString() },
  { channel_id: 'dm-4', user_id: 'u5', name: 'Devon Wright', avatar_url: null, last_message: 'https://chatnook.app/invite', last_at: new Date(Date.now() - 9 * 86_400_000).toISOString() },
]

export const previewMembers: Member[] = [
  { user_id: PREVIEW_SELF_ID, nickname: null, joined_at: '', profile: people[PREVIEW_SELF_ID], roles: [{ id: 'r1', name: 'Owner', color: 0xff7a45, position: 2 }] },
  { user_id: 'u2', nickname: null, joined_at: '', profile: people.u2, roles: [{ id: 'r2', name: 'Moderator', color: 0x34d399, position: 1 }] },
  { user_id: 'u3', nickname: null, joined_at: '', profile: people.u3, roles: [{ id: 'r3', name: 'Member', color: null, position: 0 }] },
  { user_id: 'u4', nickname: null, joined_at: '', profile: people.u4, roles: [{ id: 'r3', name: 'Member', color: null, position: 0 }] },
  { user_id: 'u5', nickname: null, joined_at: '', profile: people.u5, roles: [{ id: 'r3', name: 'Member', color: null, position: 0 }] },
]

export const previewPeople = people
