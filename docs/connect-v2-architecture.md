# Connect v2 Architecture

> Redesign of the Deadly Connect system — a Spotify Connect-like feature that
> synchronizes playback state across multiple devices (iOS, Android, web) through
> an authoritative server. This replaces v1 entirely — a clean break, not a
> migration.

## Design Principles

1. **Server is the single source of truth.** Clients never own state. They send
   commands (intent); the server validates, mutates, and broadcasts.
2. **Full state snapshots, not deltas.** The state payload is ~200 bytes. Send
   the entire thing on every change. No missed-delta bugs, no reconciliation.
3. **Monotonic version counter.** Every mutation increments an integer version.
   Clients ignore any state with version <= their current version. Total ordering
   with zero ambiguity.
4. **One code path for mutations.** Every state change — play, pause, seek,
   transfer, load, disconnect — goes through a single `mutateState()` function
   on the server. No special relay paths, no dual state systems.
5. **Optimistic UI with server reconciliation.** Clients may optimistically
   update their UI on command send, but snap to server state when the broadcast
   arrives. Active device acts optimistically; remote control devices show a
   loading/pending indicator and wait for server confirmation.
6. **Clients sync their clocks to the server.** Position interpolation depends
   on `Date.now() - state.positionTs`, where `positionTs` is the server's
   wall-clock. Any client whose clock disagrees with the server's by more than
   ~1s will mis-interpolate and skip or misseek. Every Connect client — current
   (web/iOS/Android) or future (Alexa, Sonos, CarPlay, tvOS) — MUST measure and
   apply a server-clock offset. See **Clock Sync** below.

## Why v1 Failed

- Clients pushed state (`session_update`), server relayed it. Two simultaneous
  updates produced frankenstate via `Object.assign`.
- Dual state systems (`ActiveSession` + `UserPlaybackState`) that could diverge.
- No version counter — only timestamps, which can collide or go backwards.
- 5 different message types mutated state through 5 different code paths.
- `session_play_on` was a device-to-device relay that also mutated state — a
  second mutation path that raced with the first.
- No heartbeat — ghost devices persisted until WebSocket close (unreliable on
  mobile networks).

---

## State

One `ConnectState` object per user. Always exists once a device connects (even
if nothing is playing). Server is the sole owner and mutator.

```typescript
interface ConnectState {
  // ── Versioning ──
  version: number;              // Monotonic, incremented on every mutation

  // ── What's loaded (null = nothing) ──
  showId: string | null;
  recordingId: string | null;
  tracks: SessionTrack[];       // Always present when a show is loaded
  trackIndex: number;

  // ── Playback position ──
  positionMs: number;           // Position at the moment of last update
  positionTs: number;           // Server timestamp (ms) when positionMs was recorded
  durationMs: number;           // Duration of current track

  // ── Playback status ──
  playing: boolean;             // true = playing, false = paused or stopped

  // ── Active device (null = parked / no device playing) ──
  activeDeviceId: string | null;
  activeDeviceName: string | null;
  activeDeviceType: DeviceType | null;

  // ── Show metadata for display ──
  date: string | null;
  venue: string | null;
  location: string | null;
}
```

### Derived States

There is no `status` enum. Status is derived:

| `playing` | `activeDeviceId` | Meaning |
|-----------|-------------------|---------|
| `false`   | `null`            | **Parked** — nothing active, last position preserved |
| `false`   | `"abc"`           | **Paused** — device abc has session, playback paused |
| `true`    | `"abc"`           | **Playing** — device abc is actively playing |
| `true`    | `null`            | Invalid — server must never produce this (invariant enforced in `mutate()`) |

### Initial State

When a user's first device connects and there is no persisted state:

```typescript
{
  version: 0,
  showId: null,
  recordingId: null,
  tracks: [],
  trackIndex: 0,
  positionMs: 0,
  positionTs: Date.now(),
  durationMs: 0,
  playing: false,
  activeDeviceId: null,
  activeDeviceName: null,
  activeDeviceType: null,
  date: null,
  venue: null,
  location: null,
}
```

When there IS persisted state from a previous session (loaded from SQLite), the
state is hydrated with the saved fields, `version: 0`, `playing: false`,
`activeDeviceId: null` (parked).

### Position Interpolation

Clients compute the current playback position without real-time streaming.
`positionTs` is a **server** wall-clock timestamp, so each client must apply
its measured `serverTimeOffsetMs` (see **Clock Sync**) when interpolating:

