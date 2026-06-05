# User-Data Sync (Mobile ↔ Server)

How favorites, reviews, and recents round-trip between a phone and the
server. This is the conflict-resolution contract that iOS and Android both
implement, plus a QA smoke-test checklist.

Background and the full work breakdown live in
`PLANS/mobile-server-sync.md`. This page is the authoritative description of
the **merge behavior** — keep it in step with the code if the rule changes.

## What syncs

| Record kind | Direction | Source of truth on apply |
|---|---|---|
| Favorite shows | push + pull | LWW by `updatedAt` |
| Favorite songs | push + pull | LWW by `updatedAt` (natural key `showId, trackTitle`) |
| Reviews (+ player tags) | push + pull | LWW by `updatedAt`; tags replaced wholesale |
| Recents | **push only** (announce-on-play) | server stamps `last_played_at` |
| Playback position | **not synced** | cut → Connect-v2 / WebSocket |
| Settings | **not synced** | deferred (client semantics unclear) |

All reads stay local-first; the network is only touched to push writes and
to pull the bulk backup. The app works offline and stays instant.

## How it moves

**Push** is an outbox. Every write site (favorite/unfavorite, review
edit, player-tag edit, meaningful play) enqueues a row
(`SyncOutboxEntity` on Android / equivalent on iOS) keyed by
`kind` + `refId`, then triggers a flush. The flush drains the queue in a
fixed order (favorite shows → songs → recents → reviews); a successful
push deletes the outbox row, a failure records the error and **leaves the
row queued** to retry on the next flush. There is no exponential backoff —
the retry triggers are simply the next enqueue, foreground, sign-in,
cold-start, or backfill.

**Pull** is `GET /api/user/sync` → a `BackupV3` blob, merged into the
local DB record-by-record. It runs on:

- **sign-in** (auth transitions to SignedIn),
- **cold start** (already signed in at launch),
- **foreground** (`ON_START` / `scenePhase` active, if signed in),
- **after a successful push flush** (so a local write reflects any
  server-side changes immediately).

Concurrent pulls are coalesced onto a single in-flight task
(`UserSyncCoordinator` / the `inFlight` task on iOS) so two triggers can't
race.

## The merge rule (LWW)

For every record kind the comparator is the record's **`updatedAt`**.
Server timestamps are in **seconds**; local timestamps are in
**milliseconds**, so the apply path multiplies the remote value by 1000
before comparing.

```
remoteUpdatedMs = dto.updatedAt * 1000
if (local != null && local.updatedAt >= remoteUpdatedMs) skip   // keep local
else apply remote
```

### Ties go to **local**

Because the comparison is `>=`, when both sides carry the identical
`updatedAt` the **local row wins** and the remote is skipped. This is
deliberate and consistent across both platforms and all record kinds — a
tie means "the device already has this exact version," so there is nothing
to apply. (An earlier draft of the plan guessed "prefer remote on tie";
the shipped behavior is the opposite. The choice is arbitrary but
must stay consistent — do not flip one platform.)

### Deleted on one side, edited on the other

Deletion is a **tombstone**, not a row removal: each table has a
`deletedAt` column. UI reads filter `WHERE deletedAt IS NULL`, but the
merge reads *including* tombstones and treats a tombstone as an ordinary
record carrying its own `updatedAt`.

So a delete and an edit are resolved by the same LWW rule:

- Delete at T2, edit at T1 (T1 < T2) → tombstone wins, the row stays
  deleted.
- Edit at T2, delete at T1 (T1 < T2) → the edit wins, the row comes back.

A tombstoned row pushes as `DELETE`; a live row pushes as `PUT`.

## Per-kind specifics

- **Downloads are device-local.** `downloadedRecordingId` /
  `downloadedFormat` on a favorite show are **never** overwritten by a
  pull — the apply path keeps whatever the local row already has. One
  device's download state never clobbers another's.
- **Shows missing from the local catalog are skipped.** Favorites,
  favorite-songs, and reviews FK to the shows table. If a pulled row
  references a `showId` the local catalog doesn't have (older data
  version), it's counted as `skippedMissingShow` and left for a future
  data refresh to import — it is not an error.
- **Recents are announce-on-play only (v0).** Push sends
  `PUT /api/user/recent/:showId`; the server stamps the time and counts
  the play. There is no recent tombstone/clear propagation and no
  faithful backfill of historical recents (the endpoint takes no client
  timestamp — backfill only enqueues the top 4). **Recents are not
  applied on pull** — the pull merges shows, songs, and reviews only.
- **Player tags travel with the review.** The server's review upsert
  *replaces all tags* for the show on every `PUT`, so the push always
  gathers the current local tags and sends them, and the apply replaces
  the local tag set with the remote one. LWW is at the review level; tags
  are children of the review.

## Backfill

Granular push only fires on *new* actions, so anything already in the
local store before sync existed would never reach the server. Both
platforms close this with `enqueueAllLocalAndFlush()`, run **once** on
startup (gated by a `localBackfilledV1` pref flag) and also exposed as a
manual **"Push all local data"** dev button. It enqueues every local
favorite (shows + songs), the top 4 recents, and every review into the
outbox and drains them through the normal granular push (LWW per row).

## Disaster recovery

`PUT /api/user/sync` (full bulk push) is **not** part of the steady-state
path — it's last-write-wins *wholesale* and can clobber server-newer data.
It lives only behind a hidden dev button. Normal flow is always granular
push + bulk pull.

## QA smoke-test checklist

"Favorite on phone → see on web" and the conflict edges. Run signed in as
the same account on phone and web.

### Happy path
1. **Favorite a show on the phone** → open `/me/favorites` on web →
   the show appears (allow a focus/refresh on web).
2. **Unfavorite it on the phone** → web → it disappears.
3. **Favorite a song** on the phone → web favorites-songs surface (once
   built) reflects it.
4. **Write/edit a review** (rating + notes + a player tag) on the phone →
   `/me/reviews` on web shows the rating, notes, and the tag.
5. **Delete the review** on the phone → web → it disappears.
6. **Play a show** for ≥10s on the phone → `/me/recent` on web lists it
   most-recent-first.

### Backfill
7. Sign in on a phone with a pre-existing local library on a **fresh
   server** (or tap **Push all local data**) → web shows favorites,
   reviews, and the top-4 recents.

### Conflict / LWW
8. **Edit-vs-edit:** change a review on the phone, then change the same
   review elsewhere later → the later `updatedAt` wins after both sync.
9. **Delete-vs-edit:** delete a favorite on phone while it still exists
   server-side → after sync the later action wins (delete if the
   tombstone is newer; resurrected if the edit is newer).
10. **Offline push retry:** turn off the network, favorite a show
    (queued), restore the network, foreground the app → the queued write
    flushes and appears on web.

### Negative / robustness
11. **Missing-catalog show:** a pulled favorite for a `showId` not in the
    local catalog is skipped without error (check `skippedMissingShow` in
    the apply log), and imports after a data refresh.
12. **Downloads untouched:** a downloaded recording on the phone is **not**
    cleared by a pull from another device.
