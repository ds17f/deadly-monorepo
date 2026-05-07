#!/usr/bin/env bash
#
# Pull the live Let's Encrypt certs + keys for thedeadly.app (and the
# share/www subdomains) from the DO Caddy container into .secrets/le-cert/.
# Phase 0.2 of the Hetzner migration.
#
# Usage:
#   scripts/pull-cert-from-do.sh <DO_IP> [hostname ...]
#   DO_IP=1.2.3.4 scripts/pull-cert-from-do.sh [hostname ...]
#
# Default hostnames cover everything Caddy serves on prod: thedeadly.app,
# share.thedeadly.app, www.thedeadly.app. Pass an explicit list to override.
#
# Output (per hostname):
#   .secrets/le-cert/<host>.tar.gz   (the cert dir, gzipped)
#   .secrets/le-cert/<host>.crt      (extracted, for inspection)
#
# The tarballs are what Phase 1.3 (push-cert-to-hetzner.sh) ships up.

set -euo pipefail

DO_IP="${1:-${DO_IP:-}}"
if [ -z "$DO_IP" ]; then
  echo "error: DO_IP not provided. Pass as \$1 or set DO_IP env var." >&2
  exit 2
fi
shift || true

HOSTNAMES=("$@")
if [ ${#HOSTNAMES[@]} -eq 0 ]; then
  HOSTNAMES=(thedeadly.app share.thedeadly.app www.thedeadly.app)
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_ROOT/.secrets/le-cert"
CERT_BASE="/data/caddy/certificates/acme-v02.api.letsencrypt.org-directory"

SSH_KEY="${SSH_KEY:-$REPO_ROOT/ssh-key-2026-03-15.key}"
if [ ! -f "$SSH_KEY" ]; then
  echo "error: SSH key not found at $SSH_KEY. Set SSH_KEY env var to override." >&2
  exit 1
fi
SSH_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new)

mkdir -p "$OUT_DIR"

for HOSTNAME in "${HOSTNAMES[@]}"; do
  TARBALL="$OUT_DIR/$HOSTNAME.tar.gz"
  CRT="$OUT_DIR/$HOSTNAME.crt"

  echo
  echo "=== $HOSTNAME ==="
  echo "==> Verifying cert exists on DO Caddy at $DO_IP"
  ssh "${SSH_OPTS[@]}" "deploy@$DO_IP" "cd /opt/deadly && docker compose exec -T caddy ls $CERT_BASE/$HOSTNAME/" \
    || { echo "error: cert dir for $HOSTNAME not found on DO." >&2; exit 1; }

  echo "==> Pulling cert tarball into $TARBALL"
  ssh "${SSH_OPTS[@]}" "deploy@$DO_IP" "cd /opt/deadly && docker compose exec -T caddy tar -czf - -C $CERT_BASE $HOSTNAME" \
    > "$TARBALL"
  chmod 600 "$TARBALL"

  echo "==> Extracting .crt for inspection"
  tar -xzOf "$TARBALL" "$HOSTNAME/$HOSTNAME.crt" > "$CRT"

  echo "==> Cert details:"
  openssl x509 -in "$CRT" -noout -subject -issuer -dates
done

echo
echo "==> Done. Staged ${#HOSTNAMES[@]} cert(s) in $OUT_DIR/"
echo "    .secrets/ is gitignored. Delete this directory after Phase 4 decommission."