```
serverNow = Date.now() + serverTimeOffsetMs
currentPosition = state.playing
  ? state.positionMs + (serverNow - state.positionTs)
  : state.positionMs
```

The active device sends periodic `position` commands (~5s) to correct drift.
These go through the normal mutation path — server updates `positionMs` and
`positionTs`, increments version, broadcasts.

### Clock Sync

`positionTs` is server wall-clock time. A client whose local clock is offset
from the server by N ms will read every interpolation as N ms off, causing
audible skipping (forward jumps when the seek-guard threshold is crossed) or
misseeks on track changes. Therefore every client measures the offset.

**Protocol** — a stateless `time_sync` round-trip on the same WebSocket:

```
client → { "type": "time_sync", "clientTs": <localNow ms> }
server → { "type": "time_sync", "clientTs": <echoed>, "serverTs": <Date.now() ms> }
```

The server reply is immediate, does not mutate state, and is not broadcast.

**Client algorithm** (identical across platforms):

1. After `register`, send 3 `time_sync` requests spaced ~200ms apart.
2. For each reply, compute:
   - `rtt = localNow - clientTs`
   - `offset = serverTs - (clientTs + rtt / 2)`
3. Keep the sample with the **smallest RTT** (NTP min-delay heuristic).
4. Re-run the 3-sample sync every **5 minutes** while connected.
5. On disconnect, clear the offset; re-sync on reconnect.
6. Default `serverTimeOffsetMs = 0` until the first sync completes (legacy
   behavior in the brief pre-sync window).

Apply correction wherever a client compares its local clock against
`positionTs`:

```
elapsedMs = (Date.now() + serverTimeOffsetMs) - state.positionTs
```

---

## Devices

```typescript
interface ConnectDevice {
  deviceId: string;
  userId: string;
  type: DeviceType;             // "ios" | "android" | "web" (extensible)
  name: string;
  lastHeartbeat: number;        // Server timestamp of last heartbeat
}
```

`DeviceType` is a string union that can be extended (e.g. `"tvos"`, `"carplay"`)
without server changes. The server treats all device types identically — the
type is purely metadata for display ("Playing on Damian's iPhone").

### Heartbeat & Lease

- Devices send a `heartbeat` message every **15 seconds**.
- Server updates `lastHeartbeat` on receipt.
- Server runs a sweep every **10 seconds**. Any device with
  `lastHeartbeat < now - 45s` is evicted.
- If the evicted device was the active device: server parks the session
  (`activeDeviceId = null`, `playing = false`), persists to DB, version++,
  broadcasts.
- The `register` message counts as the first heartbeat.

### Device List

The server maintains a device list per user and broadcasts it as a `devices`
message whenever it changes (register, unregister, heartbeat eviction).

---

## Protocol

WebSocket at `/ws/connect`. Authenticated via JWT bearer token in the
`Authorization` header (or token query param as fallback for browsers). Uses
the same `requireAuth` middleware as v1.

### Client -> Server (4 message types)

Every command includes the sender's device ID implicitly (the server tracks
which socket belongs to which device after `register`).

#### `register`

Sent once on connect. The server responds with the current `state` and `devices`.

```json
{
  "type": "register",
  "deviceId": "uuid",
  "deviceType": "ios",
  "deviceName": "Damian's iPhone"
}
```

#### `command`

All state-change requests. The server validates, mutates state, and broadcasts.

```json
{ "type": "command", "action": "play" }
{ "type": "command", "action": "pause" }
{ "type": "command", "action": "stop" }
{ "type": "command", "action": "seek", "positionMs": 45000 }
{ "type": "command", "action": "next" }
{ "type": "command", "action": "prev" }
{ "type": "command", "action": "transfer", "targetDeviceId": "uuid" }
{ "type": "command", "action": "position", "positionMs": 47500 }
{
  "type": "command",
  "action": "load",
  "showId": "gd1977-05-08",
  "recordingId": "sbd-1234",
  "tracks": [{ "title": "Scarlet Begonias", "durationMs": 312000 }, ...],
  "trackIndex": 0,
  "positionMs": 0,
  "autoplay": true,
  "date": "1977-05-08",
  "venue": "Barton Hall, Cornell University",
  "location": "Ithaca, NY"
}
```

#### `heartbeat`

