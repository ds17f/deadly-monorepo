# ADR-0017: Connect — honor a remote seek by intent (a nonce), not by position magnitude

## Status

Accepted (2026-06-16). Refines
[ADR-0006](0006-connect-v2.md) (server is the transport source of truth). Touches
the same `reactToState` seek branch that
[ADR-0016](0016-connect-liveness-detection-and-connect-handshake.md) reasons about,
but is orthogonal: ADR-0016 is about *liveness/double-play*, this is about *when an
active device should move its own playhead in response to server state*.

In-memory session state is unchanged — this does **not** un-defer
[ADR-0008](0008-connect-cloud-coordination-deferred.md). It adds one additive field
to `ConnectState`.

## Context

The active (audio-producing) Connect device subscribes to broadcast `ConnectState`
and, in `reactToState`, reconciles its local player against it: same track, follow
remote seeks; track changed, skip; etc.

`ConnectState` carries a single `positionMs`/`positionTs` pair that is overloaded
for two very different things:

- an **explicit seek** — a controller (or another device) dragged the scrubber and
  wants the playhead to jump; and
- a **routine position report** — the active device's own ~5s heartbeat of "here is
  where I am now," which the server stores into the very same `positionMs`.

The active device cannot tell these apart from `positionMs` alone, so the original
code used a **magnitude heuristic**: follow a remote `positionMs` change only if it
differs from the *local* playhead by more than a threshold (Android
`REMOTE_SEEK_THRESHOLD_MS = 2000`; iOS inline `delta > 2000`). The local-position
comparison (rather than old-vs-new server state) was a deliberate attempt to ignore
our own reports echoing back.

### Why the heuristic is wrong in both directions

A threshold on distance is the wrong axis, because the thing we actually care about
is *intent*, not *distance*:

- **False positives → "skips."** Position reports are sampled and arrive jittery and
  slightly stale relative to the live playhead. When our own report echoes back, the
  local playhead has already advanced past it; if that gap crosses the threshold the
  active device "follows" a seek to a position *it itself reported a moment ago* —
  yanking audio backward. This is the user-visible **skipping** bug.
- **False negatives.** A deliberate, *small* seek from another device — nudging back
  3s to catch a lyric — is under the threshold and silently ignored. The smaller and
  more intentional the seek, the more likely it is dropped.

No single threshold value fixes both: lower it and echoes skip more; raise it and
real seeks are dropped. The axis is wrong.

## Decision

Distinguish the two meanings **at the source**, where they are already distinct, and
carry that distinction in state.

The server already has separate handlers — `handleSeek` (an explicit `seek` command)
and `handlePosition` (a routine report). Add a monotonic counter to `ConnectState`:

```
seekNonce: number   // bumped ONLY by handleSeek; never by handlePosition
```

`handleSeek` does `seekNonce = state.seekNonce + 1`; `handlePosition` leaves it
untouched. The active device then follows a remote seek **iff `seekNonce`
advanced** — keying on intent, not on how far `positionMs` moved:

```
if active, same track, new.seekNonce != old.seekNonce, and we did not issue it:
    seek to new.positionMs
```

A position-report echo never bumps the nonce, so it can never trigger a self-seek
(kills the skip). A real seek of *any* size bumps it, so a 3s backward nudge is
honored exactly like a 3-minute jump.

### "We did not issue it"

When the active device issues its own seek it already moved its local player, then
the server echoes the nonce bump back. The client suppresses re-seeking on its own
echo by checking the in-flight command: Android compares the `cmd` snapshot taken at
the top of `reactToState`; iOS captures `pendingCommand` into `cmdAtEntry` before the
pending-command-clearing block nils it, and skips the seek when it equals `"seek"`.
(iOS also confirms/clears its pending `seek` on the nonce change rather than on a
`positionMs` change, so a seek to the same position still confirms.)

### Compatibility

`seekNonce` is additive and optional, exactly like `pendingAdvance`
(ADR-0010 §7). Old clients ignore the unknown field. New clients tolerate a server
that omits it: Android via the Kotlin default (`val seekNonce: Int = 0`), iOS via an
optional (`let seekNonce: Int?`, `decodeIfPresent` → `nil`, coalesced to `0`). The
web client has no active-device seek-reconciliation path and is unaffected. So the
field is safe to ship to a mixed fleet in any deploy order.

## Alternatives considered

- **Tune the threshold.** Rejected — no value separates jittery self-echoes from
  small intentional seeks, because the signal is intent, not distance.
- **Send seeks on a separate channel / message type instead of via state.** The
  server already routes `seek` and `position` to separate handlers, but the *active
  device* learns about a remote seek only through the broadcast `ConnectState`
  snapshot, not by replaying commands. A nonce on the snapshot is the minimal change
  that preserves "state is the source of truth" (ADR-0006) without a second delivery
  path to keep consistent.
- **A timestamp of the last seek instead of a counter.** A monotonic counter is
  immune to clock questions and to two seeks landing in the same millisecond; the
  client only ever asks "did it change," never "how recent."

## Consequences

- The skipping bug is eliminated by construction, not by tuning: routine reports
  cannot move the nonce.
- Small/precise remote seeks now land, including backward nudges that the threshold
  used to swallow.
- `ConnectState` gains one `Int`. `REMOTE_SEEK_THRESHOLD_MS` and the iOS inline
  threshold are deleted — the local-vs-server position comparison they needed is gone.
- `seekNonce` resets to `0` on server restart (state is in-memory). A reconnecting
  client may see `nonce 0 == 0` and simply not treat the post-restart state as a new
  seek, which is correct: it reconciles position through the normal become-active /
  handshake path (ADR-0016), not through this branch.

## Two related fixes shipped on this branch

On-device testing (single device against a server with an artificial broadcast delay
— see the `CONNECT_BROADCAST_DELAY_MS` knob in `broadcastState`, kept as a documented
test affordance) verified the nonce behavior and surfaced two pre-existing,
independent bugs in the remote-seek path. Both are fixed here because they make the
seek experience whole:

1. **`durationMs=0` on hydrated sessions → controller seeks to track start.** A
   controller computes seek position as `fraction × durationMs`. `durationMs` in
   shared state was only written by `load`/`seek`, so a hydrated/restored session
   left it `0` and every drag mapped to `0`. Fix: the active device now carries its
   real `durationMs` in routine **position reports** (`handlePosition` accepts it and
   overwrites only a `>0`, changed value), so controllers always have a valid scale.
   All three clients send it; the iOS controller additionally computes against its
   locally-resolved track duration with a `durationMs > 0` guard. This was present on
   the old threshold build too (a `pos=0` seek tripped the magnitude check just the
   same) — ADR-0017 only made it legible.

2. **Controller scrubber wasn't optimistic → laggy feel + duplicate seeks.** The
   controller's thumb was driven purely by echoed server state, so after a drag it
   snapped back until the round-trip completed; users re-dragged, firing duplicate
   seeks (observed as three `handleSeek`s from one gesture). Fix: on a remote seek
   the controller holds the scrubber at the requested position until **its** seek
   echoes back (`seekNonce` advances past the value captured at send) or a ~6s
   deadline passes — reusing this ADR's nonce as the confirmation signal. iOS gates
   the hold functionally inside `interpolatedRemotePositionMs` (no mutation during
   render); Android weaves an `optimisticSeek` `StateFlow` into `playbackStatus`.
