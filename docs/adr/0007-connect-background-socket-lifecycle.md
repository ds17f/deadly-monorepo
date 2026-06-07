# ADR-0007: Connect — background socket lifecycle and the active-device presence boundary

## Status

Accepted (2026-06-07). Extends [ADR-0006](0006-connect-v2.md) (does not
supersede it — 0006's decisions stand). Execution plan:
[`PLANS/connect-background-keepalive.md`](../../PLANS/connect-background-keepalive.md).

## Context

ADR-0006 made the server the source of truth, with a 15s heartbeat / 45s sweep
lease (decision #4) and presence defined as "live while a device is connected"
(cost #5). It did not pin down **when the client holds the socket open** — and
both apps tied it to the foreground UI lifecycle:

- Android `MainActivity.onStop()` → `connectService.stop()`
- iOS `deadlyApp` `.onChange(of: scenePhase) { .background → connectService.stop() }`

So the socket was torn down the instant the app backgrounded. Locking the screen
while listening — the single most common action — therefore killed the session:
within ~4s the server's `unregisterDevice` cleared the active device
(`playing=false, activeDeviceId=null`) while audio kept playing locally on the OS
audio service. Two reproducible symptoms followed: every viewer (web/other
devices) flipped to **paused** with a frozen scrubber, and on returning the
player **rewound to a past track** (the active device auto-advanced while
disconnected, then the reconnect replayed the server's stale `trackIndex` and the
not-active sync path dragged the local player backward). Both are the same root
cause: the active, still-playing device disconnecting.

This also collides with the external-media-control fix
([#53](https://github.com/ds17f/deadly-monorepo/pull/53)), which forwards
lock-screen/headphone pauses to the session and *assumes the active device stays
connected while it plays*.

**Hard platform limit.** You cannot guarantee staying connected. iOS grants
background execution only while audio is actively playing; once audio stops it
suspends within seconds and a `URLSessionWebSocketTask` dies (a `.background`
`URLSession` does not support WebSocket tasks). Drops are therefore unavoidable —
any design must tolerate them rather than try to defeat them.

## Decision

1. **The Connect socket's lifetime follows playback/process, not the
   Activity/scene.** Remove the background `stop()` calls on both platforms.
   While audio plays the OS keeps the process alive (Android foreground
   MediaSession service; iOS background-audio), so the WS + heartbeat keep
   running and the active device stays in the session under lock. The socket is
   torn down only on **explicit logout** and **process death/suspension** (the
   close happens for free when the process dies).

2. **The 45s heartbeat sweep (ADR-0006 #4) is the sole backstop** for genuine
   suspension/death. No new background-keepalive mechanism — no `.background`
   `URLSession`, no silent-audio trick, no extra timers.

3. **We do NOT add a server-side grace period** before clearing
   `playing`/`activeDeviceId` on an active-device socket close, and we do **not**
   loosen the epoch-gated reconnect-reclaim. Transient-drop robustness is
   deferred until real-world evidence shows it matters; if it does, prefer a
   **client-side reconnect-reclaim** over new server state.

4. **Presence boundary reaffirmed and sharpened (ADR-0006 cost #5):** a device is
   in the session **only while its socket is live**. After a real suspension or
   quit it leaves within ≤45s. We accept a ≤45s "ghost active device" window as
   the price of *not* clearing the session instantly-but-wrongly on every
   transient drop.

## Consequences

**Gains.** Locking the screen while listening keeps the session coherent on all
viewers; the rewind-on-return is gone; the #53 external-control fix's assumption
holds. Client-only change — no new wire fields, no server deploy, no change to
the v2 state machine.

**Costs we accept.**
- iOS cannot stay connected once audio *stops* (it suspends): paused-in-
  background and force-quit drop the socket and are cleared by the 45s sweep — a
  ≤45s window where a gone device still shows as the active owner. Cosmetic,
  pre-existing, tunable later by shortening the sweep. Not a reason to add #3's
  server grace.
- A transient network drop that *straddles a track boundary* while backgrounded
  can still cause a one-track rewind on reconnect. Out of scope by decision (rare
  × narrow window × low severity).
- The WS lives slightly longer while backgrounded-and-playing. Negligible — the
  radio is already up for audio streaming.

## Alternatives considered

- **Server grace period before clearing the active device on socket close (#2).**
  Rejected for now. It only helps a transient network drop that coincides with a
  track boundary — rare event × seconds-wide window × low severity (a one-track
  rewind) — while adding a "gone but still active for N seconds" intermediate
  state on top of the restart/transfer/reclaim machinery (ADR-0006 #7), the most
  regression-prone part of the system. That is precisely the kind of overloaded
  state that produced the original desync bugs. Build only with evidence.
- **Loosen the epoch-gated reconnect-reclaim** so a still-playing device
  re-asserts forward on any reconnect. Rejected for now: the epoch gate exists
  specifically to disambiguate "I was parked/stopped by another device" from "I
  dropped and I'm still the rightful owner." Loosening it re-introduces the
  ambiguity ADR-0006 #7 removed.
- **Defeat iOS suspension** with a `.background` `URLSession` or a silent-audio
  keepalive. Rejected: WebSocket tasks aren't supported on background sessions;
  silent audio is an App Store rejection / battery-abuse risk; and it fights the
  "presence = live while connected" model rather than honoring it.
- **Shorten the 45s sweep** to trim the post-suspension ghost window. Possible
  future tuning, not part of this decision.
