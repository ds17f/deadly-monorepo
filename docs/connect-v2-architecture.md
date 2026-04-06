# Connect v2 Architecture

> Redesign of the Deadly Connect system — a Spotify Connect-like feature that
> synchronizes playback state across multiple devices (iOS, Android, web) through
> an authoritative server.

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
   arrives.

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
| `true`    | `null`            | Invalid — server must never produce this |

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

Clients compute the current playback position without real-time streaming:

```
currentPosition = state.playing
  ? state.positionMs + (Date.now() - state.positionTs)
  : state.positionMs
```

The active device sends periodic `position` commands (~5s) to correct drift.
These go through the normal mutation path — server updates `positionMs` and
`positionTs`, increments version, broadcasts.

---

## Devices

```typescript
interface ConnectDevice {
  deviceId: string;
  userId: string;
  type: DeviceType;             // "ios" | "android" | "web"
  name: string;
  lastHeartbeat: number;        // Server timestamp of last heartbeat
}
```

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

WebSocket at `/ws/connect`. Authenticated via the same mechanism as v1.

### Client -> Server (3 message types)

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

### Server -> Client (3 message types)

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

---

## Server Command Processing

All commands go through a single function. Pseudocode:

```typescript
function handleCommand(userId: string, deviceId: string, cmd: Command): void {
  const state = getOrCreateState(userId);
  const device = getDevice(userId, deviceId);
  if (!device) return sendError(deviceId, "Not registered");

  switch (cmd.action) {
    case "play": {
      if (!state.showId) return sendError(deviceId, "Nothing loaded");
      if (!state.activeDeviceId) return sendError(deviceId, "No active device — use transfer or load");
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
      // Snapshot position at transfer time
      const elapsed = state.playing ? Date.now() - state.positionTs : 0;
      mutate(state, {
        activeDeviceId: target.device.deviceId,
        activeDeviceName: target.device.name,
        activeDeviceType: target.device.type,
        playing: true,
        positionMs: state.positionMs + elapsed,
        positionTs: Date.now(),
      });
      break;
    }

    case "load": {
      // Snapshot position of current track before loading new show
      const elapsed = state.playing ? Date.now() - state.positionTs : 0;
      const wasPlaying = state.playing;
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
        // If autoplay, claim the device and start playing
        ...(cmd.autoplay ? {
          activeDeviceId: deviceId,
          activeDeviceName: device.name,
          activeDeviceType: device.type,
          playing: true,
        } : {
          // Keep current device/playing state unless autoplay
        }),
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
  state.version++;
  broadcast(state);  // Send full snapshot to all user's devices
}
```

### Key Behaviors

**`play`/`pause` are remote control, not ownership claims.** Any device can
send `play` or `pause` and it affects the current active device. The
`activeDeviceId` does not change. If device A is playing and device B sends
`pause`, A pauses. B sends `play`, A resumes. B is just a remote.

**Transfer is always explicit.** To move playback to a different device, you
must send `transfer` (targeting another device or yourself) or `load` with
`autoplay: true`. There is no implicit transfer — pressing play on a non-active
device does NOT steal the session.

**Self-transfer is how you "claim" the session.** If device B wants to take
over playback from device A, B sends `{ action: "transfer", targetDeviceId: "B" }`.
The server switches `activeDeviceId` to B and broadcasts. A sees the state
change and stops itself. This is explicit and unambiguous.

**Devices react to state, not to commands.** There is no `session_stop` message.
When a device receives a state broadcast where `activeDeviceId` is no longer
its own ID, it stops playback. When it receives a state where `activeDeviceId`
IS its own ID and `playing` is true, it starts playback. The state is the
single source of truth.

**Position snapshotting:** Before any transition that changes playback state
(pause, stop, transfer, load), the server computes the *actual* current position
from `positionMs + elapsed` and writes it into the new state. This prevents
position jumps.

**Error recovery:** Every error response includes the current state. The client
can always reconcile by snapping to the included state.

---

## Client Behavior

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
  // Correct position if diverged > threshold
  if abs(localPosition - interpolatedServerPosition) > 2000ms:
    seekTo(interpolatedServerPosition)
else:
  // I am NOT the active device
  if locallyPlaying:
    stopPlayback()              // I lost active status
  updateRemoteControlUI(incoming)
```

### On User Action
1. Optionally update local UI optimistically
2. Send `command` to server
3. Wait for `state` broadcast — snap to it regardless

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

---

## Migration Path

v2 is a clean break. The server endpoint will be `/ws/connect/v2`. Clients can
be migrated one at a time:
1. Ship server with both `/ws/connect` (v1) and `/ws/connect/v2` endpoints
2. Migrate web first (fastest iteration)
3. Migrate iOS and Android
4. Remove v1 endpoint

v1 and v2 sessions are independent — a v1 client and v2 client for the same
user will not see each other. This is acceptable during migration since the
user controls which app version they're running.

---

## Open Questions

- **Track duration unit**: v1 uses seconds in `SessionTrack.duration` but ms
  everywhere else. v2 should standardize on ms (`durationMs`) everywhere.
- **Volume**: Not in scope for v2. Per-device volume is handled locally.
- **Queue / shuffle / repeat**: Not in scope for v2. Sequential track playback
  only. Can be added as state fields later.
- **Remote commands from non-active device**: All commands (`play`, `pause`,
  `seek`, `next`, `prev`) are remote control — any connected device can send
  them and they affect the active device. Only `transfer` and `load` change
  which device is active. Only `position` is restricted to the active device
  (since only it knows the real playback position).
