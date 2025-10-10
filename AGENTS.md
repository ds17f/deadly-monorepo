# Repository Guidelines

## Project Structure & Module Organization
```
deadly/                # iOS Swift UI app (KMM shared module)
dead/v2/               # Android Kotlin app (new primary Android entry point)
shared/                # Optional KMM common code, if reused
README.md
AGENTS.md              # This contributor guide
```
* **iOS** source lives under `deadly` – Swift UI files, Xcode project and any KMM shared code.
* **Android** source lives under `dead/v2` – Gradle‑based Kotlin app.
* Place new shared Kotlin/Swift modules in a top‑level `shared/` directory.

## Build, Test, & Development Commands
- **iOS**: `cd deadly && xcodebuild -scheme DeadlyApp -configuration Debug`
  – builds the iOS app.
- **Android**: `cd dead/v2 && ./gradlew assembleDebug`
  – compiles the Android APK.
- **Run Tests (iOS)**: `xcodebuild test -scheme DeadlyAppTests`
- **Run Tests (Android)**: `./gradlew test`

## Coding Style & Naming Conventions
* **Swift**: 4‑space indentation, `CamelCase` for types, `lowerCamelCase` for variables/functions. Run `swiftlint`.
* **Kotlin**: 4‑space indentation, `PascalCase` for classes, `camelCase` for members. Use `ktlint`.
* **Git paths**: keep folder names lowercase with hyphens if needed (`deadly`, `dead-v2`).

## Testing Guidelines
* iOS uses XCTest – test files end with `Tests.swift` and reside alongside the target they verify.
* Android uses JUnit/AndroidX – test classes end with `Test.kt` inside `src/test/java` or `src/androidTest/java`.
* Aim for **≥80 %** line coverage; run `./gradlew jacocoTestReport` for Android and check Xcode coverage reports.

## Commit & Pull Request Guidelines
* **Commit messages** – start with a short imperative title (≤50 chars), blank line, then optional description. Example:
  ```
  Add login screen UI

  Refactor AuthViewModel to use StateFlow.
  ```
* **PRs** must reference an issue (`Fixes #123`), include a concise description of changes, and attach screenshots for UI updates.
* Keep PRs focused: one functional change per PR whenever possible.

## Additional Notes
* **Security** – never commit API keys; use environment variables or the `local.properties` file (ignored by Git).
* **CI** – the repository uses GitHub Actions to run both iOS and Android builds on every push.

