# Prebuilt catalog DB — first-launch fast path

Replace the slow first-launch JSON import (download 25 MB `data.zip` → extract
~20k JSON → parse → insert → build FTS, minutes on venue cell) with a prebuilt
SQLite **catalog seed** built in CI and bulk-copied into each app's DB.

Binding decisions + trade-offs: [`docs/adr/0007-prebuilt-catalog-db.md`](../docs/adr/0007-prebuilt-catalog-db.md).

## Release & versioning mechanics (how a seed reaches a device) — 2026-06-08

The thing that unblocks device testing without risking users:

- **Both apps pin an EXACT data tag, not "latest".** iOS
  `GitHubReleasesClient.swift:17` `dataVersion = "2.3.0"` → fetches
  `releases/tags/data-v2.3.0` (the method is *named* `fetchLatestRelease()` but is
  pinned). Android `GitHubDataService.kt:21` `REQUIRED_DATA_VERSION = "2.3.0"` →
  `getRelease("data-v2.3.0")`. A shipped binary only ever fetches the tag baked into
  it → **publishing a new `data-vX` tag never reaches production users** until an app
  update bumps the constant. So device-test (Phase 3/2 device runs) and publish are
  NOT chicken-and-egg — cut throwaway test tags freely.
- **Asset discovery is additive:** iOS `catalogDbAsset` finds `catalog*.zip`, falls
  back to `data.zip`. The live `data-v2.3.0` release has only `data.zip` (no seed),
  which is why on-device testing needs a NEW tag that carries `catalog.db.zip`.
- **Versioning: NOT 3.0.0.** Data *content* is unchanged (same `stage02`; `data.zip`
  byte-identical) — `catalog.db.zip` is additive packaging. Scheme:
  - throwaway test cuts → **patch** (`data-v2.3.1`, `.2`, …)
  - the real ship → **minor** `data-v2.4.0` ("package gained a seed artifact")
  - reserve **major** `3.0.0` for the composite-PK / `recording_shows` schema break
    (the Known-limitation below). Major/minor is purely cosmetic anyway since apps
    pin exact tags (no range-matching).
- **Cut the tag from THIS branch, not main.** `data-release.yml` triggers on tag
  name `data-v*` regardless of branch, and for a tag push GitHub runs the
  workflow + builder **as they exist at the tagged commit**. The builder
  (`build_catalog_db.py`) + the seed-aware workflow live **only on
  `prebuilt-catalog-db`** (not merged to main) → tagging main yields a release with
  only `data.zip`. CI reads the version from the **tag name**
  (`--version ${GITHUB_REF_NAME#data-v}`), not `data/version`, and self-rebuilds
  `stage02` from the stage01 cache.
- **Two ways to tag:**
  - `make data-release VERSION=X` — writes `data/version`, commits
    `chore(all/data): bump…` to the branch, tags, and **only prints** a push hint
    (does NOT push; unlike app `release.sh` it does not auto-push or trigger store
    builds). ⚠️ Do **not** run its printed `git push origin main data-v…` hint.
  - Throwaway test cut (preferred while iterating) — manual tag, no branch churn:
    `git tag data-v2.3.1 HEAD && git push origin data-v2.3.1` (tag only, moves no
    branch ref). Use the make target for the real `2.4.0` ship where the
    `data/version` bump is meaningful.
- **Device testing also needs the app constants bumped** to the test version in the
  LOCAL dev build (iOS `dataVersion`, Android `REQUIRED_DATA_VERSION`); set them to
  the real ship version (`2.4.0`) before merging the feature.

## Status (2026-06-08)

- 🟡 **Phase 3 iOS — CODE COMPLETE, UNCOMMITTED** in this worktree (16 changed
  files). App target builds; test-target connect-v2 drift repairs are in the working
  tree but unverified. Commit split: Phase 3 feature commit + a separate
  `fix(ios/tests):` for the connect-v2 repair. See the Phase 3 block below.
- **Next concrete steps:** (1) commit Phase 3; (2) run only the seed tests
  (`-only-testing:deadlyTests/SeedImportServiceTests
  -only-testing:deadlyTests/CatalogSeedSchemaDriftTests` — whole target still must
  compile, the slow part); (3) cut `data-v2.3.1` from this branch + bump the two app
  constants locally → Android & iOS device first-launch runs; iterate via patch
  bumps; (4) real ship at `data-v2.4.0`.

## Status (2026-06-06)

