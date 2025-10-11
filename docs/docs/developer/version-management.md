# Version Management

This document describes the automated version management system for the Deadly monorepo, which keeps Android and iOS versions synchronized.

## Overview

Version management in this project is fully automated using:

- **Single Source of Truth**: `version.properties` at project root
- **Conventional Commits**: Version bumps determined automatically from commit messages
- **Cross-Platform**: Both Android and iOS use the same version numbers
- **Automated Releases**: GitHub Actions builds and publishes releases when tags are pushed

## Version Properties File

The `version.properties` file at the project root contains:

```properties
# Version configuration for both Android and iOS
# This is the single source of truth for app versioning
VERSION_NAME=1.0.0
VERSION_CODE=1
```

- `VERSION_NAME`: User-facing version (e.g., "1.0.0")
- `VERSION_CODE`: Build number (increments with each release)

Both Android and iOS read from this file during the build process.

## How It Works

### Android

The `androidApp/app/build.gradle.kts` reads from `version.properties`:

```kotlin
val versionPropsFile = rootProject.file("../../version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { versionProps.load(it) }
}
val appVersionName = versionProps.getProperty("VERSION_NAME") ?: "1.0.0"
val appVersionCode = versionProps.getProperty("VERSION_CODE")?.toIntOrNull() ?: 1

android {
    defaultConfig {
        versionCode = appVersionCode
        versionName = appVersionName
    }
}
```

### iOS

The iOS fastlane `update_version_from_properties` private lane reads from `version.properties` and updates the Xcode project:

```ruby
version_props = File.read("../../version.properties")
version_name = version_props.match(/VERSION_NAME=(.+)/)[1].strip
version_code = version_props.match(/VERSION_CODE=(.+)/)[1].strip

# Update Xcode project build settings
project.targets.each do |target|
  target.build_configurations.each do |config|
    config.build_settings["MARKETING_VERSION"] = version_name
    config.build_settings["CURRENT_PROJECT_VERSION"] = version_code
  end
end
```

## Creating Releases

### Automatic Version Bumping (Recommended)

The release script analyzes your commits using conventional commit format and automatically determines the version bump:

```bash
# Dry run to see what would happen
make release-dry-run

# Create release with automatic versioning
make release
```

**Version Bump Rules:**

- **Major** (1.0.0 → 2.0.0): Commits with breaking changes (`feat!:`, `fix!:`, etc.)
- **Minor** (1.0.0 → 1.1.0): New features (`feat:`)
- **Patch** (1.0.0 → 1.0.1): Bug fixes (`fix:`) or any other commits

### Manual Version Specification

To specify a version manually:

```bash
# Create release with specific version
make release-version VERSION=1.2.3
```

## Conventional Commits

Use these commit message prefixes to enable automatic version bumping:

- `feat:` - New feature (minor version bump)
- `fix:` - Bug fix (patch version bump)
- `feat!:` or `fix!:` - Breaking change (major version bump)
- `docs:` - Documentation changes
- `refactor:` - Code refactoring
- `test:` - Test changes
- `build:` - Build system changes
- `ci:` - CI/CD changes
- `perf:` - Performance improvements

**Examples:**

```bash
git commit -m "feat: add search history feature"
git commit -m "fix: resolve crash on startup"
git commit -m "feat!: redesign navigation system"
git commit -m "docs: update release process documentation"
```

## Release Workflow

When you run `make release`:

1. **Analyze Commits**: Script checks commits since last tag using conventional commit format
2. **Determine Version**: Automatically calculates new version (major/minor/patch)
3. **Generate Changelog**: Creates CHANGELOG.md from commit messages
4. **Update Files**: Updates `version.properties` with new version and incremented build code
5. **Git Operations**: Commits changes, creates annotated tag, pushes to origin
6. **GitHub Actions**: Workflows trigger automatically on tag push
7. **Automatic Deployment**: Both platforms deploy to testing tracks
   - Android: Play Store Internal Testing
   - iOS: TestFlight

### GitHub Actions Build and Deployment

When a tag matching `v*` is pushed (e.g., `v1.0.0`):

1. **Build Android**: Builds signed APK and AAB using fastlane
2. **Deploy Android**: Automatically deploys to Play Store Internal Testing
3. **Build iOS**: Builds signed IPA using fastlane
4. **Deploy iOS**: Automatically deploys to TestFlight
5. **Upload Artifacts**: Both builds uploaded as backup artifacts (30-day retention)

**Result**: Your release is automatically available to testers on both platforms!

## Files Modified During Release

The `scripts/release.sh` script modifies:

- `version.properties` - Updates VERSION_NAME and VERSION_CODE
- `CHANGELOG.md` - Adds new version section with commits

During the build:

- **Android**: `build.gradle.kts` reads from `version.properties`
- **iOS**: Fastlane updates the Xcode project from `version.properties`

## Common Tasks

### Check Current Version

```bash
cat version.properties
```

### Preview Release Without Making Changes

```bash
make release-dry-run
```

This shows:
- What version will be created
- Which commits will be included
- What the changelog will contain

### Create Release Automatically

```bash
make release
```

The script will:
- Analyze commits
- Determine version bump
- Generate changelog
- Create and push git tag
- Trigger GitHub Actions

### Create Release With Specific Version

```bash
make release-version VERSION=2.0.0
```

### View Recent Releases

```bash
git tag -l
git show v1.0.0  # Show tag details
```

## Troubleshooting

### "No changes detected since last release"

You need at least one commit since the last tag. Make a commit with a conventional commit message.

### "Tag already exists"

The version already has a tag. Either delete the tag or specify a different version:

```bash
# Delete local tag
git tag -d v1.0.0

# Delete remote tag
git push origin :refs/tags/v1.0.0
```

### GitHub Actions Not Triggering

Ensure:

1. Tag was pushed to origin: `git push origin v1.0.0`
2. Tag matches pattern `v*` (e.g., `v1.0.0`, not `1.0.0`)
3. GitHub Actions are enabled in repository settings

### Version Mismatch Between Platforms

The automated system prevents this by using `version.properties` as the single source of truth. Both platforms read from the same file during build.

## Best Practices

1. **Use Conventional Commits**: Enables automatic version bumping and changelog generation
2. **Dry Run First**: Always run `make release-dry-run` before actual release
3. **Review Changelog**: Check that generated CHANGELOG.md is accurate
4. **Test Builds**: Run local builds before pushing tags
5. **Keep Versions Synced**: Never manually edit version.properties without also updating git tags

## Example Release Session

```bash
# 1. Make some commits
git commit -m "feat: add playlist sharing"
git commit -m "fix: resolve audio playback issue"
git commit -m "docs: update README"

# 2. Preview the release
make release-dry-run

# Output shows:
# - New version will be 1.1.0 (minor bump due to feat:)
# - VERSION_CODE will increment from 5 to 6
# - Changelog will include all three commits

# 3. Create the release
make release

# Script will:
# - Update version.properties to 1.1.0, code 6
# - Generate CHANGELOG.md
# - Commit changes
# - Create tag v1.1.0
# - Push to GitHub

# 4. GitHub Actions automatically builds and deploys to testing tracks
#    - Android: Play Store Internal Testing
#    - iOS: TestFlight
# 5. Test the releases on both platforms
```

## References

- [Conventional Commits Specification](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [CI/CD Documentation](ci-cd.md)
