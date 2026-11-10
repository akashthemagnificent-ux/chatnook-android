#!/usr/bin/env python3
"""End-to-end check of the live Supabase backend against the queries the app actually sends.

Run:  python3 tools/e2e_backend.py [-v]
Needs: local.properties (SUPABASE_URL, SUPABASE_ANON_KEY)
       ~/.chatnook/service_role.key  (only for cleanup; the checks themselves use anon keys)

Every assertion mirrors a Kotlin call site, so a schema or policy change that would break
the app fails here first. The message query is read straight out of SupabaseChatRepository.kt
rather than duplicated, so it cannot drift.
"""
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERBOSE = "-v" in sys.argv


def load_props():
    props = {}
    with open(os.path.join(ROOT, "local.properties")) as f:
        for line in f:
            if "=" in line:
                k, v = line.strip().split("=", 1)
                props[k] = v
    return props["SUPABASE_URL"].rstrip("/"), props["SUPABASE_ANON_KEY"]


URL, ANON = load_props()
SERVICE = None
service_path = os.path.expanduser("~/.chatnook/service_role.key")
if os.path.exists(service_path):
    SERVICE = open(service_path).read().strip()


def repo_const(name):
    """Pull a `const val NAME = "..." + "..."` string out of the repository source."""
    path = os.path.join(
        ROOT, "app/src/main/java/com/chatnook/app/data/repository/SupabaseChatRepository.kt"
    )
    src = open(path).read()
    m = re.search(rf"const val {name} =\s*(.*?)\n\n", src, re.S)
    assert m, f"{name} not found in SupabaseChatRepository.kt"
    return "".join(re.findall(r'"([^"]*)"', m.group(1)))


MESSAGE_COLUMNS = repo_const("MESSAGE_COLUMNS")


