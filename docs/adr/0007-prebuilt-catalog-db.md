# ADR-0007: Prebuilt catalog DB for first-launch

## Status

Accepted (2026-06-06). **Partially implemented (2026-06-07).**

- ✅ **Phase 1 — pipeline producer** (`catalog_schema.json` contract,
  `build_catalog_db.py`, `make data-build-db`, `data-release.yml` publishes
  `catalog.db.zip`). Not yet tagged/published.
- 🟡 **Phase 2 — Android consumer:** code-complete on `prebuilt-catalog-db`,
  compiles locally with the Room schema JSON committed and the drift test passing;
  pending a device first-launch run. See PLANS Status for the file-by-file list.
- 🟡 **Phase 3 — iOS consumer / Phase 4 — iOS fallback:** code-complete; the app
  target builds (`make ios-remote-build` → BUILD SUCCEEDED). Seed-preferred import
  with JSON fallback via `SeedImportService` + `ShowSearchText`. New seed tests are
  written but the iOS test target is blocked by pre-existing, unrelated drift
  (connect-v2/sync test constructors) — repair is a separate task.

**Implementation corrections to decisions below** (surfaced while building Phase 2):
- Decision #5 named Android's FTS table `shows_fts`; the table the app actually
  registers and queries is **`show_search`** (`ShowSearchEntity`). The decision
  stands (FTS rebuilt on-device); only the name was wrong. The dead, unregistered
  `ShowFtsEntity`/`shows_fts` has since been **deleted**.
- Decision #7's "reuse the `ZIP_BACKUP` idea" landed as: a new attach-and-copy
  `SeedDatabaseImportService`, driven by the `DatabaseSource.SEED` enum value
  (renamed from the legacy `ZIP_BACKUP` — a full-DB *restore* that wrote to the
  wrong filename and was effectively dead). The old `DatabaseRestoreService` and
  the now-unreachable splash source-selection prompt have been **deleted**.

Execution detail, phased steps, and findings live in
[`PLANS/prebuilt-catalog-db.md`](../../PLANS/prebuilt-catalog-db.md); this ADR
records the binding decisions and trade-offs.

## Context

First launch after install is slow. Neither app ships catalog data; both must
**download a 25 MB `data.zip` from GitHub Releases → extract thousands of JSON
files → parse each → batch-insert into SQLite → build the FTS index** before the
app is usable. On venue cell this is minutes. The parse/insert/FTS phase is the
on-device long pole; the download is network-bound and unreliable.

The two apps do the *same* job with different first-run UX (iOS: a modal
`fullScreenCover` over nav; Android: a dedicated `splash` route with phase
progress + lyric quotes), but the underlying cost is identical.

Three latent problems compound it:
- **No resumability.** iOS writes the `data_version` row only at the very end, so
  a kill mid-import re-does everything from zero (and deletes the downloaded zip).
- **Android can silently false-complete.** Its "is data present?" gate is
  `isDatabaseHealthy()` = `showCount > 0 && recordingCount > 0`. A kill *during*
  the recordings import leaves both counts > 0, so the next launch reports healthy
  and **skips re-import**, stranding a permanently incomplete catalog until a
  version bump forces an upgrade.
- **Skip is incoherent.** iOS's import keeps running detached after Skip (so you
  land on a half-empty home); Android's import is cancelled with the splash
  ViewModel.

The data pipeline already lives in-repo (`deadly-monorepo/data/`); CI
(`data-release.yml`, on `data-v*` tags) already builds and publishes `data.zip`
from it. `../dead-metadata` is a stale upstream fork, not in any build path.

## Decision

Ship a **prebuilt catalog DB seed** and copy it into each app's database instead
of importing JSON on-device.

1. **Build one neutral `catalog.db` in CI** from `stage02`, alongside the existing
   `data.zip`, published as a release asset (`catalog.db.zip`). The data pipeline
   — which already owns the canonical JSON — becomes the single owner of the
   catalog *schema contract* (`data/catalog_schema.json`).
2. **Attach-and-copy, not drop-in.** Each app creates/migrates its **own** empty
   DB normally, then `ATTACH` the downloaded seed and bulk `INSERT…SELECT` the
   catalog tables, all in **one transaction**, then writes `data_version` in that
   same transaction. The app's ORM stays authoritative over its real DB; the seed
   is only ever opened as a raw data source. This sidesteps **GRDB's migration
   bookkeeping** (iOS) and **Room's schema identity-hash crash** (Android) that a
   drop-in live DB would trip. Bulk copy of ~20k rows is sub-second.
3. **Catalog data only; device-local state excluded.** The seed carries `shows`
   (catalog columns), `recordings`, `dead_collections`, and `data_version`. It
   omits per-device columns (`isFavorite`/`favoritedAt`) and tables
   (`library_shows`, `recent_shows`, reviews, sync outbox) — those are created
   empty by each app's own migrations and filled locally.
4. **One canonical seed serves both platforms.** `shows` and `recordings` column
   names are already identical across GRDB and Room, so a direct `INSERT…SELECT`
   works with no per-platform column mapping for the base tables.
5. **FTS is rebuilt on-device, never shipped.** Both apps use an FTS4 table named
   `show_search` with a `unicode61 "tokenchars=-."` tokenizer (the names happen to
   match; iOS and Android still each own their own index). The indexed `searchText`
   is a *computed* blob (multi-format dates, member/song lists, source-type tags),
   so the rebuild recomputes it from the copied `shows` rows — it is not a column
   copy. On Android that logic is shared between the JSON and seed import paths via
   `ShowSearchText` so search is identical regardless of source. Shipping the index
   would couple the seed to one platform's FTS engine/version.
