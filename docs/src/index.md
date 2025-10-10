# Deadly Monorepo Documentation

> Welcome to the documentation for the **Deadly** monorepo, which hosts native iOS and Android applications built from a shared Kotlin Multiplatform foundation.

## Overview
- Two platform apps live in `iosApp/` (Swift UI) and `androidApp/` (Kotlin).
- Shared KMM code can be placed under `shared/`.
- See the **Repository Guidelines** (`AGENTS.md`) for contribution standards, build commands, and project layout.

## Getting Started
1. Clone the repository.
2. Follow the setup instructions in `README.md` to install required tools (Xcode, Android Studio, Gradle).
3. Run the appropriate build command:
   - iOS: `cd iosApp && xcodebuild ...`
   - Android: `cd androidApp && ./gradlew assembleDebug`

---

*This site is generated with **MkDocs** and published via GitHub Pages.*
