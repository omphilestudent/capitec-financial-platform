#!/usr/bin/env sh
# Restores a backup made by scripts/dev/backup.sh into the running stack.
#
#   scripts/dev/restore.sh backups/<timestamp> --yes
#
# DESTRUCTIVE: every service database in the backup is dropped and recreated from the dump,
# and the statement storage is replaced by the archive. Nothing is restored unless --yes is
# given. Take a fresh backup first if the current data matters.
#
# Restore runs in this order: make sure the databases exist (db-init), restore each database
# with pg_restore --clean, then replace the statement storage. Restart the services afterwards
# so they reconnect: docker compose restart
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

SRC="${1:-}"
if [ -z "$SRC" ] || [ "${2:-}" != "--yes" ]; then
  echo "usage: scripts/dev/restore.sh <backup-dir> --yes" >&2
  echo "This replaces the data of the running stack with the backup. Add --yes to confirm." >&2
  exit 1
fi
[ -d "$SRC/postgres" ] || { echo "restore: $SRC/postgres not found" >&2; exit 1; }
[ -f "$SRC/statements.tar.gz" ] || { echo "restore: $SRC/statements.tar.gz not found" >&2; exit 1; }

PG_USER="$(sed -n 's/^POSTGRES_USER=//p' .env 2>/dev/null | tail -n 1)"
PG_USER="${PG_USER:-app_user}"

echo "restore: ensuring service databases exist"
docker compose up -d postgres --wait
docker compose run --rm db-init >/dev/null

for dump in "$SRC"/postgres/*.pgdump; do
  db="$(basename "$dump" .pgdump)"
  echo "restore: $db"
  docker compose exec -T postgres pg_restore -U "$PG_USER" -d "$db" --clean --if-exists --no-owner < "$dump"
done

echo "restore: replacing statement storage"
docker compose exec -T statement-service sh -c \
  'find /var/lib/capitec/statements -mindepth 1 -delete && tar -C /var/lib/capitec/statements -xzf -' \
  < "$SRC/statements.tar.gz"

echo "restore: done. Restart the services to reconnect: docker compose restart"
