// Schema validator: applies supabase/schema.sql to a real Postgres (PGlite).
// Stubs the Supabase-managed `auth` schema + roles, then exercises RLS as
// three real users. Anything that runs as superuser bypasses RLS, so every
// assertion below runs inside `set local role authenticated`.
import { PGlite } from '@electric-sql/pglite'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const defaultSchema = resolve(here, '../../supabase/migrations/0001_discord_parity.sql')
const sql = readFileSync(process.argv[2] || defaultSchema, 'utf8')

const db = await new PGlite()
let fail = 0
const ok = (m) => console.log(`  ok   ${m}`)
const bad = (m, e) => { fail++; console.log(`  FAIL ${m}: ${e.message}`) }

// --- Supabase stubs -------------------------------------------------------
await db.exec(`
  do $$ begin
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
      create role authenticated nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'anon') then
      create role anon nologin;
    end if;
  end $$;
  create schema if not exists auth;
  create table if not exists auth.users (
    id uuid primary key default gen_random_uuid(),
    email text,
    raw_user_meta_data jsonb default '{}'::jsonb
  );
  create or replace function auth.uid() returns uuid
    language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  do $$ begin
    if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
      create publication supabase_realtime;
    end if;
  end $$;
`)

// --- 1. apply schema, twice (idempotency) ---------------------------------
for (const pass of [1, 2]) {
  try {
    await db.exec(sql)
    ok(`schema applied (pass ${pass})`)
  } catch (e) {
    bad(`schema apply (pass ${pass})`, e)
    process.exit(1)
  }
}

// Supabase grants these to authenticated by default; without them RLS is moot
// because the role cannot even see the tables.
await db.exec(`
  grant usage on schema public to authenticated, anon;
  grant all on all tables in schema public to authenticated;
  grant select on all tables in schema public to anon;
  grant execute on all functions in schema public to authenticated;
`)

// --- 2. expected tables exist ---------------------------------------------
const tables = ['profiles','guilds','guild_members','roles','member_roles','channels',
  'dm_participants','messages','attachments','reactions','read_states','custom_emojis','push_tokens']
for (const t of tables) {
  const r = await db.query(`select 1 from information_schema.tables where table_schema='public' and table_name=$1`, [t])
  if (r.rows.length) ok(`table ${t}`); else bad(`table ${t}`, new Error('missing'))
}

// --- 3. RLS enabled everywhere --------------------------------------------
const noRls = await db.query(`
  select c.relname from pg_class c join pg_namespace n on n.oid=c.relnamespace
  where n.nspname='public' and c.relkind='r' and not c.relrowsecurity`)
if (noRls.rows.length === 0) ok('RLS enabled on all public tables')
else bad('RLS', new Error(noRls.rows.map(r => r.relname).join(',')))

// --- 4. trigger creates a profile for a new auth user ---------------------
const A = '11111111-1111-1111-1111-111111111111'
const B = '22222222-2222-2222-2222-222222222222'
const C = '55555555-5555-5555-5555-555555555555'
await db.query(`insert into auth.users (id, email, raw_user_meta_data) values ($1,'a@x.com','{"username":"alice"}')`, [A])
await db.query(`insert into auth.users (id, email, raw_user_meta_data) values ($1,'b@x.com','{"username":"alice"}')`, [B])
await db.query(`insert into auth.users (id, email, raw_user_meta_data) values ($1,'c@x.com','{"username":"carol"}')`, [C])
const profs = await db.query(`select id, username from public.profiles order by username`)
if (profs.rows.length === 3) ok('handle_new_user created 3 profiles')
else bad('profiles created', new Error(String(profs.rows.length)))
const uniq = new Set(profs.rows.map(r => r.username)).size
if (uniq === 3) ok('duplicate usernames de-duplicated')
else bad('username uniqueness', new Error(profs.rows.map(r => r.username).join(',')))

// --- impersonation helper -------------------------------------------------
// Mirrors Supabase: role `authenticated` + request.jwt.claim.sub for auth.uid().
const as = async (uid, fn) => {
  await db.exec('begin')
  await db.query(`select set_config('request.jwt.claim.sub', $1, true)`, [uid])
  await db.exec('set local role authenticated')
  try {
    const r = await fn()
    await db.exec('commit')
    return r
  } catch (e) {
    await db.exec('rollback')
    throw e
  }
}
const asAnon = async (fn) => {
  await db.exec('begin')
  await db.query(`select set_config('request.jwt.claim.sub', '', true)`)
  await db.exec('set local role anon')
  try {
    const r = await fn()
    await db.exec('commit')
    return r
  } catch (e) {
    await db.exec('rollback')
    throw e
  }
}

