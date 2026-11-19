#!/usr/bin/env sh
# Backs up every service database and the statement storage of the running stack.
#
#   scripts/dev/backup.sh                 # writes backups/<UTC timestamp>/
#   scripts/dev/backup.sh path/to/dir     # writes to a directory of your choice
#
# Output (git-ignored: backups/ and *.pgdump, *.tar.gz are never committed):
#   <dir>/postgres/<database>.pgdump   pg_dump custom format, one per service database
#   <dir>/statements.tar.gz            contents of the statement storage
#   <dir>/MANIFEST.txt                 when and from which project the backup was taken
#
# The stack keeps running during a backup; each dump is a consistent snapshot of one database.
# Restore with scripts/dev/restore.sh.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

DATABASES="customer_service_db account_service_db transaction_service_db case_service_db
fraud_detection_db notification_db statement_service_db transaction_aggregation_db"

PG_USER="$(sed -n 's/^POSTGRES_USER=//p' .env 2>/dev/null | tail -n 1)"
PG_USER="${PG_USER:-app_user}"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="${1:-backups/$STAMP}"
mkdir -p "$OUT/postgres"

if ! docker compose ps --status running --services 2>/dev/null | grep -qx postgres; then
  echo "backup: the postgres service is not running. Start the stack first." >&2
  exit 1
fi

for db in $DATABASES; do
  echo "backup: dumping $db"
  docker compose exec -T postgres pg_dump -U "$PG_USER" -Fc -d "$db" > "$OUT/postgres/$db.pgdump"
done

echo "backup: archiving statement storage"
docker compose exec -T statement-service tar -C /var/lib/capitec/statements -czf - . > "$OUT/statements.tar.gz"

{
  echo "created_utc=$STAMP"
  echo "project=$(basename "$ROOT")"
  echo "git_commit=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
  echo "databases=$(echo $DATABASES | tr ' ' ',')"
} > "$OUT/MANIFEST.txt"

echo "backup: done -> $OUT"
ls -lh "$OUT" "$OUT/postgres" | sed 's/^/  /'
