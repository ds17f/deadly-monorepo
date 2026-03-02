# Changelog

## [2.10.0] - 2026-03-01

### New Features
* persist and restore playback position across app termination (858193b)
* add confirmation dialog before deleting downloads (8b2d825)
* use square icon for cover art placeholders (369920f)
* add deep link and Universal Link support for sharing (70b86d0)
* add privacy manifest and CI workflow for App Store submission (0885934)

### Bug Fixes
* silence audio blip during playback restoration seek (8fe8078)
* resume paused show instead of restarting from track 0 (a9a81d4)
* make miniplayer full width by removing horizontal padding (b378f6e)
* remove phishing heuristic triggers from share site (b870d2c)
* restrict app to iPhone only (729ba88)
* use absolute paths in xcodebuild commands (d43ec8b)
* use xcodebuild directly with API key for automatic signing (9a49c89)
* remove PROVISIONING_PROFILE_SPECIFIER to avoid SPM conflicts (af8601d)
* add xcargs to override automatic signing in CI (bc38941)
* install provisioning profile to correct directory (ca690ef)
* update iOS release workflow for macOS 15 and Ruby 3.3 (c2482b4)

### Other Changes
* chore: release ios version 2.11.0 (fec7463)
* chore(build): add android-install make target for debug APK installation (230b6fa)
* chore: release ios version 2.10.1 (fe75db0)
* chore(ios): declare non-exempt encryption status in Info.plist (5e56329)
* chore: release ios version 2.10.0 (4d46944)
* chore(build): split version.properties and CHANGELOG per platform (c29844f)
* chore: release version 2.9.1 (57013d4)


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
## [2.8.0] - 2026-02-20

### New Features
* add artwork and full metadata to AA recent item (f4c60d4)
* restore last played session on Android Auto reconnect (674cb9c)
* implement library export and import functionality (af90e93)
* add Archive.org link on share pages when recording ID is present (855278f)

### Bug Fixes
* mitigate audio skip when scanning QR code / opening deep link (b73f276)
* clean up deep link and player navigation back stack (70db741)
* prevent cached QR code images in share intent (bc3a02f)
* use custom URL scheme for "Open in App" button on share pages (696520a)

### CI Changes
* remove environment approval gate from promotion workflow (8dfd719)
## [2.7.1] - 2026-02-19

### Bug Fixes
* use deadly_logo fallback in QR share card when cover missing (c9ae037)
* add detailed logging for QR share cover image loading (fef7c78)
## [2.7.0] - 2026-02-19

### New Features
* implement force online mode for offline detection override (14c68ba)
* enhance QR share with full concert poster bitmap (aca8367)

### Bug Fixes
* skip changelog upload in promote lanes (4a1c38a)
* replace upload key with Play Store signing key in assetlinks.json (7ef45e0)

### Code Refactoring
* replace QR sheet with full-screen display dialog and simplify share flow (34092bf)
## [2.6.0] - 2026-02-18

### New Features
* add QR code and share choice sheet components (543d547)
* migrate GitHub Pages to web/ on share.thedeadly.app (c0059c5)
* implement deep link sharing via thedeadly.app (ab9848d)

### Bug Fixes
* add debug package ID to assetlinks.json (b0409a2)
* add real cert fingerprints to assetlinks.json and .nojekyll (26b252c)
* read showDate/venue/location from metadata during auto-save (a57c6b7)
## [2.5.0] - 2026-02-18

### New Features
* replace decade browse gradient overlays with curated decade photos (dd61dda)
* persist recording preferences and handle download conflicts (065b615)
## [2.4.1] - 2026-02-18

### Bug Fixes
* persist grid/list display mode preference (f40dfe6)
* remove black space above MiniPlayer and tighten padding (9d105a7)
* restore playback state across app restarts and stop on task removal (67d1cff)
* improve offline mode UX across nav and playlist screen (96ccf5c)

### Other Changes
* chore(android): remove debug UI panels and debug methods from services (e028dc0)
## [2.4.0] - 2026-02-17

### New Features
* Add offline mode with network monitoring and offline-aware navigation (800a0de)
## [2.3.0] - 2026-02-17

### New Features
* Push release notes to Play Store on deploy and promote (baa67e3)
* Add pause/resume downloads with Downloads screen integration (d551ce4)
* Make active download items tappable to navigate to show (d60f3c1)
* Add Downloads screen with cover art, navigation from Library (9883ba0)
* Add client-side sorting to search results (cfd6517)

### Bug Fixes
* Download selected recording and show 3-state track download icons (f1442f5)
* Tapping media notification now opens the app (db29a4a)

