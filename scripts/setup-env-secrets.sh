#!/bin/bash

# Upload app secrets to a GitHub environment
# Requires: gh CLI installed and authenticated
#
# Usage:
#   ./scripts/setup-env-secrets.sh <environment>
#
# Environments: alpha, beta, prod
#
# Reads credentials from:
#   .env                          — Auth, Google, Apple, Analytics secrets
#   .secrets/AuthKey_S72YXG4V8W.p8 — Apple private key (PEM)
#
# AUTH_URL is derived from the environment's SITE_ADDRESS variable.

set -euo pipefail

BOLD=$(tput bold)
NORMAL=$(tput sgr0)
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

ENV="${1:-}"
if [ -z "$ENV" ]; then
    echo -e "${RED}Usage: $0 <environment>${NC}"
    echo "  Environments: alpha, beta, prod"
    exit 1
fi

if [[ ! "$ENV" =~ ^(alpha|beta|prod)$ ]]; then
    echo -e "${RED}Error: Invalid environment '${ENV}'. Must be: alpha, beta, prod${NC}"
    exit 1
fi

echo "${BOLD}Environment Secrets: ${ENV}${NORMAL}"
echo "=============================="
echo ""

# Check prerequisites
if ! command -v gh &> /dev/null; then
    echo -e "${RED}Error: gh CLI is not installed${NC}"
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo -e "${RED}Error: gh CLI is not authenticated${NC}"
    exit 1
fi

if [ ! -f ".env" ]; then
    echo -e "${RED}Error: .env not found${NC}"
    exit 1
fi

# Read .env values (skip comments and blank lines)
read_env() {
    grep "^${1}=" .env | head -1 | cut -d'=' -f2-
}

AUTH_SECRET=$(read_env AUTH_SECRET)
GOOGLE_CLIENT_ID=$(read_env GOOGLE_CLIENT_ID)
GOOGLE_CLIENT_SECRET=$(read_env GOOGLE_CLIENT_SECRET)
GOOGLE_IOS_CLIENT_ID=$(read_env GOOGLE_IOS_CLIENT_ID)
GOOGLE_ANDROID_CLIENT_ID=$(read_env GOOGLE_ANDROID_CLIENT_ID)
APPLE_CLIENT_ID=$(read_env APPLE_CLIENT_ID)
APPLE_TEAM_ID=$(read_env APPLE_TEAM_ID)
APPLE_KEY_ID=$(read_env APPLE_KEY_ID)
ANALYTICS_API_KEY=$(read_env ANALYTICS_API_KEY)

# Derive AUTH_URL from the environment's SITE_ADDRESS
SITE_ADDRESS=$(gh api "repos/{owner}/{repo}/environments/${ENV}/variables/SITE_ADDRESS" --jq '.value' 2>/dev/null || true)
if [ -z "$SITE_ADDRESS" ]; then
    echo -e "${RED}Error: SITE_ADDRESS variable not set on '${ENV}' environment${NC}"
    echo "Set it first: gh api -X POST repos/{owner}/{repo}/environments/${ENV}/variables -f name=SITE_ADDRESS -f value=<domain>"
    exit 1
fi
AUTH_URL="https://${SITE_ADDRESS}"

# Read Apple private key from PEM file
APPLE_KEY_FILE=".secrets/AuthKey_${APPLE_KEY_ID}.p8"
if [ ! -f "$APPLE_KEY_FILE" ]; then
    echo -e "${RED}Error: ${APPLE_KEY_FILE} not found${NC}"
    exit 1
fi
APPLE_PRIVATE_KEY=$(cat "$APPLE_KEY_FILE")

# Show what will be set
echo "SITE_ADDRESS = ${SITE_ADDRESS}"
echo "AUTH_URL     = ${AUTH_URL}"
echo ""
echo "Secrets to upload to '${ENV}' environment:"
echo "  AUTH_SECRET"
echo "  AUTH_URL"
echo "  GOOGLE_CLIENT_ID"
echo "  GOOGLE_CLIENT_SECRET"
echo "  GOOGLE_IOS_CLIENT_ID"
echo "  GOOGLE_ANDROID_CLIENT_ID"
echo "  APPLE_CLIENT_ID"
echo "  APPLE_TEAM_ID"
echo "  APPLE_KEY_ID"
echo "  APPLE_PRIVATE_KEY"
echo "  ANALYTICS_API_KEY"
echo ""

REPO=$(gh repo view --json nameWithOwner --jq '.nameWithOwner')

set_secret() {
    local name="$1"
    local value="$2"
    echo -e "${GREEN}+${NC} ${name}"
    echo "$value" | gh secret set "$name" --env "$ENV"
}

echo -e "${BLUE}Uploading secrets to '${ENV}'...${NC}"
echo ""

set_secret AUTH_SECRET "$AUTH_SECRET"
set_secret AUTH_URL "$AUTH_URL"
set_secret GOOGLE_CLIENT_ID "$GOOGLE_CLIENT_ID"
set_secret GOOGLE_CLIENT_SECRET "$GOOGLE_CLIENT_SECRET"
set_secret GOOGLE_IOS_CLIENT_ID "$GOOGLE_IOS_CLIENT_ID"
set_secret GOOGLE_ANDROID_CLIENT_ID "$GOOGLE_ANDROID_CLIENT_ID"
set_secret APPLE_CLIENT_ID "$APPLE_CLIENT_ID"
set_secret APPLE_TEAM_ID "$APPLE_TEAM_ID"
set_secret APPLE_KEY_ID "$APPLE_KEY_ID"
set_secret APPLE_PRIVATE_KEY "$APPLE_PRIVATE_KEY"
set_secret ANALYTICS_API_KEY "$ANALYTICS_API_KEY"

echo ""
echo -e "${GREEN}All secrets uploaded to '${ENV}' environment.${NC}"
echo ""
echo "Verify: gh api repos/${REPO}/environments/${ENV}/secrets --jq '.secrets[].name'"
