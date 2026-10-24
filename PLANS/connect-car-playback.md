# Connect and car playback

**Status:** Not started. Connect is disabled. Do this work as part of the Connect rework (ADR-0016 §3 handshake, protocol 3).

**Reference branch:** `connect-car-playback-wip` (commit `0764d6c9`). This branch has client-side Connect rules for Android and iOS. They are not tested on a device. Do not merge the branch as it is. Use it as a source for the rework.

## Problem

Android Auto and CarPlay can play with no phone UI. Connect starts only from the phone UI. Thus the server does not get information about car playback.

When the user opens the phone app, Connect connects and applies the old server state to the live player:

- Android: `ConnectServiceImpl.reactToState` loads the server show if the recording is different. If only the track is different, it seeks to the server track and pauses.
- iOS: the `NEW RECORDING` branch in `ConnectService.reactToState` runs before the active-device guard. It loads the server show, which stops the car audio.
- iOS: CarPlay and Siri call `StreamPlayer.loadQueue` directly. They do not go through `PlaylistServiceImpl`, so Connect does not get a `load`.

## Rules on the reference branch

- On the first state after a connect, if the local player plays a different show or track, keep the local playback. Send the local state to the server. Do not apply the server state.
- After the first state, apply a real show change from another device as usual.
- Tell Connect when the car or Siri loads a show and the socket is live.

## Decision: the device that makes the sound wins

Case: the phone plays in the car and a different device owns the Connect session.

- **Use the Android behavior on all platforms:** the phone takes the session (`transfer`, then `load`). The other device pauses.
- Do not use the iOS behavior on the branch. On iOS, the phone keeps playing locally and does not tell the server. Then two devices can show different playback.

## Why we did not ship it

These rules are client-side patches on top of the real root cause: there is no connect handshake, and the server keeps the last write. Put them into the protocol 3 handshake. Do not add them as more client detectors.

## Related

- The position save and restore fix for car playback (branch `android-aa-position-save`) is independent of Connect. On iOS, the restore skips when Connect has a recording. On Android, the restore has no Connect check, because `core:media` cannot use `core:connect` (circular dependency). If Connect loads a show first, the restore sees a queue and skips. If the restore loads first, Connect replaces the queue. Add the check again in the rework, if necessary.
- `docs/PROTOCOL.md` does not describe these rules yet. Update it when the rework is done.