// --- 5. guild + channel + message, then RLS as a non-member ---------------
const G = '33333333-3333-3333-3333-333333333333'
const CH = '44444444-4444-4444-4444-444444444444'
try {
  await as(A, async () => {
    await db.query(`insert into public.guilds (id, name, owner_id) values ($1,'Test',$2)`, [G, A])
    // owner membership is created by the guild trigger, not by the client
    await db.query(`insert into public.channels (id, guild_id, name) values ($1,$2,'general')`, [CH, G])
    await db.query(`insert into public.messages (channel_id, author_id, content) values ($1,$2,'hello')`, [CH, A])
  })
  ok('member can insert guild/channel/message')
} catch (e) {
  bad('member insert', e)
}

// B is not a member: must see no guilds, no channels, no messages.
const g = await as(B, () => db.query(`select id from public.guilds`))
const c = await as(B, () => db.query(`select id from public.channels`))
const m = await as(B, () => db.query(`select id from public.messages`))
if (g.rows.length === 0) ok('non-member sees no guilds')
else bad('guild leak', new Error(`${g.rows.length} visible`))
if (c.rows.length === 0) ok('non-member sees no channels')
else bad('channel leak', new Error(`${c.rows.length} visible`))
if (m.rows.length === 0) ok('non-member sees no messages')
else bad('message leak', new Error(`${m.rows.length} visible`))

// Non-member cannot inject a message into a channel they cannot see.
let injected = false
try {
  await as(B, () => db.query(`insert into public.messages (channel_id, author_id, content) values ($1,$2,'intruder')`, [CH, B]))
  injected = true
} catch { /* expected */ }
if (!injected) ok('non-member cannot insert into a channel')
else bad('message insert leak', new Error('insert succeeded'))

// A self-join without an invite must not be possible (guild_join_self hole).
let selfJoined = false
try {
  await as(C, () => db.query(`insert into public.guild_members (guild_id, user_id) values ($1,$2)`, [G, C]))
  selfJoined = true
} catch { /* expected */ }
if (!selfJoined) ok('non-member cannot self-join a guild')
else bad('guild self-join hole', new Error('insert succeeded'))

// anonymous (no JWT) sees nothing
const anonG = await asAnon(() => db.query(`select id from public.guilds`))
if (anonG.rows.length === 0) ok('anon sees no guilds')
else bad('anon guild leak', new Error(`${anonG.rows.length} visible`))

// --- 6. DM creation is idempotent & participant-scoped -------------------
let dm1, dm2
try {
  dm1 = (await as(A, () => db.query(`select public.find_or_create_dm($1) as id`, [B]))).rows[0].id
  dm2 = (await as(A, () => db.query(`select public.find_or_create_dm($1) as id`, [B]))).rows[0].id
  if (dm1 === dm2) ok('find_or_create_dm is idempotent')
  else bad('dm idempotency', new Error(`${dm1} != ${dm2}`))
} catch (e) {
  bad('find_or_create_dm', e)
}
if (dm1) {
  const parts = await as(A, () => db.query(`select count(*)::int c from public.dm_participants where channel_id=$1`, [dm1]))
  if (parts.rows[0].c === 2) ok('DM has exactly 2 participants')
  else bad('dm participants', new Error(String(parts.rows[0].c)))

  // B is a participant and can read it; C is not and must not.
  const bSees = await as(B, () => db.query(`select id from public.channels where id=$1`, [dm1]))
  if (bSees.rows.length === 1) ok('DM participant can see the channel')
  else bad('dm participant blind', new Error('0 rows'))

  const cSees = await as(C, () => db.query(`select id from public.channels where id=$1`, [dm1]))
  if (cSees.rows.length === 0) ok('non-participant cannot see the DM')
  else bad('dm leak', new Error('visible'))

  const cMsgs = await as(C, () => db.query(`select id from public.messages where channel_id=$1`, [dm1]))
  if (cMsgs.rows.length === 0) ok('non-participant cannot read DM messages')
  else bad('dm message leak', new Error(`${cMsgs.rows.length} visible`))

  // A third user must not be able to graft themselves onto the DM.
  let grafted = false
  try {
    await as(C, () => db.query(`insert into public.dm_participants (channel_id, user_id) values ($1,$2)`, [dm1, C]))
    grafted = true
  } catch { /* expected */ }
  if (!grafted) ok('non-participant cannot join the DM')
  else bad('dm participant hole', new Error('insert succeeded'))
}

// --- 7. realtime publication wired ---------------------------------------
const pub = await db.query(`select tablename from pg_publication_tables where pubname='supabase_realtime'`)
const pubTables = pub.rows.map(r => r.tablename)
if (['messages', 'reactions', 'channels'].every(t => pubTables.includes(t)))
  ok('realtime publication includes messages/reactions/channels')
else bad('realtime publication', new Error(pubTables.join(',') || 'empty'))

console.log(fail === 0 ? '\nSCHEMA OK' : `\n${fail} FAILURE(S)`)
process.exit(fail === 0 ? 0 : 1)
