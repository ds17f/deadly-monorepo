# ADR-0016: Connect — fast liveness detection + full-state handshake on (re)connect

## Status

Proposed (2026-06-16). Extends
[ADR-0011](0011-connect-session-ownership-and-protocol-versioning.md) (ownership
as a renewable lease + WS protocol versioning) and
[ADR-0006](0006-connect-v2.md) (server is the transport source of truth). ADR-0011
defined *how a still-connected device recovers ownership*; this ADR closes the two
gaps ADR-0011 assumes away — *how fast a device notices its socket is dead*, and
*what state it asserts the instant it reconnects*.

Does **not** un-defer [ADR-0008](0008-connect-cloud-coordination-deferred.md):
session state stays in-memory. What changes is the liveness budget and the connect
message contract.

## Context

ADR-0011's ownership lease heals an *ownerless* session — but only **once the
audio-producing device reconnects** and only when **`lease.recordingId ===
state.recordingId`** (`api/src/connect/state.ts` `handleHeartbeat`). Two real
conditions violate those preconditions and the lease silently does nothing.

Diagnosed 2026-06-16 from an Android bug report (`SM-S926U`, app 2.40.1,
`deadly-logs-20260616-105106.txt`).

### The failure that motivated this

A session started at 10:35:48. The client believed it already had a live socket
(`startIfAuthenticated` took the `if (shouldConnect)` early-return; `_isConnected
== true`), so `sendSeek`/`sendPlay` at 10:35:51–52 were written into a **dead
socket** — buffered, never delivered, never confirmed (`pendingCommand 'play' not
confirmed in 6000ms`). Then **4m13s of total silence** — no state, no position
heartbeat — until `Failure: Connection timed out` at 10:40:11 finally triggered a
reconnect. Local audio played the whole time; Connect was a ghost for ~4.5 minutes.

### Root cause: a liveness budget that is asymmetric and far too slow

The OkHttp client is built with `readTimeout(0)` (`ConnectServiceImpl.kt:85`)
"for WebSocket keep-alive" but **no `pingInterval`** — the other half of the
idiom. With no ping frames and an infinite read timeout, a half-open socket is
only detected by the OS TCP retransmission timeout (~4.5 min observed). Meanwhile
the server evicts a silent device at **`lastHeartbeat > 45_000`, swept every 10s**
(`state.ts` `startHeartbeatSweep`), and the client heartbeats every **15s**
(`HEARTBEAT_INTERVAL_MS`). The numbers:

| Actor | Notices a dead peer in | Action on notice |
|---|---|---|
| Server sweep | ~45–55s | nulls `activeDeviceId`, sets `playing=false` |
| Client (no ping) | up to ~4.5 min (OS TCP) | `onFailure` → reconnect |

This asymmetry is what makes two devices play at once. The server frees the
session at ~50s and a second device legitimately claims the vacuum — but the
zombie's **client** keeps producing audio for up to ~4.5 min because nothing tells
it to stop. **Double-play window ≈ 50s → 4.5min, i.e. up to ~3.5 minutes of two
devices playing the same session.** ADR-0011's lease cannot help: the zombie never
sends a renewal because it doesn't know it disconnected.

### Second gap: reconnect reacts to a pre-handshake stale snapshot

`sendRegister` carries identity only (`deviceId`/type/name/`protocolVersion`/
`appVersion`) — **no `recordingId`/`trackIndex`/`positionMs`/`playing`**. So the
first `state` the client reacts to after reconnect is the server's **stale
memory**, and the first corrective heartbeat (which *does* carry the lease) does
not fire until `HEARTBEAT_INTERVAL_MS` (15s) later. In that 0–15s window
`reactToState` acts on a snapshot that predates the client ever telling the server
where it is. If the local recording diverged during the dead-socket gap (user
picked a new show), the stale snapshot wins: `reclaimOwnerless` requires
`localRecordingId == new.recordingId` and the lease requires the same
(`state.ts:302`), so both back off, the `wasActive && !nowActive` branch
(`ConnectServiceImpl.kt:600`) **pauses the new show**, and the recording-mismatch
branch (`:610`) **reloads the server's old recording, paused** — clobbering the
user's choice.

Both gaps are the same disease ADR-0011 named — *live local audio vs. a server
view that is stale across a disconnect* — surfacing wherever the lease's two
preconditions (timely reconnect, matching recording) don't hold.

## Decision

### 1. Detect a dead socket in seconds, not minutes (client ping)