Keepalive. No payload needed.

```json
{ "type": "heartbeat" }
```

#### `time_sync`

Clock-offset probe. Server echoes `clientTs` and stamps `serverTs`. Stateless;
does not mutate or broadcast. See **Clock Sync** for the sampling algorithm.

```json
{ "type": "time_sync", "clientTs": 1718990123456 }
```

### Server -> Client (4 message types)

#### `state`

Full `ConnectState` snapshot. Sent:
- On register (current state)
- On every state mutation (broadcast to all user's devices)
- In error responses (so client can reconcile)

```json
{
  "type": "state",
  "state": { ... }
}
```

#### `devices`

Current device list. Sent on register, device join/leave, heartbeat eviction.

```json
{
  "type": "devices",
  "devices": [
    { "deviceId": "uuid", "deviceType": "ios", "deviceName": "Damian's iPhone" },
    { "deviceId": "uuid", "deviceType": "web", "deviceName": "Chrome - macbook" }
  ]
}
```

#### `error`

Command rejected. Always includes current state for reconciliation.

```json
{
  "type": "error",
  "message": "Target device not found",
  "state": { ... }
}
```

#### `time_sync`

Reply to a client `time_sync` probe. Sent only to the requesting socket.

```json
{
  "type": "time_sync",
  "clientTs": 1718990123456,
  "serverTs": 1718990123478
}
```

---

## Server Command Processing

All commands go through a single function. The server knows which device sent
each command (tracked at `register` time). Pseudocode:

```typescript
function handleCommand(userId: string, deviceId: string, cmd: Command): void {
  const state = getOrCreateState(userId);
  const device = getDevice(userId, deviceId);
  if (!device) return sendError(deviceId, "Not registered");

  switch (cmd.action) {
    case "play": {
      if (!state.showId) return sendError(deviceId, "Nothing loaded");
      // If parked (no active device), the sender fills the vacancy
      if (!state.activeDeviceId) {
        mutate(state, {
          activeDeviceId: deviceId,
          activeDeviceName: device.name,
          activeDeviceType: device.type,
          playing: true,
          positionTs: Date.now(),
        });
        break;
      }
      if (state.playing) return; // Already playing, no-op
      // Remote control: resume playback on whoever the active device is
      mutate(state, {
        playing: true,
        positionTs: Date.now(),  // positionMs stays the same, clock restarts
      });
      break;
    }

    case "pause": {
      if (!state.activeDeviceId) return; // Nothing to pause
      if (!state.playing) return; // Already paused, no-op
      // Remote control: pause playback on whoever the active device is
      // Snapshot position before pausing
      const elapsed = Date.now() - state.positionTs;
      mutate(state, {
        playing: false,
        positionMs: state.positionMs + elapsed,
        positionTs: Date.now(),
      });
      persist(userId, state);  // Meaningful transition
      break;
    }

    case "seek": {
      mutate(state, {
        positionMs: cmd.positionMs,
        positionTs: Date.now(),
      });
      break;
    }

    case "next": {
      const newIndex = Math.min(state.trackIndex + 1, state.tracks.length - 1);
      if (newIndex === state.trackIndex) return; // Already at end
      const track = state.tracks[newIndex];
      mutate(state, {
        trackIndex: newIndex,
        positionMs: 0,
        positionTs: Date.now(),
        durationMs: track.durationMs,
      });
      break;
    }

    case "prev": {
      const newIndex = Math.max(state.trackIndex - 1, 0);
      if (newIndex === state.trackIndex) return; // Already at start
      const track = state.tracks[newIndex];
      mutate(state, {
        trackIndex: newIndex,
        positionMs: 0,
        positionTs: Date.now(),
        durationMs: track.durationMs,
      });
      break;
    }

    case "stop": {
      const elapsed = state.playing ? Date.now() - state.positionTs : 0;
      mutate(state, {
        activeDeviceId: null,
        activeDeviceName: null,
        activeDeviceType: null,
        playing: false,
        positionMs: state.positionMs + elapsed,
        positionTs: Date.now(),
      });
      persist(userId, state);  // Meaningful transition
      break;
    }

    case "transfer": {
      const target = getDevice(userId, cmd.targetDeviceId);
      if (!target) return sendError(deviceId, "Target device not found");
      // Two-phase transfer — see "Transfer Protocol" section below
      beginTransfer(userId, state, target);
      break;
    }

    case "load": {
      mutate(state, {
        showId: cmd.showId,
        recordingId: cmd.recordingId,
        tracks: cmd.tracks,
        trackIndex: cmd.trackIndex ?? 0,
        positionMs: cmd.positionMs ?? 0,
        positionTs: Date.now(),
        durationMs: cmd.tracks[cmd.trackIndex ?? 0]?.durationMs ?? 0,
        date: cmd.date ?? null,
        venue: cmd.venue ?? null,
        location: cmd.location ?? null,
        // If autoplay, play on the CURRENT active device (not the sender).
        // If no active device (parked), the sender fills the vacancy.
        ...(cmd.autoplay ? {
          activeDeviceId: state.activeDeviceId ?? deviceId,
          activeDeviceName: state.activeDeviceName ?? device.name,
          activeDeviceType: state.activeDeviceType ?? device.type,
          playing: true,
        } : {}),
      });
      persist(userId, state);  // Meaningful transition
      break;
    }

    case "position": {
      // Only accept from the active device
      if (state.activeDeviceId !== deviceId) return;
      mutate(state, {
        positionMs: cmd.positionMs,
        positionTs: Date.now(),
      });
      // Don't persist — just in-memory drift correction
      break;
    }
  }
}

function mutate(state: ConnectState, patch: Partial<ConnectState>): void {
  Object.assign(state, patch);
  // Invariant: cannot be playing without an active device
  if (state.playing && !state.activeDeviceId) {
    state.playing = false;
  }
  state.version++;
  broadcast(state);  // Send full snapshot to all user's devices
}
```

---

## Transfer Protocol

Transfer is the most complex operation because it must guarantee **no audio
overlap** — the old device must stop before the new device starts. This is a
hard requirement.

### Two-Phase Transfer with Acknowledgment

When a transfer is requested (e.g. B sends `{ action: "transfer", targetDeviceId: "B" }`):

**Phase 1 — Park (stop the old device):**
1. Server snapshots position: `positionMs + elapsed`
2. Server sets `activeDeviceId = null`, `playing = false`, version++, broadcasts.
3. Device A receives the broadcast, sees it's no longer active, stops audio.
4. Device A sends `{ action: "position", positionMs: <actual stop position> }` — its
   true final position at the moment audio stopped.

**Phase 2 — Activate (start the new device):**
5. Server receives A's final position report, updates `positionMs`.
6. Server sets `activeDeviceId = B`, `playing = true`, version++, broadcasts.
7. Device B receives the broadcast, sees it's now active, starts playback at the
   position A reported.

**Timeout:** If A doesn't report its position within **1 second**, the server
proceeds with phase 2 using the interpolated position from phase 1. The transfer
must not hang waiting for a dead device.

**Self-transfer:** If B transfers to itself and B is already the active device,
this is a no-op. If B is not active, the same two-phase protocol applies.

**No active device (parked):** If the session is parked, phase 1 is skipped —
the server goes directly to phase 2.

### Why Not Single-Phase?

In a single-phase transfer (just set `activeDeviceId = B` and broadcast), both
A and B receive the state simultaneously. B starts playing while A is still
stopping — brief audio overlap. Two-phase guarantees: A stops → gap → B starts.

---

### Key Behaviors

**`play`/`pause` are remote control, not ownership claims.** Any device can
send `play` or `pause` and it affects the current active device. The
`activeDeviceId` does not change. If device A is playing and device B sends
`pause`, A pauses. B sends `play`, A resumes. B is just a remote.

**`play` when parked fills the vacancy.** If no device is active and a device
sends `play`, the sending device becomes the active device. This is the only
case where `play` changes `activeDeviceId`.

**`load` with `autoplay` plays on the current active device.** It does NOT
transfer ownership to the sender. If device A is active and device B sends
`load` with `autoplay: true`, the server loads the new show and A starts
playing it. If no device is active (parked), the sender fills the vacancy.

**Transfer is always explicit.** The only ways to change which device is active:
1. `transfer` targeting another device
2. `transfer` targeting yourself (self-claim)
3. Filling a vacancy when parked (via `play` or `load` with `autoplay`)

**Devices react to state, not to commands.** When a device receives a state
broadcast where `activeDeviceId` is no longer its own ID, it stops playback.
When it receives a state where `activeDeviceId` IS its own ID and `playing` is
true, it starts playback. The state is the single source of truth.

**Position snapshotting:** Before any transition that changes playback state
(pause, stop, transfer, load), the server computes the *actual* current position
from `positionMs + elapsed` and writes it into the new state. This prevents
position jumps.

**Error recovery:** Every error response includes the current state. The client
can always reconcile by snapping to the included state.

---

## Client Behavior

### Optimistic UI

Two distinct behaviors depending on whether the local device is actively playing:

**Active device (playing audio locally):** Act optimistically. When the user
hits play/skip/pause, update the local player immediately. The server broadcast
arrives ~50ms later — reconcile (usually a no-op since the prediction was
correct).

**Remote control (not playing audio):** Show a spinner/loading indicator after
sending a command. Do NOT animate the progress bar or show play state — that
creates a confusing "looks like it's playing but no sound" experience. Wait for
the server broadcast to confirm the remote device acted, then update the UI.

### On Connect
1. Open WebSocket to `/ws/connect`
2. Send `register` with device info
3. Receive `state` and `devices` messages
4. If `state.activeDeviceId === myDeviceId` and `state.playing` — resume
   playback at interpolated position
5. Start heartbeat interval (15s)

### On State Received
```
if incoming.version <= myVersion:
  ignore (stale)

myVersion = incoming.version
myState = incoming

if incoming.activeDeviceId === myDeviceId:
  // I am the active device
  if incoming.playing && !locallyPlaying:
    startPlayback(incoming)     // Transfer landed on me, or resume
  if !incoming.playing && locallyPlaying:
    pausePlayback()
  // Do NOT seek to correct server position drift — the local audio engine
  // is the authority. Position reports FROM this device keep the server in
  // sync, not the other way around. Only seek on explicit commands.
else:
  // I am NOT the active device
  if locallyPlaying:
    stopPlayback()              // I lost active status
    reportFinalPosition()       // Send position command with actual stop position
  updateRemoteControlUI(incoming)  // Snap to server position (visual only)
```

### On User Action
1. If active device: update local UI/audio optimistically
2. If remote control: show loading indicator
3. Send `command` to server
4. Wait for `state` broadcast — snap to it

### On Disconnect / Background
- Stop heartbeat
- On reconnect: re-register, receive fresh state, reconcile

---

## Persistence

### When to persist to SQLite
- `load` — new show loaded
- `pause` — user intentionally paused
- `stop` — session parked
- Device eviction (heartbeat timeout) — park + persist
- WebSocket close of active device — park + persist

### What to persist
```sql
-- Same table as v1, just the fields needed to hydrate a parked state
user_id, show_id, recording_id, track_index, position_ms,
date, venue, location, updated_at
```

### What NOT to persist
- Every 5s position tick (too chatty, marginal value)
- Version number (resets to 0 on server restart, which is fine — clients
  reconnect and get fresh state)

---

## What This Eliminates

| v1 Problem | v2 Solution |
|------------|-------------|
| Clients push state | Clients send commands only |
| Dual state systems | Single `ConnectState` |
| No version counter | Monotonic `version` on every mutation |
| 5 mutation paths | Single `mutateState()` function |
| Device-to-device relay races | No relay — just state broadcasts |
| Ghost devices | Heartbeat with 45s lease |
| Position update bypass | Position goes through `mutateState()` like everything else |
| `session_claim` complexity | Explicit `transfer` to self; `play`/`pause` are pure remote control |
| `Object.assign` frankenstate | Single mutation function with position snapshotting |
| Audio overlap on transfer | Two-phase transfer with acknowledgment |

---

## Open Questions

- **Track duration unit**: v1 uses seconds in `SessionTrack.duration` but ms
  everywhere else. v2 should standardize on ms (`durationMs`) everywhere.
- **Remote volume control**: Future feature. Per-device volume reported via
  `devices` message, `{ action: "volume", level: 0.0-1.0 }` command to change
  the active device's volume. Hardware volume button interception on mobile
  when in remote-control mode. Not MVP — defer to a later iteration.
- **Queue / shuffle / repeat**: Not in scope for v2. Sequential track playback
  only. Can be added as state fields later.
- **Remote commands from non-active device**: All commands (`play`, `pause`,
  `seek`, `next`, `prev`) are remote control — any connected device can send
  them and they affect the active device. Only `transfer` and `load` change
  which device is active. Only `position` is restricted to the active device
  (since only it knows the real playback position).
