# Changelog

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

