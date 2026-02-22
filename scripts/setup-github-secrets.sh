#!/bin/bash

# Upload all required secrets to GitHub repository
# Requires: gh CLI installed and authenticated
#
# Usage:
#   ./scripts/setup-github-secrets.sh
#
# Prerequisites:
#   1. Install gh CLI: brew install gh
#   2. Authenticate: gh auth login
#   3. Ensure all files exist in .secrets/ directory

set -e

BOLD=$(tput bold)
NORMAL=$(tput sgr0)
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "${BOLD}🔐 GitHub Secrets Upload Script${NORMAL}"
echo "=================================="
echo ""

# OS-aware base64 encode: macOS uses -i, Linux uses plain base64
base64_encode() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        base64 -i "$1"
    else
        base64 -w 0 "$1"
    fi
}

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ Error: gh CLI is not installed${NC}"
    echo -e "${BLUE}💡 Install with: brew install gh${NC}"
    exit 1
fi

# Check if gh is authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${RED}❌ Error: gh CLI is not authenticated${NC}"
    echo -e "${BLUE}💡 Authenticate with: gh auth login${NC}"
    exit 1
fi

# Verify .secrets directory exists
if [ ! -d ".secrets" ]; then
    echo -e "${RED}❌ Error: .secrets/ directory not found${NC}"
    exit 1
fi

echo -e "${BLUE}ℹ️  This will upload 12 secrets to your GitHub repository${NC}"
echo ""
echo "Secrets to be uploaded:"
echo "  Android (6):"
echo "    - ANDROID_KEYSTORE_BASE64"
echo "    - ANDROID_KEYSTORE_PASSWORD"
echo "    - ANDROID_KEY_ALIAS"
echo "    - ANDROID_KEY_PASSWORD"
echo "    - PLAY_STORE_JSON_BASE64"
echo "    - GENIUS_ACCESS_TOKEN"
echo ""
echo "  iOS (6):"
echo "    - IOS_CERTIFICATE_BASE64"
echo "    - IOS_CERTIFICATE_PASSWORD"
echo "    - IOS_PROVISIONING_PROFILE_BASE64"
echo "    - APP_STORE_CONNECT_KEY_ID"
echo "    - APP_STORE_CONNECT_ISSUER_ID"
echo "    - APP_STORE_CONNECT_KEY_BASE64"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}⚠️  Cancelled${NC}"
    exit 0
fi

echo ""
echo -e "${BLUE}📱 Uploading Android secrets...${NC}"

# Android keystore
if [ ! -f ".secrets/my-release-key.jks" ]; then
    echo -e "${RED}❌ Error: .secrets/my-release-key.jks not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Uploading ANDROID_KEYSTORE_BASE64..."
base64_encode .secrets/my-release-key.jks | gh secret set ANDROID_KEYSTORE_BASE64

# Android keystore properties
if [ ! -f ".secrets/keystore.properties" ]; then
    echo -e "${RED}❌ Error: .secrets/keystore.properties not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Uploading ANDROID_KEYSTORE_PASSWORD..."
grep storePassword .secrets/keystore.properties | cut -d'=' -f2 | gh secret set ANDROID_KEYSTORE_PASSWORD
echo -e "${GREEN}✓${NC} Uploading ANDROID_KEY_ALIAS..."
grep keyAlias .secrets/keystore.properties | cut -d'=' -f2 | gh secret set ANDROID_KEY_ALIAS
echo -e "${GREEN}✓${NC} Uploading ANDROID_KEY_PASSWORD..."
grep keyPassword .secrets/keystore.properties | cut -d'=' -f2 | gh secret set ANDROID_KEY_PASSWORD

# Play Store service account JSON
if [ ! -f ".secrets/thedeadly-app-f48493c2a133.json" ]; then
    echo -e "${YELLOW}⚠️  Warning: .secrets/thedeadly-app-f48493c2a133.json not found - skipping PLAY_STORE_JSON_BASE64${NC}"
else
    echo -e "${GREEN}✓${NC} Uploading PLAY_STORE_JSON_BASE64..."
    base64_encode .secrets/thedeadly-app-f48493c2a133.json | gh secret set PLAY_STORE_JSON_BASE64
fi

# Genius API access token (for lyrics)
if [ ! -f "androidApp/local.properties" ]; then
    echo -e "${YELLOW}⚠️  Warning: androidApp/local.properties not found - skipping GENIUS_ACCESS_TOKEN${NC}"
else
    GENIUS_TOKEN=$(grep genius.access.token androidApp/local.properties | cut -d'=' -f2)
    if [ -z "$GENIUS_TOKEN" ]; then
        echo -e "${YELLOW}⚠️  Warning: genius.access.token not found in local.properties - skipping GENIUS_ACCESS_TOKEN${NC}"
    else
        echo -e "${GREEN}✓${NC} Uploading GENIUS_ACCESS_TOKEN..."
        echo "$GENIUS_TOKEN" | gh secret set GENIUS_ACCESS_TOKEN
    fi
fi

echo ""
echo -e "${BLUE}🍎 Uploading iOS secrets...${NC}"

# iOS certificate
if [ ! -f ".secrets/DeadlyApp_AppStore2.p12" ]; then
    echo -e "${RED}❌ Error: .secrets/DeadlyApp_AppStore2.p12 not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Uploading IOS_CERTIFICATE_BASE64..."
base64_encode .secrets/DeadlyApp_AppStore2.p12 | gh secret set IOS_CERTIFICATE_BASE64

# iOS certificate password
if [ ! -f ".secrets/cert_password.txt" ]; then
    echo -e "${RED}❌ Error: .secrets/cert_password.txt not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Uploading IOS_CERTIFICATE_PASSWORD..."
cat .secrets/cert_password.txt | gh secret set IOS_CERTIFICATE_PASSWORD

# iOS provisioning profile
if [ ! -f ".secrets/DeadlyApp_AppStore2.mobileprovision" ]; then
    echo -e "${RED}❌ Error: .secrets/DeadlyApp_AppStore2.mobileprovision not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Uploading IOS_PROVISIONING_PROFILE_BASE64..."
base64_encode .secrets/DeadlyApp_AppStore2.mobileprovision | gh secret set IOS_PROVISIONING_PROFILE_BASE64

# App Store Connect API Key
echo -e "${GREEN}✓${NC} Uploading APP_STORE_CONNECT_KEY_ID..."
echo "V862XWV7WB" | gh secret set APP_STORE_CONNECT_KEY_ID

echo -e "${GREEN}✓${NC} Uploading APP_STORE_CONNECT_ISSUER_ID..."
echo "9501cc7b-1a6c-4e4d-8c37-c04149a31886" | gh secret set APP_STORE_CONNECT_ISSUER_ID

if [ ! -f ".secrets/AuthKey_V862XWV7WB.p8" ]; then
    echo -e "${YELLOW}⚠️  Warning: .secrets/AuthKey_V862XWV7WB.p8 not found - skipping APP_STORE_CONNECT_KEY_BASE64${NC}"
else
    echo -e "${GREEN}✓${NC} Uploading APP_STORE_CONNECT_KEY_BASE64..."
    base64_encode .secrets/AuthKey_V862XWV7WB.p8 | gh secret set APP_STORE_CONNECT_KEY_BASE64
fi

echo ""
echo -e "${GREEN}✅ All secrets uploaded successfully!${NC}"
echo ""
echo "Next steps:"
echo "  1. Verify secrets in GitHub: gh secret list"
echo "  2. Create a release: make release"
echo "  3. GitHub Actions will automatically build both platforms"
echo ""
echo "💀 Keep on truckin'! 💀"