### Performance Improvements
* Fix download-related UI bottlenecks across three hot paths (cc3c55b)
## [2.2.0] - 2026-02-17

### New Features
* Add download infrastructure and wire reactive download progress to UI (e8225b5)

### Bug Fixes
* Resolve download UI reactivity — stable scroll, accurate progress, icon clearing (6965ca4)
## [2.1.1] - 2026-02-16

### Bug Fixes
* Dynamically measure miniplayer bottom padding instead of hardcoding (490efeb)
* Restructure release pipeline into multi-job workflow with gated promotions (46a5743)

### Other Changes
* security: Remove debug Play Store auth workflow (3252f6d)
* security: Add repo hardening — CODEOWNERS, workflow permissions, rulesets (45b413c)
## [2.1.0] - 2026-02-15

### New Features
* Add settings toggle to hide shows without recordings (8e16191)
* Add show cover art from ticket/photo images with data upgrade pipeline (b867e21)
* Deploy to both internal testing and closed alpha on release (1c57520)

### Other Changes
* chore(gitignore): Ignore Pi and Images (c5c60fb)
## [2.0.0] - 2026-02-15

### New Features
* Add migration import for library transfer from old app (9e32d29)
* Add platform-specific release pipeline for monorepo (DEAD-14) (512900c)
* Add ArtworkProvider ContentProvider for waveform-filtered artwork (DEAD-55) (c575c1b)
* Add artwork from Archive.org across all surfaces, filter waveform placeholders (DEAD-55) (4f53eca)
* Show date + venue instead of "Grateful Dead" in notification artist field (DEAD-54) (8d57058)
* Add Android Auto browse tree with library/display/root fixes (DEAD-53) (3fa012f)
* Add song title scrubber, venue/lyrics data fixes, Genius auth (DEAD-52) (75ba2fd)
* Add "Coming Soon" toasts to stubbed feature buttons (DEAD-43) (38cdbb9)
* Add pull-to-refresh, search navigation, and shortcut catalog (abbd91e)
* Port Android V2 app to monorepo (b6d7c54)
* initialize monorepo for iOS and Android apps (df21bba)

### Bug Fixes
* Deploy to closed testing (alpha) instead of internal testing (0c4fd41)
* Add contents:write permission for GitHub Release creation (39e4761)
* Use absolute path for Play Store JSON in Fastlane Appfile (e3fae1d)
* Bump AGP 8.6.0 → 8.13.2 and add GitHub Release to CI workflow (555b7da)
* Make featured collections reactive to database changes (6c85211)
* Use show's default recording for Android Auto playback (91fb036)
* Filter waveform thumbnails from notifications, show deadly logo instead (DEAD-55) (31a122b)
* Use single Archive.org endpoint for consistent artwork across all surfaces (DEAD-55) (d5497a4)
* Use deadly logo as fallback on all surfaces, remove music note overrides (DEAD-55) (01f6d05)
* Use resolved recordingId for playlist artwork instead of nav param (d71fde4)
* Use deadly logo as artwork fallback, add high-res images for large surfaces (DEAD-55) (926b4d3)
* Clean up build config — version path, signing, proguard, catalog (DEAD-12) (0171f22)
* restore gradle.properties without hardcoded credentials (acadf4a)
* Fix the path to the upload artifact (d9d7809)
* Copy docs from other project (0f73549)
* Copy workflow from elsewhere (96aa43b)
* Fix the broken workflow file (c4dc8fc)
* Update the docs workflow to use the makefile (5695a69)

### Code Refactoring
* Remove unused theme asset system — drop 2 modules (DEAD-40) (635e2e8)
* Rename V2/V1 code identifiers, data constants, and remove V1 toggle (DEAD-11) (0fc0b5a)
* Rename packages from com.deadly.v2 to com.grateful.deadly (DEAD-46, DEAD-47) (a6ada07)
* Merge v2/app into app/ and remove v2/ directory (DEAD-45) (1c674fe)
* Move v2/core and v2/feature modules to top level (DEAD-44) (02638d3)
* Remove dead onNavigateToPlayer from search feature (add3670)

### Documentation Updates
* Enhance MkDocs theme with navigation features, icons, and extensions (67e804f)
* Add comprehensive feature documentation for all app screens (3f98ec2)
* Add comprehensive Archive.org API integration documentation (a818011)
* Restructure architecture-patterns TODO to reflect Phase 1 completion (fc9d0ed)
* Add comprehensive database documentation for Android implementation (153a8ff)
* Add comprehensive architecture documentation and update TODO checklist (39fb971)
* Add TODO section documenting critical missing documentation (f12d2c8)
* Update the readme (a85a58d)
* Add CI/CD infrastructure and developer documentation (1a122a4)
* add MkDocs docs structure, makefile targets, CI workflow (d1cf77a)

