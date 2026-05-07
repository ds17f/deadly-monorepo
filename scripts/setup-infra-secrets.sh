#!/bin/bash

# Upload infrastructure secrets to GitHub repository
# Requires: gh CLI installed and authenticated
#
# Usage:
#   ./scripts/setup-infra-secrets.sh
#
# Reads credentials from:
#   .secrets/b2-api-keys.txt     — Backblaze B2 keys
#   infra/digitalocean/terraform.tfvars — DO API token
#   ssh-key-2026-03-15.key       — SSH private key
#   ssh-key-2026-03-15.key.pub   — SSH public key

set -e

BOLD=$(tput bold)
NORMAL=$(tput sgr0)
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "${BOLD}Infrastructure Secrets Upload${NORMAL}"
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

# Source B2 keys
if [ ! -f ".secrets/b2-api-keys.txt" ]; then
    echo -e "${RED}Error: .secrets/b2-api-keys.txt not found${NC}"
    exit 1
fi
source .secrets/b2-api-keys.txt

# Read DO token
if [ ! -f "infra/digitalocean/terraform.tfvars" ]; then
    echo -e "${RED}Error: infra/digitalocean/terraform.tfvars not found${NC}"
    exit 1
fi
DO_TOKEN=$(grep do_token infra/digitalocean/terraform.tfvars | sed 's/.*"\(.*\)"/\1/')

# Read Hetzner token (bare token, one line)
if [ ! -f ".secrets/hetzner-key.txt" ]; then
    echo -e "${RED}Error: .secrets/hetzner-key.txt not found${NC}"
    exit 1
fi
HCLOUD_TOKEN=$(tr -d '[:space:]' < .secrets/hetzner-key.txt)

# Read GoDaddy API credentials (KEY:SECRET format, one line)
if [ ! -f ".secrets/godaddy-key.txt" ]; then
    echo -e "${RED}Error: .secrets/godaddy-key.txt not found${NC}"
    exit 1
fi
GODADDY_RAW=$(tr -d '[:space:]' < .secrets/godaddy-key.txt)
GODADDY_KEY="${GODADDY_RAW%%:*}"
GODADDY_SECRET="${GODADDY_RAW#*:}"

echo "Secrets to upload:"
echo "  B2_TFSTATE_KEY_ID"
echo "  B2_TFSTATE_APP_KEY"
echo "  B2_BACKUPS_KEY_ID"
echo "  B2_BACKUPS_APP_KEY"
echo "  DO_API_TOKEN"
echo "  HCLOUD_TOKEN"
echo "  GODADDY_KEY"
echo "  GODADDY_SECRET"
echo "  SSH_PRIVATE_KEY"
echo "  SSH_PUBLIC_KEY"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled"
    exit 0
fi

echo ""
echo -e "${BLUE}Uploading B2 secrets...${NC}"

echo -e "${GREEN}+${NC} B2_TFSTATE_KEY_ID"
echo "$B2_TFSTATE_KEY_ID" | gh secret set B2_TFSTATE_KEY_ID

echo -e "${GREEN}+${NC} B2_TFSTATE_APP_KEY"
echo "$B2_TFSTATE_APP_KEY" | gh secret set B2_TFSTATE_APP_KEY

echo -e "${GREEN}+${NC} B2_BACKUPS_KEY_ID"
echo "$B2_BACKUPS_KEY_ID" | gh secret set B2_BACKUPS_KEY_ID

echo -e "${GREEN}+${NC} B2_BACKUPS_APP_KEY"
echo "$B2_BACKUPS_APP_KEY" | gh secret set B2_BACKUPS_APP_KEY

echo ""
echo -e "${BLUE}Uploading DO secret...${NC}"

echo -e "${GREEN}+${NC} DO_API_TOKEN"
echo "$DO_TOKEN" | gh secret set DO_API_TOKEN

echo ""
echo -e "${BLUE}Uploading Hetzner secret...${NC}"

echo -e "${GREEN}+${NC} HCLOUD_TOKEN"
echo "$HCLOUD_TOKEN" | gh secret set HCLOUD_TOKEN

echo ""
echo -e "${BLUE}Uploading GoDaddy secrets...${NC}"

echo -e "${GREEN}+${NC} GODADDY_KEY"
echo -n "$GODADDY_KEY" | gh secret set GODADDY_KEY

echo -e "${GREEN}+${NC} GODADDY_SECRET"
echo -n "$GODADDY_SECRET" | gh secret set GODADDY_SECRET

echo ""
echo -e "${BLUE}Uploading SSH secrets...${NC}"

if [ ! -f "ssh-key-2026-03-15.key" ]; then
    echo -e "${RED}Error: ssh-key-2026-03-15.key not found${NC}"
    exit 1
fi
echo -e "${GREEN}+${NC} SSH_PRIVATE_KEY"
gh secret set SSH_PRIVATE_KEY < ssh-key-2026-03-15.key

echo -e "${GREEN}+${NC} SSH_PUBLIC_KEY"
gh secret set SSH_PUBLIC_KEY < ssh-key-2026-03-15.key.pub

echo ""
echo -e "${GREEN}All infrastructure secrets uploaded.${NC}"
echo ""
echo "Verify: gh secret list"
