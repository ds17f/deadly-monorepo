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
| `1` | Chunk A (ADR-0011) | Client sends `protocolVersion` + `appVersion` on `register`. Purely additive — no message shape changed, no server behavior branches on it yet. Establishes the primitive + telemetry that later chunks gate on. |

### Reserved / planned

- **Ownership lease (ADR-0011 Chunk B)** will be gated on `protocolVersion >= 1`:
  the audio-producing device renews ownership by piggybacking `{ playing,
  recordingId, positionMs }` on the heartbeat. When that lands, note here whether it
  rides on `1` or bumps to `2`.

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
