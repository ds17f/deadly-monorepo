#!/usr/bin/env bash
# retry-apply.sh — Retries terraform apply across availability domains until
# the A1.Flex instance is created. OCI free tier ARM capacity is often
# exhausted, so this script cycles through all ADs on a timer.
#
# Usage:
#   ./retry-apply.sh [interval_minutes]
#
# Options:
#   interval_minutes  Time between retries (default: 5)
#
# Environment:
#   TERRAFORM         Path to terraform binary (default: terraform)
#   TF_LOG_DIR        Directory for log files (default: ./logs)
#
# Logs are written to $TF_LOG_DIR/retry-YYYY-MM-DD.log

set -euo pipefail

INTERVAL_MINUTES="${1:-5}"
INTERVAL_SECONDS=$((INTERVAL_MINUTES * 60))
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TERRAFORM="${TERRAFORM:-terraform}"
MAX_ADS=3
TF_LOG_DIR="${TF_LOG_DIR:-${SCRIPT_DIR}/logs}"
LOG_FILE="${TF_LOG_DIR}/retry-$(date '+%Y-%m-%d').log"

cd "$SCRIPT_DIR"
mkdir -p "$TF_LOG_DIR"

# ── helpers ──────────────────────────────────────────────────────────────
log() {
  local timestamp
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  local msg="[${timestamp}] $*"
  echo "$msg"
  echo "$msg" >> "$LOG_FILE"
}

separator() {
  local line="────────────────────────────────────────────────────────"
  echo "$line"
  echo "$line" >> "$LOG_FILE"
}

# ── pre-flight checks ───────────────────────────────────────────────────
if ! command -v "$TERRAFORM" &>/dev/null; then
  log "ERROR: terraform not found at '${TERRAFORM}'"
  log "Set TERRAFORM env var to the correct path (e.g. TERRAFORM=~/bin/terraform)"
  exit 1
fi

if [ ! -f terraform.tfstate ] && [ ! -f .terraform/terraform.tfstate ]; then
  log "WARNING: No state file found. Running terraform init..."
  $TERRAFORM init >> "$LOG_FILE" 2>&1
fi

# ── banner ───────────────────────────────────────────────────────────────
separator
log "OCI A1.Flex Instance — Retry Script"
separator
log "Region:          $(grep 'default.*us-' variables.tf | head -1 | sed 's/.*"\(.*\)"/\1/')"
log "Availability:    Cycling through ${MAX_ADS} ADs"
log "Retry interval:  Every ${INTERVAL_MINUTES} minutes"
log "Terraform:       $($TERRAFORM version -json 2>/dev/null | head -1 || $TERRAFORM version | head -1)"
log "Log file:        ${LOG_FILE}"
separator
log "Press Ctrl+C to stop"
echo ""

# ── retry loop ───────────────────────────────────────────────────────────
cycle=0
attempt=0
start_time=$(date +%s)

while true; do
  cycle=$((cycle + 1))
  elapsed=$(( $(date +%s) - start_time ))
  elapsed_human=$(printf '%02dh:%02dm:%02ds' $((elapsed/3600)) $((elapsed%3600/60)) $((elapsed%60)))

  separator
  log "CYCLE #${cycle}  |  Trying all ${MAX_ADS} ADs  |  Elapsed: ${elapsed_human}"
  separator

  cycle_success=false

  for ad_index in $(seq 0 $((MAX_ADS - 1))); do
    attempt=$((attempt + 1))
    log "  AD-$((ad_index + 1)) (attempt #${attempt})..."

    tf_output_file="${TF_LOG_DIR}/tf-attempt-${attempt}.log"

    if $TERRAFORM apply -auto-approve -var="availability_domain_index=${ad_index}" > "$tf_output_file" 2>&1; then
      echo ""
      separator
      log "SUCCESS — Instance created on AD-$((ad_index + 1)) after ${attempt} attempts (${elapsed_human})"
      separator

      $TERRAFORM output | tee -a "$LOG_FILE"

      echo ""
      log "Full terraform output: ${tf_output_file}"
      separator
      exit 0
    fi

    if grep -q "Out of host capacity" "$tf_output_file"; then
      log "  AD-$((ad_index + 1)): Out of capacity"
    else
      separator
      log "ERROR: Non-capacity failure on AD-$((ad_index + 1)). Check terraform output:"
      log "  ${tf_output_file}"
      separator
      tail -20 "$tf_output_file" | tee -a "$LOG_FILE"
      exit 1
    fi
  done

  log "All ${MAX_ADS} ADs exhausted. Waiting ${INTERVAL_MINUTES} minutes before next cycle..."
  echo ""

  # countdown
  remaining=$INTERVAL_SECONDS
  while [ $remaining -gt 0 ]; do
    printf "\r  Waiting... %02d:%02d remaining" $((remaining / 60)) $((remaining % 60))
    sleep 10
    remaining=$((remaining - 10))
  done
  printf "\r  Waiting... done.                    \n"
done
