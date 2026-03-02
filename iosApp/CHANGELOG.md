# Changelog

## [2.11.0] - 2026-03-01

### New Features
* persist and restore playback position across app termination (858193b)
* add confirmation dialog before deleting downloads (8b2d825)
* use square icon for cover art placeholders (369920f)

### Bug Fixes
* silence audio blip during playback restoration seek (8fe8078)
* resume paused show instead of restarting from track 0 (a9a81d4)
* make miniplayer full width by removing horizontal padding (b378f6e)

### Other Changes
* chore(build): add android-install make target for debug APK installation (230b6fa)


## [2.10.1] - 2026-03-01

### Other Changes
* chore(ios): declare non-exempt encryption status in Info.plist (5e56329)
## [2.10.0] - 2026-03-01

### New Features
* add deep link and Universal Link support for sharing (70b86d0)
* add privacy manifest and CI workflow for App Store submission (0885934)

### Bug Fixes
* remove phishing heuristic triggers from share site (b870d2c)

### Other Changes
* chore(build): split version.properties and CHANGELOG per platform (c29844f)
## [2.9.1] - 2026-02-27

### Bug Fixes
* restrict app to iPhone only (729ba88)
## [2.9.0] - 2026-02-27

### New Features
* DEAD-143 search screen visual parity with Android (78d4ea1)
* DEAD-147 mini player progress bar, subtitle format, theme-adaptive UI (61243e0)
* DEAD-154 add sliding window download queue for in-order track downloads (fba7e59)
* DEAD-154 add setlist sheet to show detail screen (14aa4d8)
* DEAD-154 add review details sheet to show detail screen (d9f2d40)
* DEAD-145 show detail screen — layout redesign to match Android (9193feb)
* DEAD-144 library screen — pin, context menu, grid redesign (7104d2b)
* add home screen parity with Android (ae4d92a)
* add next/previous show navigation with chronological browsing (815289f)
* show app logo in lock screen and Now Playing when artwork unavailable (7c62efb)
* integrate offline download management across app architecture (DEAD-133) (80ee140)
* implement offline download management with progress tracking (eb3646d)
* introduce MiniPlayerService adapter to decouple mini player from StreamPlayer (DEAD-105) (4fdc90d)
* build settings screen with preferences, cache mgmt, and about page (DEAD-119) (751df4d)
* redesign splash screens with branding and quotes (DEAD-101) (a93347a)
* add library import/export to Library screen for migration (5b32b87)
* add offline detection and automatic play tracking (b00b464)
* implement show recording filter and persist recording selection (1e2e786)
* implement Search screen with browse shortcuts and result filtering (d397803)
* implement Collections feature with tag filtering and search (dd7967e)
* implement Library service and screen with filtering and sorting (a5b7087)
* move library and share actions to show detail action row (4576382)
* implement full player screen with lyrics, venue info, and share (4ad2e1b)
* implement app architecture with audio playback and database layers (b920843)

### Bug Fixes
* use build setting variables for version in Info.plist (1751d0f)
* fix Fastlane gym export and add ExportOptions.plist (d95756b)
* resolve cover art cache key collision (984b0f6)
* DEAD-157 move offline banner below nav bar (9cf02f7)
* reduce excess whitespace in library screen filter chips (925c6dd)
* add disk caching and prefetching to eliminate artwork flash (bcbddc5)
* update library and download state when navigating shows (7103f69)
* show offline message when show not downloaded (672528b)
* add disk caching to ArchiveMetadataClient for offline playback (540f4af)
* eliminate placeholder flash in TIGDH carousel with image caching (f4871e1)
* resolve playback bugs for background audio, track times, and error handling (0446e59)
* prevent mini player overlay from covering scrollable content (3393830)
* fall back to expired cache when API is unavailable (00fe319)
* make Genius API token available in release builds (57793e9)

### Other Changes
* chore: add .build/ and *.db to gitignore (1cc4596)
* micro: DEAD-157 fix offline tab interception with onChange (e03ecd4)
* micro: DEAD-157 navigate directly to Downloads when offline (fc07486)
* micro: DEAD-157 offline navigation behavior and bottom banner (1dfb52c)
* micro: use square logo for artwork placeholders (0582c6e)
* micro: add iOS app icon with full-bleed red/blue lightning bolt (f8d6c37)
