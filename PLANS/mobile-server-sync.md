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

> **Re-scope (2026-05-31)**: With issue 3 (favorites) done and tested
> on device, we re-prioritized the tail of this epic:
> - **Playback-position sync is cut from issue 4.** Per-action REST push
>   for position is heavy and low-value on its own — the place it actually
>   matters is Spotify-Connect-style remote control, which is WebSocket-
>   based, not this REST sync. Position moves to the connect/WS work
>   (see *Connect-v2 / WebSocket* below). Issue 4 is now recents-only.
> - **Recents stays** (issue 4) — cheap, already naturally debounced.
> - **The web user-profile UI ([web-profile.md](./web-profile.md)) is the
>   next thing we build** — more impactful and ships immediately, vs. WS
>   which is a larger effort. Mobile push (issue 3) already feeds it.
> - **Connect-v2 / WebSocket is the strong follow-on**, MVP'd from the
>   existing `connect-v2` branch + `docs/connect-v2-architecture` branch
>   rather than from scratch.

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

**Backfill gap (surfaced 2026-05-31).** Granular push only fires on *new*
actions, so anything already in the local store before sync existed — and
recents prior to issue 4 — was never pushed. A user with years of local
favorites sees an empty web profile until they re-toggle. Options to close
it:
- **(a) Bulk push** `PUT /api/user/sync` from the local V3 backup on
  sign-in (or a dev button). One call backfills *everything* — favorites,
  recents (with real timestamps, unlike the announce endpoint), reviews.
  Downside: it's last-write-wins-wholesale, so it can clobber server-newer
  data — fine for the dev/empty-server case, risky multi-device.
- **(b) Enqueue-all** existing local rows into the outbox once, letting the
  granular push drain them (LWW per row via `updatedAt`). Safer, but
  recents still can't be backfilled faithfully (announce endpoint has no
  client timestamp).
- Recommended: (a) gated to "server has no/less data" or behind an
  explicit one-time "upload my library" action, since the realistic case
  is phone-is-source-of-truth → mostly-empty server.

**Resolved (2026-06-01) via option (b).** Both platforms now run
`enqueueAllLocalAndFlush()` once on startup (gated by a `localBackfilledV1`
pref flag) and expose a manual "Push all local data" dev button. It enqueues
every local favorite (shows + songs) + top 4 recents into the outbox and
drains via the granular push (LWW per row). Recents are still
announce-on-play (top 4, no faithful timestamps) per the "we don't care
about backfill, pull the top 4 and post them" call. iOS `34c0cce2`,
Android `87981e0b`.

### 3. Granular push for favorite shows + songs
- iOS + Android: every favorite/unfavorite call site additionally fires:
  - Add: `PUT /api/user/favorites/shows/:showId` (or `/songs`) with the
    V3 record body including the local `addedAt`.
  - Remove: `DELETE /api/user/favorites/shows/:showId`.
- Debounce burst changes (e.g., user mass-favorites from a list) with a
  short queue + coalescing per `showId`.
- Retry policy: queue failed writes, retry on next foreground / network
  regain. Don't block the UI.