### CI Changes
* Debug workflow - test Appfile resolution and absolute path (d082746)
* Add debug workflow for Play Store auth troubleshooting (680c8fd)

### Other Changes
* chore: release version 2.0.0 (e0757bc)
* chore: release version 2.0.0 (c98757f)
* chore: release version 2.0.0 (e8eb64c)
* Merge pull request #21 from ds17f/claude/dead-11-remove-v2-naming (b24df5a)
* chore: Eradicate all V2/V1 naming from UI strings, log tags, comments, and remaining code identifiers (DEAD-11) (fde4fce)
* chore: Clean up V2 strings, log tags, and align compileSdk (DEAD-48, DEAD-49) (43b4c27)
* chore: Clean up misleading TODO comments in fallback collections (db283d7)
* Merge pull request #20 from ds17f/claude/dead-42-fix-search-navigation (86abd9e)
* chore(git): ignore secrets directory at root (9f7da16)
* first commit (36e5323)
## [2.0.0] - 2026-02-15

### New Features
* Add migration import for library transfer from old app (9e32d29)
* Add platform-specific release pipeline for monorepo (DEAD-14) (512900c)
* Add ArtworkProvider ContentProvider for waveform-filtered artwork (DEAD-55) (c575c1b)
* Add artwork from Archive.org across all surfaces, filter waveform placeholders (DEAD-55) (4f53eca)
* Show date + venue instead of "Grateful Dead" in notification artist field (DEAD-54) (8d57058)
* Add Android Auto browse tree with library/display/root fixes (DEAD-53) (3fa012f)
* Add song title scrubber, venue/lyrics data fixes, Genius auth (DEAD-52) (75ba2fd)
* Add "Coming Soon" toasts to stubbed feature buttons (DEAD-43) (38cdbb9)
* Add pull-to-refresh, search navigation, and shortcut catalog (abbd91e)
* Port Android V2 app to monorepo (b6d7c54)
* initialize monorepo for iOS and Android apps (df21bba)

### Bug Fixes
* Use absolute path for Play Store JSON in Fastlane Appfile (e3fae1d)
* Bump AGP 8.6.0 → 8.13.2 and add GitHub Release to CI workflow (555b7da)
* Make featured collections reactive to database changes (6c85211)
* Use show's default recording for Android Auto playback (91fb036)
* Filter waveform thumbnails from notifications, show deadly logo instead (DEAD-55) (31a122b)
* Use single Archive.org endpoint for consistent artwork across all surfaces (DEAD-55) (d5497a4)
* Use deadly logo as fallback on all surfaces, remove music note overrides (DEAD-55) (01f6d05)
* Use resolved recordingId for playlist artwork instead of nav param (d71fde4)
* Use deadly logo as artwork fallback, add high-res images for large surfaces (DEAD-55) (926b4d3)
* Clean up build config — version path, signing, proguard, catalog (DEAD-12) (0171f22)
* restore gradle.properties without hardcoded credentials (acadf4a)
* Fix the path to the upload artifact (d9d7809)
* Copy docs from other project (0f73549)
* Copy workflow from elsewhere (96aa43b)
* Fix the broken workflow file (c4dc8fc)
* Update the docs workflow to use the makefile (5695a69)

### Code Refactoring
* Remove unused theme asset system — drop 2 modules (DEAD-40) (635e2e8)
* Rename V2/V1 code identifiers, data constants, and remove V1 toggle (DEAD-11) (0fc0b5a)
* Rename packages from com.deadly.v2 to com.grateful.deadly (DEAD-46, DEAD-47) (a6ada07)
* Merge v2/app into app/ and remove v2/ directory (DEAD-45) (1c674fe)
* Move v2/core and v2/feature modules to top level (DEAD-44) (02638d3)
* Remove dead onNavigateToPlayer from search feature (add3670)

### Documentation Updates
* Enhance MkDocs theme with navigation features, icons, and extensions (67e804f)
* Add comprehensive feature documentation for all app screens (3f98ec2)
* Add comprehensive Archive.org API integration documentation (a818011)
* Restructure architecture-patterns TODO to reflect Phase 1 completion (fc9d0ed)
* Add comprehensive database documentation for Android implementation (153a8ff)
* Add comprehensive architecture documentation and update TODO checklist (39fb971)
* Add TODO section documenting critical missing documentation (f12d2c8)
* Update the readme (a85a58d)
* Add CI/CD infrastructure and developer documentation (1a122a4)
* add MkDocs docs structure, makefile targets, CI workflow (d1cf77a)

