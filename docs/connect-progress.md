# Spotify Connect–Style Implementation Progress

Last updated: 2026-04-03

## Architecture Reference

See the original architecture proposal for the full design: [connect-architecture.md](./connect-architecture.md) (if committed) or the conversation history where it was first shared.

---

## Progress Checklist

### Session Management

- [x] Single canonical session object — `UserPlaybackState` per user with `activeDeviceId`, persisted to DB
- [x] Active device tracked explicitly — `activeDeviceId` in `UserPlaybackState`, enforced server-side

### Real-Time Layer

- [x] Updates pushed (not polled) — WebSocket + Redis pub/sub for multi-instance support
- [x] Latency accounted for — Timestamp-based interpolation on web (`positionMs + (now - updatedAt)`)

### Playback Sync

- [x] Timestamps used with position — `updatedAt` sent with every state update
- [x] Drift handled — Web interpolates smoothly; >2s divergence triggers correction

### Device Roles

- [x] Any device can act as controller — All devices can send commands
- [x] Player role clearly defined — Via `activeDeviceId` (single active player enforced)

### Playback Transfer

- [x] Clean handoff mechanism — `session_play_on` → server stops old device via `session_stop` → relays state to target
- [x] Old device stops reliably — `session_stop` implemented on all three platforms

### Reliability

- [x] Reconnects handled — All platforms auto-reconnect with exponential backoff; fetch latest state on reconnect
- [x] Duplicate playback prevented — `session_claim` + `session_stop` enforce mutual exclusion

---

## Message Types (11 total)

| Message | Direction | Purpose |
|---------|-----------|---------|
| `register` | Client → Server | Device joins session |
| `devices` | Server → Client | Broadcast connected devices list |
| `command` | Client → Server | Play, pause, stop, next, prev, seek |
| `position_update` | Client → Server → Clients | Periodic position sync (~15s) |
| `session_update` | Client → Server → Clients | Full state on play/pause/stop/track change |
| `session_claim` | Client → Server | Device claims active playback ownership |
| `session_play_on` | Client → Server → Target | Transfer playback to another device |
| `session_stop` | Server → Client | Tell device to stop (mutual exclusion) |
| `user_state` | Server → Client | Send canonical user playback state |
| `state_clear` | Client → Server | Clear all playback state |
| `error` | Server → Client | Error notification |

---

## Queue / Track List Status

Track lists **are** passed through messages. `SessionTrack[]` (title + duration) is included in:

- `session_update` — active device broadcasts its track list
- `session_play_on` — tracks travel with the transfer
- Server uses the track list for next/prev command resolution

This is a **per-recording track list**, scoped to the current show. It does **not** implement the richer queue model:

```json
{
  "currentTrack": "track_1",
  "queue": ["track_2", "track_3"],
  "history": ["track_0"],
  "shuffle": false,
  "repeat": "off"
}
```

### What's missing for full queue support

- Cross-show queuing (e.g., "queue up another show after this one")
- Shuffle state synced across devices
- Repeat mode synced across devices
- Explicit history tracking via Connect (handled separately in DB)

For a Grateful Dead show app where tracks are sequential within a recording, the per-show track list approach is sufficient unless cross-show playlists are added.

---

## Remaining Gaps

| Gap | Severity | Notes |
|-----|----------|-------|
| Rich queue sync (shuffle, repeat, history) | Low | Only matters if cross-show queuing is added |
| Capability enforcement | Low | All devices declare both "playback" + "control"; never differentiated |
| Clock drift compensation | Low | No server-time sync; relies on device clocks being close enough |

---

## Key Files

### Server (API)

- `api/src/connect/types.ts` — Type definitions
- `api/src/connect/routes.ts` — WebSocket handler & message routing
- `api/src/connect/registry.ts` — In-memory state, broadcasting, Redis relay

### Web Client

- `ui/src/lib/connectWs.ts` — Browser WebSocket wrapper
- `ui/src/components/connect/ConnectProvider.tsx` — React context provider
- `ui/src/components/player/PlayerProvider.tsx` — Audio engine + Connect integration
- `ui/src/hooks/useInterpolatedPosition.ts` — Timestamp-based position interpolation

### iOS Client

- `iosApp/deadly/Core/Connect/ConnectWebSocket.swift` — WebSocket transport
- `iosApp/deadly/Core/Connect/ConnectService.swift` — Connection lifecycle & message handling
- `iosApp/deadly/Core/Connect/ConnectModels.swift` — Codable models

### Android Client

- `androidApp/core/api/connect/src/main/java/com/grateful/deadly/core/api/connect/ConnectService.kt` — Interface
- `androidApp/core/api/connect/src/main/java/com/grateful/deadly/core/api/connect/ConnectModels.kt` — Data models
- `androidApp/core/connect/src/main/java/com/grateful/deadly/core/connect/ConnectServiceImpl.kt` — OkHttp WebSocket implementation
- `androidApp/core/connect/src/main/java/com/grateful/deadly/core/connect/ConnectPlaybackHandler.kt` — Playback logic & state diff detection
