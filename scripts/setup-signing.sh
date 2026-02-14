#!/bin/bash

# Deadly - Signing Setup Script
# Creates .secrets/ directory, generates a release keystore, and creates keystore.properties
#
# Usage:
#   ./scripts/setup-signing.sh

set -e

BOLD=$(tput bold)
NORMAL=$(tput sgr0)
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SECRETS_DIR=".secrets"
KEYSTORE_FILE="$SECRETS_DIR/my-release-key.jks"
PROPERTIES_FILE="$SECRETS_DIR/keystore.properties"
KEY_ALIAS="deadly-release"

echo "${BOLD}🔐 Deadly Signing Setup${NORMAL}"
echo "========================="
echo ""

# Check if keytool is available
if ! command -v keytool &> /dev/null; then
    echo -e "${RED}❌ Error: keytool not found. Install a JDK first.${NC}"
    exit 1
fi

# Create .secrets directory
mkdir -p "$SECRETS_DIR"
echo -e "${GREEN}✅ Created $SECRETS_DIR/ directory${NC}"

# Check if keystore already exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo -e "${YELLOW}⚠️ Keystore already exists at $KEYSTORE_FILE${NC}"
    read -p "Overwrite? (y/n) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}ℹ️ Keeping existing keystore${NC}"
        # Still generate properties if missing
        if [ ! -f "$PROPERTIES_FILE" ]; then
            echo ""
            echo -e "${BLUE}ℹ️ Generating keystore.properties for existing keystore...${NC}"
            read -sp "Enter keystore password: " STORE_PASSWORD
            echo ""
            read -sp "Enter key password: " KEY_PASSWORD
            echo ""

            cat > "$PROPERTIES_FILE" << EOF
storeFile=$KEYSTORE_FILE
storePassword=$STORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
EOF
            echo -e "${GREEN}✅ Created $PROPERTIES_FILE${NC}"
        fi
        echo ""
        echo "Next steps:"
        echo "  1. Run 'make setup-github-secrets' to upload secrets to GitHub"
        echo ""
        echo "💀 Keep on truckin'! 💀"
        exit 0
    fi
fi

# Prompt for passwords
echo -e "${BLUE}ℹ️ Generating release keystore...${NC}"
echo ""
read -sp "Enter keystore password (min 6 chars): " STORE_PASSWORD
echo ""
if [ ${#STORE_PASSWORD} -lt 6 ]; then
    echo -e "${RED}❌ Error: Password must be at least 6 characters${NC}"
    exit 1
fi

read -sp "Enter key password (min 6 chars, or press Enter to use same as keystore): " KEY_PASSWORD
echo ""
if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$STORE_PASSWORD"
fi
if [ ${#KEY_PASSWORD} -lt 6 ]; then
    echo -e "${RED}❌ Error: Password must be at least 6 characters${NC}"
    exit 1
fi

# Prompt for certificate details
echo ""
echo -e "${BLUE}ℹ️ Certificate details (press Enter for defaults):${NC}"
read -p "  Organization [Grateful Deadly]: " ORG
ORG=${ORG:-"Grateful Deadly"}
read -p "  Country code [US]: " COUNTRY
COUNTRY=${COUNTRY:-"US"}

# Generate keystore
echo ""
echo -e "${BLUE}ℹ️ Generating keystore...${NC}"
keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -alias "$KEY_ALIAS" \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=Deadly, O=$ORG, C=$COUNTRY"

echo -e "${GREEN}✅ Keystore generated at $KEYSTORE_FILE${NC}"

# Validate the keystore
echo -e "${BLUE}ℹ️ Validating keystore...${NC}"
if keytool -list -keystore "$KEYSTORE_FILE" -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Keystore validated successfully${NC}"
else
    echo -e "${RED}❌ Error: Keystore validation failed${NC}"
    exit 1
fi

# Create keystore.properties
cat > "$PROPERTIES_FILE" << EOF
storeFile=$KEYSTORE_FILE
storePassword=$STORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
EOF

echo -e "${GREEN}✅ Created $PROPERTIES_FILE${NC}"

echo ""
echo -e "${GREEN}🎉 Signing setup complete!${NC}"
echo ""
echo "Files created:"
echo "  - $KEYSTORE_FILE (release keystore)"
echo "  - $PROPERTIES_FILE (credentials for Gradle)"
echo ""
echo -e "${YELLOW}⚠️ These files are gitignored. Do NOT commit them.${NC}"
echo ""
echo "Next steps:"
echo "  1. Run 'make setup-github-secrets' to upload secrets to GitHub"
echo "  2. Run 'make android-build-release' to test a local signed build"
echo "  3. Run 'make release-dry-run' to preview the release"
echo ""
echo "💀 Keep on truckin'! 💀"
