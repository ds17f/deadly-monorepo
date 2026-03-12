#!/bin/bash

# Deadly - Release Script
# Auto-generates version from conventional commits, creates changelog,
# updates version in version.properties, creates a git tag, and pushes to origin
#
# Usage:
#   ./scripts/release.sh                          - Android release with automatic version
#   ./scripts/release.sh --platform ios            - iOS release with automatic version
#   ./scripts/release.sh --platform android 1.2.3  - Android release with specified version
#   ./scripts/release.sh --dry-run                 - Simulate release without making changes
#   ./scripts/release.sh --platform android --dry-run 1.2.3

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

TEMP_CHANGELOG="/tmp/temp_changelog.md"
VERSION_PROVIDED=false
DRY_RUN=false
PLATFORM="android"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --platform)
      PLATFORM="$2"
      shift 2
      ;;
    *)
      VERSION="$1"
      VERSION_PROVIDED=true
      shift
      ;;
  esac
done

# Validate platform
case "$PLATFORM" in
  android)
    VERSION_PROPS="androidApp/version.properties"
    CHANGELOG_FILE="androidApp/CHANGELOG.md"
    TAG_MATCH="android/v*"
    EXCLUDE_PLATFORM="ios"
    ;;
  ios)
    VERSION_PROPS="iosApp/version.properties"
    CHANGELOG_FILE="iosApp/CHANGELOG.md"
    TAG_MATCH="ios/v*"
    EXCLUDE_PLATFORM="android"
    ;;
  all)
    echo -e "${RED}❌ Error: --platform all is no longer supported.${NC}"
    echo -e "${RED}   With independent versioning, release each platform separately:${NC}"
    echo -e "${RED}   ./scripts/release.sh --platform android${NC}"
    echo -e "${RED}   ./scripts/release.sh --platform ios${NC}"
    exit 1
    ;;
  *)
    echo -e "${RED}❌ Error: Invalid platform '$PLATFORM'. Must be android or ios${NC}"
    exit 1
    ;;
esac

if [ "$DRY_RUN" = true ]; then
  echo -e "${YELLOW}🧪 DRY RUN MODE - No changes will be made${NC}"
fi

echo -e "${BLUE}ℹ️ Platform: ${PLATFORM}${NC}"

if [ "$VERSION_PROVIDED" = true ]; then
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

# Get latest tag - only look at same-platform tags
LATEST_TAG=$(git describe --tags --abbrev=0 --match "$TAG_MATCH" 2>/dev/null || echo "none")
if [ "$LATEST_TAG" == "none" ]; then
  # Fall back to old-style v* tags
  LATEST_TAG=$(git describe --tags --abbrev=0 --match "v*" 2>/dev/null || echo "none")
fi
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
  BREAKING_CHANGES=$(git log ${FROM_REVISION} --pretty=format:"%s" \
    | grep -v "(${EXCLUDE_PLATFORM}[/)]" \
    | grep -E "^[a-z]+(\([^)]+\))?!:" || echo "")
  BREAKING_CHANGES_COUNT=$(echo "$BREAKING_CHANGES" | grep -v "^$" | wc -l | tr -d ' ')

  # Check for features
  FEATURES=$(git log ${FROM_REVISION} --pretty=format:"%s" \
    | grep -v "(${EXCLUDE_PLATFORM}[/)]" \
    | grep -E "^feat(\([^)]+\))?:" || echo "")
  FEATURES_COUNT=$(echo "$FEATURES" | grep -v "^$" | wc -l | tr -d ' ')

  # Check for fixes
  FIXES=$(git log ${FROM_REVISION} --pretty=format:"%s" \
    | grep -v "(${EXCLUDE_PLATFORM}[/)]" \
    | grep -E "^fix(\([^)]+\))?:" || echo "")
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

# Calculate tag name based on platform
TAG="${PLATFORM}/v${VERSION}"

# Check if the target tag already exists
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo -e "${RED}❌ Error: Tag $TAG already exists${NC}"
  exit 1
fi

echo -e "${BLUE}🏷️ Tag to create: ${TAG}${NC}"

