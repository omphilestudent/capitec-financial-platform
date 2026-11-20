#!/usr/bin/env sh
# Fraud demonstration for Omphile Mohlala: one suspicious transaction, followed through the real
# fraud alert and case workflow. Needs the platform running (scripts/dev/bootstrap.sh).
#
#   scripts/dev/seed-fraud-demo.sh [--amount 60000] [--currency ZAR] [--timeout 60]
#
# See scripts/dev/seed-fraud-demo.mjs for what it does, and README.md for cleanup.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

command -v node >/dev/null 2>&1 || { echo "seed-fraud-demo: Node.js 18 or later is required" >&2; exit 2; }
exec node "$ROOT/scripts/dev/seed-fraud-demo.mjs" "$@"
