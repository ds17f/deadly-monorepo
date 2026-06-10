# ADR-0010: Show queue (persistent "play next" list of shows)

## Status

Accepted (2026-06-09). Implementation in progress on `show-queue` (both
platforms). Execution detail lives in
[`PLANS/ROADMAP.md`](../../PLANS/ROADMAP.md) §1; this ADR records the binding
decisions and trade-offs.

## Context

The most-requested feature cluster from the community (r/thedeadlyapp,
2026-06) is playback queue + autoplay + shuffle (ROADMAP §1). This ADR covers
the first and foundational piece: a **queue of shows** — a "play next" list
separate from Favorites.

Two facts about the current player shape this decision:

1. **A "queue" already exists, but it's the wrong layer.** `QueueInfo`
   (Android) / `QueueState` (iOS) track `currentIndex` / `totalTracks` *within
   the currently-loaded show* — the ExoPlayer / `StreamPlayer` media-item list.
   `PlayerQueueSheet` renders it. That is the **within-show track queue**. It is
   not, and must not be confused with, the show queue this ADR introduces.

2. **The only existing notion of "what plays after this show" is
   chronological.** `PlaylistService.navigateToNextShow()` advances to the next
   show *by date*. There is no user-curated ordering.

3. **There is no per-show resume.** A single global `last_played_track`
   (showId, recordingId, trackIndex, positionMs) is persisted every 5s and on
   background, purely for *app-relaunch* restoration
   (`PlaybackRestorationService` / `LastPlayedTrackStore` on iOS, the equivalent
   on Android). It is **overwritten** the moment a different show plays.
   Consequently, **playing a show always starts from the beginning** — returning
   to a show you abandoned mid-set does not resume.

Why a show queue and not just "favorite then play": Favorites are permanent and
**synced**, and they feed the Fan-Favorites discovery rail (ADR-0005). Using
Favorites as a play-next list would pollute those stats and never clear. The
queue must be a distinct, transient-by-semantics, local-only structure.

## Decision

### 1. Unified model — the queue is the single source of "what plays next"

Modeled on Apple Music "Playing Next" / YouTube Music up-next, **not** a
two-state dormant/active queue. There is no "enter the queue" mode to learn.

- The **current show is a separate pointer** (the loaded show in
  `PlaylistService`). The **queue holds upcoming shows only** — it does not
  contain the now-playing show.
- **Playing any show makes it current and removes it from the queue** if it was
  there ("insert at head, pop it off" — net effect: it becomes current and is
  not left in the upcoming list). Playing a show that wasn't queued simply
  becomes current; there is nothing to pop.
- **"Add to Queue" appends to the bottom.** The list is freely **reorderable**
  and entries are **swipe-removable**.
- An **empty queue is the normal resting state**, not an error.

### 2. Auto-advance is gated and cancelable

When the current show ends **and** the queue is non-empty:

- A **cancelable countdown** ("N seconds until next show… [Cancel]") precedes
  advancing. Cancel stops playback and leaves the queue intact.
- Advancing pops the head of the queue and makes it current.
- Auto-advance is **gated by the autoplay setting** (ROADMAP §1 bullet 2):
  - **Autoplay OFF** → end of show stops; the queue stays populated; the user
    taps the next show manually. The queue is a pure "what's next" list.
  - **Autoplay ON** → countdown → auto-advance.

The queue's data model is identical under both settings — the setting only
controls whether end-of-show advances automatically. Building the queue now
therefore does **not** pre-spend bullet 2; it leaves a clean switch for it.

Auto-advancing between whole shows (2+ hours) is a much larger event than the
next-track autoplay users expect, which is exactly why it is gated **and**
softened by the countdown.

### 3. Queue entry shape

A queue entry is:

```
showId         : String         -- required
recordingId    : String?        -- null = resolve to recommended at play time
resumeTrackIndex   : Int?        -- null = start from the beginning (the default)
resumePositionMs   : Int64?      -- paired with resumeTrackIndex
position       : Int            -- explicit ordering, 0-based
addedAt        : Int64          -- enqueue timestamp
```

- The unit is the **whole show**. Track-level / playlist queuing is explicitly
  deferred (ROADMAP §8, and the track≠setlist limitation in ADR-0007 §9).
- `recordingId` is nullable and resolved to the recommended recording at play
  time, keeping entries forward-compatible with the source/recording picker
  (ROADMAP §3).
