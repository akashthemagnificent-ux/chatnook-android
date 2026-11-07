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
    def __init__(self, token=None):
        self.token = token

    def _req(self, path, method="GET", body=None, token=None, key=None, prefer=None):
        headers = {"apikey": key or ANON, "Content-Type": "application/json"}
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
admin = Api(SERVICE) if SERVICE else None
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

    print("keyset pagination")
    batch = [{"channel_id": cid, "author_id": a_uid, "content": f"page {i}"} for i in range(60)]
    a.post("/rest/v1/messages", batch)
    st, page = a.get(f"/rest/v1/messages?select={MESSAGE_COLUMNS}&channel_id=eq.{cid}"
                     "&order=created_at.desc,id.desc&limit=50")
    check("first page full", st == 200 and len(page) == 50, f"{st} {len(page) if st == 200 else page}")
    if st == 200 and page:
        oldest = page[-1]
        iso, mid = oldest["created_at"], oldest["id"]
        st2, older = a.get(
            f"/rest/v1/messages?select={MESSAGE_COLUMNS}&channel_id=eq.{cid}"
            f"&or=(created_at.lt.{urllib.parse.quote(iso)},and(created_at.eq.{urllib.parse.quote(iso)},id.lt.{mid}))"
            "&order=created_at.desc,id.desc&limit=50")
        check("older page query", st2 == 200, f"{st2} {str(older)[:200]}")
        if st2 == 200:
            ids = {m["id"] for m in page} & {m["id"] for m in older}
            check("pages do not overlap", not ids, ids)
            check("61 messages split 50+11", len(older) == 11, len(older))

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

    print("DMs")
    st, dm = a.rpc("find_or_create_dm", {"other_user": b_uid})
    check("find_or_create_dm rpc", st == 200 and isinstance(dm, str), f"{st} {dm}")
    if st == 200:
        st, again = a.rpc("find_or_create_dm", {"other_user": b_uid})
        check("dm is reused", st == 200 and again == dm, f"{st} {again}")
        check("dm visible to both",
              a.get(f"/rest/v1/channels?select=id&id=eq.{dm}")[1] != []
              and b.get(f"/rest/v1/channels?select=id&id=eq.{dm}")[1] != [])
        c_tok, c_uid = signup()
        created_users.append(c_uid)
        check("dm hidden from third party",
              Api(c_tok).get(f"/rest/v1/channels?select=id&id=eq.{dm}")[1] == [])


if __name__ == "__main__":
    sys.exit(main())