**Status: DONE.**
- 3a (shows) — landed in 85e5b91f, tested end-to-end across iOS/Android/web.
- 3b (songs) — landed and **device-tested**. Server contract changed:
  `DELETE /api/user/favorites/songs/:id` replaced with
  `DELETE /api/user/favorites/songs?showId=X&trackTitle=Y` (natural key,
  since mobile clients don't track the server autoincrement id). Web has
  no favorite-songs UI yet so no client coordination needed.
  - Device testing surfaced a phantom-duplicate bug: the local natural key
    was `(showId, trackTitle, recordingId)` but the server keys on
    `(user, showId, trackTitle)`. Fixed in c4b7e26b — local identity
    relaxed to `(showId, trackTitle)` on both platforms (recordingId is
    now metadata only), with additive dedupe migrations (Android v24→25,
    iOS v14).

### 4. Granular push for recent shows (recents-only — position cut) — LANDED
- **Landed both platforms** (Android `443d56f0`, iOS `03d19780`; iOS build
  verified on the remote Mac). `recordShowPlay` now enqueues a
  `KIND_RECENT` outbox row (refId = showId) flushed alongside the favorite
  kinds; `UserSyncService.putRecent` / `UserSyncAPIClient.putRecent` →
  `PUT /api/user/recent/:showId`.
- **Announce-on-play only (v0)**: no recent tombstone/clear propagation,
  and **no backfill** of pre-existing local recents (the endpoint takes no
  client timestamp). See the backfill note under issue 2.
- Recent: `PUT /api/user/recent/:showId`. Server already tracks
  `last_played_at` / `total_play_count`, so we just announce the play; no
  client-side counter math.
- **No server spam — the local model already debounces for us.** Both
  platforms record a recent through a single `recordShowPlay(showId)`
  chokepoint (`RecentShowsServiceImpl` on iOS + Android) gated by:
  - a "meaningful play" filter — ≥10s of playback, or ≥25% for tracks
    under 40s; and
  - a `hasRecordedCurrentTrack` flag that is reset **only when the show
    changes** (`currentTrackShowId`), not per track.
  So sitting and listening to tracks 1→2→3 of one show fires
  `recordShowPlay` exactly once. The outbox enqueue slots into that same
  chokepoint (same pattern as the favorites outbox in issue 3) and
  inherits the once-per-show-session dedup for free — no per-tick or
  per-track pushes. Re-playing a show later legitimately bumps it (a new
  session → a new push → server updates `last_played_at`), which is
  correct "most-recent-first" behavior.
- **Playback position is cut** (was the second half of this issue). REST
  per-action position push is heavy and only pays off for Connect-style
  remote control, which is WebSocket-based. Position lives with the
  connect/WS work, not here. See *Connect-v2 / WebSocket* below.

### 4b. Granular + apply sync for reviews — LANDED
- **Landed both platforms** (Android `7d584844`, iOS `b35ae703`; iOS build
  verified on the remote Mac). Reviews now sync **bidirectionally**,
  mirroring the favorite-song stack:
  - **Push**: a `review` outbox kind (refId = showId) enqueued at every
    write site — rating/notes/recording+playing quality, *and* player-tag
    edits. The flusher reads the review row + its player tags and
    `PUT`s `/api/user/reviews/:showId`; a tombstoned row flushes as
    `DELETE`. Backfill (`enqueueAllLocalAndFlush`) includes reviews.
  - **Apply**: `applyReviews` merges the backup's reviews with LWW by
    `updatedAt`, skips shows missing from the local catalog, and replaces
    local player tags with the remote set.
- **Player tags travel with the review.** The server's `upsertReview`
  *replaces all tags* for the show on every PUT, so omitting them would
  wipe them — they're always gathered on push and replaced on apply. LWW
  is at the review level (tags are children of the review).
- **Delete is now a tombstone** (was a hard delete): review reads filter
  `deletedAt`, with a tombstone-inclusive reader backing the push. iOS
  `show_reviews.deletedAt` column already existed (v15 migration); only the
  record struct needed the field.
- **Reactive indicator** (`b0d00ad9`): the playlist "you have a review"
  marker observes the review now (Android `getShowReviewFlow`, iOS
  `observeShowReview`) so a synced review flips it live.

### 5. LWW conflict policy doc + dev smoke test
- Short doc in `docs/` (or appended here) describing:
  - The merge rule per record kind (which timestamp is the comparator).
  - Behavior when timestamps tie (prefer remote — arbitrary but consistent).
  - What happens when a record is deleted on one side and updated on the
    other (deletion needs a tombstone OR we treat any local record as
    authoritative if it has a later timestamp — pick one and document).
- "Favorite on phone → see on web" smoke test checklist for QA.

## Follow-ups (not blocking this branch)

- **Replace imperative caches with reactive observation on both platforms.**
  Surfaced during 3b device testing: imperative caches set by explicit
  refresh calls leave the UI stale when something else writes the
  underlying SQLite (sync apply, in particular). Per-track indicators
  already use GRDB `ValueObservation` (iOS) and Room `Flow` (Android) and
  Just Work; the list caches did not.
  - **iOS: done (b5decdb7).** `FavoritesServiceImpl` is now fully
    observation-driven — both `shows` and `songs` subscribe to GRDB
    `ValueObservation`, the `reviewService` dependency is gone, and the
    sync-apply refresh band-aid (ab22ea5c) was removed.
  - **Android: still a band-aid.** `FavoritesViewModel` collects
    `getFavoriteTracksFlow` for songs but the broader cache shape isn't
    observation-driven yet. Bring it to parity with iOS.
  - **Other record kinds** (recents, reviews, position) still need the
    same treatment on both platforms once they sync.
  File a Linear ticket; tackle as its own session.

## Connect-v2 / WebSocket (strong follow-on, after web profile)

Real-time, Spotify-Connect-style cross-device control: see what's playing
elsewhere, transfer playback, push position/transport in real time. This
is the *right* home for playback-position sync (cut from issue 4) — it
needs a live channel, not REST per-action writes.

We are **not designing this from scratch.** A lot of good work already
exists and is going a bit stale:
- `connect-v2` branch — the main draft implementation.
- `docs/connect-v2-architecture` branch — carries `docs/connect-v2-architecture.md`
  plus an `api/src/connect/` surface (`registry.ts`, `routes.ts`,
  `types.ts`) and `PlayerConnectSheet.kt`.
- Related: `connect/full-draft`, `connect/device-sheet`,
  `mobile/connect-device-sheet`.

The core value there was **a relatively clean, simple model for connect
state + WS message types.** Plan to MVP by either:
1. Rebasing `connect-v2` onto current head (preferred if not too painful), or
2. Reading through it and porting the WS implementation + message pattern
   onto head deliberately.

Then promote `docs/connect-v2-architecture.md` into a numbered ADR
(`docs/adr/0006-*`) so the model is captured in head, not stranded on a
branch.

**Open question — web changes the model.** The connect-v2 state/message
design predates the web app having a real signed-in presence. A browser
tab is now a potential connect participant (controller and/or target),
which may change device identity, presence/liveness, and which transport
events a non-native player can honor. Revisit the state model with web as
a first-class participant before committing to the ported design.

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
