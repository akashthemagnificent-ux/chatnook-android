#!/usr/bin/env python3
"""Run SQL against the live ChatNook database.

The direct host (db.<ref>.supabase.co) is IPv6-only and unreachable from this machine,
so this always goes through the session pooler in ap-south-1.

Usage:
  python3 tools/db.py supabase/migrations/0003_attachments.sql
  python3 tools/db.py -c "select count(*) from public.guilds"

Password: ~/.chatnook/db.password, or $CHATNOOK_DB_PASSWORD.
Requires: pip install --user --break-system-packages pg8000
"""
import os
import sys

import pg8000.native

PROJECT_REF = "rwfedxfmffgmdirbcidt"
POOLER_HOST = "aws-0-ap-south-1.pooler.supabase.com"
POOLER_USER = f"postgres.{PROJECT_REF}"


def password():
    env = os.environ.get("CHATNOOK_DB_PASSWORD")
    if env:
        return env.strip()
    path = os.path.expanduser("~/.chatnook/db.password")
    return open(path).read().strip()


def connect():
    return pg8000.native.Connection(
        user=POOLER_USER,
        password=password(),
        host=POOLER_HOST,
        port=5432,
        database="postgres",
        timeout=30,
    )


def statements(sql):
    """Split on top-level semicolons, leaving $$ ... $$ bodies and strings intact."""
    out, buf, i = [], [], 0
    in_dollar = in_quote = False
    while i < len(sql):
        # Comments must go before quote tracking: an apostrophe in prose ("Discord's CDN")
        # otherwise flips the state and every later statement is swallowed.
        if not in_quote and not in_dollar and sql.startswith("--", i):
            newline = sql.find("\n", i)
            i = len(sql) if newline < 0 else newline
            continue
        if not in_quote and sql.startswith("$$", i):
            in_dollar = not in_dollar
            buf.append("$$")
            i += 2
            continue
        ch = sql[i]
        if not in_dollar and ch == "'":
            in_quote = not in_quote
        if ch == ";" and not in_dollar and not in_quote:
            text = "".join(buf).strip()
            if text:
                out.append(text)
            buf = []
        else:
            buf.append(ch)
        i += 1
    tail = "".join(buf).strip()
    if tail:
        out.append(tail)
    return out


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2

    if args[0] == "-c":
        sql, echo = args[1], False
    else:
        sql, echo = open(args[0]).read(), True

    conn = connect()
    try:
        for n, stmt in enumerate(statements(sql), 1):
            label = " ".join(stmt.split())[:70]
            try:
                rows = conn.run(stmt)
            except Exception as e:
                print(f"[{n}] FAILED: {label}\n     {e}")
                return 1
            if echo:
                print(f"[{n}] ok: {label}")
            elif rows:
                for row in rows:
                    print(row)
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