6. **`data.zip` stays as fallback.** New app versions prefer the seed and fall back
   to the JSON import if the asset is missing or the copy fails. Older app versions
   keep using `data.zip` unchanged. Web continues to consume `data.zip`.
7. **Recoverability comes from the transactional copy.** The copy either commits
   (with the `data_version` row) or it doesn't — no partial state. Android's health
   gate is changed to require a **committed version row** (matching iOS) instead of
   "any rows," closing the silent false-complete. The downloaded seed is persisted
   so a relaunch skips re-download.
8. **A schema-drift guard keeps the contract honest.** `catalog_schema.json` is the
   source of truth; unit tests on both apps assert their expected catalog columns
   are covered by it, so adding a column to either app fails CI until the builder
   and contract are updated. Room's `exportSchema` is turned on to give a versioned
   schema + hash to assert against.
9. **The builder canonicalizes recording↔show linkage at build time.** Recording
   JSON carries no `show_id`; linkage is inverted from each show's `recordings[]`.
   Two anomalies are resolved once, in CI, instead of per-device per-platform:
   - **Orphan recordings are dropped.** ~209 of 17,854 tapes (studio sessions,
     rehearsals, interviews, unknown/"various" venues, partial dates) are
     referenced by no catalog show. Both apps query recordings `WHERE show_id = ?`,
     so an unlinked recording is unreachable in-app anyway; the iOS importer already
     skips them. The seed omits them.
   - **Shared recordings get a deterministic single owner.** ~57 tapes are listed by
     two shows (early/late splits, same-date multi-venue, e.g. the 1966-01-29
     acid-test *and* the Matrix). Because `recordings.identifier` is the **sole
     primary key** on both platforms (and lookups are `WHERE show_id = ?`), the
     schema structurally cannot attach one tape to two shows — and today the two
     apps resolve the conflict *differently* (iOS `onConflict: .ignore` keeps the
     **first** show; Android `OnConflictStrategy.REPLACE` keeps the **last**), so the
     same tape appears under different shows on iOS vs Android. The seed assigns one
     show deterministically (first by sorted `show_id`), which makes both platforms
     consistent — but the tape's *other* show still misses it.

     **This is a known, accepted limitation, not a fix.** Full fidelity ("one tape,
     many shows") needs a composite identity — `(identifier, show_id)` PK or a
     `recording_shows` join table — with both apps querying through it. That is a
     coordinated schema migration across iOS (GRDB) + Android (Room) + the seed
     builder + `catalog_schema.json`, plus a data backfill: a real chunk of work for
     a ~57-tape edge case, so it is **deferred, not in scope here**. Revisit if/when
     early/late and multi-venue dates need to show their full recording set.

## Consequences

**Gains.**
- Download shrinks from **25 MB → ~2.2 MB gzip** (the `data.zip` carries
  per-recording track listings and `ai_review` blobs the apps parse and discard).
- The entire extract/parse/insert/FTS-build phase is replaced by a sub-second bulk
  copy — first launch goes from minutes to seconds.
- Kill/skip during setup is recoverable (transactional copy; persisted download;
  fixed health gate) instead of silently-broken or fully-redone.
- The catalog schema gets a single explicit contract owned by the pipeline.
- **Recording↔show linkage becomes consistent across platforms.** Resolving orphans
  and shared-tape ownership once at build time fixes a pre-existing iOS/Android
  divergence (first-wins vs last-wins) for the ~57 shared tapes — for free.

**Costs we accept.**
- The builder must reproduce the apps' parse logic in Python (date components,
  `songList` from setlist, `memberList` from lineup, `bestSourceType` ranking,
  `totalReviews` = high+low, recording→show linkage). The drift guard (#8) is the
  mitigation; without it the seed can silently diverge from the apps.
- Two import paths exist during the transition (seed + JSON fallback) until old
  versions age out.
- The seed is rebuilt and re-published whenever the catalog schema changes, pinned
  to a data version the apps require.
- Room `exportSchema` must stay on and its schema JSON committed.

## Alternatives considered

- **Ship a per-platform, drop-in live DB** (the app's actual database, ready to
  open). Rejected: GRDB would re-run `CREATE TABLE` over a DB with no
  `grdb_migrations` bookkeeping, and Room would reject a foreign schema on its
  identity-hash check. Making both accept a drop-in is fragile and doubles the
  artifact + drift surface. Attach-and-copy keeps each ORM authoritative.
- **Keep the JSON import, just make the loading screen "cooler."** Rejected as the
  primary fix: it dresses up a multi-minute wait instead of removing it. (A nicer
  loader is still fine to do later; with the seed the wait is short enough that it
  matters far less.)
- **Use Android's existing `ZIP_BACKUP` path** (download a prebuilt DB and restore
  the file in place). It was coded but unused (and has since been deleted), and
  restoring the file as Room's live DB still hits the identity-hash requirement. The
  attach-and-copy approach reuses the *idea* (prebuilt DB) without the drop-in
  fragility, and works identically on iOS.
- **Ship the FTS index in the seed.** Rejected: the two platforms' FTS tables
  differ (name + tokenizer), and a shipped index couples the seed to one FTS
  engine version. On-device rebuild is cheap for 2,313 rows.
- **Two prebuilt DBs, one per platform, built in CI.** Workable but doubles the
  drift surface and still needs the Room-hash handling; the columns already align,
  so one canonical seed + on-device FTS is simpler.
- **Resolve the schema by running each app's real migrator in CI** (Swift on
  macOS / Room on JVM) to guarantee fidelity. Heavier CI; deferred in favour of the
  Python builder + `catalog_schema.json` contract + drift tests.
