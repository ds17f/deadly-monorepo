# Mobile ↔ server user-data sync

Plug the existing V3 user-data API on the server into iOS and Android so
favorites, recent shows, reviews, recording preferences, and playback
position round-trip between phone and server. This is the foundation that
makes the web profile (see [web-profile.md](./web-profile.md)) actually
useful — without it, a user signing in on web sees an empty library
because nothing has ever been pushed.

Ships behind a Settings toggle. Goal: dev workflow where "I favorited a
show on my phone, opened the website, it shows up" works end-to-end. Not
required to ship to App Store / Play Store on day one.

## Why this exists

The server already has the full V3 user-data surface built and waiting
(`api/src/routes/user.ts`, backed by `api/src/db/userdata.ts` and SQLite
tables). The mobile apps store everything locally and never call it.
The V3 types on iOS (`FavoritesExportFormat.swift`,
`FavoritesImportExportService.swift`) match the server's `BackupV3`
contract — iOS already serializes to and from V3 for file-based
export/import. Android has no equivalent yet.

So the gap is not "design a sync system." It's "wire the existing V3
serializer to HTTP and add the inverse on Android."

## Decisions already made

- **Granular per-action writes**, bulk pull on first sign-in. Per-action
  matches what the web client will do; bulk pull is just for populating
  local state quickly after sign-in or when the toggle flips on.
- **Last-write-wins by timestamp** for conflict resolution. Every V3
  record already carries `addedAt` / `updatedAt`; later timestamp wins on
  merge. No CRDT, no operational transform, no per-field merge.
- **Dev/Settings toggle** gates everything *in the final shipping
  build*. During dev work, sync is unconditionally on — the toggle is
  noise that slows down iteration. Toggle gets added at the end before
  we cut a TestFlight/internal build (see new final work item).
- **DB migrations must be additive / non-destructive.** Every schema
  change here (adding `deleted_at` columns, sync-state columns) is a
  pure addition. Never drop or rename columns; never write a migration
  that destroys existing local user data. iOS users have years of
  favorites in their local stores.
- **iOS and Android in parallel**, same contract on both. iOS has a head
  start because the V3 serializer exists; Android starts from scratch.
- **Local-first reads, API as sync target.** All user-data reads
  (favorites, recents, position) come from on-device storage — never the
  network. The app stays instant and works offline. `/api/user/*` is
  only touched for writes (granular push) and the bulk-pull merge on
  sign-in / toggle-flip / cold-launch refresh. Don't refactor reads to
  hit the API later — that's the whole point.
- **Soft-delete tombstones on both sides.** Each user-data table (server
  + local) gets a `deleted_at` column. Deletes set it; UI queries filter
  `WHERE deleted_at IS NULL`; LWW merge treats a tombstone as "exists
  with later timestamp, represents absence." Hard-delete locally after
  the server confirms propagation (or after a ~30 day TTL) to keep
  tables from bloating.
- **Anonymous-to-authed migration: LWW, no special case.** On first
  sign-in, merge local into server (and vice versa) using the same
  timestamp rule as steady-state. Local-only items have `addedAt`
  values that don't exist server-side, so they survive the merge
  naturally; no migration code path needed.

## Server surface (already built, do not re-design)

All under `/api/user/*`, all `preHandler: requireAuth`:

| Endpoint | Purpose |
|---|---|
| `GET /api/user/sync` | Full V3 backup (bulk pull) |
| `PUT /api/user/sync` | Full V3 import (bulk push — escape hatch only) |
| `GET/PUT/DELETE /api/user/favorites/shows[/:showId]` | Favorite shows |
| `GET/PUT/DELETE /api/user/favorites/songs[/:id]` | Favorite songs |
| `GET/PUT/DELETE /api/user/reviews[/:showId]` | Show reviews + player tags |
| `GET/PUT /api/user/recordings[/:showId]` | Recording preference per show |
| `GET/PUT /api/user/recent[/:showId]` | Recent shows |
| `GET/PUT /api/user/position` | Playback position |
| `GET/PUT /api/user/settings` | User settings |

V3 types of record (defined in `api/src/db/userdata.ts`):
`FavoriteShowV3`, `FavoriteTrackV3`, `ReviewV3`, `PlaybackPositionV3`,
`SettingsV3`, `BackupV3`. iOS mirrors live in
`FavoritesExportFormat.swift`.

## Work breakdown

> **Order note**: After issue 1 landed, we re-ordered the rest. Issue 2
> (bulk pull + LWW merge) now comes *last* because building the
> granular push (issues 3+4) first means LWW has both directions
> available the moment merge lands. New issue 1.5 adds the additive
> soft-delete schema migration that issues 3/4 depend on. The toggle
> was dropped — there's no automatic behavior to gate; dev buttons are
> the explicit triggers.

### 1. HTTP client scaffolding (iOS + Android)
- Typed API client on each platform against `/api/user/*`. Reuse the
  existing auth session (whatever currently drives the Connect WS auth).
