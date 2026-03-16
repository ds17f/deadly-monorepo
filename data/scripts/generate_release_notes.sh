#!/bin/bash
# Release notes generation script for conventional commits
# Usage: ./generate_release_notes.sh <version>

set -e

VERSION="$1"
if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version>" >&2
    exit 1
fi

# Get latest tag
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

# If no previous tags, show initial release notes
if [ -z "$LATEST_TAG" ]; then
    echo "## Initial Release"
    echo ""
    echo "### Changes"
    git log --oneline --pretty=format:"- %s (%h)" | head -20
    echo ""
    echo "**Full Changelog**: Initial release"
    exit 0
fi

echo "## What's Changed"
echo ""

# Track if we found any commits
FOUND_COMMITS=false

# New Features
FEAT_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="^feat:" --oneline --pretty=format:"- %s (%h)")
if [ -n "$FEAT_COMMITS" ]; then
    echo "### ✨ New Features"
    echo "$FEAT_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# Bug Fixes  
FIX_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="^fix:" --oneline --pretty=format:"- %s (%h)")
if [ -n "$FIX_COMMITS" ]; then
    echo "### 🐛 Bug Fixes"
    echo "$FIX_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# Code Refactoring
REFACTOR_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="^refactor:" --oneline --pretty=format:"- %s (%h)")
if [ -n "$REFACTOR_COMMITS" ]; then
    echo "### ♻️ Code Refactoring"
    echo "$REFACTOR_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# Data & Processing
DATA_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="^data:" --oneline --pretty=format:"- %s (%h)")
if [ -n "$DATA_COMMITS" ]; then
    echo "### 📊 Data & Processing"
    echo "$DATA_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# Performance Improvements
PERF_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="^perf:" --oneline --pretty=format:"- %s (%h)")
if [ -n "$PERF_COMMITS" ]; then
    echo "### 🚀 Performance Improvements"
    echo "$PERF_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# Documentation
DOCS_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="^docs:" --oneline --pretty=format:"- %s (%h)")
if [ -n "$DOCS_COMMITS" ]; then
    echo "### 📚 Documentation"
    echo "$DOCS_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# Maintenance (chore, style, test, ci, build)
MAINT_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="^chore:" --grep="^style:" --grep="^test:" --grep="^ci:" --grep="^build:" --oneline --pretty=format:"- %s (%h)")
if [ -n "$MAINT_COMMITS" ]; then
    echo "### 🔧 Maintenance"
    echo "$MAINT_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# Other changes (anything not matching conventional commits)
OTHER_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="^feat:" --grep="^fix:" --grep="^refactor:" --grep="^data:" --grep="^perf:" --grep="^docs:" --grep="^chore:" --grep="^style:" --grep="^test:" --grep="^ci:" --grep="^build:" --invert-grep --oneline --pretty=format:"- %s (%h)")
if [ -n "$OTHER_COMMITS" ]; then
    echo "### 🔧 Other Changes"
    echo "$OTHER_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# Breaking Changes
BREAKING_COMMITS=$(git log "$LATEST_TAG"..HEAD --grep="BREAKING CHANGE" --grep="!:" --oneline --pretty=format:"- %s (%h)")
if [ -n "$BREAKING_COMMITS" ]; then
    echo "### ⚠️ Breaking Changes"
    echo "$BREAKING_COMMITS"
    echo ""
    FOUND_COMMITS=true
fi

# If no categorized commits found, show all commits as fallback
if [ "$FOUND_COMMITS" = false ]; then
    echo "### Changes"
    git log "$LATEST_TAG"..HEAD --oneline --pretty=format:"- %s (%h)"
    echo ""
fi

# Get repository name for changelog link
REPO_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [ -n "$REPO_URL" ]; then
    REPO_NAME=$(echo "$REPO_URL" | sed 's/.*github.com[:/]\([^.]*\).*/\1/')
    echo "**Full Changelog**: https://github.com/$REPO_NAME/compare/$LATEST_TAG...v$VERSION"
else
    echo "**Full Changelog**: $LATEST_TAG...v$VERSION"
fi