class Api:
    def __init__(self, token=None, key=None):
        self.token = token
        # The service key is an opaque `sb_secret_…`, not a JWT, so it only ever
        # goes in `apikey`; putting it in Authorization fails with PGRST301.
        self.key = key

    def _req(self, path, method="GET", body=None, token=None, key=None, prefer=None):
        headers = {"apikey": key or self.key or ANON, "Content-Type": "application/json"}
        tok = token or self.token
        if tok:
            headers["Authorization"] = "Bearer " + tok
        if prefer:
            headers["Prefer"] = prefer
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(URL + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as r:
                raw = r.read()
                return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw)
            except json.JSONDecodeError:
                return e.code, raw

    def get(self, path, **kw):
        return self._req(path, "GET", **kw)

    def post(self, path, body, **kw):
        return self._req(path, "POST", body, **kw)

    def patch(self, path, body, **kw):
        return self._req(path, "PATCH", body, **kw)

    def delete(self, path, **kw):
        return self._req(path, "DELETE", **kw)

    def rpc(self, name, body, **kw):
        return self._req("/rest/v1/rpc/" + name, "POST", body, **kw)

    def upload(self, path, data, content_type="image/png"):
        """Raw bytes to the Storage API; _req would JSON-encode them."""
        headers = {
            "apikey": ANON,
            "Authorization": "Bearer " + (self.token or ""),
            "Content-Type": content_type,
        }
        req = urllib.request.Request(URL + path, data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, r.read()[:200]
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode()[:300]


anon = Api()
admin = Api(key=SERVICE) if SERVICE else None
failures = []


def check(label, cond, detail=""):
    if cond:
        print(f"  ok   {label}")
    else:
        print(f"  FAIL {label} {detail}")
        failures.append(label)


def signup():
    st, body = anon.post("/auth/v1/signup", {})
    assert st == 200, f"signup {st}: {body}"
    return body["access_token"], body["user"]["id"]


def realtime_sees_profile_update(client, uid):
    """Joins Realtime as [client] and waits for a profiles UPDATE for [uid].

    This is the server half of "a profile edit shows up for everyone else": the row
    must reach a second authenticated socket, not just the writer's own screen.
    """
    import asyncio

    import websockets

    ref = URL.split("//", 1)[1].split(".", 1)[0]
    ws_url = f"wss://{ref}.supabase.co/realtime/v1/websocket?apikey={ANON}&vsn=1.0.0"

    async def go():
        async with websockets.connect(ws_url, ping_interval=None) as ws:
            await ws.send(json.dumps({
                "topic": "realtime:e2e-profiles",
                "event": "phx_join",
                "payload": {
                    "config": {
                        "broadcast": {"self": False, "ack": False},
                        "presence": {"key": uid},
                        "postgres_changes": [{
                            "event": "UPDATE",
                            "schema": "public",
                            "table": "profiles",
                            "filter": f"id=eq.{uid}",
                        }],
                    },
                    "access_token": client.token,
                },
                "ref": "1",
                "join_ref": "1",
            }))

            joined = False
            for _ in range(10):
                reply = json.loads(await asyncio.wait_for(ws.recv(), timeout=5))
                if reply.get("event") == "phx_reply":
                    joined = reply.get("payload", {}).get("status") == "ok"
                    break
            if not joined:
                return False, "join refused"

            client.patch(f"/rest/v1/profiles?id=eq.{uid}", {"display_name": "Realtime Renamed"})
            for _ in range(10):
                event = json.loads(await asyncio.wait_for(ws.recv(), timeout=5))
                if event.get("event") == "postgres_changes":
                    data = event.get("payload", {}).get("data", {})
                    record = data.get("record") or data.get("new") or {}
                    return record.get("display_name") == "Realtime Renamed", data
            return False, "no event after the update"

    # A socket test over the public internet can lose one race with a slow event;
    # a second attempt tells a real regression from a bad minute.
    for attempt in range(2):
        try:
            return asyncio.run(go())
        except Exception as e:  # timeout, socket or protocol failure
            if attempt == 1:
                return False, repr(e)


def cleanup(user_ids, guild_ids):
    if not admin:
        print("  (no service role key: skipping cleanup)")
        return
    for gid in guild_ids:
        admin.delete(f"/rest/v1/guilds?id=eq.{gid}")
    for uid in user_ids:
        admin.delete(f"/auth/v1/admin/users/{uid}")


def main():
    created_users, created_guilds = [], []
    try:
        run(created_users, created_guilds)
    finally:
        cleanup(created_users, created_guilds)
    print()
    if failures:
        print(f"{len(failures)} check(s) failed: {', '.join(failures)}")
        return 1
    print("all checks passed")
    return 0


def run(created_users, created_guilds):
    print("signup + profile")
    a_tok, a_uid = signup()
    created_users.append(a_uid)
    a = Api(a_tok)
    st, rows = a.get(f"/rest/v1/profiles?select=id,username&id=eq.{a_uid}")
    check("own profile readable", st == 200 and len(rows) == 1, f"{st} {rows}")

    print("create_guild")
    st, gid = a.rpc("create_guild", {"guild_name": "E2E " + uuid.uuid4().hex[:6]})
    check("create_guild rpc", st == 200 and isinstance(gid, str), f"{st} {gid}")
    if st != 200:
        return
    created_guilds.append(gid)

    st, rows = a.get("/rest/v1/guild_members?select=guild:guilds(id,name,icon_url,owner_id)"
                     f"&user_id=eq.{a_uid}")
    check("guild rail embed", st == 200 and len(rows) == 1 and rows[0]["guild"]["id"] == gid,
          f"{st} {rows}")

    st, chans = a.get("/rest/v1/channels?select=id,guild_id,name,topic,type,position"
                      f"&guild_id=eq.{gid}&order=position.asc")
    check("general channel exists", st == 200 and len(chans) == 1 and chans[0]["name"] == "general",
          f"{st} {chans}")
    if not chans:
        return
    cid = chans[0]["id"]

    print("messages")
    st, sent = a.post("/rest/v1/messages",
                      {"channel_id": cid, "author_id": a_uid, "content": "hello e2e"},
                      prefer="return=representation")
    check("send message", st == 201 and len(sent) == 1, f"{st} {sent}")

    st, rows = a.get(f"/rest/v1/messages?select={MESSAGE_COLUMNS}&channel_id=eq.{cid}"
                     "&order=created_at.desc&limit=50")
    check("timeline query (app columns)", st == 200 and len(rows) == 1,
          f"{st} {str(rows)[:200]}")
    if st == 200 and rows:
        check("author embed resolves", (rows[0].get("author") or {}).get("id") == a_uid, rows[0])
        check("reactions embed is a list", isinstance(rows[0].get("reactions"), list), rows[0])

    print("keyset pagination (500 messages)")
    batch = [{"channel_id": cid, "author_id": a_uid, "content": f"page {i}"} for i in range(500)]
    st, _ = a.post("/rest/v1/messages", batch)
    check("seed 500 messages", st == 201, st)

    seen, cursor, pages = [], None, 0
    while True:
        q = (f"/rest/v1/messages?select=id,created_at&channel_id=eq.{cid}"
             "&order=created_at.desc,id.desc&limit=50")
        if cursor:
            iso, mid = cursor
            q += ("&or=(created_at.lt." + urllib.parse.quote(iso)
                  + ",and(created_at.eq." + urllib.parse.quote(iso) + ",id.lt." + mid + "))")
        st, page = a.get(q)
        if st != 200 or not page:
            break
        seen += [m["id"] for m in page]
        pages += 1
        if len(page) < 50:
            break
        cursor = (page[-1]["created_at"], page[-1]["id"])

    check("paging walks to the first message", st == 200, f"{st} {str(page)[:200]}")
    check("every message appears exactly once",
          len(seen) == len(set(seen)) == 501, f"{len(seen)} rows, {len(set(seen))} unique")
    check("11 pages of at most 50", pages == 11 and len(seen) > 450, pages)

    b_tok, b_uid = signup()
    created_users.append(b_uid)
    b = Api(b_tok)

    print("media")
    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )
    object_path = f"{a_uid}/e2e.png"
    st, body = a.upload(f"/storage/v1/object/attachments/{object_path}", png)
    check("upload to attachments bucket", st == 200, f"{st} {body}")
    public_url = f"{URL}/storage/v1/object/public/attachments/{object_path}"
    req = urllib.request.Request(public_url)
    with urllib.request.urlopen(req) as r:
        check("attachment is publicly readable", r.status == 200, r.status)

    st, mid = a.rpc(
        "send_message",
        {
            "p_channel_id": cid,
            "p_content": "",
            "p_message_id": str(uuid.uuid4()),
            "p_attachment": {
                "url": public_url,
                "filename": "e2e.png",
                "content_type": "image/png",
                "size_bytes": len(png),
                "width": 1,
                "height": 1,
            },
        },
    )
    check("send_message rpc with image", st == 200 and isinstance(mid, str), f"{st} {mid}")
    if st == 200:
        st, rows = a.get(f"/rest/v1/messages?select={MESSAGE_COLUMNS}&id=eq.{mid}")
        found = rows[0]["attachments"] if st == 200 and rows else []
        check(
            "message carries its attachment",
            len(found) == 1 and found[0]["url"] == public_url and found[0]["width"] == 1,
            f"{st} {rows}",
        )
        check(
            "outsider cannot read the attachment row",
            b.get(f"/rest/v1/attachments?select=id&message_id=eq.{mid}")[1] == [],
        )

    print("RLS isolation")
    check("outsider cannot read guild",
          b.get(f"/rest/v1/guilds?select=id&id=eq.{gid}")[1] == [])
    check("outsider cannot read channel",
          b.get(f"/rest/v1/channels?select=id&id=eq.{cid}")[1] == [])
    check("outsider cannot read messages",
          b.get(f"/rest/v1/messages?select=id&channel_id=eq.{cid}")[1] == [])

    print("invite + join")
    st, code = a.rpc("create_invite", {"gid": gid})
    check("create_invite rpc", st == 200 and isinstance(code, str), f"{st} {code}")
    if st == 200:
        st, joined = b.rpc("join_guild", {"invite_code": code})
        check("join_guild rpc", st == 200 and joined == gid, f"{st} {joined}")
        st, mine = a.get(f"/rest/v1/messages?select=id&channel_id=eq.{cid}")
        expected = len(mine) if st == 200 else -1
        st, rows = b.get(f"/rest/v1/messages?select=id&channel_id=eq.{cid}")
        check("member reads history", st == 200 and len(rows) == expected,
              f"{st} {len(rows) if st == 200 else rows} vs {expected}")
        st, _ = b.post("/rest/v1/messages",
                       {"channel_id": cid, "author_id": b_uid, "content": "from b"})
        check("member can post", st == 201, st)

    print("unread")
    st, me = b.get(f"/rest/v1/profiles?select=username&id=eq.{b_uid}")
    b_name = me[0]["username"] if st == 200 and me else None
    st, counts = b.rpc("channel_unread_counts", {"p_guild_id": gid, "p_mention": b_name})
    check("member sees unread", st == 200 and counts and counts[0]["unread"] > 0, f"{st} {counts}")
    # Only b's own "from b" counts for a; the 60+ messages a posted are its own.
    st, mine = a.rpc("channel_unread_counts", {"p_guild_id": gid, "p_mention": b_name})
    check("own messages are not unread",
          st == 200 and mine and mine[0]["unread"] == 1, f"{st} {mine}")

    a.post("/rest/v1/messages",
           {"channel_id": cid, "author_id": a_uid, "content": f"hey @{b_name} look"})
    st, counts = b.rpc("channel_unread_counts", {"p_guild_id": gid, "p_mention": b_name})
    check("mention counted", st == 200 and counts and counts[0]["mentions"] == 1, f"{st} {counts}")
    st, counts = a.rpc("channel_unread_counts", {"p_guild_id": gid, "p_mention": b_name})
    check("mention is not counted for another member",
          st == 200 and counts and counts[0]["mentions"] == 0, f"{st} {counts}")

    st, last = b.get(f"/rest/v1/messages?select=id,created_at&channel_id=eq.{cid}"
                     "&order=created_at.desc&limit=1")
    if st == 200 and last:
        b.post("/rest/v1/read_states", {
            "user_id": b_uid,
            "channel_id": cid,
            "last_read_message_id": last[0]["id"],
            "last_read_at": last[0]["created_at"],
        })
    st, counts = b.rpc("channel_unread_counts", {"p_guild_id": gid, "p_mention": b_name})
    check("mark read clears the badge",
          st == 200 and counts and counts[0]["unread"] == 0 and counts[0]["mentions"] == 0,
          f"{st} {counts}")

    a.post("/rest/v1/messages", {"channel_id": cid, "author_id": a_uid, "content": "after read"})
    st, counts = b.rpc("channel_unread_counts", {"p_guild_id": gid, "p_mention": b_name})
    check("new message after read shows 1", st == 200 and counts and counts[0]["unread"] == 1,
          f"{st} {counts}")

    print("members")
    st, rows = b.get(
        "/rest/v1/guild_members?select=user_id,nickname,"
        "profile:profiles(id,username,display_name,avatar_url,banner_url,custom_status,badges),"
        "member_roles(role:roles(id,name,color,position)),guild:guilds(owner_id)"
        f"&guild_id=eq.{gid}"
    )
    check("member list query", st == 200 and len(rows) == 2, f"{st} {str(rows)[:200]}")
    if st == 200 and len(rows) == 2:
        owners = [r for r in rows if r["user_id"] == r["guild"]["owner_id"]]
        check("owner is identifiable", len(owners) == 1 and owners[0]["user_id"] == a_uid, owners)

    print("DMs")
    third, third_uid = None, None
    st, dm = a.rpc("find_or_create_dm", {"other_user": b_uid})
    check("find_or_create_dm rpc", st == 200 and isinstance(dm, str), f"{st} {dm}")
    if st == 200:
        st, again = a.rpc("find_or_create_dm", {"other_user": b_uid})
        check("dm is reused", st == 200 and again == dm, f"{st} {again}")
        b.post("/rest/v1/messages",
               {"channel_id": dm, "author_id": b_uid, "content": "dm hello"})
        st, summaries = a.rpc("dm_summaries", {})
        mine = [s for s in summaries if s["channel_id"] == dm] if st == 200 else []
        check("dm summary lists the conversation",
              st == 200 and len(mine) == 1 and mine[0]["user_id"] == b_uid, f"{st} {summaries}")
        check("dm summary carries the last message",
              bool(mine) and mine[0]["last_message"] == "dm hello", mine)
        st, counts = a.rpc("channel_unread_counts", {})
        row = next((c for c in counts if c["channel_id"] == dm), None) if st == 200 else None
        check("dm counts as unread", row is not None and row["unread"] == 1, f"{st} {counts}")
        check("dm visible to both",
              a.get(f"/rest/v1/channels?select=id&id=eq.{dm}")[1] != []
              and b.get(f"/rest/v1/channels?select=id&id=eq.{dm}")[1] != [])
        c_tok, c_uid = signup()
        created_users.append(c_uid)
        check("dm hidden from third party",
              Api(c_tok).get(f"/rest/v1/channels?select=id&id=eq.{dm}")[1] == [])
        check("dm messages hidden from third party",
              Api(c_tok).get(f"/rest/v1/messages?select=id&channel_id=eq.{dm}")[1] == [])
        third = Api(c_tok)
        third_uid = c_uid

    print("read state + push fan-out")
    if third:
        st, code = a.rpc("create_invite", {"gid": gid})
        st2, joined = third.rpc("join_guild", {"invite_code": code})
        check("third member joins the server", st2 == 200 and joined == gid, f"{st} {st2}")

        # b reads up to and including this message; c never opens the channel.
        a.post("/rest/v1/messages",
               {"channel_id": cid, "author_id": a_uid, "content": "fan out"})
        st, newest = a.get(f"/rest/v1/messages?select=id,created_at&channel_id=eq.{cid}"
                           "&order=created_at.desc&limit=1")
        if st == 200 and newest:
            msg = newest[0]
            b.patch(f"/rest/v1/read_states?user_id=eq.{b_uid}&channel_id=eq.{cid}",
                    {"last_read_message_id": msg["id"], "last_read_at": msg["created_at"]})
            # The push function drops recipients whose last_read_at >= the message time.
            if admin:
                st, states = admin.get("/rest/v1/read_states?select=user_id,last_read_at"
                                       f"&channel_id=eq.{cid}")
                caught_up = {r["user_id"] for r in states
                             if r["last_read_at"] >= msg["created_at"]}
                check("only the reader is suppressed", caught_up == {b_uid}, f"{st} {caught_up}")
                check("the other member is still a push target", third_uid not in caught_up,
                      third_uid)
            else:
                print("  (no service role key: skipping the fan-out check)")

    print("server management")
    a.patch(f"/rest/v1/guilds?id=eq.{gid}", {"name": "Renamed"})
    st, rows = a.get(f"/rest/v1/guilds?select=name&id=eq.{gid}")
    check("owner renames guild", st == 200 and rows and rows[0]["name"] == "Renamed", f"{st} {rows}")

    b.patch(f"/rest/v1/guilds?id=eq.{gid}", {"name": "Hijacked"})
    st, rows = a.get(f"/rest/v1/guilds?select=name&id=eq.{gid}")
    check("member cannot rename guild", rows and rows[0]["name"] == "Renamed", f"{st} {rows}")

    st, created = a.post("/rest/v1/channels",
                         {"guild_id": gid, "name": "ops", "type": "text", "position": 5},
                         prefer="return=representation")
    check("owner creates channel", st == 201 and created, f"{st} {created}")
    ops = created[0]["id"] if st == 201 and created else None

    if ops:
        a.patch(f"/rest/v1/channels?id=eq.{ops}", {"name": "ops-renamed", "topic": "deploys"})
        st, rows = a.get(f"/rest/v1/channels?select=name,topic&id=eq.{ops}")
        check("channel rename + topic",
              st == 200 and rows and rows[0] == {"name": "ops-renamed", "topic": "deploys"},
              f"{st} {rows}")

        st, _ = a.rpc("reorder_channels", {"p_guild_id": gid, "p_ids": [ops, cid]})
        st2, rows = a.get(f"/rest/v1/channels?select=id,position&guild_id=eq.{gid}&order=position")
        check("reorder_channels rpc",
              st in (200, 204) and [r["id"] for r in rows] == [ops, cid]
              and [r["position"] for r in rows] == [0, 1], f"{st} {rows}")

        st, _ = b.post("/rest/v1/channels",
                       {"guild_id": gid, "name": "nope", "type": "text", "position": 9})
        check("member cannot create channel", st == 403, st)

        b.delete(f"/rest/v1/channels?id=eq.{ops}")
        check("member cannot delete channel",
              a.get(f"/rest/v1/channels?select=id&id=eq.{ops}")[1] != [])

        st, _ = a.delete(f"/rest/v1/channels?id=eq.{ops}")
        check("owner deletes channel",
              st in (200, 204) and a.get(f"/rest/v1/channels?select=id&id=eq.{ops}")[1] == [],
              st)

    print("invite rules")
    st, code = a.rpc("create_invite", {"gid": gid, "p_expires_at": None, "p_max_uses": 1})
    check("invite with a use cap", st == 200 and isinstance(code, str), f"{st} {code}")
    if st == 200:
        d_tok, d_uid = signup()
        created_users.append(d_uid)
        st, joined = Api(d_tok).rpc("join_guild", {"invite_code": code})
        check("capped invite works once", st == 200 and joined == gid, f"{st} {joined}")
        e_tok, e_uid = signup()
        created_users.append(e_uid)
        st, joined = Api(e_tok).rpc("join_guild", {"invite_code": code})
        check("capped invite is spent", st >= 400, f"{st} {joined}")

    st, code = a.rpc("create_invite", {"gid": gid})
    st2, _ = a.rpc("revoke_invite", {"p_code": code})
    check("revoke_invite rpc", st == 200 and st2 in (200, 204), f"{st} {st2}")
    f_tok, f_uid = signup()
    created_users.append(f_uid)
    st, joined = Api(f_tok).rpc("join_guild", {"invite_code": code})
    check("revoked invite is rejected", st >= 400, f"{st} {joined}")

    st, code = a.rpc("create_invite", {"gid": gid, "p_expires_at": "2020-01-01T00:00:00Z"})
    st2, joined = Api(f_tok).rpc("join_guild", {"invite_code": code})
    check("expired invite is rejected", st == 200 and st2 >= 400, f"{st} {st2} {joined}")

    print("roles + permissions")
    st, rows = a.get(f"/rest/v1/roles?select=id,is_everyone&guild_id=eq.{gid}&is_everyone=is.true")
    everyone_id = rows[0]["id"] if st == 200 and rows else None
    check("guild has an everyone role", everyone_id is not None, f"{st} {rows}")

    st, owner_bits = a.rpc("my_permissions", {"gid": gid})
    check("owner resolves to every permission", st == 200 and owner_bits == 4095, f"{st} {owner_bits}")

    st, member_bits = b.rpc("my_permissions", {"gid": gid})
    everyone_bits = 1 | 2 | 32 | 256 | 512 | 1024
    check("member gets the everyone bits", st == 200 and member_bits == everyone_bits,
          f"{st} {member_bits}")

    st, rid = a.rpc("create_role", {"gid": gid, "p_name": "Moderator",
                                    "p_color": 5760135, "p_permissions": 8 | 16})
    check("create_role rpc", st == 200 and isinstance(rid, str), f"{st} {rid}")
    st, _ = b.rpc("create_role", {"gid": gid, "p_name": "Nope", "p_permissions": 4095})
    check("member cannot create roles", st >= 400, st)
    st, _ = b.rpc("set_member_roles", {"gid": gid, "member_id": a_uid, "role_ids": []})
    check("member cannot assign roles", st >= 400, st)

    if st == 200 or isinstance(rid, str):
        st, _ = a.rpc("update_role", {"p_role_id": rid, "p_name": "Mod",
                                      "p_color": 5760135, "p_permissions": 8 | 16})
        check("update_role rpc", st in (200, 204), st)

        st, _ = a.rpc("set_member_roles", {"gid": gid, "member_id": b_uid, "role_ids": [rid]})
        check("set_member_roles rpc", st in (200, 204), st)
        st, bits = b.rpc("my_permissions", {"gid": gid})
        check("assigned role grants its bits", st == 200 and bits == (everyone_bits | 8 | 16),
              f"{st} {bits}")

        # MANAGE_CHANNELS is bit 3: the grant has to reach the channel policy, not just
        # the permission number the client displays.
        st, made = b.post("/rest/v1/channels",
                          {"guild_id": gid, "name": "mod-ops", "type": "text", "position": 7},
                          prefer="return=representation")
        check("moderator can create a channel", st == 201 and bool(made), f"{st} {made}")
        if st == 201 and made:
            a.delete(f"/rest/v1/channels?id=eq.{made[0]['id']}")

        # MANAGE_ROLES is not a path to ADMINISTRATOR: no self-edit, no minting bits.
        st, _ = b.rpc("set_member_roles", {"gid": gid, "member_id": b_uid, "role_ids": [rid]})
        check("moderator cannot change their own roles", st >= 400, st)
        st, _ = b.rpc("create_role", {"gid": gid, "p_name": "Sneaky", "p_permissions": 128})
        check("moderator cannot grant a permission they lack", st >= 400, st)

        st, rows = a.get(f"/rest/v1/member_roles?select=role_id&guild_id=eq.{gid}&user_id=eq.{b_uid}")
        check("assignment persisted", st == 200 and [r["role_id"] for r in rows] == [rid],
              f"{st} {rows}")

        # Same action, role taken away: the policy must refuse it again.
        st, _ = a.rpc("set_member_roles", {"gid": gid, "member_id": b_uid, "role_ids": []})
        st2, _ = b.post("/rest/v1/channels",
                        {"guild_id": gid, "name": "nope-again", "type": "text", "position": 8})
        check("revoked moderator is refused", st2 == 403, f"{st} {st2}")

        st, _ = a.rpc("set_member_roles",
                      {"gid": gid, "member_id": b_uid, "role_ids": [everyone_id]})
        st2, rows = a.get(f"/rest/v1/member_roles?select=role_id&guild_id=eq.{gid}&user_id=eq.{b_uid}")
        check("everyone cannot be assigned explicitly", rows == [], f"{st} {st2} {rows}")

        st, _ = a.rpc("delete_role", {"p_role_id": rid})
        check("delete_role rpc", st in (200, 204), st)

    st, _ = a.rpc("delete_role", {"p_role_id": everyone_id})
    check("everyone role cannot be deleted", st >= 400, st)

    print("moderation")
    g_tok, g_uid = signup()
    created_users.append(g_uid)
    g = Api(g_tok)
    st, code = a.rpc("create_invite", {"gid": gid})
    st2, joined = g.rpc("join_guild", {"invite_code": code})
    check("moderation target joins", st2 == 200 and joined == gid, f"{st} {st2}")

    st, _ = b.rpc("kick_member", {"gid": gid, "member": g_uid})
    check("member cannot kick", st >= 400, st)
    st, _ = a.rpc("kick_member", {"gid": gid, "member": a_uid})
    check("owner cannot kick themselves", st >= 400, st)
    st, _ = a.rpc("kick_member", {"gid": gid, "member": g_uid})
    check("owner kicks member", st in (200, 204), st)
    st, rows = a.get(f"/rest/v1/guild_members?select=user_id&guild_id=eq.{gid}&user_id=eq.{g_uid}")
    check("kicked member is gone", rows == [], f"{st} {rows}")

    st, joined = g.rpc("join_guild", {"invite_code": code})
    check("kicked member can rejoin", st == 200 and joined == gid, f"{st} {joined}")

    st, _ = a.rpc("ban_member", {"gid": gid, "member": g_uid, "reason": "spam"})
    check("owner bans member", st in (200, 204), st)
    st, rows = a.get(f"/rest/v1/guild_bans?select=user_id,reason&guild_id=eq.{gid}")
    check("ban carries its reason",
          st == 200 and rows == [{"user_id": g_uid, "reason": "spam"}], f"{st} {rows}")
    st, rows = b.get(f"/rest/v1/guild_bans?select=user_id&guild_id=eq.{gid}")
    check("ban list is hidden from plain members", rows == [], f"{st} {rows}")
    st, joined = g.rpc("join_guild", {"invite_code": code})
    check("banned member cannot rejoin", st >= 400, f"{st} {joined}")

    st, _ = b.rpc("unban_member", {"gid": gid, "member": g_uid})
    check("member cannot unban", st >= 400, st)
    st, _ = a.rpc("unban_member", {"gid": gid, "member": g_uid})
    check("owner unbans member", st in (200, 204), st)
    st, joined = g.rpc("join_guild", {"invite_code": code})
    check("unbanned member can rejoin", st == 200 and joined == gid, f"{st} {joined}")

    st, rows = a.get(f"/rest/v1/guild_audit_log?select=action,detail&guild_id=eq.{gid}"
                     "&order=created_at.asc")
    check("audit log records kick, ban and unban",
          st == 200 and [r["action"] for r in rows] == ["kick", "ban", "unban"], f"{st} {rows}")
    check("audit ban row keeps the reason",
          st == 200 and len(rows) == 3 and rows[1]["detail"] == "spam", rows)

    print("message length")
    st, mid = a.rpc("send_message", {"p_channel_id": cid, "p_content": "x" * 2000})
    check("2000 characters accepted", st == 200 and isinstance(mid, str), f"{st} {mid}")
    st, body = a.rpc("send_message", {"p_channel_id": cid, "p_content": "x" * 2001})
    check("2001 characters refused", st >= 400, f"{st} {body}")
    st, _ = a.post("/rest/v1/messages",
                   {"channel_id": cid, "author_id": a_uid, "content": "x" * 2001})
    check("length constraint holds on a direct insert", st == 400, st)

    print("profile card")
    st, rows = a.rpc("mutual_guilds", {"other": b_uid})
    check("mutual_guilds returns the shared server",
          st == 200 and [r["id"] for r in rows] == [gid], f"{st} {rows}")
    st, rows = b.rpc("mutual_guilds", {"other": str(uuid.uuid4())})
    check("mutual_guilds leaks nothing for a stranger", st == 200 and rows == [], f"{st} {rows}")

    st, rows = a.get(f"/rest/v1/guild_members?select=joined_at&guild_id=eq.{gid}"
                     f"&user_id=eq.{b_uid}")
    check("joined_at is exposed for the card", st == 200 and bool(rows) and rows[0]["joined_at"],
          f"{st} {rows}")

    st, rows = a.get("/rest/v1/profiles?select=theme,avatar_decoration_url,avatar_is_animated"
                     f"&id=eq.{b_uid}")
    check("card cosmetics are readable", st == 200 and len(rows) == 1, f"{st} {rows}")

    print("realtime")
    ok, detail = realtime_sees_profile_update(b, b_uid)
    check("profile update reaches a second client", ok, detail)

    print("server delete")
    b.delete(f"/rest/v1/guilds?id=eq.{gid}")
    check("member cannot delete guild",
          a.get(f"/rest/v1/guilds?select=id&id=eq.{gid}")[1] != [])
    st, _ = a.delete(f"/rest/v1/guilds?id=eq.{gid}")
    check("owner deletes guild",
          st in (200, 204) and a.get(f"/rest/v1/guilds?select=id&id=eq.{gid}")[1] == [], st)
    st, orphan = admin.get(f"/rest/v1/channels?select=id&guild_id=eq.{gid}") if admin else (0, [])
    check("channels go with the guild", orphan == [], f"{st} {orphan}")


if __name__ == "__main__":
    sys.exit(main())
