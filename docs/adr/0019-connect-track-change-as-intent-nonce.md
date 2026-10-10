# ADR-0019: Connect — track changes as an intent nonce; handshake ships (ADR-0016 §3)

## Status

Accepted (2026-07-05). Generalizes
[ADR-0017](0017-connect-seek-as-intent-not-magnitude.md) (seek-as-intent-nonce) to
track changes, and records that
[ADR-0016 §3](0016-connect-liveness-detection-and-connect-handshake.md) (the
full-state handshake on (re)connect) is now **built** — ADR-0016 previously had
only its §1/§2 liveness half shipped. Both land behind `protocolVersion` 3. Adds
two additive fields to `ConnectState`/`register`; does not un-defer
[ADR-0008](0008-connect-cloud-coordination-deferred.md).

## Context

ADR-0017 fixed the "skips" bug for **seeks** by keying the active device's reaction
on intent — a `seekNonce` bumped only by an explicit `seek` — rather than on
position magnitude. It deliberately scoped itself to seeks.

The same disease lives on the **track-change** axis. The active device learns about
a remote `next`/`prev` only through the broadcast `ConnectState`, and could not tell
"another device pressed next" from "the echo of the `next` I just issued." The
clients guessed with a *coincidence* check — "is my engine already on
`new.trackIndex`? then it's my echo, skip." Two failures fall out of that guess,
both observed in the field:

- **Auto-advance restart / spurious `next`.** After the active device advances a
  track locally and emits `next`, the server echoes the new `trackIndex` back ~5s
  later. The coincidence guard usually suppresses it — but when the local engine's
  index and the echo momentarily disagree (a mis-inferred gapless advance on iOS, a
  race on Android), the device "follows" its own echo and restarts/!jumps the
  track, and because the index already moved the guard can never *correct* it.
- **It cannot distinguish a genuine remote skip from an echo** on principle — the
  same reason ADR-0017 rejected the magnitude threshold. It is the wrong axis.

Separately, ADR-0016 §3 (declare full state on reconnect so the client never reacts
to a pre-handshake snapshot) was designed but never implemented; only the
`pingInterval` liveness half shipped. The stale-snapshot clobber — a reconnecting
device reloading the server's *old* recording paused, or self-resuming a pause —
remained open on all three clients.

## Decision

### 1. `trackNonce` — track changes are intent, not coincidence

Add a monotonic `trackNonce` to `ConnectState`, bumped **only** by `handleNext` /
`handlePrev` (an explicit track command), never by `load` (a new show) or a routine
position report. The active device follows a remote track change **iff `trackNonce`
advanced and it did not issue the command** — exactly ADR-0017's rule on a second
axis:

```
if active, new.trackNonce != old.trackNonce, and we did not issue it:
    skip to new.trackIndex
```

A position/load echo never bumps the nonce, so it can never trigger a self-skip; a
real remote `next`/`prev` always bumps it, so it is always honored. The "did not
issue it" suppression reuses each client's in-flight-command snapshot, identical to
the `seekNonce` own-echo suppression. The trackIndex-coincidence guard is retained
only as a cheap "already there, don't restart" safety, not as the discriminator.

### 2. Handshake on register (ADR-0016 §3 implemented)

A device holding live audio sends an optional `handshake` payload on `register`
(the load payload shape). The server adopts it as authoritative **before its first
broadcast**, under the ownership lease's conservative rule (ADR-0011 §2): only a
**playing** device claims, and only into a **vacuum** — it never preempts a
different current owner. The first post-reconnect `state` therefore reflects the
live device's reality, so no client needs to special-case the recording-divergence
or self-resume clobber; the class is closed at the source.

### 3. Compatibility

Both fields are additive/optional, gated on `protocolVersion >= 3`, exactly like
`seekNonce`/`pendingAdvance` before them. Old clients omit the handshake (server
falls back to a plain register + the heartbeat lease) and ignore `trackNonce`
(coalesced to 0). Safe to ship to a mixed fleet in any deploy order. Server always
bumps `trackNonce`; only proto-3 clients react to it.

## Consequences

- The auto-advance restart / spurious-`next` family is fixed by construction on the
  *follow* side, the same way the skip bug was: an echo cannot move the nonce.
- The reconnect stale-snapshot clobber is eliminated for proto-3 clients — the
  reason ADR-0016 §3 existed.
- `ConnectState` gains one `Int`; `register` gains one optional object. The
  per-axis intent-nonce pattern (`seekNonce`, `trackNonce`) is now the established
  way Connect attributes a state delta; `play`/`pause` remain idempotent-reconciled
  and can adopt the same pattern (or a general `originDeviceId`) if a future echo
  bug on that axis warrants it.
- `trackNonce` resets to 0 on server restart (in-memory state); a reconnecting
  client sees `0 == 0` and simply doesn't treat the post-restart state as a new
  track command, which is correct — it reconciles through the handshake path.
