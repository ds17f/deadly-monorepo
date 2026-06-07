# Connect — keep the WS alive while backgrounded during playback

Stop tearing down the Connect WebSocket when the app backgrounds, so the
**active, still-playing** device stays in the session while the screen is locked.
Client-only, both platforms. No server changes.

Bound by [ADR-0007](../docs/adr/0007-connect-background-socket-lifecycle.md)
(socket lifetime follows playback/process, not the Activity/scene; #2 server
grace and reconnect-reclaim deliberately deferred).

## Status (2026-06-07)

- 🔲 **Not started.** Diagnosis complete and empirically reproduced (client +
  server logs, Android + iOS). Design agreed: ship **#1 (client keep-alive)
  only**; explicitly **defer** server grace + reconnect-reclaim (see "Out of
  scope").

## Problem

While listening with the app backgrounded (locked screen), the Connect session
falls apart:

- The **web/other devices flip to "paused" within ~4s** of locking, and the
  scrubber freezes — even though audio keeps playing on the phone.
- On returning to the app, the **miniplayer/player rewind to a past track**
  (the lock screen/playlist were correct).

Both are the **same root cause** and both are 100% reproducible.

### Root cause — the app disconnects Connect on background

The WebSocket is torn down the instant the app backgrounds:

- **Android** — `MainActivity.onStop()` calls `connectService.stop()`
  (`androidApp/app/src/main/java/com/grateful/deadly/MainActivity.kt:67`).
- **iOS** — the `.onChange(of: scenePhase)` `.background` branch calls
  `container.connectService.stop()`
  (`iosApp/deadly/App/deadlyApp.swift:161-166`).

Confirmed sequence (Android adb + local docker):

```
client: ConnectService: stop() called
client: Closed: code=1000 reason=                         (clean close)
server: unregisterDevice: iPhone[ios] / Pixel[android]
server: mutate: playing=false activeDevice=null           → web shows paused
```

Because the device was the **active** device, the server's `unregisterDevice`
sets `playing=false, activeDeviceId=null` and freezes `trackIndex` at its
last-reported value. Audio keeps playing locally (Android foreground
MediaSession service / iOS background-audio mode) and auto-advances, but
`sendNext` is dropped on the floor (socket is gone). On foreground the client
reconnects, the server replays its **stale** state, and the not-active branch
syncs the local player **backward**:

```
server: State … track=10 … activeDevice=null
client: reactToState: NOT ACTIVE — syncing track 11 -> 10   (local rewound)
```

## Fix (#1) — don't tear down on background

Keep the singleton ConnectService connected when the app backgrounds. While
audio is playing the OS keeps the process alive (Android foreground service /
iOS background-audio), so the WS + 15s heartbeat keep running, `sendNext`/
position reports keep flowing, and the server stays in sync. Nothing rewinds on
return because nothing ever went stale.

When playback genuinely stops and the OS suspends/kills the process, the socket
closes on its own and the server's **existing 45s heartbeat sweep** clears the
session. We rely on that backstop instead of an explicit background `stop()`.

### Android
- `MainActivity.onStop()` (`MainActivity.kt:63-68`): **remove the
  `connectService.stop()` call.** Keep `mediaControllerRepository.notifyAppBackgrounded()`
  and `analyticsService.flush()`.
- `onStart()` still calls `connectService.startIfAuthenticated()` — already a
  no-op when `shouldConnect` is set, so re-foregrounding won't double-connect.
- ConnectService now stops on: explicit **logout** (existing `authService` path)
  and **process death** (socket closes → server sweep). That's the intended
  lifetime: the session follows playback/process, not the Activity.

### iOS
- `deadlyApp.swift:161-166`: **remove `container.connectService.stop()`** from
  the `.background` branch. Keep `playbackRestorationService.saveNow()`.
- `willEnterForeground` already calls `startIfAuthenticated()` (reconnect after
  a real suspension), so the foreground path is unchanged.
- Verify the heartbeat (`Task`/`Timer`) keeps firing during background-audio
  execution; it should, since the app is actively running to play audio. No new
  Info.plist capability needed (audio background mode already keeps the app
  alive; networking is permitted while running).

## Out of scope (deliberately deferred)

- **Server grace period** before clearing `playing`/`activeDeviceId` on an
  active-device socket close (#2). Only helps a **transient network drop that
  straddles a track boundary** — rare event × narrow window × low severity (a
  one-track rewind) — at the cost of changes to the fragile restart/transfer/
  reclaim state machine. Build only if real-world transient-drop reports appear,
  and prefer **client-side reconnect-reclaim** over server state then.
- **Reconnect-reclaim loosening** (let a still-playing device re-assert forward
  on reconnect without an epoch change). Same reasoning; the epoch gate exists
  specifically to avoid the "was I parked or did I drop?" ambiguity.

## Known residual behavior (accepted)

- **iOS suspends when audio stops.** If the user pauses (or the queue ends) in
  the background, iOS suspends within seconds and the socket dies; the server's
  45s sweep then clears the session. → a **≤45s window** where a suspended
  device still shows as the active owner. Cosmetic, pre-existing, tunable later
  (shorten the sweep) if it ever matters. Not a reason to pull in #2.
- **Transient network drop at a track boundary** still risks a one-track rewind
  (see Out of scope).

## Test matrix

Reproduce against the local docker API (`make docker-up`); watch `make
docker-logs | grep '[Connect]'` (server) + `adb logcat | grep ConnectService`
(Android client) / Console.app subsystem `com.grateful.deadly` (iOS).

- [ ] **Lock while playing (the bug):** play → lock. Web keeps `playing=true`,
      scrubber keeps advancing. Server shows **no** `unregisterDevice` / no
      `playing=false` on lock.
- [ ] **Track advance while locked:** let a track end while locked → unlock.
      Player/miniplayer show the **advanced** track (no rewind). Server
      `trackIndex` advanced via `sendNext` while backgrounded.
- [ ] **Foreground round-trip:** background and foreground repeatedly; no
      double-connect, no spurious pause/play.
- [ ] **Force-quit / swipe-away:** session clears within ~45s (heartbeat sweep);
      web eventually shows paused. (Accepted, not instant.)
- [ ] **Logout** still disconnects immediately.
- [ ] **Two-device:** A active+locked keeps playing on web's view; transfer
      A→B still works after a background round-trip.
- [ ] iOS: confirm heartbeats keep firing while backgrounded+playing (server
      `lastHeartbeat` stays fresh, no 45s eviction mid-playback).

## Rollout

Single client-only change, both platforms. Conventional commit:
`fix(mobile/connect): keep Connect connected while backgrounded during playback`
(`fix` + `mobile` scope → lands in both iOS and Android changelogs). No server
deploy, no wire-format change.

Relates to [[project_connect_v2_port]] and the external-media-control fix
(`#53`, the play-intent reconciler), which assumes the active device stays
connected while playing — this plan makes that assumption hold under lock.