# Check if the release commit for this version already exists (idempotent bump)
RELEASE_COMMIT_EXISTS=false
if git log -1 --pretty=format:"%s" | grep -q "^chore: release ${PLATFORM} version $VERSION$"; then
  echo -e "${BLUE}ℹ️ Release commit for $VERSION already exists, skipping version bump${NC}"
  RELEASE_COMMIT_EXISTS=true
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
# Args: type title [extra_exclude_pattern]
extract_commits() {
  local type=$1
  local title=$2
  local extra_exclude=${3:-}
  local commits

  # Filter by type, then exclude opposite-platform commits
  commits=$(git log ${FROM_REVISION} --pretty=format:"* %s (%h)" \
    | grep "^* ${type}" \
    | grep -v "(${EXCLUDE_PLATFORM}[/)]" \
    || true)

  # Apply optional extra exclude pattern (e.g. to filter out ci-scoped commits)
  if [ -n "$extra_exclude" ] && [ -n "$commits" ]; then
    commits=$(echo "$commits" | grep -v "$extra_exclude" || true)
  fi

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
# feat and fix exclude ci-scoped commits — those go in CI Changes
extract_commits "feat" "New Features" "([^)]*ci"
extract_commits "fix" "Bug Fixes" "([^)]*ci"
extract_commits "perf" "Performance Improvements"
extract_commits "refactor" "Code Refactoring"
extract_commits "docs" "Documentation Updates"
extract_commits "test" "Tests"
extract_commits "build" "Build System"

# CI Changes: pure ci: type + feat/fix commits with ci in scope, both filtered by platform
CI_FROM_TYPE=$(git log ${FROM_REVISION} --pretty=format:"* %s (%h)" \
  | grep "^* ci" \
  | grep -v "(${EXCLUDE_PLATFORM}[/)]" \
  || true)
CI_FROM_FEAT_FIX=$(git log ${FROM_REVISION} --pretty=format:"* %s (%h)" \
  | grep -E "^\* (feat|fix)" \
  | grep "([^)]*ci" \
  | grep -v "(${EXCLUDE_PLATFORM}[/)]" \
  || true)
CI_COMMITS=$(printf "%s\n%s" "$CI_FROM_TYPE" "$CI_FROM_FEAT_FIX" | grep -v "^$" || true)
if [ -n "$CI_COMMITS" ]; then
  echo "### CI Changes" >> "$TEMP_CHANGELOG"
  echo "$CI_COMMITS" | while IFS= read -r commit; do
    clean_msg=$(echo "$commit" | sed "s/^\* [a-z]*[^:]*: //")
    echo "* $clean_msg" >> "$TEMP_CHANGELOG"
  done
  echo "" >> "$TEMP_CHANGELOG"
fi

# Get miscellaneous commits (those not following conventional commit format)
# Exclude opposite-platform commits and release chore commits
MISC_COMMITS=$(git log ${FROM_REVISION} --pretty=format:"* %s (%h)" \
  | grep -v "(${EXCLUDE_PLATFORM}[/)]" \
  | grep -v "^* chore: release " \
  | grep -v "^* \(feat\|fix\|perf\|refactor\|docs\|test\|build\|ci\)" || true)

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
  if [ "$PLATFORM" = "ios" ]; then
    echo "  - iosApp/deadly.xcodeproj/project.pbxproj (MARKETING_VERSION + CURRENT_PROJECT_VERSION)"
  fi
  echo -e "${YELLOW}🧪 DRY RUN: Would create tag: ${TAG}${NC}"
  echo -e "${YELLOW}🧪 DRY RUN: Would create commit with message:${NC}"
  echo "  chore: release ${PLATFORM} version $VERSION"
  echo "  "
  echo "  Release summary:"
  echo "$CHANGELOG_SECTION" | sed 's/^/  /'
  echo "  "
  echo "  Version code updated from $CURRENT_CODE to $NEW_CODE"
elif [ "$RELEASE_COMMIT_EXISTS" = true ]; then
  echo -e "${BLUE}ℹ️ Skipping version bump (release commit already exists)${NC}"
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

  echo -e "${GREEN}✅ Updated $VERSION_PROPS to version $VERSION (code: $NEW_CODE)${NC}"

  # Sync Xcode project version for iOS releases
  if [ "$PLATFORM" = "ios" ]; then
    PBXPROJ="iosApp/deadly.xcodeproj/project.pbxproj"
    echo "📝 Syncing Xcode project version..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
      sed -i '' "s/MARKETING_VERSION = [^;]*;/MARKETING_VERSION = $VERSION;/g" "$PBXPROJ"
      sed -i '' "s/CURRENT_PROJECT_VERSION = [^;]*;/CURRENT_PROJECT_VERSION = $NEW_CODE;/g" "$PBXPROJ"
    else
      sed -i "s/MARKETING_VERSION = [^;]*;/MARKETING_VERSION = $VERSION;/g" "$PBXPROJ"
      sed -i "s/CURRENT_PROJECT_VERSION = [^;]*;/CURRENT_PROJECT_VERSION = $NEW_CODE;/g" "$PBXPROJ"
    fi
    echo -e "${GREEN}✅ Synced $PBXPROJ${NC}"
  fi

  # Commit changes with changelog details
  echo "📦 Committing version changes..."
  git add "$VERSION_PROPS" "$CHANGELOG_FILE"
  if [ "$PLATFORM" = "ios" ]; then
    git add "$PBXPROJ"
  fi

  # Create commit message with changelog summary
  COMMIT_MESSAGE="chore: release ${PLATFORM} version $VERSION

Release summary:
$CHANGELOG_SECTION

Version code updated from $CURRENT_CODE to $NEW_CODE"

  git commit -m "$COMMIT_MESSAGE"
fi

# Create tag (unless dry run)
if [ "$DRY_RUN" = false ]; then
  echo "🏷️ Creating tag $TAG..."

  TAG_MESSAGE="Deadly ${PLATFORM} $VERSION

Changes in this release:

$CHANGELOG_SECTION"

  git tag -a "$TAG" -m "$TAG_MESSAGE"
fi

# Handle pushing or next steps
if [ "$DRY_RUN" = true ]; then
  echo ""
  echo -e "${YELLOW}🧪 DRY RUN SUMMARY:${NC}"
  echo "  • Version to release: $VERSION (code: $NEW_CODE)"
  echo "  • Tag to create: ${TAG}"
  if [ "$PLATFORM" = "ios" ]; then
    echo "  • Files to change: $VERSION_PROPS, $CHANGELOG_FILE, iosApp/deadly.xcodeproj/project.pbxproj"
  else
    echo "  • Files to change: $VERSION_PROPS, $CHANGELOG_FILE"
  fi
  echo "  • Commit message: chore: release ${PLATFORM} version $VERSION (code: $NEW_CODE)"
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
  echo "Tag pushed: $TAG"
  echo ""
  echo "Next steps:"
  if [ "$PLATFORM" = "android" ]; then
    echo "  - GitHub Actions will build the Android release (android-release workflow)"
  elif [ "$PLATFORM" = "ios" ]; then
    echo "  - GitHub Actions will build the iOS release (ios-release workflow)"
  fi
  echo "  - Check the release workflow status on GitHub"
  echo "  - Download the release artifacts when complete"
  echo ""
  echo "💀 Keep on truckin'! 💀"
fi
