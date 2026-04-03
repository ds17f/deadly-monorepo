# Changelog

## [2.26.0] - 2026-04-03

### New Features
* add settings tracking to match Android parity (a7159106)


## [2.25.0] - 2026-04-03

### New Features
* send search query text in search events (ae0890f0)

### Other Changes
* chore: add ANALYTICS_API_KEY and GOOGLE_ANDROID_CLIENT_ID to secrets setup script (9200c871)
## [2.24.1] - 2026-04-02
## [2.24.0] - 2026-03-31

### CI Changes
* filter web and infra commits from mobile changelogs (d36ebe8d)
## [2.22.0] - 2026-03-24

### New Features
* add anonymous usage analytics with opt-out support (a2c63c49)
* add custom dev server URL in developer settings (7bcf952b)
* add mobile Google and Apple Sign-In with unified connect transport (b4a8b192)
* add beta share links toggle in developer settings (554d2d04)
* add beta share domain and consolidate share landing page (68fcde08)
* consolidate data pipeline into monorepo and wire versioned releases (e6e5957e)
* simplify sharing to URL-only and align share URLs to /shows/ path (1d5095c0)

### Bug Fixes
* wire ANALYTICS_API_KEY into CI/CD and release workflows (7afe976f)
* pass Google mobile client IDs to API container (7f873a9b)

### Documentation Updates
* update dead-metadata refs to monorepo data pipeline (7bf56288)

### Other Changes
* chore(ci): standardize workflow display names with platform prefix (6d6a1059)
* chore(ci): Add makefile targets for full rebuild of docker remote and logs (2e8ba1b7)
* chore: rename api-remote-* targets to docker-remote-* and update remote path (714be733)
## [2.21.0] - 2026-03-15

### New Features
* re-enable CarPlay audio entitlement and scene config (209b723)
* add source type badge with persisted data and user settings (9806fac)

### Other Changes
* chore: commit changes to gitignore for keys (4e6b1f5)
## [2.20.0] - 2026-03-13

### New Features
* replace text store links with Google Play badge images (903e5fe)
* replace coming soon text with Google Play Store link (c3ba97f)
* add privacy policy section to legal screen (315aa81)
* add privacy policy page and footer links (bacf52b)
* add missing actions to player and playlist menu sheets (5b2aa97)
* add long-press detail popovers, fix font scaling, and improve accessibility (84009a6)
* add share chooser with message and image sharing (415be97)
* add CarPlay, Android Auto browse enhancements, and DHU targets (ddf03d1)
* move filter chips above count/sort row (a2494a2)
* era browse loads all shows, chips filter client-side (02f5d94)
* add decade filter chips to search results (08f6b0f)

### Bug Fixes
* remove CarPlay config and add INIntentsSupported for Siri (8604f00)
* remove carplay-audio entitlement to unblock release (0c7c686)
* remove 40px gap under filter chips (ebc2cfe)
* fix decade buttons and header count display (5480fcb)
* "All" chip loads all decades instead of leaving results (84bb476)
* tapping "All" chip clears era browse (baf10e1)

### CI Changes
* fix empty release notes across all platforms (d69bc21)

### Other Changes
* Revert "chore: release ios version 2.20.0" (e6475e9)
## [2.19.0] - 2026-03-09

### New Features
* add graphic equalizer with presets (9427c0c)
## [2.18.0] - 2026-03-08

### New Features
* add "See Reviews" to search result long-press menu (b9ac4e7)
* add long-press to toggle favorites on search results (a131165)
* reverse toggle to "Include shows without recordings" (c39559f)

### Bug Fixes
* white text with crimson icons and chevrons on action rows (138a6db)
* use white text for nav bar title instead of tint color (2b2e7d2)
* force opaque tab bar via UIKit appearance proxy (4cc9f13)
* opaque nav bar and header padding for Xcode 26 build (1208792)
* use unencoded slash in GitHub release URLs (8f74215)
* disable Liquid Glass via UIDesignRequiresCompatibility (bc441ea)
* update simulator destination from iPhone 16 to iPhone 17 for Xcode 26 (7a9172c)

