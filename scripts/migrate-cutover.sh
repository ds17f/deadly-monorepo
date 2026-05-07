#!/usr/bin/env bash
#
# DO -> Hetzner cutover orchestrator. Implements Phase 2 and (optionally)
# Phase 3 of MIGRATION.md. Run from the repo root on a laptop that can
# SSH to deploy@$DO_IP and deploy@$HZ_IP.
#
# Usage:
#   scripts/migrate-cutover.sh --do-ip <ip> --hz-ip <ip> [--flip-dns]
#   scripts/migrate-cutover.sh --do-ip <ip> --rollback
#
# Flags:
#   --do-ip <ip>    DigitalOcean prod IP (required)
#   --hz-ip <ip>    Hetzner prod IP (required unless --rollback)
#   --flip-dns      After Phase 2 + soak, also do Phase 3 (PATCH GoDaddy DNS)
#   --rollback      Revert DO to its pre-cutover state (Caddyfile + API)
#
# The script pauses for confirmation at:
#   - Start (preflight summary)
#   - End of Phase 2 (soak window — manually verify, then continue/rollback)
#   - Before Phase 3 DNS flip (if --flip-dns)

set -euo pipefail

# ---------------------------------------------------------------- args ----

DO_IP=""
HZ_IP=""
FLIP_DNS=false
ROLLBACK=false

while [ $# -gt 0 ]; do
  case "$1" in
    --do-ip) DO_IP="$2"; shift 2 ;;
    --hz-ip) HZ_IP="$2"; shift 2 ;;
    --flip-dns) FLIP_DNS=true; shift ;;
    --rollback) ROLLBACK=true; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

if [ -z "$DO_IP" ]; then echo "error: --do-ip required" >&2; exit 2; fi
if ! $ROLLBACK && [ -z "$HZ_IP" ]; then echo "error: --hz-ip required" >&2; exit 2; fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOMAIN="thedeadly.app"
SITE_ADDRESS="$DOMAIN"
SHARE_ADDRESS="share.$DOMAIN"

DO_SSH="deploy@$DO_IP"
HZ_SSH="deploy@$HZ_IP"

SSH_KEY="${SSH_KEY:-$REPO_ROOT/ssh-key-2026-03-15.key}"
if [ ! -f "$SSH_KEY" ]; then
  echo "error: SSH key not found at $SSH_KEY. Set SSH_KEY env var to override." >&2
  exit 1
fi
SSH_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o BatchMode=yes)
SCP_OPTS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new)

# -------------------------------------------------------------- helpers ---

if [ -t 1 ]; then
  C_HEAD=$'\033[1;36m'; C_OK=$'\033[1;32m'; C_WARN=$'\033[1;33m'; C_RST=$'\033[0m'
else
  C_HEAD=""; C_OK=""; C_WARN=""; C_RST=""
fi

phase() { echo; echo "${C_HEAD}=== $* ===${C_RST}"; }
ok()    { echo "${C_OK}✓${C_RST} $*"; }
warn()  { echo "${C_WARN}!${C_RST} $*"; }

confirm() {
  local prompt="$1" reply
  read -r -p "$prompt [y/N] " reply
  [[ "$reply" =~ ^[Yy]$ ]]
}

ssh_do() { ssh "${SSH_OPTS[@]}" "$DO_SSH" "$@"; }
ssh_hz() { ssh "${SSH_OPTS[@]}" "$HZ_SSH" "$@"; }
scp_()   { scp "${SCP_OPTS[@]}" "$@"; }

curl_resolve_health() {
  local ip="$1"
  curl -fsS --max-time 10 --resolve "${SITE_ADDRESS}:443:${ip}" \
    "https://${SITE_ADDRESS}/api/health" -o /dev/null
}

# -------------------------------------------------------------- phases ---

preflight() {
  phase "Preflight"

  echo "DO: $DO_IP"
  echo "HZ: $HZ_IP"
  echo "Mode: $($ROLLBACK && echo ROLLBACK || ($FLIP_DNS && echo 'CUTOVER + DNS FLIP' || echo 'CUTOVER only'))"

  # TTL must be 600
  local ttl
  ttl=$(dig +noall +answer "$SITE_ADDRESS" A | awk '{print $2}' | head -1)
  if [ "${ttl:-9999}" -gt 600 ]; then
    warn "DNS TTL is ${ttl}s — should be ≤600. Continue at your own risk."
    confirm "Proceed anyway?" || exit 1
  else
    ok "DNS TTL: ${ttl}s"
  fi

  if ! $ROLLBACK; then
    if curl_resolve_health "$HZ_IP"; then
      ok "HZ /api/health responds (via --resolve)"
    else
      echo "error: HZ /api/health failed. Aborting." >&2; exit 1
    fi
  fi

  if curl_resolve_health "$DO_IP"; then
    ok "DO /api/health responds (via --resolve)"
  else
    warn "DO /api/health did not respond. (Expected if API is already stopped.)"
  fi

  echo
  if ! confirm "Continue?"; then exit 0; fi
}