Configure `pingInterval` on the Connect OkHttp client (and the iOS
`URLSessionWebSocketTask` equivalent app-level ping timer). OkHttp then sends WS
ping frames; if no pong returns within the interval it fails the socket and the
existing `onFailure → handleDisconnect → reconnect` path runs. `readTimeout(0)`
stays — `pingInterval` is the liveness half it was always missing.

### 2. Make the liveness budget symmetric: client notices before the server frees the session

Choose the client ping interval so the **producing device detects its dead socket
and reconnects (re-asserting the lease) before the server's 45s eviction nulls the
session.** A ~20s ping gives detection in ~20–40s, inside the server's 45–55s
window. This closes the double-play window from the right end: the zombie pauses /
reconnects *before* a second device is handed an ownerless session. The server
sweep threshold and the client ping/heartbeat intervals are now one **coupled
budget**, documented together, not three independently-chosen constants.

### 3. (Re)connect is a full-state handshake, not a bare register

The audio-producing device declares its complete authoritative state **as part of
(re)connecting** — `recordingId`, `trackIndex`, `positionMs`, `playing`,
tracklist — so the server's **first** post-reconnect snapshot already reflects the
live device's reality. Implementation reuses ADR-0011's lease payload: send it on
`register` (or an immediate first heartbeat that precedes any broadcast the client
will react to), additive and gated on `protocolVersion`. The client does **not**
run `reactToState` against any snapshot known to predate its own handshake (the
existing `currentVersion = -1` reset becomes "ignore until handshake acked").

Together these collapse the recording-divergence special-casing: on reconnect the
producing device's declared state is authoritative and asserted atomically, so the
stale-snapshot race in §Context-2 cannot fire.

### 4. Compatibility — same rules as ADR-0011 §4

Additive fields only; gated on `protocolVersion`. Old clients send a bare
`register` and keep today's behavior (the ghost + the slow OS-TCP detection) — **no
worse than now**, never preempted because the lease still only fills a vacuum
(ADR-0011 §2). The server reads sensible defaults when the handshake payload is
absent. Advance `MIN_SUPPORTED` on telemetry, not hope.

## Consequences

- **Double-play shrinks from ~minutes to ~seconds**, and is bounded by the *coupled*
  ping/sweep budget rather than the OS TCP timeout. The most user-audible Connect
  failure ("both my devices are playing") largely disappears for updated clients.
- **Reconnect stops reacting to stale state.** The recording-change-on-reconnect
  clobber is eliminated for handshake-capable clients; the lease's
  matching-recording precondition stops being a silent trap.
- **One coupled liveness budget.** The ping interval, heartbeat interval, and
  server sweep threshold must be reasoned about together (and asserted by a test /
  documented constant), or the asymmetry creeps back. This is new coupling we
  accept deliberately.
- **More frequent pings = marginally more wakeups/battery** on mobile while
  connected. Connect already holds a foreground socket during playback
  (ADR-0007); a 20s ping is negligible against the 15s heartbeat already in flight.
- **Old clients keep both gaps** until their users update — consistent with
  ADR-0011's accepted "forced update is acceptable for Connect" stance.
- **Single-instance assumption still load-bearing** (ADR-0011): the handshake
  reconciles against one in-memory `userStates`; horizontal scaling would need the
  presence/state registry that ADR-0008 defers.

## Alternatives considered

- **Only add `pingInterval`, skip the handshake.** Shrinks the double-play window
  (the loudest symptom) with a one-line change, but leaves the 0–15s post-reconnect
  stale-snapshot race and the recording-divergence clobber intact. Worth shipping
  *first* as the high-value, low-risk piece — but not the whole fix. This ADR keeps
  both under one coherent decision so the ping isn't mistaken for "done."
- **Shorten only the server sweep** (e.g. evict at 20s). Makes the server free the
  session *sooner*, which **widens** the double-play window unless the client also
  detects faster — it treats the wrong end. The client must notice first; the
  server timeout follows from that, not the reverse.
- **Server pings the client instead of the client pinging.** Equivalent liveness
  signal, but the client must detect *its own* dead socket to stop local audio and
  reconnect — a server-side ping that fails only tells the *server*, which already
  has its sweep. The authority that needs to react (the one making sound) is the
  client, so the client must ping.
- **Persist session state in Redis** (ADR-0011's rejected "root-lite"). Would let a
  reconnecting client rehydrate, but does nothing for liveness detection (the
  zombie still plays for 4.5 min) and is still gated on social (ADR-0008).
  Orthogonal to both gaps here.
- **Keep patching `reactToState` branches** for each new divergence (the next
  recording/track/position special-case). Rejected for the reason ADR-0011 exists:
  it is detector accretion. The handshake removes the *class* of "reacted to stale
  state on reconnect," rather than the next instance of it.
