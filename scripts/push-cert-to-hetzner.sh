#!/usr/bin/env bash
#
# Push the staged LE cert from .secrets/le-cert/ into the Hetzner Caddy
# data volume. Phase 1.3 of the migration. Run AFTER provisioning the HZ
# box (1.2) and BEFORE the first deploy (1.4) — otherwise Caddy starts
# with no cert, attempts ACME against DNS that still points at DO, and
# burns Let's Encrypt rate-limit attempts.
#
# Operates directly on the named volume (deadly_caddy_data) via a
# throwaway busybox container, so Caddy doesn't need to be running.
#
# Usage:
#   scripts/push-cert-to-hetzner.sh <HZ_IP>
#   HZ_IP=1.2.3.4 scripts/push-cert-to-hetzner.sh

set -euo pipefail

HZ_IP="${1:-${HZ_IP:-}}"
if [ -z "$HZ_IP" ]; then
  echo "error: HZ_IP not provided. Pass as \$1 or set HZ_IP env var." >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARBALL="$REPO_ROOT/.secrets/le-cert/thedeadly.app.tar.gz"
HOSTNAME="thedeadly.app"
VOLUME="deadly_caddy_data"
CERT_BASE_IN_VOLUME="caddy/certificates/acme-v02.api.letsencrypt.org-directory"

SSH_KEY="${SSH_KEY:-$REPO_ROOT/ssh-key-2026-03-15.key}"
if [ ! -f "$SSH_KEY" ]; then
  echo "error: SSH key not found at $SSH_KEY. Set SSH_KEY env var to override." >&2
  exit 1
fi
SSH_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new)

if [ ! -f "$TARBALL" ]; then
  echo "error: $TARBALL not found. Run scripts/pull-cert-from-do.sh first." >&2
  exit 1
fi

echo "==> Verifying staged cert is still valid"
TMP_CRT="$(mktemp)"
trap 'rm -f "$TMP_CRT"' EXIT
tar -xzOf "$TARBALL" "$HOSTNAME/$HOSTNAME.crt" > "$TMP_CRT"
if ! openssl x509 -in "$TMP_CRT" -noout -checkend 0 >/dev/null; then
  echo "error: staged cert has expired. Re-run pull-cert-from-do.sh." >&2
  exit 1
fi
DAYS_LEFT=$(( ( $(date -d "$(openssl x509 -in "$TMP_CRT" -noout -enddate | cut -d= -f2)" +%s) - $(date +%s) ) / 86400 ))
echo "    cert valid for $DAYS_LEFT more days"
if [ "$DAYS_LEFT" -lt 30 ]; then
  echo "warning: cert has <30 days left. Consider re-pulling from DO after forcing renewal there." >&2
fi

echo "==> Ensuring volume $VOLUME exists on $HZ_IP"
ssh "${SSH_OPTS[@]}" "deploy@$HZ_IP" "docker volume create $VOLUME >/dev/null"

echo "==> Streaming tarball into $VOLUME:/$CERT_BASE_IN_VOLUME/"
# busybox tar can't create parent dirs from -C, so mkdir -p first.
ssh "${SSH_OPTS[@]}" "deploy@$HZ_IP" "docker run --rm -i -v $VOLUME:/data busybox sh -c '
  mkdir -p /data/$CERT_BASE_IN_VOLUME &&
  tar -xzf - -C /data/$CERT_BASE_IN_VOLUME
'" < "$TARBALL"

echo "==> Verifying cert is in the volume"
ssh "${SSH_OPTS[@]}" "deploy@$HZ_IP" "docker run --rm -v $VOLUME:/data busybox ls /data/$CERT_BASE_IN_VOLUME/$HOSTNAME/"

echo
echo "==> Done. Caddy will pick up the cert on its first start (Phase 1.4 deploy)."
