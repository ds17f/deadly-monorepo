---
icon: material/code-braces
---

# Developer Guide

Welcome to the Deadly app developer documentation. This section contains guides for building, releasing, and maintaining the Deadly apps.

## Getting Started

The Deadly project is a monorepo containing separate native iOS and Android applications that share a common purpose: providing access to the Grateful Dead's vast archive of live recordings.

### Repository Structure

```
deadly-monorepo/
├── androidApp/        # Android V2 application
│   ├── app/          # Main app module (wrapper)
│   ├── v2/           # V2 architecture modules
│   │   ├── app/      # Core V2 app module
│   │   ├── core/     # Core modules (database, network, design, theme)
│   │   └── feature/  # Feature modules (home, search, library, player, etc.)
│   └── fastlane/     # Android build automation
├── iosApp/           # iOS native application
│   └── fastlane/     # iOS build automation
├── version.properties # Single source of truth for versioning
├── scripts/          # Release and automation scripts
└── docs/             # Documentation (you are here!)
```

## Quick Links

### Release Management
- **[Version Management](version-management.md)** - How versioning works, conventional commits, and creating releases
- **[CI/CD System](ci-cd.md)** - Complete guide to automated builds, GitHub Actions, and secrets

### Build Guides
- **Android**: See [CI/CD documentation](ci-cd.md#android-fastlane) for fastlane lanes
- **iOS**: See [CI/CD documentation](ci-cd.md#ios-fastlane) for fastlane lanes

## Common Tasks

### Creating a Release

```bash
# Preview what would be released
make release-dry-run

# Create release with automatic version from commits
make release

# Create release with specific version
make release-version VERSION=1.2.3
```

### Building Locally

**Android:**
```bash
make android-build-release    # Build signed APK
make android-build-bundle      # Build signed AAB
```

**iOS:**
```bash
make ios-build-release        # Build signed IPA
```

### Deploying to Stores

**Android - Play Store Internal Testing:**
```bash
make android-deploy-testing
```

**iOS - TestFlight:**
```bash
make ios-deploy-testflight
```

## Architecture

### Android V2
The Android app uses a modular V2 architecture with:
- Feature modules (home, search, library, player, etc.)
- Core modules (database, network, design, theme)
- Jetpack Compose UI
- Hilt dependency injection
- Room database with FTS

### iOS
The iOS app is a native SwiftUI application with:
- SwiftUI declarative UI
- Combine for reactive programming
- Core Data for local storage
- AVFoundation for audio playback

## Development Workflow

1. **Create feature branch** from `main`
2. **Make commits** using [conventional commit format](version-management.md#conventional-commits)
3. **Test locally** on both platforms if possible
4. **Create pull request** to `main`
5. **After merge**, create release with `make release`
6. **GitHub Actions** automatically build both platforms
7. **Download artifacts** from Actions tab or deploy to stores

## Getting Help

- **Build Issues**: See [CI/CD troubleshooting](ci-cd.md#troubleshooting)
- **Release Issues**: See [Version Management troubleshooting](version-management.md#troubleshooting)
- **GitHub**: [Create an issue](https://github.com/ds17f/deadly-monorepo/issues)

## Contributing

Contributions are welcome! Please:
1. Use conventional commit format for all commits
2. Test on both platforms when possible
3. Update documentation when adding features
4. Follow existing code style and patterns

---

**Next Steps:**
- Read the [Version Management](version-management.md) guide
- Learn about [CI/CD System](ci-cd.md)
- Explore the codebase structure