- The **resume snapshot is normally null** (shows start from the beginning). It
  is populated **only** for re-queued interrupted shows (Decision #4). Because
  there is no per-show resume store (Context #3), the resume position cannot be
  recovered later — it must be captured onto the entry at interrupt time.

### 4. Interrupt handling — replace by default, re-queue by one-tap nudge

When a **play-now** action interrupts a show in progress (playing A, user plays
B):

- **Default is replace** (standard for Apple Music / Spotify play-now). B
  becomes current; A is not automatically re-queued.
- A **non-blocking snackbar/toast** offers a single-tap escape hatch:
  > ▶ Now playing **B** · **[Queue A]**

  Tapping **Queue A** inserts A at the **head of the upcoming queue** with a
  **resume snapshot** (`resumeTrackIndex` / `resumePositionMs`) captured from
  where A was interrupted, so A resumes mid-set when reached. Ignoring it lets
  the snackbar fade — plain replace.
- **No modal prompt.** Asking on every interrupt is friction on a hot path.

Intentional users avoid the interrupt entirely by using **"Add to Queue"**
(keeps A playing, B queued behind it) instead of play-now. The two player
gestures — **Play** and **Add to Queue** — are both exposed.

### 5. Persistence

The queue is backed by a **local database table** (`play_queue`): Room on
Android, GRDB on iOS. It is:

- **Persistent** across app kills — it is "a thing," not in-memory state.
- **Not synced** to the server and **not a Favorite** — it does not feed
  Fan-Favorites or any discovery rail.
- **Transient by semantics only**: it shrinks as shows are consumed.

### 6. UI home

- **Canonical surface:** a third segment on the **Favorites** screen —
  **Shows · Songs · Queue** — reusing the existing segmented control
  (`SingleChoiceSegmentedButtonRow` on Android, `Picker(.segmented)` on iOS) and
  the favorites-style list rows, with **drag-to-reorder** and
  **swipe-to-remove**.
- **Fast access while playing:** a **Queue button on the player** (now-playing
  screen) that opens the same list. This is distinct from the existing
  `PlayerQueueSheet` (the within-show track list). The player surfaces **two**
  affordances: tracks-in-this-show vs shows-up-next.

### 7. Service contract (parity across platforms, no shared module)

A new `PlayQueueService` on each platform (paralleling existing service
patterns; no KMM shared module exists yet). Both platforms implement the same
conceptual contract:

- observable ordered list of upcoming entries (`StateFlow` / `@Published`)
- `enqueue(showId, recordingId?)` — append to bottom
- `enqueueNext(entry)` / `insertHead(entry)` — used by the interrupt re-queue,
  carries the resume snapshot
- `move(from, to)` — reorder
- `remove(entryId)` / `clear()`
- `peekNext()` / `popNext()` — used by auto-advance

Playback integration lives in `PlaylistService`: `playShow`/`playTrack` consult
and pop the queue; end-of-show detection drives the countdown + `popNext()`.

## Consequences

**Gained:**
- One playback code path. `playShow(x)` is "make x current (pop from queue if
  present)"; "what's next" is always "peek the queue head." No dormant/active
  state machine.
- The autoplay setting (bullet 2) drops in as a single gate with no model
  change.
- A persistent, reorderable, self-consuming list that stays out of
  Favorites/Fan-Favorites entirely.
- Forward-compatible with the recording picker (§3) via nullable `recordingId`.

**Accepted / given up:**
- **Two visually similar "queues"** in the player (within-show tracks vs
  upcoming shows). Mitigated by distinct affordances and labels, but it is a
  real surface-area cost and a naming hazard for future contributors.
- **Putting a transient, local, unsynced list under a tab named "Favorites"**
  is a slight taxonomic mismatch. Accepted because that screen is effectively
  the library surface and the segment is plainly labeled "Queue."
- **Resume-on-interrupt is best-effort and one-shot.** The snapshot is captured
  only at interrupt time onto the re-queued entry; there is still no general
  per-show resume. A second interrupt of the same show overwrites nothing global
  but produces a second entry decision for the user.
- **No cross-device queue.** The queue does not participate in Connect-v2 or
  user-data sync (ADR-0006). A queue built on the phone is not visible on web or
  another device. Deferred, consistent with sync v0 non-goals.
- **Parallel implementations** on two platforms must be kept at contract
  parity by discipline, not by a shared module.

## Alternatives considered

**Two-state dormant/active queue.** The queue only drives playback once you
"play from it"; playing from elsewhere is a one-off that never engages the
queue. Rejected: it is the *less* standard model (the big players use unified
up-next), it forces users to learn when the queue does and doesn't take over
("why did it auto-advance this time?"), and it requires an explicit
mode/state machine in the player. The unified model is both simpler to build
and less surprising.

**Queue as a Favorites-style synced list.** Reuse the favorites sync/store
machinery. Rejected: pollutes Fan-Favorites stats (ADR-0005), never clears, and
conflates permanent curation with transient play-next intent. Cross-device queue
sync is a deferred non-goal anyway.

**In-memory only (truly transient).** Drop the table; lose the queue on process
death. Rejected: mobile processes are killed constantly and the app already
persists playback state for restoration — a queue that evaporates on kill would
feel broken. "Transient" is honored semantically (self-consuming), not by
volatility.

**Modal prompt on interrupt** ("Add current show back to the queue?"). Rejected:
a blocking question on a frequent, low-stakes action. Replaced by the
non-blocking "Queue A" snackbar (Decision #4).

**Reuse the within-show track queue (`QueueInfo`/`QueueState`).** Rejected:
wrong altitude. That structure is the media-item list for a single loaded show;
the show queue is a higher layer over `PlaylistService.loadShow`. Overloading it
would entangle per-track navigation with show ordering.

**Carry a general per-show resume store** (so any returned-to show resumes).
Tempting, and it would make the interrupt resume "free," but it is a larger
behavior change (every show would resume, changing today's start-from-the-top
expectation) and is out of scope here. The queue entry carries its own snapshot
instead; a general resume store can supersede this later.
