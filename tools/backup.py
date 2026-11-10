#!/usr/bin/env python3
"""Dump the database and every stored file into one archive, and report quota.

Run: python3 tools/backup.py [--out DIR]
Needs: ~/.chatnook/db.password or CHATNOOK_DB_PASSWORD, and either pg_dump on PATH
       or Docker (the postgres:17 image is pulled on demand).

The archive is written unencrypted; the caller encrypts it before it leaves the
machine (see .github/workflows/backup.yml and docs/BACKUP.md). Exits 2 when the
database or storage is above 80% of the free plan, after writing the archive.
"""
import argparse
import os
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import db  # noqa: E402  (reads the pooler constants and the password)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
URL = f"https://{db.PROJECT_REF}.supabase.co"

# Free plan ceilings, from supabase.com/pricing (checked 2026-11-10).
DB_LIMIT = 500 * 1024 * 1024
STORAGE_LIMIT = 1024 * 1024 * 1024
WARN_AT = 0.80


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{n} B"
        n /= 1024


def dump_db(path):
    """pg_dump the whole database; use the server's major version for the client."""
    common = [
        "-h", db.POOLER_HOST, "-p", "5432", "-U", db.POOLER_USER,
        "-d", "postgres", "--no-owner", "--no-privileges", "--format=plain",
        # A restore replaces what is there instead of failing on existing tables.
        "--clean", "--if-exists",
    ]
    env = {**os.environ, "PGPASSWORD": db.password()}
    if shutil.which("pg_dump"):
        subprocess.run(["pg_dump", *common, "-f", path], check=True, env=env)
        return
    if not shutil.which("docker"):
        raise SystemExit("need pg_dump or docker to dump the database")

    folder = os.path.dirname(os.path.abspath(path))
    name = os.path.basename(path)
    # The client must be at least the server's major version; 17 matches the project.
    subprocess.run(
        [
            "docker", "run", "--rm", "-e", "PGPASSWORD",
            "-v", f"{folder}:/out", "postgres:17",
            "pg_dump", *common, "-f", f"/out/{name}",
        ],
        check=True,
        env=env,
    )


def download_storage(conn, folder):
    """Every object in every public bucket, mirrored under folder/bucket/name."""
    rows = conn.run("select bucket_id, name from storage.objects order by bucket_id, name")
    total, missing = 0, []
    for bucket, name in rows:
        url = f"{URL}/storage/v1/object/public/{bucket}/{urllib.parse.quote(name, safe='/')}"
        dest = os.path.join(folder, bucket, *name.split("/"))
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        try:
            urllib.request.urlretrieve(url, dest)
            total += os.path.getsize(dest)
        except (urllib.error.HTTPError, urllib.error.URLError) as e:
            missing.append(f"{bucket}/{name}: {e}")
    return len(rows), total, missing


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=os.path.join(ROOT, "build", "backup"))
    args = parser.parse_args()

    stamp = time.strftime("%Y%m%d-%H%M")
    staging = os.path.join(args.out, "staging")
    os.makedirs(staging, exist_ok=True)

    print("dumping the database…", flush=True)
    dump_db(os.path.join(staging, "db.sql"))

    conn = db.connect()
    try:
        print("downloading stored files…", flush=True)
        objects, storage_bytes, missing = download_storage(conn, os.path.join(staging, "storage"))
        db_bytes = conn.run("select pg_database_size(current_database())")[0][0]
        messages = conn.run("select count(*) from public.messages")[0][0]
        users = conn.run("select count(*) from public.profiles")[0][0]
    finally:
        conn.close()

    with open(os.path.join(staging, "manifest.txt"), "w") as f:
        f.write(f"created {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"database {db_bytes} bytes, {messages} messages, {users} profiles\n")
        f.write(f"storage {storage_bytes} bytes in {objects} objects\n")
        for line in missing:
            f.write(f"missing {line}\n")

    archive = os.path.join(args.out, f"chatnook-backup-{stamp}.tar.gz")
    with tarfile.open(archive, "w:gz") as tar:
        for name in ("db.sql", "storage", "manifest.txt"):
            tar.add(os.path.join(staging, name), arcname=name)
    shutil.rmtree(staging)

    db_pct = db_bytes / DB_LIMIT
    storage_pct = storage_bytes / STORAGE_LIMIT
    print()
    print(f"database {human(db_bytes)} of {human(DB_LIMIT)} ({db_pct:.0%}), {messages} messages")
    print(f"storage  {human(storage_bytes)} of {human(STORAGE_LIMIT)} ({storage_pct:.0%}), "
          f"{objects} files")
    print(f"archive  {archive} ({human(os.path.getsize(archive))})")
    for line in missing:
        print(f"warning: could not download {line}")
    if db_pct > WARN_AT or storage_pct > WARN_AT:
        print("::warning::backup written, but the free plan is over 80% full")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