### CI Changes
* Debug workflow - test Appfile resolution and absolute path (d082746)
* Add debug workflow for Play Store auth troubleshooting (680c8fd)

### Other Changes
* chore: release version 2.0.0 (c98757f)
* chore: release version 2.0.0 (e8eb64c)
* Merge pull request #21 from ds17f/claude/dead-11-remove-v2-naming (b24df5a)
* chore: Eradicate all V2/V1 naming from UI strings, log tags, comments, and remaining code identifiers (DEAD-11) (fde4fce)
* chore: Clean up V2 strings, log tags, and align compileSdk (DEAD-48, DEAD-49) (43b4c27)
* chore: Clean up misleading TODO comments in fallback collections (db283d7)
* Merge pull request #20 from ds17f/claude/dead-42-fix-search-navigation (86abd9e)
* chore(git): ignore secrets directory at root (9f7da16)
* first commit (36e5323)
## [2.0.0] - 2026-02-14

### New Features
* Add migration import for library transfer from old app (9e32d29)
* Add platform-specific release pipeline for monorepo (DEAD-14) (512900c)
* Add ArtworkProvider ContentProvider for waveform-filtered artwork (DEAD-55) (c575c1b)
* Add artwork from Archive.org across all surfaces, filter waveform placeholders (DEAD-55) (4f53eca)
* Show date + venue instead of "Grateful Dead" in notification artist field (DEAD-54) (8d57058)
* Add Android Auto browse tree with library/display/root fixes (DEAD-53) (3fa012f)
* Add song title scrubber, venue/lyrics data fixes, Genius auth (DEAD-52) (75ba2fd)
* Add "Coming Soon" toasts to stubbed feature buttons (DEAD-43) (38cdbb9)
* Add pull-to-refresh, search navigation, and shortcut catalog (abbd91e)
* Port Android V2 app to monorepo (b6d7c54)
* initialize monorepo for iOS and Android apps (df21bba)

### Bug Fixes
* Bump AGP 8.6.0 → 8.13.2 and add GitHub Release to CI workflow (555b7da)
* Make featured collections reactive to database changes (6c85211)
* Use show's default recording for Android Auto playback (91fb036)
* Filter waveform thumbnails from notifications, show deadly logo instead (DEAD-55) (31a122b)
* Use single Archive.org endpoint for consistent artwork across all surfaces (DEAD-55) (d5497a4)
* Use deadly logo as fallback on all surfaces, remove music note overrides (DEAD-55) (01f6d05)
* Use resolved recordingId for playlist artwork instead of nav param (d71fde4)
* Use deadly logo as artwork fallback, add high-res images for large surfaces (DEAD-55) (926b4d3)
* Clean up build config — version path, signing, proguard, catalog (DEAD-12) (0171f22)
* restore gradle.properties without hardcoded credentials (acadf4a)
* Fix the path to the upload artifact (d9d7809)
* Copy docs from other project (0f73549)
* Copy workflow from elsewhere (96aa43b)
* Fix the broken workflow file (c4dc8fc)
* Update the docs workflow to use the makefile (5695a69)

### Code Refactoring
* Remove unused theme asset system — drop 2 modules (DEAD-40) (635e2e8)
* Rename V2/V1 code identifiers, data constants, and remove V1 toggle (DEAD-11) (0fc0b5a)
* Rename packages from com.deadly.v2 to com.grateful.deadly (DEAD-46, DEAD-47) (a6ada07)
* Merge v2/app into app/ and remove v2/ directory (DEAD-45) (1c674fe)
* Move v2/core and v2/feature modules to top level (DEAD-44) (02638d3)
* Remove dead onNavigateToPlayer from search feature (add3670)

### Documentation Updates
* Enhance MkDocs theme with navigation features, icons, and extensions (67e804f)
* Add comprehensive feature documentation for all app screens (3f98ec2)
* Add comprehensive Archive.org API integration documentation (a818011)
* Restructure architecture-patterns TODO to reflect Phase 1 completion (fc9d0ed)
* Add comprehensive database documentation for Android implementation (153a8ff)
* Add comprehensive architecture documentation and update TODO checklist (39fb971)
* Add TODO section documenting critical missing documentation (f12d2c8)
* Update the readme (a85a58d)
* Add CI/CD infrastructure and developer documentation (1a122a4)
* add MkDocs docs structure, makefile targets, CI workflow (d1cf77a)

