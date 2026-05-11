# Rebase Warning — connect-v2

This branch was rebased onto `main` on 2026-05-10 with **50 commits replayed**. The rebase has **not been built or tested** — verify before trusting it.

## Conflicts resolved during rebase

Several conflicts involved real semantic changes on `main` that may not compose cleanly with the Connect v2 work. Audit these spots first:

1. **`androidApp/app/src/main/java/com/grateful/deadly/MainActivity.kt`**
   - Kept both `mediaControllerRepository` (from main) and `connectService` (from branch) injections.

2. **`iosApp/deadly/Core/Service/PlaylistServiceImpl.swift`** (two conflicts)
   - Main moved `playback_start` analytics into a **dwell-window observer**; the branch still did it inline. Kept main's observer pattern and dropped the inline `analyticsService.track("playback_start", ...)` + `playbackStartInfo` assignment from the branch.
   - Kept the branch's `suppressConnectNotify` guard around `connectService?.sendLoad(...)`.
   - **Verify:** analytics still fire correctly for Connect-initiated loads, and the observer's source attribution (`"browse"` / `"auto_advance"`) lines up with what the branch expects.

3. **`Makefile`**
   - Combined main's `ios-promote` + `remote-sync` targets with the branch's `android-remote-clean` target. Also kept main's `android-remote-build: remote-sync` dependency.

4. **`iosApp/deadly/Feature/Player/PlayerScreen.swift`**
   - Main added `playlistService.noteUserSkip(forward:)` analytics before direct `streamPlayer.previous()`/`next()`.
   - Branch replaced the direct stream calls with `miniPlayerService.skipPrev()` / `skipNext()` (Connect-aware routing).
   - Resolution: call `noteUserSkip` **then** the miniPlayerService skip method. Confirm `skipPrev`/`skipNext` still trigger the analytics path correctly.

5. **`iosApp/deadly/Feature/ShowDetail/ShowDetailScreen.swift`**
   - Kept main's new `source: "browse"` argument on `playTrack(at:0, source:)`.

## Before merging

- [ ] `make android-remote-install` builds clean
- [ ] `make ios-remote-install` builds clean
- [ ] Smoke test Connect: cast to another device, hardware volume, skip/next/prev, seek
- [ ] Smoke test local playback analytics: `playback_start`, `playback_end`, source attribution
- [ ] Delete this file
