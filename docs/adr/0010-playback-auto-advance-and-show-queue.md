# ADR-0010: Playback auto-advance + show queue (local-first, transport-decoupled)

## Status

Accepted (2026-06-10). Implementation on `show-queue-v2`, built and verified
chunk-by-chunk across iOS, Android, and web simultaneously.

**Supersedes an earlier, abandoned design** (the `show-queue` branch, never
merged). That branch shipped the queue and auto-advance as one entangled change
and accreted a layer of patches around Connect — the "advance immediately, no
countdown, because the server keeps dragging us back" workaround, a client-
guessed `isActiveDevice` gate, an `isAutoAdvancing` suppression flag, and a
cold-start `hasBeenPlaying` guard. Those patches were the symptom; the root
cause is recorded under "Why the first model was wrong" below. This ADR records
the corrected model.

Related: [ADR-0006](0006-connect-v2.md) (Connect-v2, server is transport SoT),
[ADR-0007](0007-connect-background-socket-lifecycle.md) (background socket
lifecycle), [ADR-0008](0008-connect-cloud-coordination-deferred.md) (durable
server-side session state deferred).

## Context

The community's top ask (ROADMAP §1) is a playback queue + autoplay. Two facts
shape the design:

1. **Connect-v2 makes the server the single source of truth for transport.**
   Clients send commands; the server owns `playing` / `activeDeviceId` / position
   and broadcasts a full snapshot (ADR-0006). The server is deliberately
   **transport-only and has no catalog** — display metadata and "what's next" are
   client-resolved (Amendments #1, #4); a server-side track cache was built and
   reverted.
2. **There is no per-show resume.** A single global `last_played_track` exists
   only for app-relaunch restoration; shows otherwise start from the beginning.

### Why the first model was wrong

Auto-advance is a **transport transition** — "stop playing A, start playing B."
In Connect-v2, transport is the server's job. The abandoned design made advancing
a **client decision executed locally while interrogating transport state**: the
client decided there was a next show, guessed whether it was the device that
should act, and tried to drive a paused countdown — while the server still
believed A was `playing` and broadcast that, dragging the device back. The two
authorities fought. Every patch on that branch was a concession to the fight.
A single-source-of-truth model cannot tolerate a second, local authority over the
same state; the exceptions become whack-a-mole.

## Decision

### 1. The advance signal is "the final track completed," never "playback stopped"

"Playback stopped" is ambiguous — it conflates end-of-show with user-stop, pause,
error, transfer-park, and the cold-start restored-state case. The signal is
instead **positive and specific**: the *last track of the current show reached
its natural end of content*. Stop/pause/error/transfer do not produce it; a
cold-start restore does not produce it (no track completed in this session). So
we never disambiguate a generic stop — we never listen for one.

Disambiguation lives **inside each platform's player adapter** and each emits the
same semantic event:

> **`onShowCompleted(showId)`** — fired only when the final track of `showId`
> reaches natural end-of-content.

Per-platform mapping:
- **Android (Media3/ExoPlayer):** `STATE_ENDED` (reached only after running off
  the end of the media-item list; user-stop is `STATE_IDLE`, pause stays `READY`)
  **+ a "played in this session" guard** for the cold-start restored-ENDED case.
- **iOS (AVQueuePlayer):** `AVPlayerItemDidPlayToEndTime` on the **last** item
  (not a mid-show item).
- **Web (HTML5 audio):** the `ended` event on the **last** track.

### 2. Auto-advance is an independent coordinator that reads no transport state

The advance coordinator subscribes to `onShowCompleted` and **nothing else**. It
does not read `playing` / `activeDeviceId` / "are we parked" to decide anything —
that interrogation *was* the bug. On the event it (optionally) runs a cancelable
countdown, then calls `playShow(next)`.

- **Input-decoupled:** decided purely from the completion event + the queue +
  the timer.
- **Output-normal:** its only interaction with transport is the final
  `playShow(next)` — byte-for-byte identical to a user tapping a show. The server
  cannot tell an auto-advance from a manual play, and does not need to.

Gating falls out for free: **only the device producing audio reaches
`onShowCompleted`.** A remote-control device isn't playing the show locally, so
its player never completes — no `isActiveDevice` guess required.

### 3. Connect gains one informational primitive: "I'm done" / park

When a show completes, the active device sends a new **park** command so the
server stops believing it is `playing` (transitions to parked / `playing=false`).
This is what kills the v1 fight: once the server is out of `playing=true`, the
device can hold a countdown and advance without being dragged back.

- Park is a **transport fact**, not a command to the local player — at
  end-of-show the audio has already stopped on its own. It informs Connect; it
  drives nothing.
- Park carries **no queue state and no catalog data.** The server gains no
  knowledge of the queue and makes no advance decision (consistent with
  ADR-0006: server owns transport, clients resolve shows).
- The wire change is **additive** (ADR-0006 §8): a new optional command old
  clients ignore. First implementation may reuse the existing `stop` command if
  the ~1s "stopped" flicker on remote devices is acceptable; a dedicated park
  action that holds device ownership is the seamless upgrade if it isn't.

### 4. The queue is local-first; the server is a synced mirror, never an authority

**You can build a queue without an account, so the queue must live locally.**
Connect-v2 is per-user — no account means no Connect session, so a signed-out
user is always single-device and never has the fight or a transfer.

- The **local queue is authoritative** for every operation; the advance logic
  reads the local copy. Works fully signed-out.
- For **signed-in** users the queue **syncs as a whole-list snapshot** (debounced
  PUT, last-writer-wins) through the existing userdata layer
  (`api/src/db/userdata`), exactly like Favorites and playback position. A
  dropped or out-of-order snapshot just means the next one wins; a per-op delta
  log would need ordering/conflict guarantees the tiny list doesn't justify.
- Sync makes the queue **travel** with the user; it is **not** on the advance
  critical path. The transfer-mid-queue edge (active device moves, queue must
  follow) is covered because each signed-in device holds a synced copy; staleness
  at the moment of transfer is handled by refreshing the queue when a device
  becomes active.

This places the server-side queue firmly in **durable user data** (no push, no
live-session coordination), so it is compatible with ADR-0008's deferral of
durable *session* state — it is the "server-mostly easy half" that ADR named.

### 5. Advance target resolution

The advance coordinator computes the next show as: **queue head if present, else
the next show chronologically** (later date), resolved client-side from the
catalog. The server never resolves a show — it has no catalog. Chronological
auto-advance is therefore just "advance with an empty queue."

### 6. Build order: prove each piece on all three platforms before the next

Built chunk-by-chunk; each chunk is one capability, contract-defined once,
implemented **and verified on iOS, Android, and web** before the next. (Web audio
is verified on beta / another machine, not the primary dev box.) This is the
deliberate antidote to the first model's "one big change, many small problems."

### 7. Cross-device countdown over Connect (one shared note + explicit commands)

The end-of-show countdown is shown on **every** device in the session and can be
controlled from any of them — but the device producing audio stays solely
responsible for actually starting the next show. The mechanism is **one shared
piece of server state**, not per-second messaging:

- **Server state:** an additive, optional `pendingAdvance: { showId, deadline } | null`
  on `ConnectState` (the "note"). `deadline` is an absolute **server timestamp**.
  Additive-only per ADR-0006 §8; old clients ignore it.
- **Explicit commands** (no implicit inference — mirrors ADR-0006's `epoch`
  lesson that explicit beats inferred):
  - `announce_next { showId, deadline }` → server **sets** the note.
  - `cancel_advance` → server **clears** the note.
  - `advance_now` → server sets the note's **`deadline = now`** ("Play now").
- **Remotes count down locally.** Each device renders the note and computes
  `remaining = deadline − serverNow` using the existing clock sync
  (`serverTimeOffsetMs`), ticking on its own. The `announce` is the *only*
  message; there is **no per-second traffic**. Remotes resolve the show's
  art/info from `showId` themselves (consistent with Amendment #1).
- **One uniform rule for the playing device:** *advance when the note is present
  and `now ≥ deadline`.* So Cancel (note cleared → not present at the deadline →
  no advance) and Play now (deadline moved to now → advance immediately) both
  fall out of the same rule. The decision is an **explicit presence check at the
  deadline**, never an inference from a transition. Offline / no session: the
  device falls back to a plain local timer (no note exists).
- **Self-clearing:** the active device's own `load` on advance clears the note
  server-side; an explicit `cancel_advance` clears it on cancel.

This is **Phase B** (the local, active-device-only countdown is Phase A). It is
designed so the per-platform countdown UI reads a single merged source —
*local coordinator countdown OR the broadcast note* — and is therefore built
**once** per platform rather than retrofitted.

#### Sequence: normal end-of-show advance

```
 Active device (playing)        Server                Remote(s)
        |                         |                       |
   show ends                      |                       |
        |---- announce_next X,T -->|                      |
        |                     SET note {X,T}              |
        |<------- state ----------|------- state -------->|
   show "Next up Ns"                          show "Next up Ns"
        |        (every device ticks down to T locally)   |
   now >= T, note present -> play X                        |
        |---- load X ------------>|                       |
        |                    CLEAR note                   |
        |<------- state ----------|------- state -------->|
   now playing X                              hide countdown, follow X
```

#### Sequence: Cancel (from any device)

```
 Active device (playing)        Server                Remote
        |                         |<--- cancel_advance ---|   (Cancel tapped on remote)
        |                     CLEAR note                  |
        |<------- state ----------|------- state -------->|
   note no longer present:                     hide countdown
   at the deadline it won't advance;
   (and hides its countdown now)
```

#### Sequence: Play now (from any device)

```
 Active device (playing)        Server                Remote
        |                         |<--- advance_now ------|   (Play now tapped on remote)
        |                  SET note.deadline = now        |
        |<------- state ----------|------- state -------->|
   now >= deadline, note present -> play X                |
        |---- load X ------------>|                       |
        |                    CLEAR note                   |
        |<------- state ----------|------- state -------->|
   now playing X                              follow X
```

Local Cancel / Play now are the trivial cases — the active device acts directly
*and* sends the matching command so the shared note (and every remote) stays in
sync.

## Consequences

**Gained:**
- No second authority over transport, so no fight and no patch layer. The advance
  is a pure function of a specific event; its output is an ordinary play.
- End-of-show is detected by a specific positive event, eliminating the whole
  class of "stopped-for-the-wrong-reason" false advances.
- The queue works signed-out (local-first) and travels signed-in (snapshot sync),
  absorbing what was the separate `show-queue-sync` project — at no critical-path
  cost.
- One wire addition (park), additive and small.

**Accepted / given up:**
- **Parallel per-platform implementations** kept at contract parity by
  discipline (no KMM shared module). The shared contract is the `onShowCompleted`
  event, the park command, and the snapshot sync shape.
- **Reuse-`stop`-for-park** may flicker remote devices through a "stopped" state
  for ~1s before the next show loads, until/unless a dedicated park action lands.
- **Resume-on-interrupt and the end-of-show settings matrix are deferred**, not
  designed here — the first model over-built settings. Revisit minimally once the
  core advance + queue are proven.
- **Sync staleness at transfer** is best-effort (refresh-on-active), consistent
  with userdata being focus/foreground-refresh rather than realtime.

## Alternatives considered

**Server-authoritative queue + server-arbitrated advance** (server owns the
ordered list and directs the active device to advance). Rejected: it fails the
no-account case (no server identity → no queue), pulls forward durable
server-side state that ADR-0008 deferred, and is unnecessary — once advance is
input-decoupled (Decision #2) and park kills the fight (Decision #3), the server
does not need to decide or store anything for advance to be correct. The local-
first mirror (Decision #4) gets cross-device for free without making the server
an authority.

**Key advance off a generic "playback stopped" signal.** Rejected (Decision #1):
ambiguous on every platform; it is the source of the cold-start and
stopped-for-the-wrong-reason false advances.

**Infer "cancelled" implicitly from the note becoming null** (the active device
watches `pendingAdvance` go set→null). Rejected (Decision #7): it has a race —
between sending `announce` and the server echoing it back, the note still reads
null, so a naive "null ⇒ cancel" would cancel the device's own pending advance.
Worse, it repeats the exact implicit-inference mistake ADR-0006 fixed with the
explicit `epoch`. Replaced by an **explicit `cancel_advance` command** plus an
**explicit presence check at the deadline** ("advance only if the note is still
there"), which is race-free and unambiguous.

**Client-side advance that interrogates transport state to self-gate** (the
abandoned model). Rejected: that interrogation is the whack-a-mole; the
audio-producing device is intrinsically the only one that completes a show, so no
self-gate is needed.

**Per-op delta sync (outbox) for the queue.** Rejected in favor of whole-list
snapshot (Decision #4): the list is tiny and reorder/remove/pop deltas need
ordering and conflict resolution that a snapshot makes moot.
