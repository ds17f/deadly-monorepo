# ADR-0006: Connect-v2 — server-authoritative cross-device playback

## Status

Accepted (2026-06-06). Supersedes the v1 Connect system entirely.

The detailed protocol/state spec is the living document
[`docs/connect-v2-architecture.md`](../connect-v2-architecture.md); this ADR
records the binding decisions and trade-offs. Execution history is in
[`PLANS/connect-v2-port.md`](../../PLANS/connect-v2-port.md) and
[`PLANS/connect-v2-android-debugging.md`](../../PLANS/connect-v2-android-debugging.md).

## Context

Deadly needs Spotify-Connect-style control: see what's playing on your other
devices, transfer playback between them, and keep transport/position in sync
across iOS, Android, and web. It is also the backbone for playback-position sync
(deliberately cut from the REST sync layer) and the future social "hear what a
friend is playing" presence feature.

Connect v1 failed structurally: clients pushed state and the server relayed it,
so two simultaneous updates produced "frankenstate" via `Object.assign`; it ran
two divergent state systems; it had no version counter (only timestamps, which
collide or go backwards); five message types mutated state through five code
paths; a device-to-device relay raced with the primary mutation; and there was
no heartbeat, so ghost devices lingered. These are ordering/ownership bugs, not
tuning problems — they require a different shape, not a patch.

The clients also changed out from under the original v2 branch: PR #48 rewrote
the web/iOS/Android player surfaces, so v2 had to be re-integrated layer by
layer against current `main` rather than rebased.

## Decision

Adopt the Connect-v2 architecture:

1. **Server is the single source of truth.** Clients send *commands* (intent);
   the server validates, mutates, and broadcasts. Clients never push state.
2. **Full-snapshot broadcasts, one mutation path.** Every change runs through a
   single `mutate()`; the whole ~200-byte `ConnectState` is broadcast on every
   change. No deltas, no reconciliation, no relay paths.
3. **Monotonic, restart-safe version.** Clients ignore any state with
   `version <= their current`. The version is **seeded from wall-clock ms** (not
   0) so it stays monotonic across a server restart, and clients **reset their
   version watermark on every (re)connect** so the first post-restart snapshot is
   always accepted.
4. **Heartbeat lease.** Devices heartbeat every 15s; the server sweeps every 10s
   and evicts devices older than 45s, parking the session if the active one dies.
5. **Two-phase transfer.** Park the old device (phase 1, `activeDeviceId = null`)
   then activate the target (phase 2) so audio never overlaps; 1s timeout fallback.
6. **`ConnectState` is transport-only; display metadata is client-resolved.** The
   server is authoritative only for live transport it alone knows (ids, index,
   position, playing, active device, volume). Everything *displayable* — track
   title/duration/count, show date/venue — is resolved on each client from the
   show it already loads locally, indexed by `trackIndex`. A server-side
   per-recording track cache was built and reverted; the data already lives on
   every client.
7. **Explicit `epoch` (server boot id) disambiguates restart from intent.** Every
   state carries `epoch`, constant for the life of the process. A *change* in
   epoch is the authoritative "the server restarted and rehydrated this session"
   signal. A still-playing device reclaims ownership only on an epoch change with
   no active device; deliberate transitions (transfer park, stop) keep the same
   epoch and are never mistaken for a restart. This replaces earlier *implicit*
   inferences (`activeDeviceId == null`, then also `tracks.isEmpty()`) that caused
   a post-restart desync and a transfer-park double-play.
8. **The wire protocol is additive-only once shipped.** Mobile apps can't be
   force-updated, so the protocol is the one irreversible commitment. The standing
   rule: **never change/retype/remove an existing wire field — only add** (new
   optional fields / message types old clients ignore). Numeric wire fields must
   be **64-bit safe** (`version`/`epoch` are ms; a 32-bit `Int` silently drops the
   whole state on Kotlin). The social/presence protocol is **not** pre-designed —
   it will be added additively and version-gated later.

## Consequences

**Gains.** No frankenstate or divergent state; total message ordering; instant
transfers with no audio overlap; ghost devices self-evict; a server restart and a
device transfer are both recoverable without interrupting the listener; the
protocol surface stays small because clients resolve their own display data.

**Costs we accept.**
- Server session state is in-memory and wiped on restart (rehydrated lazily from
  the saved playback position, with `tracks`/`playing`/`activeDeviceId` empty).
  Recovery relies on the still-playing device noticing the epoch change and
  re-asserting (load + reclaim). That's a deliberate "transport is recoverable
  from clients" stance, not a persisted live session.
- Two-phase transfer has an inherent ~1s park→activate gap.
- Display correctness depends on each client having loaded the show; a client
  that hasn't can show sparse metadata until it resolves it.
- The protocol is a one-way door post-ship — every future change must be additive,
  forever. This is a permanent discipline, not a one-time gate.
- Presence is "live while a device is connected," never "always known" — a
  backgrounded or non-broadcasting device is invisible to the session. Acceptable
  (it matches Spotify), but the social feature must be designed around it.

**Shipped:** API + web (PR #50), iOS (#51), Android + restart/transfer resilience
(#52).

## Alternatives considered

- **Rebase v1 / replay the original v2 branch onto `main`.** Rejected: PR #48
  rewrote the player surfaces v2 was authored against; a replay re-resolves the
  same conflicts dozens of times with no working checkpoint. Layered
  re-integration using the old branch as reference was used instead.
- **Server-side per-recording track cache** (so the server owns titles/count).
  Built and then reverted: every client already fetches the show to play it, so
  routing display data through the server only added a way to get it wrong. Led to
  the client-resolve decision (#6).
- **Infer "restart" implicitly** from `activeDeviceId == null` (then stacked with
  `tracks.isEmpty()`). Rejected after it shipped two bugs: it conflated restart,
  transfer-park, and stop, which need opposite reactions. The explicit `epoch`
  (#7) names the situation the server already knows.
- **A `transferInFlight` marker** instead of `epoch`. Workable, but it only
  covers the transfer case; `epoch` answers the broader "did the server lose my
  session" directly and also makes stop/park fall out for free, so no extra flag
  is needed.
- **Pre-design the social/presence protocol as a ship gate.** Rejected: presence
  is largely derivable from existing server-side per-user `ConnectState`, and
  `deviceId`/`deviceType` are already on the wire, so it can be added additively
  and version-gated later. The durable requirement is the additive-only rule (#8),
  not an up-front design.
