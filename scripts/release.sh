#!/bin/bash

# Deadly - Release Script
# Auto-generates version from conventional commits, creates changelog,
# updates version in version.properties (for Android & iOS), creates a git tag, and pushes to origin
#
# Usage:
#   ./scripts/release.sh             - Regular release with automatic version
#   ./scripts/release.sh 1.2.3       - Release with specified version
#   ./scripts/release.sh --dry-run   - Simulate release without making changes

set -e  # Exit on any error

BOLD=$(tput bold)
NORMAL=$(tput sgr0)
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "${BOLD}💀 Deadly Release Script 💀${NORMAL}"
echo "================================="

VERSION_PROPS="version.properties"
CHANGELOG_FILE="CHANGELOG.md"
TEMP_CHANGELOG="/tmp/temp_changelog.md"
VERSION_PROVIDED=false
DRY_RUN=false

# Check for arguments
if [ "$1" == "--dry-run" ]; then
  DRY_RUN=true
  echo -e "${YELLOW}🧪 DRY RUN MODE - No changes will be made${NC}"
  echo -e "${BLUE}ℹ️ Will determine version automatically from conventional commits${NC}"
elif [ -n "$1" ]; then
  VERSION="$1"
  VERSION_PROVIDED=true

  # Check if dry run is the second argument
  if [ "$2" == "--dry-run" ]; then
    DRY_RUN=true
    echo -e "${YELLOW}🧪 DRY RUN MODE - No changes will be made${NC}"
  fi

  # Validate version format (simplified semver check)
  if ! [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9_.]+)?$ ]]; then
    echo -e "${RED}❌ Error: Version must follow semantic versioning (e.g., 1.0.0 or 1.1.0-beta)${NC}"
    exit 1
  fi

  echo -e "${BLUE}ℹ️ Manual version provided: ${VERSION}${NC}"
else
  echo -e "${BLUE}ℹ️ Will determine version automatically from conventional commits${NC}"
fi

# Check if git is clean
if [ -n "$(git status --porcelain)" ]; then
  echo -e "${YELLOW}⚠️ Warning: Working directory not clean${NC}"
  echo "Uncommitted changes detected:"
  git status --porcelain
  echo ""
  echo -e "${BLUE}ℹ️ Proceeding with release anyway...${NC}"
fi

echo "🔍 Checking current version..."
CURRENT_VERSION=$(grep 'VERSION_NAME=' "$VERSION_PROPS" | cut -d'=' -f2)
CURRENT_CODE=$(grep 'VERSION_CODE=' "$VERSION_PROPS" | cut -d'=' -f2)

# Extract current version components
IFS='.' read -r -a VERSION_PARTS <<< "$CURRENT_VERSION"
MAJOR=${VERSION_PARTS[0]}
MINOR=${VERSION_PARTS[1]}
PATCH=${VERSION_PARTS[2]%%[-+]*} # Remove any pre-release or build metadata

echo -e "${BLUE}📊 Current version: ${CURRENT_VERSION} (code: ${CURRENT_CODE})${NC}"

# Get latest tag
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "none")
if [ "$LATEST_TAG" == "none" ]; then
  echo -e "${YELLOW}⚠️ No previous tags found. This will be the first release.${NC}"
  FROM_REVISION=""
else
  echo -e "${BLUE}📋 Latest tag: ${LATEST_TAG}${NC}"
  FROM_REVISION="${LATEST_TAG}.."
fi

