# Connect — session ownership lease + WS protocol versioning (build plan)

Design spec: [`docs/adr/0011-connect-session-ownership-and-protocol-versioning.md`](../docs/adr/0011-connect-session-ownership-and-protocol-versioning.md).
Roadmap: §2 in [`ROADMAP.md`](ROADMAP.md). Extends ADR-0006/0007/0008; the failure
that motivated it surfaced in ADR-0010 §7 (cross-device end-of-show countdown).

## The shape (one paragraph)

Connect's `activeDeviceId`/`playing` is in-memory server state that clients today
re-establish only **reactively**, via a growing pile of cause-specific detectors
(epoch-change reclaim, empty-tracks re-assert). They miss cases (a fresh reconnect
*after* a restart never witnesses the epoch change; `handleDisconnect` nulls active
with no restart at all). Replace them with **one convergent mechanism**: the device
producing audio renews an **ownership lease** by piggybacking `{playing,
recordingId, positionMs}` on the existing heartbeat; the server treats the active
device as a lease the live player renews. Explicit commands stay the fast,
immediately-consistent control path — the lease is the *healing* layer. Gate it on
a new **`protocolVersion`** primitive so a mixed old/new fleet stays safe.

## Ship order — protocol versioning first; it has standalone value

### Chunk A — `protocolVersion` + `appVersion` on `register` (ship ASAP, standalone)
The enabling primitive; useful before any lease work.
- Clients send `protocolVersion` (monotonic int, new builds = `1`) and `appVersion`
  (string) in the `register` message. Absent ⇒ `0` (legacy).
- Server stamps both onto the **in-memory `liveDevices` connection record** (next to
  the socket — NOT Redis; same lifetime as the connection). Every command/heartbeat
  handler can read it.
- **Telemetry:** log the protocol/app distribution per session so we can later see
  when it's safe to retire legacy branches and advance `MIN_SUPPORTED`.
- **Soft floor:** optional `MIN_SUPPORTED` → `{type:"error", code:"client_too_old"}`
  the client renders as an "Update for Connect" nudge. Soft, not a hard reject
  (app-store review lag). Wire the client surface but keep `MIN_SUPPORTED` at `0`
  until there's a reason to raise it.
- Single source of truth for version semantics: add `docs/PROTOCOL.md`.
- Per-platform: Android `sendRegister`, iOS register payload, web WS register.
- Proof: server logs the stamped version per device; telemetry row lands.

### Chunk B — ownership lease (heartbeat-renewed), gated on `protocolVersion >= 1`
- Heartbeat (client→server) carries `{playing, recordingId, positionMs}` for the
  device producing audio. Additive fields; old clients omit them.
- Server: a renewal **claims an ownerless session** (`activeDeviceId == null`) or
  **refreshes the existing owner**. It **never preempts** a command-claimed owner
  (the §2 conservative rule — mixed-fleet safe, no split-brain flap).
- Ownership ≠ "playing now": a paused owner keeps the lease (renew while connected).
- Conflict (two devices assert an ownerless session): tiebreak by most-recent
  explicit user action, then lease timestamp. Rare — only one device makes sound
  normally.
- Verify the original bug is dead: play through an API restart → next heartbeat
  reconverges `activeDeviceId` → end-of-show advance fires. Also the
  disconnect-reconnect variant (kill the socket mid-play, reconnect).

### Chunk C — delete the reclaim heuristics
Once B is proven on all three platforms and telemetry shows the fleet on
`protocolVersion >= 1`:
- Remove epoch-change reclaim (`serverRestarted`/`lastEpoch`), empty-tracks
  re-assert (`reassertingTracks`), and the `playWhenReady` reconciler's
  `if (!isActive) return` gate — the lease subsumes them.
- Collapse the **transport-authority guards** into one lease invariant. There are
  now two "don't sync transport DOWN to stale server state because this device is
  the real source" exceptions in `reactToState`: `if (reclaimAfterRestart) return`
  and the `justBecameActive && pendingAdvance == null` become-active guard (added
  while fixing the announce-park seek-back glitch, 2026-06-11). Both are the same
  rule — *the device producing audio is the transport authority* — which the lease
  makes first-class, so both guards go away.
- Keep the deletion gated on telemetry; each removed branch is debt paid down.

## Already shipped (pre-plan)
- **Server `handleAnnounceNext` claim-when-null** (`52942d45`, `fix(all/connect)`):
  the announcing device claims active when `activeDeviceId == null`. Rescues
  end-of-show auto-advance even before the lease lands; a consistent instance of
  the §2 "claim when null" rule.

## Explicitly NOT this plan
- **Redis-persist of session state** — stays deferred (ADR-0008, gated on social);
  it wouldn't cover the disconnect variant anyway. The lease is the fix now.
- **Silent push to wake a sleeping device** — deferred (ADR-0008), orthogonal.
- **Multi-instance API** — per-connection facts live in one instance's memory;
  horizontal scaling would need sticky routing / a shared presence registry. Out of
  scope; noted in ADR-0011 Consequences.

## Status
2026-06-11: **ADR-0011 accepted (design); server claim-when-null shipped
(`52942d45`).**
- **Chunk A — DONE** (`4564d2d4`, `feat(all/connect)`): `protocolVersion`(=1) +
  `appVersion` on register, stamped onto the in-memory `liveDevices` record, with
  per-session protocol-distribution telemetry. `docs/PROTOCOL.md` added.
- **Chunk B — code-complete, built on all 3 platforms + server, NOT yet
  device-verified.** Heartbeat carries `{playing, recordingId, positionMs}` when
  audio is loaded locally; `handleHeartbeat` claims an ownerless session
  (`activeDeviceId == null`, requires `playing`, recordingId must match the
  session) and never preempts an existing owner. Gated on `protocolVersion >= 1`.
  **Outstanding: run the exit-criterion test** — play through an API restart →
  next heartbeat reconverges `activeDeviceId` → end-of-show advance fires; plus the
  disconnect-reconnect variant.
- **Chunk C — not started.** Gated on Chunk B device-verification + telemetry
  showing the fleet on `protocolVersion >= 1`.
