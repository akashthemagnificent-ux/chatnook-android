# Backup and restore

The free plan has no automatic backups and no point-in-time recovery, so this is the
only copy of the data that survives a bad migration or an accidental delete.

## What runs

`.github/workflows/backup.yml` runs every day at **03:40 UTC** (and on demand from the
Actions tab). It:

1. `pg_dump`s the whole database (schema, data, auth users) into `db.sql`.
2. Downloads every stored file (avatars, banners, emoji, message attachments).
3. Packs both plus a `manifest.txt` into `chatnook-backup-<date>.tar.gz`.
4. Encrypts it with AES-256 (`BACKUP_PASSPHRASE`) and uploads it as a workflow artifact,
   kept for **90 days**.
5. Turns the run red when the database or storage passes 80% of the free plan.

Artifacts of a public repository can be downloaded by anyone, which is why the archive
is encrypted before upload.

## The passphrase

- GitHub: repository secret `BACKUP_PASSPHRASE`.
- This machine: `~/.chatnook/backup.passphrase` (mode 600).

Both hold the same value. If the passphrase is ever lost, old archives cannot be
decrypted — copy it somewhere safe now.

## Restore

```bash
# 1. Fetch the newest artifact
gh run list --workflow=backup.yml --limit 1
gh run download <run-id> -n chatnook-backup-<run-id> -D /tmp/restore

# 2. Decrypt and unpack
cd /tmp/restore
openssl enc -d -aes-256-cbc -pbkdf2 \
  -in chatnook-backup-<date>.tar.gz.enc \
  -out backup.tar.gz -pass file:$HOME/.chatnook/backup.passphrase
tar xzf backup.tar.gz

# 3. Put the database back (this replaces existing tables)
PGPASSWORD='<db password>' psql \
  -h aws-0-ap-south-1.pooler.supabase.com -p 5432 \
  -U postgres.rwfedxfmffgmdirbcidt -d postgres -f db.sql

# 4. Put the files back (needs the service-role key)
export SUPABASE_URL=https://rwfedxfmffgmdirbcidt.supabase.co
export SERVICE_ROLE_KEY='<service role key>'
find storage -type f | while read -r f; do
  bucket=${f#storage/}; bucket=${bucket%%/*}
  path=${f#storage/$bucket/}
  curl -sS -X POST "$SUPABASE_URL/storage/v1/object/$bucket/$path" \
    -H "apikey: $SERVICE_ROLE_KEY" \
    -H "Authorization: Bearer $SERVICE_ROLE_KEY" \
    -H "Content-Type: $(file --mime-type -b "$f")" \
    --data-binary @"$f" >/dev/null
done
```

Restoring into the live project drops and recreates the tables, so do it during a quiet
moment and sign in again afterwards.

## What is not covered

- **Realtime, Edge Functions and storage policies** are configuration, not data. The
  migration files in `supabase/migrations/` are the source of truth; re-run them on a
  new project before restoring.
- **Secrets** (push webhook secret, service account) live in Vault and the Edge Function
  settings, not in the dump.
- The restore has been rehearsed by decrypting the artifact and checking its contents;
  a full restore into a scratch project has not been run yet.
