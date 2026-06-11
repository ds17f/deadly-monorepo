# ADR-0011: Connect — session ownership as a renewable lease + WS protocol versioning

## Status

Proposed (2026-06-11). Design agreed; not yet implemented. Extends
[ADR-0006](0006-connect-v2.md) (server is the transport source of truth),
[ADR-0007](0007-connect-background-socket-lifecycle.md) (socket tied to
playback/process), and [ADR-0008](0008-connect-cloud-coordination-deferred.md)
(durable cloud state deferred, gated on social). Touches the active-device path
introduced in [ADR-0010 §7](0010-playback-auto-advance-and-show-queue.md)
(cross-device end-of-show countdown), which is where the failure below first bit.

This ADR **does not un-defer ADR-0008.** Session state stays in-memory. What
changes is *how a still-connected device recovers ownership* — replacing a stack
of cause-specific detectors with one convergent mechanism.

## Context

ADR-0006 made Connect session state (`userStates`: `showId`, position,
`playing`, `activeDeviceId`) **in-memory and authoritative on the server**, with
recovery defined as "recoverable from the still-connected active device." That
recovery has, in practice, accreted into a pile of **cause-specific heuristics on
the client**, all answering one question — *"the server forgot I'm the active
device; how do I prove it again?"*:

- `serverRestarted = lastEpoch != null && epoch changed` → reclaim (Android
  `ConnectServiceImpl`), mirrored on iOS/web.
- `new.tracks.isEmpty() && cachedTracks` → re-assert the load.
- `reassertingTracks` one-shot guard so position broadcasts don't re-fire it.

### The failure that motivated this

Diagnosed 2026-06-11 from device + server logs. A phone playing a show **through a
server restart** never auto-advanced at end-of-show. Chain:

1. The API (single in-memory instance) restarted; `userStates` was wiped.
2. The phone's WS reconnected and re-registered ~1s later. The server rehydrated
   `showId`/position from the persisted playback row, but **not** `activeDeviceId`
   / `playing` — so the session came back as `playing=false, activeDevice=null`.
3. The phone kept producing audio across the reconnect (the audio engine doesn't
   care about the socket) and never re-sent `play` (no new intent), so the server
   never re-learned the phone was active. `seek`/`position` don't claim active.
4. At end-of-show the phone announced the next show; the server set the
   `pendingAdvance` note and broadcast it — but with `activeDevice=null`, **no
   device considered itself active**, so nobody fired `playShow` at the deadline.
   The countdown ran out silently.

The existing restart-reclaim path could not save this: it triggers on
**witnessing an epoch change** (`lastEpoch != null && epoch != lastEpoch`), but a
client that connects *fresh after* the restart never sees the old epoch
(`lastEpoch == null`), so `serverRestarted` is false.

### This is not a one-off

The same orphaned-ghost state — *device playing locally, server believes
`active=null`* — also arises with **no epoch change at all**: `handleDisconnect`
(state.ts) nulls `activeDeviceId` when the active socket drops, so a device that
loses its socket on a network blip and reconnects while still playing is the
identical ghost. Each new way the server can forget ownership needs a new
detector. That is the tell that the heuristics are leaves, not the root.

### Root cause