phase_2_1_stop_do_api() {
  phase "2.1 — stop DO API"
  ssh_do 'cd /opt/deadly && docker compose stop api'
  ok "DO API stopped"
}

phase_2_2_snapshot_dbs() {
  phase "2.2 — snapshot DO DBs"
  ssh_do 'cd /opt/deadly && \
    sqlite3 api-data/users.db ".backup /tmp/users.db" && \
    sqlite3 api-data/analytics.db ".backup /tmp/analytics.db" && \
    ls -la /tmp/users.db /tmp/analytics.db'
  ok "snapshots taken on DO"
}

phase_2_3_ship_dbs() {
  phase "2.3 — ship DO snapshots → local → HZ"
  local stage="${REPO_ROOT}/.secrets/cutover-dbs"
  mkdir -p "$stage"
  scp_ "$DO_SSH:/tmp/users.db" "$stage/users.db"
  scp_ "$DO_SSH:/tmp/analytics.db" "$stage/analytics.db"
  ok "pulled to $stage"

  scp_ "$stage/users.db" "$HZ_SSH:/tmp/users.db"
  scp_ "$stage/analytics.db" "$HZ_SSH:/tmp/analytics.db"
  ssh_hz 'sudo install -o deploy -g deploy -m 0644 /tmp/users.db /opt/deadly/api-data/users.db && \
          sudo install -o deploy -g deploy -m 0644 /tmp/analytics.db /opt/deadly/api-data/analytics.db && \
          rm /tmp/users.db /tmp/analytics.db'
  ok "shipped to HZ /opt/deadly/api-data/"
}

phase_2_4_restart_hz_api() {
  phase "2.4 — restart HZ API to load fresh DBs"
  ssh_hz 'cd /opt/deadly && docker compose restart api'
  for i in $(seq 1 12); do
    if curl_resolve_health "$HZ_IP"; then
      ok "HZ /api/health green (attempt $i)"
      return
    fi
    sleep 2
  done
  echo "error: HZ /api/health did not come back after restart" >&2
  exit 1
}

phase_2_5_2_6_swap_do_caddy() {
  phase "2.5 + 2.6 — swap DO Caddyfile to cutover variant"

  # Render cutover Caddyfile with HZ_IP substituted.
  local rendered="${REPO_ROOT}/.secrets/Caddyfile.cutover.rendered"
  HETZNER_IP="$HZ_IP" envsubst '${HETZNER_IP}' \
    < "${REPO_ROOT}/caddy/Caddyfile.cutover" > "$rendered"
  ok "rendered cutover Caddyfile (HETZNER_IP=$HZ_IP)"

  # Backup original from inside the running container, then ship + swap.
  ssh_do 'cd /opt/deadly && docker compose exec -T caddy cat /etc/caddy/Caddyfile > Caddyfile.original.bak'
  ok "backed up DO's original Caddyfile to /opt/deadly/Caddyfile.original.bak"

  scp_ "$rendered" "$DO_SSH:/opt/deadly/Caddyfile.cutover.rendered"
  ssh_do 'cd /opt/deadly && docker compose cp Caddyfile.cutover.rendered caddy:/etc/caddy/Caddyfile'
  ssh_do 'cd /opt/deadly && docker compose exec -T caddy caddy validate --config /etc/caddy/Caddyfile'
  ok "validated"

  ssh_do 'cd /opt/deadly && docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile'
  ok "reloaded — DO Caddy now proxies API/WS to HZ"

  # Verify proxy works through real DNS (still pointing at DO).
  if curl -fsS --max-time 10 "https://${SITE_ADDRESS}/api/health" -o /dev/null; then
    ok "https://${SITE_ADDRESS}/api/health (real DNS → DO → HZ) returns 200"
  else
    warn "proxy verification failed — investigate before continuing"
  fi
}

phase_2_7_soak() {
  phase "2.7 — soak window"
  echo "Test in browser: https://${SITE_ADDRESS}"
  echo "  - log in (full OAuth round-trip via DO → HZ)"
  echo "  - favorite a show, refresh, confirm it persists"
  echo "  - sample analytics endpoints"
  echo
  echo "Spot-check writes landed on HZ:"
  echo "  ssh $HZ_SSH 'sqlite3 /opt/deadly/api-data/users.db \\"
  echo "    \"SELECT * FROM favorite_shows ORDER BY added_at DESC LIMIT 5\"'"
  echo
  while true; do
    read -r -p "Continue to DNS flip [c], rollback [r], or wait [w]? " reply
    case "$reply" in
      c|C) return 0 ;;
      r|R) rollback; exit 0 ;;
      w|W) echo "(soaking — re-prompt when ready)" ;;
      *) ;;
    esac
  done
}

