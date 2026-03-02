#!/bin/bash
# Update the rollout status table in a GitHub release body.
#
# Usage: ./scripts/update-release-status.sh <platform> <version> <stage> <status>
#   platform: "android" | "ios"
#   version:  e.g. 2.1.0
#   stage:    "Internal Testing" | "Closed Alpha" | "Production" | "TestFlight" | "App Store"
#   status:   "deployed" | "failed"
#
# Requires: gh CLI authenticated with contents:write permission
# Set GH_TOKEN env var in CI, or use `gh auth login` locally.

set -euo pipefail

PLATFORM="${1:?Usage: $0 <platform> <version> <stage> <status>}"
VERSION="${2:?Usage: $0 <platform> <version> <stage> <status>}"
STAGE="${3:?Usage: $0 <platform> <version> <stage> <status>}"
STATUS="${4:?Usage: $0 <platform> <version> <stage> <status>}"
TAG="${PLATFORM}/v${VERSION}"
TIMESTAMP=$(date -u +"%Y-%m-%d %H:%M UTC")

case "$STATUS" in
  deployed) ICON="✅ Deployed" ;;
  failed)   ICON="❌ Failed" ;;
  *)
    echo "Error: status must be 'deployed' or 'failed', got '$STATUS'"
    exit 1
    ;;
esac

# Fetch current release body
BODY=$(gh release view "$TAG" --json body --jq '.body')

if [ -z "$BODY" ]; then
  echo "Error: could not fetch release body for $TAG"
  exit 1
fi

# Replace the matching row in the status table.
# Match: | <stage> | <anything> | <anything> |
# Replace with: | <stage> | <new status> | <timestamp> |
UPDATED=$(echo "$BODY" | perl -pe "s/\| \Q${STAGE}\E \|[^|]*\|[^|]*\|/| ${STAGE} | ${ICON} | ${TIMESTAMP} |/")

# Write back
echo "$UPDATED" > /tmp/release-body-updated.md
gh release edit "$TAG" --notes-file /tmp/release-body-updated.md
rm -f /tmp/release-body-updated.md

echo "Updated $TAG: $STAGE → $ICON ($TIMESTAMP)"