`activeDeviceId`/`playing` are **authoritative server state that clients only
re-establish *reactively*** — on detecting a specific divergence pattern. The
device that *should* own the session (it's the one making sound) has no
*declarative* way to keep asserting that; it can only react to narrowly-detected
loss. Worse, the reactive path is gated on already-being-active (the
`playWhenReady` reconciler bails with `if (!_isActiveDevice) return`), so the one
device that should reclaim is forbidden from trying.

## Decision

### 1. Ownership is a renewable lease held by the device making sound

Keep explicit commands (`load`/`play`/`pause`/`transfer`) as the **fast,
immediately-consistent authoritative path** — they remain how a user action flips
ownership instantly. *Additionally*, the device that is locally producing audio
**renews its ownership claim by piggybacking playback state on the existing
heartbeat**: `{ playing, recordingId, positionMs }`. The server treats
`activeDeviceId` as a **lease the live, audio-producing device renews**, not a
fact it must remember forever. Recovery becomes **convergent and cause-agnostic**:
restart, disconnect-cleanup, or a dropped message all reconverge within one
heartbeat interval, with no client-side "did the server restart?" inference.

This is the *correct implementation* of ADR-0006's "recoverable from the
still-connected active device," not a departure from it. Once the lease provably
covers them, the epoch-reclaim, empty-tracks re-assert, and `reassertingTracks`
heuristics are **deleted**.

### 2. The lease is conservative: it fills a vacuum, it never preempts

The renewal claims ownership **only when the session is ownerless**
(`activeDeviceId == null`) **or refreshes the existing owner**. It must **never**
revoke a device that holds ownership via an explicit command. This is the same
rule `handlePlay`/`handleLoad`/`handleAnnounceNext` already follow ("claim when
null"). It is what makes a mixed old/new fleet safe (see §4) and prevents
split-brain flapping.

- **Ownership ≠ "playing right now."** A paused owner keeps the lease — renewal
  continues while connected, playing or paused. The lease tracks *"I hold this
  session,"* not instantaneous `isPlaying` (which dips on every buffering stall).
- **Conflict resolution.** If two devices both assert an ownerless session
  (genuine split-brain), tiebreak by **most-recent explicit user action**, then by
  lease timestamp — deterministic, last-writer-wins, no flap. In normal operation
  only one device produces audio, so this is the rare path.

### 3. WS protocol versioning (the enabling primitive)

On `register`, every client sends:

- **`protocolVersion`** — a monotonic integer describing the **wire contract**.
  Absent ⇒ legacy ⇒ treated as `0`. New builds start at `1`. **Server behavior
  branches only on this.**
- **`appVersion`** — a string (build identity) for **telemetry, "please update"
  UX, and debugging only**. Behavior is **never** branched on it.

The server stamps both onto the **in-memory connection record** (`liveDevices`
entry, alongside the socket) — *not* Redis. Protocol version has the **same
lifetime as the socket**: it arrives on `register`, is valid until the socket
closes, and is re-sent on every reconnect (self-refreshing, never stale).
Persisting it would be wrong — a Redis copy could outlive the socket and misreport
a reconnecting client's capability before it re-registers.

The lease (§1) is gated on `protocolVersion >= N`. Below that, the server keeps
the legacy behavior for that device.

Lifetime boundary, stated once so it doesn't blur:

| State | Lifetime | Home |
|---|---|---|
| socket, `protocolVersion`, `appVersion`, device name/type | per-**connection** | in-memory `liveDevices` |
| `showId`, position, ownership/`playing` | per-**session** | in-memory `userStates` (Redis-persist still deferred, ADR-0008) |

### 4. Backwards compatibility (Connect is early; forced update is acceptable)

Connect is newly shipped with a small live base. We accept telling users they
**must update** to fix Connect issues — and a server-enforced floor turns the
app-store release lag into update *motivation*. Even so, the server is a single
shared deployment and mobile builds lag, so changes are **additive + conservative**:

- **Additive fields only.** New `register`/heartbeat fields are optional; old
  clients omit them and the server reads sensible defaults. Old clients ignore
  unknown keys in broadcasts (already proven by the additive `pendingAdvance` in
  ADR-0010 §7). No existing message shape changes.
- **Old clients are never preempted.** Because the lease only fills a vacuum
  (§2), a new client renewing its lease cannot yank a session a legacy client
  owns via command. Old clients keep *today's* behavior — they don't self-heal
  the restart/disconnect ghost, but they are **no worse** than now.
- **Soft floor, not a hard wall.** `protocolVersion < MIN_SUPPORTED` degrades
  Connect gracefully and surfaces an "Update for Connect" nudge (server can send
  `{type:"error", code:"client_too_old"}`). Reserve hard rejection for
  correctness/security must-haves — a hard wall would lock out users whose update
  is stuck in review. Advance `MIN_SUPPORTED` on **telemetry**, not hope.
- **Mixed fleet is permanent during rollout** (web bumps instantly on deploy,
  mobile straddles for weeks). The version makes the branches *explicit*; it does
  not excuse incompatible handlers.

### 5. Keep the `handleAnnounceNext` active-claim already shipped

The 2026-06-11 server fix — `handleAnnounceNext` claims the announcing device as
active when `activeDeviceId == null` — stands. It is a consistent instance of
"claim when null" (§2), makes `announce` match `handlePlay`/`handleLoad`, and
rescues the auto-advance failure even before the lease lands. The Android
`firstStateAfterConnect` stub started while diagnosing is **reverted** — it is a
detector the lease makes unnecessary.

## Consequences

- **One convergent recovery mechanism** replaces N cause-specific detectors. Net
  code is roughly a wash (the lease + conflict rule cost about what the deleted
  heuristics saved) — the win is *one coherent thing with a retirement path*, not
  a pile that grows by one each time the server finds a new way to forget.
- **Recovery is eventually-consistent** (bounded by the heartbeat interval) where
  the command path is immediate. Acceptable: the lease is the *healing* layer, not
  the *control* layer; user actions still flip ownership instantly via commands.
- **A version number is debt with a payoff.** Each `if (proto >= N)` branch must
  eventually be deleted; protocol-distribution telemetry is what licenses the
  deletion. Skipping the deletion step re-creates the whack-a-mole in a new shape.
- **Single source of truth for protocol semantics.** A `docs/PROTOCOL.md` (or a
  shared constant) must record what each `protocolVersion` means, or the three
  clients will disagree about what `2` implies.
- **Single-instance assumption is load-bearing.** Per-connection facts
  (`protocolVersion`) live only in that instance's memory. Horizontal scaling
  would require sticky routing or a shared presence registry (and *that* presence
  record could live in Redis, keyed to the connection and reaped on disconnect).
  Out of scope now — documented so future-us isn't surprised.
- **Old clients keep the ghost** until their users update. Chosen deliberately
  over un-deferring ADR-0008's durable state (which *would* fix old clients on the
  restart variant, server-side and update-free) — durable state remains gated on
  the social feature, and the lease additionally covers the disconnect variant
  that persistence alone would not.

## Alternatives considered

- **Persist session state in Redis (the "root-lite").** Server-only, zero wire
  change, and it would fix the restart variant for the *entire* fleet including
  old builds with no update — genuinely attractive. Rejected as the primary fix
  here because (a) it does **not** cover the disconnect-cleanup variant, where
  `handleDisconnect` actively nulls `activeDeviceId` with no restart to recover
  from, and (b) ADR-0008 deliberately gates durable server-side state on the
  social/presence feature, where it pays for itself. The lease covers what this
  bug needs without un-deferring that decision. Redis-persist remains the likely
  path *when social lands*, complementary to the lease.
- **Keep patching detectors** (finish `firstStateAfterConnect`, add the next one
  when the next trigger appears). Rejected: it is the accretion this ADR exists to
  stop; every new failure mode needs a new detector and none are ever deleted.
- **Capability flags instead of a monotonic int** (`["lease","countdown_v2"]`).
  More flexible — lets features ship out of order — but more bookkeeping than a
  single-track protocol with one client codebase per platform needs today (YAGNI).
  The int gives a clean `MIN_SUPPORTED` gate now; switch to flags if/when two
  capabilities must ship independently.
- **Branch behavior on `appVersion`.** Rejected: couples wire capability to
  marketing/build identity and forces "≥ which build has feature X" lookup tables.
  `protocolVersion` decouples them so two releases can speak the same protocol.
- **Silent push to wake a sleeping device** (the ADR-0008 Spotify path). Still
  deferred and orthogonal — this ADR is about a *connected* device recovering
  ownership, not waking a suspended one.
