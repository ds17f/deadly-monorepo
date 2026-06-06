# Connect-v2 Android — live debugging handoff (2026-06-05/06)

Written mid-session so a cold session can continue without re-deriving. Pairs
with `PLANS/connect-v2-port.md` (the port plan). The Android port (Layer 4) is
merged-in on branch `connect-v2-android` and installed on a Pixel 6; we are now
fixing functional bugs found in live two-device testing (Android ↔ iOS ↔ web).

## TL;DR root cause

**Server-side `next`/`prev` are dead whenever the session's server-side `tracks`
list is empty — and it's empty for any session that wasn't started by a fresh
"Play" (cold position-hydrate or transfer-only).** This breaks cross-device
track navigation for *all three clients*, and it **contradicts the client-resolve
decision** we made (count was supposed to be client-resolved, NOT load-bearing
server state).

## Evidence

Server (`api/src/connect/state.ts`, shipped) — `handleNext` / `handlePrev`:

```js
export function handleNext(...) {
  if (!state || !state.showId || state.tracks.length === 0) return;  // ← bails on empty tracks
  const newIndex = state.trackIndex + 1;
  if (newIndex >= state.tracks.length) return;                        // ← needs count to clamp
  ...
}
```

Device logcat (`adb logcat -s ConnectService:D PlayerServiceImpl:D`) showed the
session state carrying `"tracks":[]`, and:

```
seekToNext: local -> seekToNext + sendNext
sendNext (pending=null -> next)
State v96: track=15           ← server did NOT advance (empty tracks → handleNext bailed)
seekToPosition: local -> seek + sendSeek
sendSeek: track=16 ...        ← index only moved when a *seek* carried it
State v97: track=16
```

`handleSeek` (state.ts:404) sets `trackIndex` directly and does **not** depend on
`tracks` (and `durationMs` is optional → "unchanged" if omitted). That's the
escape hatch the fix uses.

## The architectural inconsistency (why this is "didn't implement the new protocol")

The plan's client-resolve decision (quote from `connect-v2-port.md`):

> `ConnectState` is the authority only for live transport… Everything displayable
> (track title, duration, **count**, show date/venue) is resolved on each client
> from the show it already loads locally, indexed by the session's `trackIndex` —
> **not carried as load-bearing server state**.

But in the shipped code:
- Both **iOS and Android still send `tracks` on `load`** (`PlaylistViewModel.sendLoad(tracks=…)`, iOS `sendLoad`). The `tracks: SessionTrack[]` field never left the protocol.
- The server **depends on `tracks.length`** for `next`/`prev`.

So client-resolve was applied to **display** but **navigation was never converted**
— it's still a server computation over a server-held track list. When that list
is empty, navigation dies. The count is *de facto* load-bearing, which is exactly
what we said it wouldn't be.

## Fix strategy (honor the decision; no server change)

Every client already loads the full show locally (even non-active devices —
`reactToState` loads the queue paused), so each client already knows its own
track count (`mediaControllerRepository.mediaItemCount`). Therefore:

**Compute next/prev locally and send a `seek` carrying the new `trackIndex`,
instead of the bare `next`/`prev` command the server can't process.** This drops
the server's `tracks` dependency for navigation entirely and matches "each client
resolves count itself."

## Symptom → fix map

| Symptom (observed) | Cause | Fixed by |
|---|---|---|
| Remote (non-active) next/prev does nothing | server `handleNext`/`Prev` bail on empty `tracks` | **Fix 1** (this commit): remote next/prev → local-index `seek` |
| Active device's own next/prev/auto-advance doesn't propagate index to server | `sendNext` is a no-op (empty tracks); `sendPosition` carries only `positionMs`, not `trackIndex`; index only reached server when user happened to scrub | **Fix 2 (TODO)**: active-device advance + `trackAutoAdvanced` must send an index-carrying `seek` (or add `trackIndex` to position reports) |
| Track title doesn't update on track change (but scrubbing/position does) | title comes from `currentTrackInfo` which is **local-only**; `isPlaying`/`playbackStatus` ARE remote-aware (`PlayerServiceImpl.kt:59,81`). Title only moves if the local player's index moves — which it can't while nav is broken | **Likely cascades from Fix 1+2.** If it survives: **Fix 3 (TODO)** — make `currentTrackInfo` remote-resolve title/index from `connectState.trackIndex` against the locally-loaded show, like position already does |
| Scrubber "starts from 0" when remote | suspected: the player screen's scrubber drag value is bound to the local (paused, pos-0) player instead of remote-aware `playbackStatus` | **Fix 4 (TODO)** — trace `feature/player/.../PlayerScreen.kt` scrubber binding; not yet confirmed |
| "all 3 clients struggling" / web shows wrong active device | the shared session is in a bad/empty-tracks state from earlier testing | **restart the session clean** (stop on all devices, fresh Play) before re-testing |

## Caveats for testing
- The existing server session is stuck with `tracks:[]` from earlier. Fix 1
  makes that irrelevant for nav, but **fully restart the session** (stop on all
  devices, fresh Play All) when re-testing so stale state isn't fighting you.
- Remote `seek` for next/prev sends `durationMs = state.durationMs` (the *old*
  track's duration) as a placeholder — we don't have the target track's duration
  in `PlayerServiceImpl`. Non-zero (no frozen jogger); the active device reports
  the correct duration within a beat. Acceptable; note if it looks off.

## Optional later cleanup
Once navigation is fully client-resolved (Fixes 1+2), we can **stop sending
`tracks`** and remove the field from the protocol/state. That's a coordinated
protocol change across api/web/iOS/Android — bigger, do it deliberately, not now.

## Commits on `connect-v2-android` so far
- `ea9842b0` feat: port Connect-v2 (Layer 4)
- `038aec99` fix: sync track index on first state snapshot
- `34040cb7` refactor: serialize state reactions through one queue
- `<this>` refactor: pending-command timeout + unify JSON + named thresholds
- `<next>` fix: remote next/prev via local-index seek (**Fix 1**)

## Build / install / logs
- Build: `make android-build` (now uses `./gradlew`; earlier it called a missing
  system `gradle` — fixed in the Makefile).
- Install: `make android-install` (device: Pixel 6 over adb-tls).
- Logs: `adb logcat -s ConnectService:D PlayerServiceImpl:D MiniPlayerService:D`
- Server next/prev: `api/src/connect/state.ts` `handleNext`/`handlePrev`/`handleSeek`.
