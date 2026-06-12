# ADR-0009: Non-destructive catalog refresh

## Status

Proposed (2026-06-08).

Builds on [ADR-0013](0013-prebuilt-catalog-db.md) (prebuilt catalog seed). That
ADR introduced a second import path (seed) alongside the existing JSON import;
this ADR fixes a data-loss bug that exists in **both** and predates the seed
work.

## Context

Every catalog (re-)import on both platforms clears the catalog by deleting
`shows`, then re-inserts it. On Android this is `showDao.deleteAll()`
(`DataImportService.kt:165`, the shipped JSON path) and `DELETE FROM shows`
(`SeedDatabaseImportService.kt:111`, the new seed path).

`shows` is the parent of a fan of tables via `onDelete = CASCADE` foreign keys
on `showId`. The full child set:

| Child table | Contents | Owner |
|-------------|----------|-------|
| `recordings` | catalog | pipeline |
| `favorite_shows` | user's favorited shows | **user** |
| `favorite_songs` | user's favorited tracks | **user** |
| `recording_preferences` | user's chosen recording per show | **user** |
| `show_reviews` | user's notes / ratings | **user** |
| `show_player_tags` | user's per-show player tags | **user** |

So `DELETE FROM shows` cascade-deletes all six children. Both import paths
snapshot and restore **only `favorite_shows`** (`DataImportService.kt:155-158`
+ `:371`; `SeedDatabaseImportService.kt:88-91` + `:138-141`) — the code even
comments *"CASCADE FK on shows would delete them."* The other four user tables
are silently wiped on every catalog refresh and never restored.

This is latent, not yet widely felt: `favorite_songs`, `recording_preferences`,
and `show_reviews` are recent sync-backed features (`PLANS/mobile-server-sync.md`),
the data version likely has not bumped since they shipped, and a subsequent
server pull can re-hydrate synced rows — masking the loss. But anything
local-only or not-yet-synced is gone, and the seed rollout makes catalog
refreshes routine. The trigger is a stale `data_version` (`DatabaseManager.kt:82`),
which fires on any data-version upgrade regardless of seed vs JSON.

The graph also shows the fix can be narrow. Of the catalog tables, **only
`shows` has user-data children.** `recordings` has none; `dead_collections`,
`show_search`, and `data_version` have none. Delete-and-replace is only
dangerous for `shows`.

## Decision

Make catalog refresh non-destructive to user data by never deleting `shows`.

1. **Upsert `shows`, keyed on `showId`** (Room is 2.6.1, so SQLite
   `INSERT … ON CONFLICT(showId) DO UPDATE` / Room `@Upsert` are available),
   instead of `DELETE` + `INSERT`:
   - **show changed** → in-place `UPDATE` of catalog columns. No delete, no
     cascade, all user tables untouched.
   - **show added** → inserted.
   - **show removed** → see decision #4.

   The `ON CONFLICT DO UPDATE` updates **catalog columns only** and deliberately
   leaves the denormalized device-local `isFavorite` / `favoritedAt` columns
   alone; new rows default them.