### Other Changes
* chore: release version 2.0.0 (e8eb64c)
* Merge pull request #21 from ds17f/claude/dead-11-remove-v2-naming (b24df5a)
* chore: Eradicate all V2/V1 naming from UI strings, log tags, comments, and remaining code identifiers (DEAD-11) (fde4fce)
* chore: Clean up V2 strings, log tags, and align compileSdk (DEAD-48, DEAD-49) (43b4c27)
* chore: Clean up misleading TODO comments in fallback collections (db283d7)
* Merge pull request #20 from ds17f/claude/dead-42-fix-search-navigation (86abd9e)
* chore(git): ignore secrets directory at root (9f7da16)
* first commit (36e5323)
## [2.0.0] - 2026-02-14

### New Features
* Add migration import for library transfer from old app (9e32d29)
* Add platform-specific release pipeline for monorepo (DEAD-14) (512900c)
* Add ArtworkProvider ContentProvider for waveform-filtered artwork (DEAD-55) (c575c1b)
* Add artwork from Archive.org across all surfaces, filter waveform placeholders (DEAD-55) (4f53eca)
* Show date + venue instead of "Grateful Dead" in notification artist field (DEAD-54) (8d57058)
* Add Android Auto browse tree with library/display/root fixes (DEAD-53) (3fa012f)
* Add song title scrubber, venue/lyrics data fixes, Genius auth (DEAD-52) (75ba2fd)
* Add "Coming Soon" toasts to stubbed feature buttons (DEAD-43) (38cdbb9)
* Add pull-to-refresh, search navigation, and shortcut catalog (abbd91e)
* Port Android V2 app to monorepo (b6d7c54)
* initialize monorepo for iOS and Android apps (df21bba)

### Bug Fixes
* Make featured collections reactive to database changes (6c85211)
* Use show's default recording for Android Auto playback (91fb036)
* Filter waveform thumbnails from notifications, show deadly logo instead (DEAD-55) (31a122b)
* Use single Archive.org endpoint for consistent artwork across all surfaces (DEAD-55) (d5497a4)
* Use deadly logo as fallback on all surfaces, remove music note overrides (DEAD-55) (01f6d05)
* Use resolved recordingId for playlist artwork instead of nav param (d71fde4)
* Use deadly logo as artwork fallback, add high-res images for large surfaces (DEAD-55) (926b4d3)
* Clean up build config — version path, signing, proguard, catalog (DEAD-12) (0171f22)
* restore gradle.properties without hardcoded credentials (acadf4a)
* Fix the path to the upload artifact (d9d7809)
* Copy docs from other project (0f73549)
* Copy workflow from elsewhere (96aa43b)
* Fix the broken workflow file (c4dc8fc)
* Update the docs workflow to use the makefile (5695a69)

### Code Refactoring
* Remove unused theme asset system — drop 2 modules (DEAD-40) (635e2e8)
* Rename V2/V1 code identifiers, data constants, and remove V1 toggle (DEAD-11) (0fc0b5a)
* Rename packages from com.deadly.v2 to com.grateful.deadly (DEAD-46, DEAD-47) (a6ada07)
* Merge v2/app into app/ and remove v2/ directory (DEAD-45) (1c674fe)
* Move v2/core and v2/feature modules to top level (DEAD-44) (02638d3)
* Remove dead onNavigateToPlayer from search feature (add3670)

### Documentation Updates
* Enhance MkDocs theme with navigation features, icons, and extensions (67e804f)
* Add comprehensive feature documentation for all app screens (3f98ec2)
* Add comprehensive Archive.org API integration documentation (a818011)
* Restructure architecture-patterns TODO to reflect Phase 1 completion (fc9d0ed)
* Add comprehensive database documentation for Android implementation (153a8ff)
* Add comprehensive architecture documentation and update TODO checklist (39fb971)
* Add TODO section documenting critical missing documentation (f12d2c8)
* Update the readme (a85a58d)
* Add CI/CD infrastructure and developer documentation (1a122a4)
* add MkDocs docs structure, makefile targets, CI workflow (d1cf77a)

### Other Changes
* Merge pull request #21 from ds17f/claude/dead-11-remove-v2-naming (b24df5a)
* chore: Eradicate all V2/V1 naming from UI strings, log tags, comments, and remaining code identifiers (DEAD-11) (fde4fce)
* chore: Clean up V2 strings, log tags, and align compileSdk (DEAD-48, DEAD-49) (43b4c27)
* chore: Clean up misleading TODO comments in fallback collections (db283d7)
* Merge pull request #20 from ds17f/claude/dead-42-fix-search-navigation (86abd9e)
* chore(git): ignore secrets directory at root (9f7da16)
* first commit (36e5323)
