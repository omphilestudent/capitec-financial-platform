#!/usr/bin/env sh
# Creates the local JKS truststore the service images are built with.
#
# The truststore is only needed so Maven inside the Docker build can download dependencies
# through a TLS-inspecting proxy (for example a corporate gateway). It starts from the Java
# default CA bundle, so it works without any corporate certificate, and it can add your
# organisation's root CA certificate(s) as PEM files.
#
# Usage:
#   scripts/dev/generate-truststore.sh                     # default CA bundle only
#   scripts/dev/generate-truststore.sh path/to/corp-ca.pem  # also trust a corporate root CA
#
# Output: the file named by TRUSTSTORE_FILE in .env (default .local/truststore.jks), mode 600.
# The output lives under .local/, which is git-ignored. Never commit it.
#
# Only Docker is needed: keytool runs inside the eclipse-temurin image, not on the host.
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not running. Start Docker Desktop and retry." >&2
  exit 1
fi

# TRUSTSTORE_FILE from the environment wins, then .env, then the default.
OUT="${TRUSTSTORE_FILE:-$(sed -n 's/^TRUSTSTORE_FILE=//p' .env 2>/dev/null | tail -n 1)}"
OUT="${OUT:-./.local/truststore.jks}"
case "$OUT" in
  /*) OUT_ABS="$OUT" ;;
  *) OUT_ABS="$ROOT/${OUT#./}" ;;
esac
OUT_DIR="$(dirname "$OUT_ABS")"

# Work inside the repository (git-ignored), so Docker Desktop can share the directory.
WORK="$ROOT/.local/truststore-build"
rm -rf "$WORK"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

count=0
for pem in "$@"; do
  if [ ! -f "$pem" ]; then
    echo "CA certificate not found: $pem" >&2
    exit 1
  fi
  count=$((count + 1))
  cp "$pem" "$WORK/corp-ca-$count.pem"
done

# The JDK's default bundle uses the password "changeit". The Dockerfiles default to the same.
docker run --rm -v "$WORK:/work" eclipse-temurin:21-jre-alpine sh -c '
  set -eu
  cp "$JAVA_HOME/lib/security/cacerts" /work/truststore.jks
  chmod 600 /work/truststore.jks
  for pem in /work/corp-ca-*.pem; do
    [ -f "$pem" ] || continue
    alias="$(basename "$pem" .pem)"
    keytool -importcert -noprompt -trustcacerts -alias "$alias" -file "$pem" \
      -keystore /work/truststore.jks -storepass changeit >/dev/null
  done
  keytool -list -keystore /work/truststore.jks -storepass changeit >/dev/null
'

mkdir -p "$OUT_DIR"
mv "$WORK/truststore.jks" "$OUT_ABS"
chmod 600 "$OUT_ABS"

echo "Truststore written: $OUT_ABS"
echo "  corporate CA certificates added: $count"
echo "  password: changeit (only used to read the file during image builds)"
echo "  git-ignored: yes (.local/ and *.jks are in .gitignore)"