2. **Keep `DELETE` + re-insert for the childless catalog tables** —
   `recordings`, `dead_collections`, and the `show_search` FTS (rebuilt on-device
   per ADR-0013 #5). They have no user children, so a clean replace is exact and
   simple. `recordings` is the volatile table (tapes are deprecated/added far more
   often than shows); replacing it wholesale handles its churn with no special
   casing. A `recording_preferences.recordingId` that points at a now-removed
   recording becomes a soft dangling pointer the UI already tolerates — not data
   loss of the user's intent.

3. **Reconcile the denormalized favorite flag from the source of truth.** After
   the catalog write, run one statement so `shows.isFavorite` / `favoritedAt`
   always match `favorite_shows` (which now survives the refresh in place):
   `UPDATE shows SET isFavorite = (showId IN (SELECT showId FROM favorite_shows)) …`.
   This makes the flag correct independent of insert/upsert mechanics and lets us
   **delete the favorites snapshot/restore dance** from both paths.

4. **Removed shows are left as orphans.** With pure upsert, a show dropped from a
   newer catalog stays as a local row (with its user data intact). The GD catalog
   is historical and append/correct-only, so show removals are effectively never;
   accepting a rare stale row is preferable to any cascade. (A future
   prune-if-unreferenced step — `DELETE FROM shows WHERE showId NOT IN (new) AND
   showId NOT IN (any user table)` — is noted but out of scope.)

5. **One shared helper per platform, applied to both import paths.** On each
   platform the JSON and seed paths call the same `upsert` + `reconcileFavoriteFlags`
   on the shared show DAO, ending the duplicated favorites-only preservation that
   produced this bug:
   - **Android** — `ShowDao.upsert` / `reconcileFavoriteFlags`, called from
     `DataImportService` (JSON) and `SeedDatabaseImportService` (seed).
   - **iOS** — `ShowDAO.upsertAll` / `reconcileFavoriteFlags` (GRDB), called from
     `DataImportService` (JSON) and `SeedImportService` (seed).

   The reconcile SQL is identical across platforms (the `favorite_shows` columns
   `addedToFavoritesAt` / `deletedAt` match), and the upsert leaves the
   device-local `isFavorite` / `favoritedAt` columns untouched on conflict.

6. **Rollout: ship the seed at the current data version.** Existing installs are
   not stale (`isDataVersionStale` is false), so they no-op — no re-import, no
   refresh, zero exposure. Only genuinely-new installs take the seed path. The
   fix protects the *next* real data-version bump.

No schema change is required — this is purely a change of write strategy, so
there is no migration to ship.

## Consequences

**Gains.**
- User data (`favorite_songs`, `recording_preferences`, `show_reviews`,
  `show_player_tags`, and `favorite_shows`) survives every catalog refresh on
  both paths.
- Because user tables are never deleted, **the fix needs no knowledge of them** —
  any future user table with a `shows` FK is automatically safe, with no code
  change. This removes the snapshot-list footgun permanently.
- The denormalized favorite flag is reconciled from one source of truth instead
  of reconstructed by a fragile snapshot/restore.
- The duplicated preservation logic across the two import paths collapses to one
  helper.

**Costs we accept.**
- A removed show can linger as a stale local row until a fresh install (decision
  #4). Accepted given GD show removals are effectively nonexistent.
- A `recording_preferences` / `favorite_songs` row can outlive the recording it
  references after a recordings replace, leaving a soft dangling `recordingId`.
  Pre-existing UI behavior already tolerates a missing recording.
- Two import paths still coexist this release. The JSON path's deprecation is
  **scheduled for the release after the seed bakes in the wild** — it remains the
  ADR-0013 zero-risk fallback for now, and costs nothing in the happy path
  (`data.zip` is only downloaded if the seed import fails).

## Alternatives considered

- **Extend snapshot/restore to all user tables.** Keep `DELETE FROM shows`, but
  snapshot all six children before and restore after (the existing favorites
  pattern, generalized). Rejected as the primary approach: it is correct but
  fragile — every *future* user table with a `shows` FK must be remembered and
  added here, or it silently regresses this exact bug. Upsert sidesteps the
  enumeration entirely.
- **Disable foreign keys during the swap** (`PRAGMA foreign_keys=OFF`, delete +
  re-insert without cascade). Rejected: risks orphaned child rows pointing at
  showIds the new catalog no longer contains, and fights Room, which manages the
  FK pragma per connection.
- **`INSERT OR REPLACE` for shows.** Rejected: SQLite `REPLACE` is `DELETE` +
  `INSERT` under the hood, so it fires the same CASCADE — it would not fix the
  bug. The fix specifically requires `ON CONFLICT DO UPDATE` (true in-place
  update) / Room `@Upsert`.
- **Kill the JSON import path now and rely on the seed alone.** Tempting (the
  canonical transform already lives in `build_catalog_db.py`, and the duplicated
  bug shows the cost of two paths). Rejected for this release: it removes the
  ADR-0013 zero-risk fallback in the same release the seed first ships broadly,
  with no happy-path benefit. Deferred to a follow-up release.
</content>
</invoke>
