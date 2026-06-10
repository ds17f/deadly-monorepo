# Show Queue — server sync + web (follow-up project)

Deferred from the show-queue bugfix pass (2026-06-09). Requested by the user;
**reverses an ADR-0010 decision** (the queue is currently local-only, not synced,
no cross-device). Scope it as its own effort — it spans api + web + both mobile
clients and is too big to land alongside bug fixes.

## Why it's a project, not a patch
ADR-0010 §5 explicitly made the queue **local-only / not synced** ("No
cross-device queue … Deferred, consistent with sync v0 non-goals"). Doing this
means amending that ADR and building a new synced collection end to end.

## What it touches
- **api/** — new synced collection `play_queue` (per-user, ordered). Likely
  mirrors the favorites sync machinery (see `FavoritesPushService` /
  `UserSyncAPIClient` / `SyncOutbox` on the clients, and the server userdata
  sync endpoints). Needs ordered entries (position), upsert/delete, and a
  full-list replace (reorder). Decide conflict model (last-write-wins per
  ADR-0006 userdata sync, or full-list replace on each mutation).
- **iOS** — push queue mutations through the sync outbox
  (`PlayQueueService.enqueue/remove/move/clear` → enqueue a push); apply remote
  changes via `UserSyncApplyService`. Refresh on focus/foreground (userdata sync
  is focus-refresh, not real-time — see memory `project_userdata_realtime_deferred`).
- **Android** — same: `PlayQueueServiceImpl` writes also enqueue a server push;
  apply pulls into the Room `play_queue` table.
- **web (ui/)** — add queue read + mutate; surface a "Your Queue" view and
  add-to-queue affordances, mirroring the mobile model. Web currently has no
  queue at all.

## Decisions to make first
1. Amend ADR-0010 (or new ADR) to make the queue a synced collection; note the
   reversal and why.
2. Sync granularity: per-entry outbox pushes vs. full-list snapshot replace.
   The queue reorders often → a snapshot replace on each change may be simpler
   and avoids ordering races.
3. Does the queue participate in Connect-v2 (cross-device *playback*) or only
   data sync? ADR-0010 said no Connect. Probably keep that — sync the list, not
   the playhead.
4. Analytics events for queue mutations must be registered in `EVENT_SCHEMAS`
   (see memory `project_analytics_event_allowlist`) or they're dropped.

## Verification
- Mutate queue on phone → appears on web after focus-refresh, and vice versa.
- Reorder/remove/clear propagate. Two devices converge.
- Local-only behaviors (auto-advance, pop-on-play) still work unchanged.

## Status
Not started. Bug-fix pass (tap-opens, head-only pop, auto-advance, Your Queue
rail) shipped on branch `show-queue` (commits `69cb6ba9`, `9161a853`).