if [ "$VERSION_PROVIDED" = false ]; then
  echo "🔍 Analyzing commits since last tag to determine version bump..."

  # Check for breaking changes
  BREAKING_CHANGES=$(git log ${FROM_REVISION} --pretty=format:"%s" | grep -E "^[a-z]+(\([^)]+\))?!:" || echo "")
  BREAKING_CHANGES_COUNT=$(echo "$BREAKING_CHANGES" | grep -v "^$" | wc -l | tr -d ' ')

  # Check for features
  FEATURES=$(git log ${FROM_REVISION} --pretty=format:"%s" | grep -E "^feat(\([^)]+\))?:" || echo "")
  FEATURES_COUNT=$(echo "$FEATURES" | grep -v "^$" | wc -l | tr -d ' ')

  # Check for fixes
  FIXES=$(git log ${FROM_REVISION} --pretty=format:"%s" | grep -E "^fix(\([^)]+\))?:" || echo "")
  FIXES_COUNT=$(echo "$FIXES" | grep -v "^$" | wc -l | tr -d ' ')

  # Count total commits (handle case where single commit has no trailing newline)
  COMMIT_HASHES=$(git log ${FROM_REVISION} --pretty=format:"%H")
  if [ -n "$COMMIT_HASHES" ]; then
    TOTAL_COMMITS=$(echo "$COMMIT_HASHES" | wc -l | tr -d ' ')
  else
    TOTAL_COMMITS=0
  fi

  echo -e "${BLUE}📊 Commit Analysis:${NC}"
  echo "  - Breaking changes: $BREAKING_CHANGES_COUNT"
  echo "  - New features: $FEATURES_COUNT"
  echo "  - Bug fixes: $FIXES_COUNT"
  echo "  - Total commits: $TOTAL_COMMITS"

  # Determine version bump
  NEW_MAJOR=$MAJOR
  NEW_MINOR=$MINOR
  NEW_PATCH=$PATCH

  if [ "$BREAKING_CHANGES_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}⚠️ Breaking changes detected - incrementing major version${NC}"
    NEW_MAJOR=$((MAJOR + 1))
    NEW_MINOR=0
    NEW_PATCH=0
  elif [ "$FEATURES_COUNT" -gt 0 ]; then
    echo -e "${BLUE}ℹ️ New features detected - incrementing minor version${NC}"
    NEW_MINOR=$((MINOR + 1))
    NEW_PATCH=0
  elif [ "$FIXES_COUNT" -gt 0 ] || [ "$TOTAL_COMMITS" -gt 0 ]; then
    echo -e "${BLUE}ℹ️ Bug fixes or other changes detected - incrementing patch version${NC}"
    NEW_PATCH=$((PATCH + 1))
  else
    echo -e "${RED}❌ Error: No changes detected since last release${NC}"
    exit 1
  fi

  # Compose new version
  VERSION="${NEW_MAJOR}.${NEW_MINOR}.${NEW_PATCH}"
  echo -e "${GREEN}✅ Determined version: $VERSION${NC}"
fi

# Calculate tag name
TAG="v$VERSION"

# Check if tag already exists
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo -e "${RED}❌ Error: Tag $TAG already exists${NC}"
  exit 1
fi

# Generate changelog
echo "📝 Generating changelog..."

# Ensure the changelog file exists
touch "$CHANGELOG_FILE"

# Start with header for new version
cat > "$TEMP_CHANGELOG" << EOF
# Changelog

## [${VERSION}] - $(date +"%Y-%m-%d")

EOF

# Function to extract commits of a certain type
extract_commits() {
  local type=$1
  local title=$2
  local commits

  # Use fixed strings instead of regex to avoid grep errors
  commits=$(git log ${FROM_REVISION} --pretty=format:"* %s (%h)" | grep "^* ${type}" || true)

  if [ -n "$commits" ]; then
    echo "### $title" >> "$TEMP_CHANGELOG"

    # Process each commit to clean up the message
    echo "$commits" | while IFS= read -r commit; do
      # Remove the commit type prefix and format it nicely
      clean_msg=$(echo "$commit" | sed "s/^\* ${type}[^:]*: //")
      echo "* $clean_msg" >> "$TEMP_CHANGELOG"
    done

    echo "" >> "$TEMP_CHANGELOG"
  fi
}

# Extract different types of commits
extract_commits "feat" "New Features"
extract_commits "fix" "Bug Fixes"
extract_commits "perf" "Performance Improvements"
extract_commits "refactor" "Code Refactoring"
extract_commits "docs" "Documentation Updates"
extract_commits "test" "Tests"
extract_commits "build" "Build System"
extract_commits "ci" "CI Changes"

# Get miscellaneous commits (those not following conventional commit format)
MISC_COMMITS=$(git log ${FROM_REVISION} --pretty=format:"* %s (%h)" | grep -v "^* \(feat\|fix\|perf\|refactor\|docs\|test\|build\|ci\)" || true)

if [ -n "$MISC_COMMITS" ]; then
  echo "### Other Changes" >> "$TEMP_CHANGELOG"
  echo "$MISC_COMMITS" >> "$TEMP_CHANGELOG"
  echo "" >> "$TEMP_CHANGELOG"
fi

# Add existing changelog content (skip the header and empty sections)
if [ -f "$CHANGELOG_FILE" ] && [ -s "$CHANGELOG_FILE" ]; then
  # Skip the header, add existing content
  sed '/^# Changelog/d; /^$/N; /^\n$/d' "$CHANGELOG_FILE" >> "$TEMP_CHANGELOG" || true
fi

if [ "$DRY_RUN" = false ]; then
  # Replace the existing changelog only if not dry run
  mv "$TEMP_CHANGELOG" "$CHANGELOG_FILE"
fi

echo -e "${GREEN}✅ Changelog generated${NC}"

# Show preview of the changelog
echo ""
echo "${BOLD}📋 Changelog Preview:${NORMAL}"
echo "-----------------------------"
if [ "$DRY_RUN" = true ]; then
  # In dry run, show the temp changelog
  head -n 20 "$TEMP_CHANGELOG"
else
  # In real run, show the actual changelog
  head -n 20 "$CHANGELOG_FILE"
fi
echo "..."
echo "-----------------------------"

# Update version in version.properties
echo "📝 Updating version in $VERSION_PROPS..."
NEW_CODE=$((CURRENT_CODE + 1))

# Extract the first section of the changelog for the commit and tag messages
# Use the temp changelog file since it always contains the current version
if [ -f "$TEMP_CHANGELOG" ]; then
  CHANGELOG_SECTION=$(awk "/## \[${VERSION}\]/{flag=1; print; next} /## \[/{flag=0} flag" "$TEMP_CHANGELOG")
else
  # Fallback: extract from the actual changelog file
  CHANGELOG_SECTION=$(awk "/## \[${VERSION}\]/{flag=1; print; next} /## \[/{flag=0} flag" "$CHANGELOG_FILE")
fi

if [ "$DRY_RUN" = true ]; then
  echo -e "${YELLOW}🧪 DRY RUN: Would update version to $VERSION (code: $NEW_CODE)${NC}"
  echo -e "${YELLOW}🧪 DRY RUN: Would update the following files:${NC}"
  echo "  - $VERSION_PROPS"
  echo "  - $CHANGELOG_FILE"
  echo -e "${YELLOW}🧪 DRY RUN: Would create tag $TAG with changelog content${NC}"
  echo -e "${YELLOW}🧪 DRY RUN: Would create commit with message:${NC}"
  echo "  chore: release version $VERSION"
  echo "  "
  echo "  Release summary:"
  echo "$CHANGELOG_SECTION" | sed 's/^/  /'
  echo "  "
  echo "  Version code updated from $CURRENT_CODE to $NEW_CODE"
else
  # Use sed to update versions in version.properties
  if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS requires different sed syntax
    sed -i '' "s/VERSION_CODE=$CURRENT_CODE/VERSION_CODE=$NEW_CODE/" "$VERSION_PROPS"
    sed -i '' "s/VERSION_NAME=$CURRENT_VERSION/VERSION_NAME=$VERSION/" "$VERSION_PROPS"
  else
    # Linux/others
    sed -i "s/VERSION_CODE=$CURRENT_CODE/VERSION_CODE=$NEW_CODE/" "$VERSION_PROPS"
    sed -i "s/VERSION_NAME=$CURRENT_VERSION/VERSION_NAME=$VERSION/" "$VERSION_PROPS"
  fi

  # Verify changes
  if ! grep -q "VERSION_NAME=$VERSION" "$VERSION_PROPS"; then
    echo -e "${RED}❌ Error: Failed to update version in $VERSION_PROPS${NC}"
    git checkout "$VERSION_PROPS"  # Revert changes
    exit 1
  fi

  echo -e "${GREEN}✅ Updated version.properties to version $VERSION (code: $NEW_CODE)${NC}"

  # Commit changes with changelog details
  echo "📦 Committing version changes..."
  git add "$VERSION_PROPS" "$CHANGELOG_FILE"

  # Create commit message with changelog summary
  COMMIT_MESSAGE="chore: release version $VERSION

Release summary:
$CHANGELOG_SECTION

Version code updated from $CURRENT_CODE to $NEW_CODE"

  git commit -m "$COMMIT_MESSAGE"

  # Create tag with annotated message including changelog
  echo "🏷️ Creating tag $TAG..."

  # Create a more descriptive tag message
  TAG_MESSAGE="Deadly $VERSION

Changes in this release:

$CHANGELOG_SECTION"

  git tag -a "$TAG" -m "$TAG_MESSAGE"
fi

# Handle pushing or next steps
if [ "$DRY_RUN" = true ]; then
  echo ""
  echo -e "${YELLOW}🧪 DRY RUN SUMMARY:${NC}"
  echo "  • Version to release: $VERSION (code: $NEW_CODE)"
  echo "  • Tag to create: $TAG"
  echo "  • Files to change: $VERSION_PROPS, $CHANGELOG_FILE"
  echo "  • Commit message: chore: release version $VERSION (code: $NEW_CODE)"
  echo ""
  echo -e "${GREEN}✅ Dry run complete. No changes were made.${NC}"
  echo "Run without --dry-run to perform actual release."

  # Clean up temp changelog file
  rm -f "$TEMP_CHANGELOG"

  echo ""
  echo "💀 Keep on truckin'! 💀"
else
  echo ""
  echo -e "${GREEN}🚀 Auto-pushing changes and tags to origin...${NC}"
  git push origin HEAD
  git push origin "$TAG"

  echo -e "${GREEN}✅ Release $VERSION successfully created and pushed!${NC}"
  echo ""
  echo "Next steps:"
  echo "1. GitHub Actions will now build the release for both Android and iOS"
  echo "2. Check the release workflow status on GitHub"
  echo "3. Download the release artifacts when complete"
  echo ""
  echo "💀 Keep on truckin'! 💀"
fi
