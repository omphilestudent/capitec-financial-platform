#!/usr/bin/env sh
# Shows the health of the platform stack: per-container CPU and memory against its limit,
# restart counts, health status, and startup durations taken from the service logs.
#
#   scripts/dev/resource-monitor.sh            # one snapshot
#   scripts/dev/resource-monitor.sh --watch    # refresh every 5 seconds until Ctrl-C
#   scripts/dev/resource-monitor.sh --startup  # startup durations only
#
# Read-only: it never starts, stops or changes a container. Needs Docker and the stack running.
set -eu

PROJECT="capitec-financial-platform"
MODE="${1:-snapshot}"

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not running." >&2
  exit 1
fi

containers() {
  docker ps -a --filter "label=com.docker.compose.project=$PROJECT" --format '{{.Names}}'
}

# Name, status, health, restart count, CPU %, memory usage / limit, for every container in the project.
table() {
  names="$(containers)"
  if [ -z "$names" ]; then
    echo "No containers found for project '$PROJECT'. Start the stack with: docker compose up -d --wait"
    return 0
  fi
  printf '%-52s %-12s %-10s %-9s %-8s %s\n' "CONTAINER" "STATE" "HEALTH" "RESTARTS" "CPU" "MEM (used / limit)"
  for name in $names; do
    state="$(docker inspect -f '{{.State.Status}}' "$name")"
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}' "$name")"
    restarts="$(docker inspect -f '{{.RestartCount}}' "$name")"
    oom="$(docker inspect -f '{{.State.OOMKilled}}' "$name")"
    cpu="-"; mem="-"
    if [ "$state" = "running" ]; then
      stats="$(docker stats --no-stream --format '{{.CPUPerc}}|{{.MemUsage}}' "$name" 2>/dev/null || true)"
      cpu="${stats%%|*}"
      mem="${stats#*|}"
    fi
    flag=""
    [ "$oom" = "true" ] && flag=" [OOM-KILLED]"
    printf '%-52s %-12s %-10s %-9s %-8s %s%s\n' "$name" "$state" "$health" "$restarts" "$cpu" "$mem" "$flag"
  done
}

# Total memory of running containers, so it can be compared with the 6-8 GB local target.
total_memory() {
  docker stats --no-stream --format '{{.MemUsage}}' $(docker ps --filter "label=com.docker.compose.project=$PROJECT" -q) 2>/dev/null \
    | awk '
        function toMiB(v) {
          if (v ~ /GiB$/) { sub(/GiB$/, "", v); return v * 1024 }
          if (v ~ /MiB$/) { sub(/MiB$/, "", v); return v + 0 }
          if (v ~ /KiB$/) { sub(/KiB$/, "", v); return v / 1024 }
          return 0
        }
        { split($1, parts, " / "); total += toMiB($1) }
        END { printf "Total memory in use by running containers: %.2f GiB\n", total / 1024 }'
}

# Startup duration per Spring Boot service, from the "Started X in N seconds" log line of its latest start.
startup() {
  printf '%-52s %s\n' "CONTAINER" "LAST STARTUP (Spring Boot 'Started ... in N seconds')"
  for name in $(containers); do
    line="$(docker logs "$name" 2>&1 | grep -E 'Started .* in [0-9.]+ seconds' | tail -1 || true)"
    if [ -n "$line" ]; then
      secs="$(printf '%s\n' "$line" | sed -E 's/.* in ([0-9.]+) seconds.*/\1/')"
      printf '%-52s %ss\n' "$name" "$secs"
    fi
  done
}

case "$MODE" in
  --watch)
    while true; do
      clear
      date '+%Y-%m-%d %H:%M:%S'
      table
      total_memory
      sleep 5
    done
    ;;
  --startup)
    startup
    ;;
  *)
    table
    total_memory
    ;;
esac
