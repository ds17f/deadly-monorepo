# Connect WS protocol — version semantics

Source of truth for the `protocolVersion` integer carried on the Connect
WebSocket. Spec rationale: [ADR-0011](adr/0011-connect-session-ownership-and-protocol-versioning.md).
Build plan: [`PLANS/connect-ownership-lease.md`](../PLANS/connect-ownership-lease.md).

If the three clients ever disagree about what a version number *means*, this file
is what they were supposed to agree on. Update it in the **same change** that
bumps a client's `protocolVersion`.

## What the two fields are

Both ride on the `register` message (client → server), and only there:

| Field | Type | Server uses it for | Branch on it? |
|---|---|---|---|
| `protocolVersion` | int | the **wire contract** — what message shapes/semantics this connection speaks | **Yes** — this is the *only* field behavior may branch on |
| `appVersion` | string | telemetry, "please update" UX, debugging | **Never** |

`appVersion` is marketing/build identity (iOS `CFBundleShortVersionString`,
Android `BuildConfig.VERSION_NAME`, web `NEXT_PUBLIC_DATA_VERSION`). Two releases
can ship the same `protocolVersion`; coupling capability to build identity forces
"≥ which build has feature X" lookup tables, so we don't.

## Lifetime

`protocolVersion`/`appVersion` are **per-connection**, not per-session. They arrive
on `register`, are stamped onto the in-memory `liveDevices` record next to the
socket, are valid until that socket closes, and are re-sent on every reconnect
(self-refreshing, never stale). They are **not** persisted to Redis — a persisted
copy could outlive the socket and misreport a reconnecting client's capability
before it re-registers. (Session state — `showId`, position, ownership — has the
separate per-session lifetime; see ADR-0011 §3.)

## Defaults and the soft floor

- **Absent ⇒ `0` (legacy).** A client that omits `protocolVersion` predates this
  primitive. The server reads `0` and keeps today's behavior for it.
- **`MIN_SUPPORTED = 0`** today — nothing is gated yet. When a correctness/security
  must-have needs it, raise `MIN_SUPPORTED` on **telemetry** (the per-session
  protocol distribution the server logs on register), not hope. Below the floor the
  server degrades Connect gracefully and may send `{ type: "error",
  code: "client_too_old" }` for clients to render an "Update for Connect" nudge —
  a *soft* floor, not a hard reject (app-store review lag can strand a fix).

## Version history

| `protocolVersion` | Shipped | Meaning |
|---|---|---|
| `0` | (implicit) | Legacy: any client that omits the field. No `protocolVersion`/`appVersion` on register. |
| `1` | Chunk A + B (ADR-0011) | **A:** client sends `protocolVersion` + `appVersion` on `register`. **B:** the audio-producing device piggybacks `{ playing, recordingId, positionMs }` on its `heartbeat` to renew the ownership lease. Server reads the lease only when `protocolVersion >= 1`; a renewal **claims an ownerless session** (`activeDeviceId == null`, requires `playing`) but never preempts an existing owner. Also adds `heartbeat.trackIndex` so the lease heals to the device's real track (all clients send it as of proto 3). All additive — no register/heartbeat shape that an old client sends changed. |
| `2` | Kill-switch close code (PR #86/#87, ADR-0018) | Client **understands the `4005` "Connect disabled" close code** and treats it as terminal (no reconnect). The server sends `4005` to proto ≥ 2 and `4003` to proto < 2 for the same condition (`connect/protocol.ts`). No new client→server message shape; purely a close-code capability. Android also gained the `onClosing` override so a server-initiated close reaches its terminal path without churn. |
| `3` | Handshake + track-intent nonce (ADR-0016 §3, ADR-0019) | **Handshake:** a device holding live audio sends an optional `handshake { playing, showId?, recordingId, tracks?, trackIndex, positionMs, durationMs?, date?, venue?, location? }` on `register`. The server adopts it as authoritative **before its first broadcast** (only when playing, and only if the session is ownerless or already this device's — never preempts a different owner), so the first post-reconnect `state` reflects the live device, not stale memory. Closes the reconnect stale-snapshot clobber. **`trackNonce`:** the server bumps a monotonic `state.trackNonce` on every explicit `next`/`prev` (never on `load` or a position report); a proto-3 active device follows a remote track change **iff `trackNonce` advanced and it didn't issue it**, generalizing ADR-0017's `seekNonce` to track changes and retiring the fragile trackIndex-coincidence guard. All additive: old clients omit the handshake and ignore `trackNonce`. |

### Chunk C (landed) — client recovery is now lease-driven

No wire/version change. All three clients deleted the **epoch-change reclaim**
heuristic (`lastEpoch`/`serverRestarted`) and made the reconcile loop honor one
invariant: **the device producing audio is the transport authority.** When the
session is ownerless (`activeDeviceId == null`) and we're still playing the
session's recording, we keep playing and let the heartbeat **lease** reclaim us
server-side instead of pausing or self-claiming via `load`. The empty-tracks
re-send is **kept but decoupled** — it now hydrates the tracklist with
`autoplay=false`, so it restores tracks without claiming ownership (the lease owns
that). This is what makes the lease the load-bearing, observable recovery path.

## When you bump the version

1. Decide it's a real wire-contract change (new required field, changed semantics,
   a new server branch) — not just a new build. If it's only a build, that's
   `appVersion`, leave `protocolVersion` alone.
2. Add a row to **Version history** above describing the contract delta.
3. Bump the constant in **all three clients in the same change**:
   - iOS: `ConnectService.protocolVersion` (`iosApp/.../Core/Service/ConnectService.swift`)
   - Android: `ConnectServiceImpl.CONNECT_PROTOCOL_VERSION` (`androidApp/core/connect/.../ConnectServiceImpl.kt`)
   - Web: `CONNECT_PROTOCOL_VERSION` (`ui/src/components/connect/ConnectProvider.tsx`)
   - Server reference: `CURRENT_PROTOCOL_VERSION` (`api/src/connect/types.ts`)
4. Keep server branches `if (proto >= N)` until telemetry shows the fleet has moved,
   then delete them — a version number is debt with a payoff (ADR-0011 Consequences).
