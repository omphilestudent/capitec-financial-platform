#!/usr/bin/env sh
# First-time and every-time start of the whole platform, in one command.
#
#   cp .env.example .env        # then replace every "replace-..." value (see .env.example)
#   scripts/dev/bootstrap.sh
#
# It checks the prerequisites, creates the truststore if it is missing, then runs
#   docker compose up -d --build --wait
# which starts the services in dependency order, applies Flyway migrations, seeds the
# synthetic data (the "seeder" one-shot container) and waits until every service is healthy.
# Running it again is safe: existing containers, volumes and data are kept, and seeding only adds
# what is missing.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

fail() { echo "bootstrap: $1" >&2; exit 1; }
# envval NAME DEFAULT: value of NAME from .env, or DEFAULT when it is unset.
envval() { v="$(sed -n "s/^$1=//p" .env 2>/dev/null | tail -n 1)"; echo "${v:-$2}"; }

command -v docker >/dev/null 2>&1 || fail "Docker is not installed. Install Docker Desktop first."
docker info >/dev/null 2>&1 || fail "Docker is not running. Start Docker Desktop and retry."

[ -f .env ] || fail "no .env file. Run: cp .env.example .env   (then replace the placeholder values)"

if grep -Eq '^[A-Z_]+=replace-' .env; then
  names="$(grep -E '^[A-Z_]+=replace-' .env | cut -d= -f1 | tr '\n' ' ')"
  fail "placeholder values still set in .env: $names. Replace them (openssl rand -hex 32 makes a good secret)."
fi

TRUSTSTORE="$(envval TRUSTSTORE_FILE ./.local/truststore.jks)"
case "$TRUSTSTORE" in
  /*) TRUSTSTORE_ABS="$TRUSTSTORE" ;;
  *) TRUSTSTORE_ABS="$ROOT/${TRUSTSTORE#./}" ;;
esac
if [ ! -f "$TRUSTSTORE_ABS" ]; then
  echo "bootstrap: truststore not found at $TRUSTSTORE_ABS, generating it"
  "$ROOT/scripts/dev/generate-truststore.sh"
fi

echo "bootstrap: starting the platform (first build takes several minutes)"
docker compose up -d --build --wait --wait-timeout 1800

echo
docker compose ps --format 'table {{.Service}}\t{{.State}}\t{{.Health}}\t{{.Ports}}'
echo
echo "Frontend:    http://localhost:$(envval FRONTEND_HOST_PORT 5173)"
echo "API gateway: http://localhost:$(envval API_GATEWAY_HOST_PORT 8000)/api/v1/customers"