### CI Changes
* update GitHub Actions runners from macos-15 to macos-26 (b4059aa)
## [2.17.0] - 2026-03-08

### New Features
* add "Has Review" sort option to favorites list (0be528d)
* default results to sort by show date ascending (cbf7949)
* replace .searchable() with custom search bar (019ef40)

### Bug Fixes
* show download icon on downloaded shows in list view (7ccb26f)
* include version field in backup JSON export (2ec2569)
* remove tag filter chips causing phantom gap (0f814e2)

### CI Changes
* move PROVISIONING_PROFILE_SPECIFIER to project target settings (b2cb14b)
* add CODE_SIGN_IDENTITY to gym xcargs for distribution builds (1a02efb)
## [2.16.0] - 2026-03-07

### New Features
* Adds tap logo for settings-panel (062ef7a)
* add show/song counts to sort controls row (0b929d2)

### Bug Fixes
* eliminate race condition in export favorites sheet (8960a51)
* make review icon reactive, use star indicator, populate lineup (6ad93a8)
* align color scheme — crimson/gold/green palette, fix downloads nav (a473cd3)
* always show review state indicator with color coding (d14fe8e)

### Code Refactoring
* replace track_reviews with favorite_songs table (dc8e34e)

### CI Changes
* use manual signing in deploy_testflight to stop cert proliferation (d7cb4f5)

### Other Changes
* Revert "fix(mobile/ui): always show review state indicator with color coding" (4622c79)
## [2.15.0] - 2026-03-06

### New Features
* move favorites menu to settings screen (3761cfe)
* add Shows/Songs tabs with favorite tracks browsing (0a39753)
* rename library to favorites across iOS and Android (0f715ba)
* decouple recording preferences from library, add v3 backup format (b6f5252)
* add review system with ratings, favorites, and delete support (eeb8fd6)

### Bug Fixes
* refresh home screen and restore playback after data import (afcec3f)
* compact hierarchical filter chips with "All" chip (7c91513)
* show playing state for downloaded shows in detail screen (5917e15)
* show favorite hearts on track list (737dca5)
* make favorite heart update reactively via GRDB observation (75bdd5d)
## [2.14.0] - 2026-03-03

### New Features
* unify share UX with QR card as single action (48bea44)
## [2.13.0] - 2026-03-03

### New Features
* action sheet on link open + player icon row (2ffd91f)
* add branded QR code share card (22e58c8)

### Bug Fixes
* return to source tab when navigating from now playing (7301bae)
* add white padding ring around QR logo, matching android (cb9370d)
* handle universal links via NSUserActivity (a3bb615)
* navigate to playing show, not browsed show (43c84f7)
* defer navigation after fullscreen cover dismisses (38f0d97)
* always navigate to show from player header (b8aac2f)
* use round circle logo in QR center, matching android (a039c25)
* clip QR logo overlay to circle (5704e9d)
* show loading state immediately on track tap (cb2a737)

### Code Refactoring
* redesign Our Mission and Legal screens as article-style layouts (973db65)
* simplify About section and direct version to release notes (204908d)
* add permanent developer panel and increase typography sizes (8a312e4)
* adopt platform-idiomatic layouts for Android and iOS (d38f2ac)
* redesign with dev mode, legal screen, and release notes (3f4ed3b)
## [2.12.0] - 2026-03-02

### New Features
* warn user before switching recordings with active downloads (d7b4cec)
* allow navigation to show detail from active/paused downloads (29f6ed8)

### Bug Fixes
* prevent share dialog from closing immediately on detail screen (9256d07)

### Code Refactoring
* reorganize sections with new developer section (c19b36d)

### Other Changes
* chore(build): enforce platform-scoped commits and filter changelogs by platform (838d47d)
## [2.11.0] - 2026-03-01

### New Features
* persist and restore playback position across app termination (858193b)
* add confirmation dialog before deleting downloads (8b2d825)

### Bug Fixes
* silence audio blip during playback restoration seek (8fe8078)
* resume paused show instead of restarting from track 0 (a9a81d4)
* make miniplayer full width by removing horizontal padding (b378f6e)
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
