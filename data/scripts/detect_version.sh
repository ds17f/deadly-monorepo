#!/bin/bash
# Version detection script for conventional commits
# Usage: ./detect_version.sh [manual_version]

set -e

# If manual version provided, use it
if [ -n "$1" ]; then
    echo "$1"
    exit 0
fi

# Get latest tag
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
LATEST_VERSION=$(echo "$LATEST_TAG" | sed 's/^v//')

# If no previous tags, start with 1.0.0
if [ "$LATEST_VERSION" = "0.0.0" ]; then
    echo "1.0.0"
    exit 0
fi

# Parse version components
MAJOR=$(echo "$LATEST_VERSION" | cut -d. -f1)
MINOR=$(echo "$LATEST_VERSION" | cut -d. -f2) 
PATCH=$(echo "$LATEST_VERSION" | cut -d. -f3)

# Check if there are commits since last tag
COMMITS_SINCE=$(git rev-list "$LATEST_TAG"..HEAD 2>/dev/null | wc -l)
if [ "$COMMITS_SINCE" -eq 0 ]; then
    echo "❌ No commits since last release $LATEST_TAG" >&2
    exit 1
fi

# Check for breaking changes
HAS_BREAKING=$(git log "$LATEST_TAG"..HEAD --grep="BREAKING CHANGE" --grep="!:" --oneline | wc -l)
if [ "$HAS_BREAKING" -gt 0 ]; then
    echo "$((MAJOR + 1)).0.0"
    exit 0
fi

# Check for minor changes (feat, refactor, data, perf)
HAS_MINOR=$(git log "$LATEST_TAG"..HEAD --grep="^feat:" --grep="^refactor:" --grep="^data:" --grep="^perf:" --oneline | wc -l)
if [ "$HAS_MINOR" -gt 0 ]; then
    echo "$MAJOR.$((MINOR + 1)).0"
    exit 0
fi

# Default to patch bump
echo "$MAJOR.$MINOR.$((PATCH + 1))"