- ✅ **Investigation + architecture validated.** Read both apps' launch/import
  paths; confirmed sizes and schema with a working spike against local `stage02`.
- ✅ **Phase 1 — pipeline (producer). DONE.**
  - `data/catalog_schema.json` — canonical seed schema contract (builder + drift
    tests read it as source of truth).
  - `data/scripts/build_catalog_db.py` — production builder: creates tables from
    the contract, mirrors the iOS importers, resolves collections `show_selector`
    against the shows table (validated: all 137 match the pipeline's pre-resolved
    `show_ids`), drops orphans, deterministic owner for shared recordings, sanity
    checks (fails on empty). `--version` required.
  - `make data-build-db` (root) → `package-catalog-db` (data) → `catalog.db` +
    `catalog.db.zip`. Builds in ~3s; **16.6 MB → 2.18 MB zipped**.
  - `data-release.yml` builds + publishes `catalog.db.zip` alongside `data.zip` on
    `data-v*` tags. Artifacts gitignored.
  - **Remaining for Phase 1:** cut a `data-vX` tag to publish the first
    `catalog.db.zip` (deferred until consumers are closer, to avoid a dangling
    asset — or publish early; it's additive and ignored by current apps).
- 🟡 **Phase 2 — Android consumer. CODE COMPLETE + compiles locally; needs a device run.**
  - `SeedDatabaseImportService` — ATTACH the seed to the live migrated Room DB,
    `INSERT…SELECT` catalog columns into `shows`/`recordings`/`dead_collections`
    in one txn (favorites preserved across the `shows` CASCADE), rebuild the
    `show_search` FTS on-device, copy `data_version` **last**, DETACH.
  - `ShowSearchText` — shared FTS `searchText` builder used by **both** the JSON
    importer and the seed importer, so search is identical regardless of source.
  - `DatabaseManager` — prefers the prebuilt seed, falls back to the JSON
    `data.zip` import if the seed is absent or fails (**Phase 4 for Android, done
    here**); removed the user-choice prompt. The `DatabaseSource` enum value that
    drives the seed import is named `SEED` (was the legacy `ZIP_BACKUP`, a dead
    full-DB *restore* path that even targeted the wrong filename).
  - `DatabaseHealthService` — now gates on a committed `data_version` row (closes
    the silent-partial-catalog bug).
  - `DeadlyDatabase` `exportSchema=true` + `room.schemaLocation`; drift test
    `CatalogSeedSchemaDriftTest` asserts the contract's catalog columns ⊆ Room's
    exported schema. **The exported `schemas/…/25.json` is generated on the next
    build and must be committed** (the drift test skips until it exists).
  - ⚠️ **Plan correction:** the Android FTS table is **`show_search`** (registered
    `ShowSearchEntity`), not `shows_fts`. `searchText` is a computed blob, so FTS
    is *rebuilt*, not copied.
  - **Dead code removed** (this pass): `DatabaseRestoreService` (legacy full-DB
    restore), the unregistered `ShowFtsEntity`/`shows_fts`, and the now-unreachable
    splash source-selection flow (`DatabaseImportResult.RequiresUserChoice` +
    `SourceSelectionContent` UI + `selectDatabaseSource`) — the seed path no longer
    prompts, so `RequiresUserChoice` was never returned. `ZIP_BACKUP` → `SEED`.
  - **Built locally:** `:core:database` + `:feature:splash` `compileDebugKotlin`
    pass; the Room schema `schemas/…/25.json` is generated **and committed**; the
    `CatalogSeedSchemaDriftTest` now runs (not skipped) and **passes**.
  - **Not yet done:** a device first-launch run to verify the attach-copy + FTS
    rebuild end-to-end against a published/sideloaded `catalog.db.zip`.
- 🟡 **Phase 3 — iOS consumer. CODE COMPLETE + app target builds; tests blocked.**
  - `SeedImportService` — GRDB `ATTACH` of the seed (via a new
    `AppDatabase.writeWithoutTransaction` barrier, since `ATTACH` can't run inside a
    txn) → `INSERT…SELECT` into `shows`/`recordings`/`dead_collections` in one txn
    (favorites snapshot/restored across the `shows` CASCADE) → rebuild `show_search`
    FTS → write `data_version` LAST → `DETACH`. Validates the SQLite header first.
  - `ShowSearchText` (Swift) — shared FTS builder; `ShowImporter.buildSearchText`
    now delegates to it so JSON and seed index identical text (both fed CSV inputs).
  - `DataImportService` — `run()` now tries the seed first
    (`importFromSeedIfAvailable`) and falls back to the JSON import if no
    `catalog.db.zip` asset exists or the seed fails (**Phase 4 for iOS, done here**).
    `GitHubRelease.catalogDbAsset` finds `catalog*.zip`; seed zip is downloaded to
    temp + `defer`-deleted (the single txn is interruption-safe, so a kill mid-copy
    rolls back and relaunch re-fetches the ~2 MB — no persisted-seed bookkeeping).
  - Tests written: `CatalogSeedSchemaDriftTests` (introspects a migrated in-memory
    DB; contract columns ⊆ live + PK match) and `SeedImportServiceTests` (full
    round-trip: catalog copy, FTS rebuild + MATCH, `data_version` gate, favorites
    preserved; plus a non-SQLite rejection case).
  - **Verified:** `make ios-remote-build` → **BUILD SUCCEEDED** (app target, all
    Phase 3 code).
  - **⚠️ Blocked:** the iOS *test target* does not compile on this branch due to
    **pre-existing, unrelated drift** from the connect-v2/sync work —
    `RecentShowsServiceTests`/`PlaylistServiceTests` miss the `favoritesPushService`
    arg, `DataImportServiceTests` missed `updatedAt` (fixed in passing). Running the
    new seed tests needs that suite repaired first (separate task).
- ✅ **Phase 4 — `data.zip` fallback.** Done on both platforms (seed-preferred with
  JSON fallback baked into each consumer above).

## Why (current first-launch behaviour)

- **iOS** (`deadlyApp.swift` → `DataImportScreen`/`DataImportService`): `.task`
  gates on `SELECT COUNT(*) FROM data_version`; empty → modal `fullScreenCover`
  runs download→extract→parse→**clear**→import→**write data_version (last step)**.
  Kill mid-import = full re-do (and the zip is `defer`-deleted). Skip dismisses the
  cover but the import keeps running in a detached `Task` → lands on a half-empty
  home until relaunch.
- **Android** (`MainActivity` → `splash` graph → `DatabaseManager`): `splashGraph`
  is the start destination; `SplashViewModel.viewModelScope` runs
  `initializeDataIfNeeded()`. Skip/Abort flips `isReady` and pops splash →
  ViewModel cleared → import coroutine **cancelled**. Downloaded `data.zip` is
  saved to `filesDir` (survives), import is not resumable.
- **Android silent-incomplete bug:** `DatabaseHealthService.isDatabaseHealthy()` =
  `showCount > 0 && recordingCount > 0`. Kill during the recordings import →
  healthy=true next launch → skips re-import → permanently partial catalog.
  (iOS can't hit this; it gates on the end-written `data_version` row.)

## Numbers (real, from the spike against `stage02` @ data v2.3.0)

| | Today (`data.zip`) | Prebuilt `catalog.db` |
|---|---|---|
| Download (gzip) | **25 MB** | **2.2 MB** |
| Uncompressed | ~103 MB | 16.7 MB (`shows` 9.5, `recordings` 6.1) |
| On-device | download + extract + parse 20k JSON + insert + FTS | download + bulk copy (sub-second) |

Counts: 2,313 shows, 17,854 recordings, 137 collections. `data.zip` is bloated by
per-recording track listings + `ai_review` blobs the apps parse and **discard**.

## Architecture (see ADR for rationale)

Ship one neutral `catalog.db` (catalog tables only). Each app migrates its OWN
empty DB, then `ATTACH` the seed + bulk `INSERT…SELECT` + write `data_version`, all
in **one transaction**, then rebuilds FTS on-device. Keep `data.zip` as fallback.
Avoids GRDB migration bookkeeping (iOS) and Room identity-hash crash (Android)
because the seed is never opened as the live ORM DB.

## Schema (canonical seed = catalog subset)

Column names verified identical across iOS (GRDB `ShowRecord`/`RecordingRecord` +
`AppDatabase` migrations) and Android (Room `ShowEntity`/`RecordingEntity`), so a
single seed copies into both with no per-platform mapping.

- **`shows`** (catalog only — EXCLUDES device-local `isFavorite`/`favoritedAt`):
  showId(PK), date, year, month, yearMonth, band, url, venueName, city, state,
  country, locationRaw, setlistStatus, setlistRaw, songList, lineupStatus,
  lineupRaw, memberList, showSequence, recordingsRaw, recordingCount,
  bestRecordingId, bestSourceType, averageRating, totalReviews, coverImageUrl,
  createdAt, updatedAt
- **`recordings`**: identifier(PK), show_id, source_type, rating, raw_rating,
  review_count, confidence, high_ratings, low_ratings, taper, source, lineage,
  source_type_string, collection_timestamp
- **`dead_collections`**: id(PK), name, description, tagsJson, showIdsJson,
  totalShows, primaryTag, createdAt, updatedAt
- **`data_version`**: single row (id=1, dataVersion, packageName, versionType,
  description, importedAt, gitCommit, gitTag, buildTimestamp, totalShows,
  totalVenues, totalFiles, totalSizeBytes)
- **NOT in seed:** FTS — both platforms use an FTS4 table named `show_search`
  (`unicode61 "tokenchars=-."`); the indexed `searchText` is computed, so it is
  *rebuilt* on-device, not copied. (Android's unregistered `ShowFtsEntity`/
  `shows_fts` has been deleted.) Device-local tables (`library_shows`,
  `recent_shows`, reviews, sync outbox).

### Field derivations (from iOS `ShowImporter`/`RecordingImporter`/`ImportModels`)

- JSON is snake_case (`show_id`, `location_raw`, `avg_rating`, `best_recording`,
  `source_types`, `total_high_ratings`/`total_low_ratings`, `ticket_images`).
- `year`/`month`/`yearMonth` from splitting `date`.
- `songList` = comma-joined song `name`s from `setlist[].songs[]`.
- `setlistRaw` = serialized `setlist` JSON; `lineupRaw` = serialized `lineup` JSON.
- `memberList` = comma-joined `lineup[].name`.
- `recordingsRaw` = JSON array of the show's recording ids.
- `bestSourceType` = first present in rank `["SBD","FM","MATRIX","REMASTER","AUD"]`
  from `source_types` keys.
- `averageRating` = `avg_rating` if > 0 else null. `totalReviews` = high + low.
- `coverImageUrl` = first `ticket_images` side=="front", else side=="unknown",
  else first `photos[].url`.

### Builder gotchas (surfaced by the spike — the real builder MUST handle)

1. **Recordings have no `show_id` in source** (keys: ai_review, confidence, date,
   high_ratings, identifier, lineage, location, low_ratings, rating, raw_rating,
   review_count, runtime, source, source_type, taper, title, venue). Linkage is
   inverted from each show's `recordings[]` array (mirrors iOS `recordingToShows`).
   Mapping buckets (17,854 files): **17,588** referenced by exactly 1 show,
   **57** by 2 shows, **209** orphans (0 shows), **0** dangling refs.
2. **209 orphan recordings** (file on disk, no show lists them) — studio sessions,
   rehearsals, interviews, "various"/"unknown" venues, partial dates (`XX-XX`/`00`)
   that can't match a dated show. iOS *skips* them
   (`guard let showIds = recordingToShows[recId]`); the builder drops them
   (`DROP_ORPHANS`). Not reachable in-app anyway — both apps query recordings
   `WHERE show_id = ?`, so an unlinked recording can never surface.
3. **57 recordings shared by 2 shows** — early/late splits and same-date
   multi-venue (e.g. acid-test + Matrix on 1966-01-29). `recordings.identifier` is
   the **sole PK**, so the schema can't attach one tape to two shows. **The live
   apps already disagree here:** iOS `insert(onConflict: .ignore)` keeps the
   **first** show, Android `OnConflictStrategy.REPLACE` keeps the **last** — so the
   two platforms attach these 57 tapes to *different* shows today. The seed picks
   one deterministically (first by sorted `show_id`) → **makes both platforms
   consistent** for free. (Truly fixing "one tape, two shows" would need a
   composite PK / join table — a separate, larger decision, not in scope.)
4. **Collections need `showSelector` → showIds resolution** — the spike stubbed
   this. Port `CollectionsImporter` selector logic (dates/ranges/exclusions/
   venues/years/explicit showIds) to Python and compute `totalShows` correctly.
5. Exclude the `ai_review` blob and track listings — not stored by either app.

## Plan

### Phase 1 — pipeline (producer; do first, everything depends on it)
- `data/catalog_schema.json` — the schema contract (tables/columns/types).
- `data/scripts/build_catalog_db.py` — harden the spike: real collections
  resolution, orphan-recording skip, build from `stage02`, VACUUM, gzip.
- `make data-build-db` target (mirror `data-package`).
- `data-release.yml`: add a "Build catalog.db" step; `gh release create` uploads
  both `data.zip` and `catalog.db.zip`. Cut a `data-vX` tag to publish the first.

### Phase 2 — Android (CODE COMPLETE — see Status block for the file list)
- ✅ `FileDiscoveryService`/`DownloadService` already classify `catalog.db.zip` as
  `DATABASE_ZIP`; `DatabaseManager` prefers it (seed) over `DATA_IMPORT` (JSON).
- ✅ `SeedDatabaseImportService`: ATTACH the seed → `INSERT…SELECT` into Room's DB
  in one txn → rebuild the `show_search` FTS on-device → write `data_version` last
  → DETACH. (Replaces the dead full-restore path; its `DatabaseSource` is `SEED`.)
- ✅ `isDatabaseHealthy()` now gates on a committed `data_version` row.
- ✅ Room `exportSchema = true` + `room.schemaLocation`. ⏳ commit the generated
  `schemas/…/25.json` after the first build.
- ✅ `CatalogSeedSchemaDriftTest` (catalog columns ⊆ Room's exported schema).
- ⏳ Build/verify locally (Android builds run on this machine, never remote) +
  device first-launch run.

### Phase 3 — iOS
- Add seed discovery/download (GitHub release asset).
- Add an attach-and-copy variant in `DataImportService` (GRDB `ATTACH` + bulk
  insert into the migrated DB, rebuild `show_search` FTS, write `data_version` in
  one transaction).
- Persist the downloaded seed (don't `defer`-delete) so relaunch skips re-download.
- Add schema-drift test (`deadlyTests`).

### Phase 4 — fallback
- Both apps: if the seed asset is missing or copy fails, fall back to the existing
  `data.zip` JSON import. Default to seed; zero-risk migration.

## Spike script (reproduce / recover)

`data/scripts/build_catalog_db.py` (committed, marked spike; not yet in CI). Run:
`cd data && python3 scripts/build_catalog_db.py stage02-generated-data /tmp/catalog.db 2.3.0`.
It parses `stage02` like the iOS importers, drops orphan recordings, picks a
deterministic show for shared recordings, emits the catalog-only seed, VACUUMs,
gzips, and prints row counts + size. Stdlib only (no new deps). This is the basis
for the CI-wired builder in Phase 1; the schema here is the proposed
`catalog_schema.json` contract.

## Known limitations / future work

### A recording can belong to only one show (`recordings.identifier` sole PK)
**Why it's this way:** both apps model recordings with `identifier` as the sole
primary key and look them up via `WHERE show_id = ?`. So the schema structurally
cannot attach one Archive.org tape to more than one show. This predates the
prebuilt-DB work — it's a property of the live app schemas, not something the seed
introduces.

**Impact:** ~57 of 17,854 tapes are legitimately shared by two shows (early/late
splits; same-date multi-venue, e.g. the 1966-01-29 acid-test *and* the Matrix).
Each such tape surfaces under only one of its shows. Today the apps even pick
*different* ones (iOS `onConflict: .ignore` → first; Android `REPLACE` → last), so
they disagree. The seed picks one deterministically (first by sorted `show_id`),
which at least makes both platforms consistent — but the second show still misses
the tape.

**To address later (out of scope now):** give recordings a composite identity
(`(identifier, show_id)` PK) or a `recording_shows` join table, then have both apps
query through it. That's a coordinated schema migration on iOS (GRDB) + Android
(Room) + the seed builder + `catalog_schema.json`, plus a data backfill — a real
chunk of work for a 57-tape edge case. Worth doing if/when we want full fidelity
for early/late and multi-venue dates, but not blocking the first-launch speedup.
Surfaced in ROADMAP "Deferred". See ADR-0007 decision #9.

## Open decisions

- FTS: rebuild on-device (chosen) vs ship index — chosen rebuild.
- Roll out behind a flag vs seed-with-JSON-fallback — chosen fallback (Phase 4).
- One canonical seed + on-device FTS (chosen) vs two per-platform DBs.

## Related
- ADR: [`docs/adr/0007-prebuilt-catalog-db.md`](../docs/adr/0007-prebuilt-catalog-db.md)
- Existing pipeline docs: [`data/docs/grateful-dead-database-pipeline.md`](../data/docs/grateful-dead-database-pipeline.md)
- `../dead-metadata` is a stale upstream fork of the pipeline — archive eventually.