- iOS: new `UserDataAPIClient` alongside `Core/Network/APIClient.swift`,
  reusing the existing Bearer-token plumbing.
- Android: new `core/api/usersync` module + impl using the existing
  `NetworkModule` and `AuthServiceImpl`.
- Smoke test: signed in, `GET /api/user/sync` returns 200 and the app
  parses it to `BackupV3` without crashing. No merging yet.
- **Toggle is deferred** — sync is unconditionally on during dev work.

### 1.5. Additive soft-delete schema migration (iOS + Android)
- Add `deleted_at` (and any missing `updated_at`) columns to local
  favorites / favorite-songs / reviews / recents / playback-position /
  recording-preference tables. Pure additive, never destroys data.
- Update all read paths to filter `WHERE deleted_at IS NULL`.
- Switch delete code paths to soft-delete (set `deleted_at`).
- Why now: issue 3 (granular push) needs to know "what was deleted
  recently" to fire `DELETE /api/user/favorites/shows/:id`. Without
  tombstones, deletes are invisible to the push queue.
- Hard-delete locally after server confirms propagation or after a
  ~30-day TTL — not implemented until merge lands and we have ack.

### 2. First-pull from `/api/user/sync` with LWW merge (last)
- On sign-in OR when the toggle flips on OR on app cold launch (rate-limited):
  - `GET /api/user/sync` → `BackupV3` blob.
  - For each record kind, merge into local store: keep whichever side has
    the later `addedAt` / `updatedAt`. Insert missing records on either side.
  - After merge, push anything local-newer back via the granular endpoints
    from issues 3+4 (avoids needing `PUT /api/user/sync` for normal flow).
- Bulk push (`PUT /api/user/sync`) lives only behind a hidden "Force
  upload local state" dev button — disaster recovery, not the steady-state
  path.

### 3. Granular push for favorite shows + songs
- iOS + Android: every favorite/unfavorite call site additionally fires:
  - Add: `PUT /api/user/favorites/shows/:showId` (or `/songs`) with the
    V3 record body including the local `addedAt`.
  - Remove: `DELETE /api/user/favorites/shows/:showId`.
- Debounce burst changes (e.g., user mass-favorites from a list) with a
  short queue + coalescing per `showId`.
- Retry policy: queue failed writes, retry on next foreground / network
  regain. Don't block the UI.

### 4. Granular push for recent shows + playback position
- Recent: `PUT /api/user/recent/:showId` on play-start (or first track
  advance). Server already tracks `last_played_at` / `total_play_count`,
  so we just announce the play; no client-side counter math.
- Playback position: `PUT /api/user/position` debounced to once per 15s
  while playing, once on pause, once on track change. (Already matches
  the iOS reporting cadence in `PlayerProvider`.)

### 5. LWW conflict policy doc + dev smoke test
- Short doc in `docs/` (or appended here) describing:
  - The merge rule per record kind (which timestamp is the comparator).
  - Behavior when timestamps tie (prefer remote — arbitrary but consistent).
  - What happens when a record is deleted on one side and updated on the
    other (deletion needs a tombstone OR we treat any local record as
    authoritative if it has a later timestamp — pick one and document).
- "Favorite on phone → see on web" smoke test checklist for QA.

## Out of scope (explicit non-goals)

- **Tombstones / deletion tracking across devices.** v0 LWW means
  deleting on phone while editing on web is a known foot-gun. Acceptable
  for dev; revisit before flipping default-on for production.
- **Reviews and player tags.** The server has endpoints; mobile doesn't
  surface these yet. Sync them when the feature exists locally.
- **Background sync via WorkManager / BGTaskScheduler.** v0 syncs on
  foreground / on action. Background is a follow-up.
- **Settings sync.** Server endpoint exists but client semantics are
  unclear ("dark mode preference" doesn't need to round-trip). Defer.

## Open questions

- **Favorite songs are misplaced on `ReviewService` (both platforms).**
  Conceptually they belong on `FavoritesService` (alongside favorite
  shows) but legacy migration paths bolted them onto `ReviewService` —
  this exists on iOS *and* Android (`ReviewService.kt` /
  `ReviewServiceImpl.kt` own song favorites on Android too). Decision
  for issue 3b: **carry them on the smelly service.** Both platforms
  have a single `toggleFavoriteSong` chokepoint that's a clean hook for
  outbox enqueue, and extracting on both platforms is its own refactor
  with call-site churn (`FavoritesViewModel`, `PlayerViewModel`,
  `PlaylistViewModel`, `FavoriteSongItems`, browse/media services on
  Android; equivalents on iOS). **TODO: file a Linear tech-debt ticket
  to extract favorite-songs to `FavoritesService` on both platforms,
  done after this branch merges.**

## How to track progress

This doc IS the tracker. One Linear ticket points here. As work lands,
strike through the relevant section heading and link the merging PR
inline.
