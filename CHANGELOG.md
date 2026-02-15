# Changelog

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

