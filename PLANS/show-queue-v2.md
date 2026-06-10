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

## Status
2026-06-10: **Design accepted (ADR-0010), branch created, no code yet.** Next:
Chunk 1 — `onShowCompleted` detection across the three platforms.
