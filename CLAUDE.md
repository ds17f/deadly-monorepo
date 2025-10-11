# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Architecture

This is a **monorepo** containing separate iOS and Android native applications:

- **iosApp/** - iOS application using SwiftUI, managed with Xcode
  - `deadly.xcodeproj/` - Xcode project
  - `deadly/` - Main Swift source files
  - `deadlyTests/` - Unit tests (XCTest)
  - `deadlyUITests/` - UI tests

- **androidApp/** - Android application using Kotlin, managed with Gradle
  - `app/` - Main application module
  - Package: `com.grateful.deadly`
  - Namespace: `com.grateful.deadly`
  - Min SDK: 24, Target/Compile SDK: 36
  - JVM Target: Java 11

**Note**: The directory structure differs from AGENTS.md documentation. Actual paths are `iosApp/` and `androidApp/`, not `deadly/` and `dead/v2/`.

## Build & Development Commands

### Android
```bash
# From repository root
cd androidApp
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Install debug APK to connected device
```

### iOS
```bash
# From repository root
cd iosApp
xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug build
xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Release build
```

## Testing

### Android
```bash
cd androidApp
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew jacocoTestReport       # Generate coverage report (target: ≥80%)
```

Test structure:
- Unit tests: `app/src/test/java/com/grateful/deadly/`
- Instrumented tests: `app/src/androidTest/java/com/grateful/deadly/`
- Runner: `androidx.test.runner.AndroidJUnitRunner`

### iOS
```bash
cd iosApp
xcodebuild test -project deadly.xcodeproj -scheme deadly -destination 'platform=iOS Simulator,name=iPhone 15'
```

Test structure:
- Unit tests: `deadlyTests/` (XCTest framework)
- UI tests: `deadlyUITests/`

## Code Style

### Kotlin (Android)
- 4-space indentation
- `PascalCase` for classes
- `camelCase` for members
- Use `ktlint` for linting

### Swift (iOS)
- 4-space indentation
- `CamelCase` for types
- `lowerCamelCase` for variables/functions
- Use `swiftlint` for linting

## Gradle Configuration

The Android project uses version catalogs (`libs.versions.toml`) for dependency management:
- Plugins: `android.application`, `kotlin.android`
- AndroidX libraries managed via catalog aliases

Gradle properties:
- JVM args: `-Xmx2048m`
- AndroidX enabled
- Kotlin code style: `official`
- Non-transitive R classes enabled

## Important Notes

- **Security**: Never commit API keys. Use environment variables or `local.properties` (git-ignored)
- **Project names**: iOS Xcode project name is "deadly", Android rootProject name is "Deadly"
- **No shared code yet**: While AGENTS.md mentions KMM shared modules, none currently exist
- **Future KMM**: Place shared Kotlin Multiplatform modules in top-level `shared/` directory when needed
- Always use the make targets in the makefile if there is a suitable one