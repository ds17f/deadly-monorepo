#!/usr/bin/env bash
#
# Pull the live Let's Encrypt cert + key for thedeadly.app from the DO
# Caddy container into .secrets/le-cert/. Phase 0.2 of the Hetzner migration.
#
# Usage:
#   scripts/pull-cert-from-do.sh <DO_IP>
#   DO_IP=1.2.3.4 scripts/pull-cert-from-do.sh
#
# Output:
#   .secrets/le-cert/thedeadly.app.tar.gz   (the cert dir, gzipped)
#   .secrets/le-cert/thedeadly.app.crt      (extracted, for inspection)
#
# The tarball is what Phase 1.3 (push-cert-to-hetzner.sh) ships up.
# The extracted .crt is just so we can eyeball expiry.

set -euo pipefail

DO_IP="${1:-${DO_IP:-}}"
if [ -z "$DO_IP" ]; then
  echo "error: DO_IP not provided. Pass as \$1 or set DO_IP env var." >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_ROOT/.secrets/le-cert"
TARBALL="$OUT_DIR/thedeadly.app.tar.gz"
HOSTNAME="thedeadly.app"
CERT_BASE="/data/caddy/certificates/acme-v02.api.letsencrypt.org-directory"

SSH_KEY="${SSH_KEY:-$REPO_ROOT/ssh-key-2026-03-15.key}"
if [ ! -f "$SSH_KEY" ]; then
  echo "error: SSH key not found at $SSH_KEY. Set SSH_KEY env var to override." >&2
  exit 1
fi
SSH_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new)

mkdir -p "$OUT_DIR"

echo "==> Verifying cert exists on DO Caddy at $DO_IP"
ssh "${SSH_OPTS[@]}" "deploy@$DO_IP" "cd /opt/deadly && docker compose exec -T caddy ls $CERT_BASE/$HOSTNAME/" \
  || { echo "error: cert dir not found on DO. Is Caddy running and has it acquired a cert?" >&2; exit 1; }

echo "==> Pulling cert tarball into $TARBALL"
ssh "${SSH_OPTS[@]}" "deploy@$DO_IP" "cd /opt/deadly && docker compose exec -T caddy tar -czf - -C $CERT_BASE $HOSTNAME" \
  > "$TARBALL"

echo "==> Extracting .crt for inspection"
tar -xzOf "$TARBALL" "$HOSTNAME/$HOSTNAME.crt" > "$OUT_DIR/$HOSTNAME.crt"

# Lock down permissions on the staged tarball — it contains the private key.
chmod 600 "$TARBALL"

echo "==> Cert details:"
openssl x509 -in "$OUT_DIR/$HOSTNAME.crt" -noout -subject -issuer -dates

echo
echo "==> Done. Staged at: $TARBALL"
echo "    .secrets/ is gitignored. Delete this directory after Phase 4 decommission."