phase_3_flip_dns() {
  phase "3.1 + 3.2 — flip DNS via GoDaddy"

  if ! $FLIP_DNS; then
    echo "Skipping DNS flip (--flip-dns not specified)."
    echo "When ready, re-run with --flip-dns, or run the Web - Deploy workflow with update_dns=true."
    return 0
  fi

  local key_file="${REPO_ROOT}/.secrets/godaddy-key.txt"
  if [ ! -f "$key_file" ]; then
    echo "error: $key_file not found (expected KEY:SECRET)" >&2; exit 1
  fi
  local godaddy_pair
  godaddy_pair=$(cat "$key_file")

  patch_record() {
    local fqdn="$1" name current
    if [ "$fqdn" = "$DOMAIN" ]; then name="@"; else name="${fqdn%.$DOMAIN}"; fi

    current=$(curl -fsS \
      -H "Authorization: sso-key $godaddy_pair" \
      "https://api.godaddy.com/v1/domains/$DOMAIN/records/A/$name" \
      | jq -r '.[0].data // empty')
    if [ "$current" = "$HZ_IP" ]; then
      ok "$fqdn already points to $HZ_IP"
      return
    fi
    echo "  $fqdn: $current → $HZ_IP"
    curl -fsS -X PUT \
      -H "Authorization: sso-key $godaddy_pair" \
      -H "Content-Type: application/json" \
      -d "[{\"data\":\"$HZ_IP\",\"ttl\":600}]" \
      "https://api.godaddy.com/v1/domains/$DOMAIN/records/A/$name" > /dev/null
    ok "$fqdn PATCHed"
  }

  patch_record "$SITE_ADDRESS"
  patch_record "$SHARE_ADDRESS"

  echo
  echo "Polling dig @1.1.1.1 for $SITE_ADDRESS → $HZ_IP (up to ~700s)"
  local deadline=$(( $(date +%s) + 700 )) resolved
  while [ "$(date +%s)" -lt "$deadline" ]; do
    resolved=$(dig @1.1.1.1 +short "$SITE_ADDRESS" A | head -1)
    if [ "$resolved" = "$HZ_IP" ]; then
      ok "1.1.1.1 now resolves to $HZ_IP"
      return 0
    fi
    echo "  resolves to '${resolved:-<empty>}', waiting 15s..."
    sleep 15
  done
  warn "DNS did not propagate to 1.1.1.1 within 700s — PATCH succeeded, propagation continues"
}

rollback() {
  phase "Rollback — restore DO to pre-cutover state"
  ssh_do 'cd /opt/deadly && [ -f Caddyfile.original.bak ]' || {
    echo "error: /opt/deadly/Caddyfile.original.bak not found on DO. Cannot rollback automatically." >&2
    exit 1
  }
  ssh_do 'cd /opt/deadly && docker compose cp Caddyfile.original.bak caddy:/etc/caddy/Caddyfile'
  ssh_do 'cd /opt/deadly && docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile'
  ssh_do 'cd /opt/deadly && docker compose start api'
  ok "DO Caddy restored, DO API started"
  for i in $(seq 1 12); do
    if curl_resolve_health "$DO_IP"; then
      ok "DO /api/health green"
      return
    fi
    sleep 2
  done
  warn "DO /api/health did not come back — investigate"
}

summary() {
  phase "Summary"
  if $ROLLBACK; then
    echo "Rolled back. DO is serving as before."
    echo "Hetzner is untouched (HZ DBs may contain Phase 1 test data; harmless)."
  else
    echo "Cutover complete."
    echo "  DO Caddy is proxying API/WS to HZ at $HZ_IP."
    if $FLIP_DNS; then
      echo "  DNS now points at HZ. Wait ~600s for stragglers; then proceed to Phase 4 (T+24h)."
    else
      echo "  DNS still points at DO. Re-run with --flip-dns when ready, or use the Web - Deploy workflow with update_dns=true."
    fi
  fi
}

# ----------------------------------------------------------------- main ---

if $ROLLBACK; then
  preflight
  rollback
  summary
  exit 0
fi

preflight
phase_2_1_stop_do_api
phase_2_2_snapshot_dbs
phase_2_3_ship_dbs
phase_2_4_restart_hz_api
phase_2_5_2_6_swap_do_caddy
phase_2_7_soak
phase_3_flip_dns
summary
