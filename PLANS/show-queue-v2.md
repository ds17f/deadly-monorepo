# Show Queue v2 — auto-advance + queue (build plan)

Branch `show-queue-v2` (fresh off `main`; the old `show-queue` branch is
**abandoned**, not merged). Design spec: [`docs/adr/0010-playback-auto-advance-and-show-queue.md`](../docs/adr/0010-playback-auto-advance-and-show-queue.md).
Roadmap: §1 in [`ROADMAP.md`](ROADMAP.md).

## The shape (one paragraph)

Auto-advance is an **independent coordinator** that listens for one positive
event — **`onShowCompleted(showId)`**, "the final track played to its natural
end" — and on it (optionally counts down, then) calls `playShow(next)`, which is
an ordinary play. It reads **no** transport/Connect state, so there is no fight
and no patch layer. To keep Connect from dragging the device back, the active
device sends a **park** ("I'm done") command at completion so the server stops
believing it's playing. The **queue is local-first** (works signed-out;
authoritative locally) and **syncs as a whole-list snapshot** for signed-in
users, like Favorites. The advance target is **queue head, else next show by
date** (client-resolved — the server has no catalog).

## Why this replaces the first attempt

The abandoned `show-queue` branch made advancing a client decision that
interrogated transport state and fought the server's `playing=true` (the
"advance immediately / no countdown in Connect" patch, the guessed
`isActiveDevice` gate, `isAutoAdvancing`, `hasBeenPlaying`). Root cause +
corrected model are in ADR-0010 ("Why the first model was wrong"). **Do not
reintroduce those patterns.** If you find yourself reading `playing` /
`activeDeviceId` to decide whether to advance, stop — that's the old trap.

## Build order — each chunk proven on iOS + Android + web before the next

### Chunk 1 — reliable `onShowCompleted` on all three platforms
The riskiest atom; prove it in isolation. **No advance, no park, no queue.**
- Each platform's player adapter emits/logs `onShowCompleted(showId)` only on
  natural end of the **last** track.
- Verify on-device that it fires on natural show-end and does **NOT** fire on:
  pause, user-stop, error/skip, transfer (Connect), or cold-start restored state.
- Per-platform mapping:
  - **Android (Media3):** `STATE_ENDED` + "played this session" guard.
  - **iOS (AVQueuePlayer):** `AVPlayerItemDidPlayToEndTime` on the last item.
  - **Web:** `ended` on the last track.
- Proof = a log/toast. Web audio verified on beta / another machine (the
  primary dev box may not output web audio — unverified note from a prior
  session; confirm by testing).

### Chunk 2 — chronological auto-advance + the park primitive
On top of Chunk 1's signal. Still no queue.
- Advance coordinator: `onShowCompleted` → (cancelable countdown) → `playShow`
  of the **next show by date** (client-resolved from catalog).
- Active device sends **park** at completion (first cut may reuse `stop`; add a
  dedicated park action only if the ~1s remote "stopped" flicker bothers us).
- **Verify the original bug is dead:** auto-advance works under a Connect session
  on Android (and iOS/web) — countdown holds, no restart/drag-back, advances on
  exactly the audio-producing device.
- If "next" fails even on *immediate* advance, suspect a background-socket
  gremlin (ADR-0007), not this logic.

### Chunk 3 — local queue feeding the advance
- Local `play_queue` store (Room / GRDB) + domain model. *(Salvageable from the
  abandoned branch — re-introduce deliberately, not wholesale.)*
- Surfaces: add-to-queue entry points, a view/reorder/remove list, tap-to-play.
- Advance target becomes **queue head, else chronological**. Works signed-out.

### Chunk 4 — whole-list snapshot sync (signed-in)
- Debounced PUT of the full queue to the userdata layer
  (`api/src/db/userdata`); last-writer-wins; first-pull on sign-in/foreground.
- Refresh queue when a device becomes active (covers transfer-mid-queue).
- Absorbs the old `show-queue-sync` project.

### Settings cleanup (independent, anytime)
The settings screen is overloaded — regroup and improve findability. Not
coupled to the queue. Keep any end-of-show options here **minimal** until the
core advance is proven (the first model over-built this matrix).

## Salvage from the abandoned `show-queue` branch (reference, don't cargo-cult)
Genuinely good, framework-level pieces (re-introduce in Chunk 3+):
- `play_queue` table + DAO/entity (Room) and GRDB record/DAO + migration.
- Domain model (`QueuedShow` / `QueuedShowItem`).
- Add-to-queue UI entry points and the Favorites "Queue" segment list rows.
Leave behind: the advance coordinator's transport-state interrogation, the
Connect guards, the end-of-show settings matrix, the interrupt snackbar.

## Gotcha: wipe stale DB on devices that ran the abandoned branch
The old `show-queue` branch bumped the schema (Android Room **v26** +
`MIGRATION_25_26`; iOS GRDB `v15-play-queue`). `show-queue-v2` is off `main`, so
its schema is **lower** — a device still holding the v26 DB crashes on launch
(`IllegalStateException: A migration from 26 to 25 was required but not found`;
Room can't downgrade). Fix: clear app data —
`adb shell pm clear com.grateful.deadly.debug` (Android) / delete+reinstall the
iOS app — before installing a `show-queue-v2` build on such a device.

## Status
2026-06-10: **Chunk 1 (`onShowCompleted` detection) — code complete, proven on
web + Android; iOS verification in progress.**
- **Web ✓** — `🏁 SHOW-COMPLETE` fires on natural end-of-show, silent on
  pause/stop/skip (browser console; needs a hard-refresh past the PWA service
  worker after each redeploy).
- **Android ✓** — fires on `READY → ENDED`; silent on pause, stop (`→ IDLE`),
  show-switch, and **force-quit + cold relaunch** (the `hasPlayedThisSession`
  guard — the exact false-positive the old build tripped on).
- **iOS ✓** — verified in Console.app (`PlaylistService` category): fires on
  natural end-of-show, silent on the negatives.

**Chunk 1 COMPLETE on all three platforms.** Next: Chunk 2 — chronological
auto-advance + the Connect "park" primitive (verify the original
Android-under-Connect bug is dead).

### Known pre-existing bug to fix *during* Chunk 2 (not a Chunk 1 regression)
iOS miniplayer play/pause icon **sometimes** sticks on "play" after
`ended → tap a song in the same show` — the full-player button + track highlight
stay correct. Both icons read `service.isPlaying` (= `playbackState == .playing`,
false during `.buffering`); the miniplayer-only divergence is a SwiftUI
re-render timing gap in `MiniPlayerOverlay` as the engine settles
`buffering → playing`. Fix in Chunk 2 by driving the icon off **play-intent**
(`StreamPlayer.onPlayIntentChange`, true through buffering) rather than the
knife-edge `.playing` state — Chunk 2 reworks this exact `ended → play next`
transition anyway.
