# Connect-v2 Port Plan

Bring the `connect-v2` branch — Spotify-Connect-style cross-device playback
control over WebSocket — onto current `main`. Tracks ROADMAP §2.

Working branch: **`connect-v2-port`** (off `main`; distinct from the stale
`origin/connect-v2` we port *from*). Reference impl lives on `origin/connect-v2`.

## Status (2026-06-05)

- ✅ **Layer 1 — API server** — committed `55629cf8`. v2 state machine
  (`api/src/connect/state.ts` + rewritten `routes.ts`/`types.ts`), v1
  registry/Redis removed, `resolveUser` exported, heartbeat-sweep stop on
  shutdown. `make api-typecheck` + `make api-test` (133) green.
- ✅ **Layer 2 — Web client** — committed `0e8a014a`. Full v2 web client
  (`ConnectProvider`/`ConnectContext`/`types/connect.ts`/`DeviceList`),
  `PlayerProvider` merged onto main's engine, `HeaderPlayer`/`PlayerRailPanel`
  rewired, v1 files deleted. `make ui-build` green (TS + ESLint + 4646 pages).
- ✅ **Deployed to beta** (2026-06-05) — `beta.thedeadly.app`, Hetzner
  `deadly-beta` @ `178.156.230.179`, image `82c25758` (branch head). Verified
  live: a real browser completed WS upgrade → cookie auth → device register →
  state snapshot (`ws/connect: authenticated` + `registerDevice` in api logs).
  Full two-browser transport/transfer smoke test still worth doing.
- ✅ **Layer 3 — iOS** — **merged in PR #51** (`14dc2df3` on `main`).
  Additive `ConnectService`/`ConnectModels`/`ConnectScreen`/
  `ConnectSheet`; engine (`StreamPlayer`/`AudioStreamEngine`) gained
  `skipTo(autoplay:)` + public `onTrackComplete` + `loadedTracks` accessor,
  reconciled into main's DEAD-335-rewritten engine; `MiniPlayerServiceImpl`
  merges main's skeleton/`restoredTrack` shell with Connect remote-state;
  DI + lifecycle wired; player surfaces routed through `MiniPlayerService`.
  Builds green on the remote Mac; bug-fixed against local docker + a real
  device ↔ web. Remote-control fixes in the same PR: offline play/pause
  spinner; **client-resolve display metadata** (see decision below); web
  venue/date from Archive.org + "Invalid Date" killed; transfer-in lands at
  the server position even when the receiver was paused; show-page now-playing
  indicator when remote; desktop queue rail fills the column.
  Still TODO: device install (`make ios-remote-install`, needs
  `KEYCHAIN_PASSWORD`) + a real two-device beta smoke pass.
- ✅ **Layer 4 — Android** — **in PR #52** (`connect-v2-android`). Full port
  (new `core/connect/` module, lifecycle, player/miniplayer/settings surfaces),
  reconciled against main's rewritten media stack (kept Android Auto / DEAD-360,
  took only additive `MediaControllerRepository` APIs). PR #52 also hardens the
  shared protocol so a session survives a **server restart** and a **device
  transfer** on all clients (see the restart/transfer resilience note below).
  Built locally + tested on a Pixel 6. See Layer 4 section below.

**First shippable unit = Layer 1 + Layer 2 together** (atomic — shipped in #50).
iOS (Layer 3) merged in #51; Android (Layer 4) in **PR #52**. Next: merge #52,
two-device beta smoke pass, then the carried two-way-door follow-ups
(lock-screen→remote control, background WS) and the pre-ship presence gate.

### Restart / transfer resilience (PR #52 — applies to all clients)
Two bugs (post-restart desync, transfer-park double-play) shared one root: state
was *inferred* from overloaded fields. `activeDeviceId == null` meant three
things needing opposite reactions — restart (reclaim), transfer phase-1 park
(pause), stop (pause) — and a restart wiped the in-memory session (version→0,
active/playing/tracks lost). Fix: the server stamps every state with an explicit
**`epoch`** (boot id); a change is the authoritative "server restarted" signal,
so reclaim fires only on `epoch changed && activeDeviceId == null && locally
playing`, and the null-active/empty-tracks heuristics are gone. Plus: restored
the park/handoff pause, monotonic `version` + watermark-reset-on-reconnect, and
`version`/`epoch` as 64-bit (`Long` on Android — the ms values overflow `Int`).
Details: [`connect-v2-android-debugging.md`](connect-v2-android-debugging.md).

## Ship checklist — blockers only

What stands between **what we have now** (the cross-device control backbone on
all four surfaces) and putting it in users' hands. Everything *not* on this list
— lock-screen→remote control, background WS, multi-tab presence, the
remote-command spinner, the ADR promotion, and the presence/social feature
itself — is a **two-way door**: it can land in a later update and is explicitly
NOT a ship blocker.

- [ ] **Merge PR #52** — Android (Layer 4) + the restart/transfer resilience.
      No Android Connect ships without it.
- [ ] **Deploy Connect to production**, not just beta — mobile store builds point
      at the prod API, so the `/ws/connect` endpoint + v2 session machine (from
      #50) must actually be deployed there.
- [ ] **Two-device beta smoke pass** on `beta.thedeadly.app` — real
      iOS ↔ Android ↔ web: transfer each hop, and kill/restart the server
      mid-play. The last validation before cutting store builds.

**Decided (2026-06-06): the social protocol is NOT a pre-ship gate.** We won't
pre-design presence/device-identity. The social "see/hear what a friend is
playing" feature will be **gated behind app version** and built **additively**
later — and most of it is server-side anyway: the live per-user playback already
lives in `userStates: Map<userId, ConnectState>`, so "what is friend X playing"
is answerable via a future REST endpoint + friends graph with **no wire change**;
`deviceId`/`deviceType` are already on the protocol. The only **standing rule**
that survives (the real lesson, not a gate): **once shipped, never change /
retype / remove an existing wire field — only add.** Version-gating protects the
*new* feature on old clients; it does nothing if you retype a field the *current*
feature depends on (cf. the `version: Int → Long` near-miss). Presence is "live
while a device is connected," not "always known" — that's the accepted boundary.

### Key architectural decision: client-resolve display metadata (supersedes the server track-cache idea)
The shared `ConnectState` is the authority **only for live transport** the
server alone knows: `showId`, `recordingId`, `trackIndex`, `positionMs`/
`positionTs`, `playing`, active device, volume. **Everything displayable**
(track title, duration, count, show date/venue) is **resolved on each client**
from the show it already loads locally, indexed by the session's `trackIndex` —
*not* carried as load-bearing server state.

Why: the position-only hydrate (cold open / restart) leaves `tracks` empty and
date/venue null in server state, which surfaced as blank subtitles, a frozen
jogger (`position ÷ 0`), missing "next", and web "Invalid Date". A server-side
per-recording track cache was built **and then reverted** (commits `f1e82351` →
`f56f5e87`) once we realized every client already fetches the show to play it —
so the data is on the client; routing it through the server only added a way to
get it wrong. Resolution per platform:
- **iOS** reads title/duration/show-info from `StreamPlayer.loadedTracks[trackIndex]`
  + `TrackItem.metadata`.
- **Web** reads its fetched tracks for the title and pulls date/venue from the
  `archive.org/metadata` it already loads (`fetchArchiveShowMeta`); the showId
  slug (`YYYY-MM-DD-…`) is the date fallback.

Android (Layer 4) should follow the same rule: resolve display metadata locally;
treat `ConnectState` as transport only.

### Key decisions made during the iOS (Layer 3) port
- **Connect starts on `willEnterForeground`, not `scenePhase .active`** — main
  uses a `UIApplicationDelegateAdaptor`, under which scene-phase `.active`
  doesn't fire reliably (main's own sync code notes this). Stop stays on
  `scenePhase .background` (reliable); network-restore reconnect rides
  `networkMonitor.isConnected`.
- **Transfer-in seek when paused**: the audio engine drops `seek(to:)` while
  not playing, so on becoming active the position is stashed as
  `pendingSeekOnFirstPlay` (consumed by `play()`'s first-play dance) instead —
  otherwise a freshly-hydrated receiver starts the track from 0:00.
- **`onLoadShow` uses main's native `playTrack(at:source:autoPlay:)`** instead of
  the reference's poll-until-playing-then-pause hack — main's `loadQueue`/engine
  already honor `autoPlay:` (the new `skipTo(autoplay:)` adds the paused-load path).
- **`MiniPlayerServiceImpl` merged**, not replaced: main's skeleton/`restoredTrack`
  launch shell is preserved; Connect's `remote` (non-active shared session) state
  takes precedence in every computed property, and the `!isSkeleton` action
  guards now sit *after* the remote-control branch so remote commands still send.
- **Player UI keeps main's buffering/preparing spinner states** and folds
  `isPendingCommand` into the same spinner condition; transport routes through
  `MiniPlayerService` so the screen is remote-aware.

### Key decisions made during the iOS (Layer 3) port
- **Connect starts on `willEnterForeground`, not `scenePhase .active`** — main
  uses a `UIApplicationDelegateAdaptor`, under which scene-phase `.active`
  doesn't fire reliably (main's own sync code notes this). Stop stays on
  `scenePhase .background` (reliable); network-restore reconnect rides
  `networkMonitor.isConnected`.
- **`onLoadShow` uses main's native `playTrack(at:source:autoPlay:)`** instead of
  the reference's poll-until-playing-then-pause hack — main's `loadQueue`/engine
  already honor `autoPlay:` (the new `skipTo(autoplay:)` adds the paused-load path).
- **`MiniPlayerServiceImpl` merged**, not replaced: main's skeleton/`restoredTrack`
  launch shell is preserved; Connect's `remote` (non-active shared session) state
  takes precedence in every computed property, and the `!isSkeleton` action
  guards now sit *after* the remote-control branch so remote commands still send.
- **Player UI keeps main's buffering/preparing spinner states** and folds
  `isPendingCommand` into the same spinner condition; transport routes through
  `MiniPlayerService` so the screen is remote-aware.

### How beta was stood up (repeatable)
1. `gh workflow run infra-manage.yml --ref connect-v2-port -f action=launch -f provider=hetzner -f environment=beta` (approve the `beta` env gate — beta approvals are fine to self-approve; prod is not).
2. `gh workflow run build-images.yml --ref connect-v2-port -f ref=connect-v2-port`.
3. `gh workflow run web-deploy.yml --ref connect-v2-port -f environment=beta -f provider=hetzner -f ref=connect-v2-port -f update_dns=true`.
4. **Cold-standup gotcha:** `web-deploy`'s Health check probes
   `https://beta.thedeadly.app/api/health` and runs *before* the GoDaddy DNS
   step — so on a fresh beta (DNS still on the parking/forwarding A records) it
   fails and the DNS update is skipped. Workaround used: set the GoDaddy A
   records manually (same API the workflow uses; key in `.secrets/godaddy-key.txt`):
   `PUT …/v1/domains/thedeadly.app/records/A/{beta,share.beta}` → box IP, then
   `docker compose restart caddy` to force a fresh ACME run. There is **no
   separate GoDaddy forward *rule*** (`/forwards` → NOT_FOUND); the "forwarding"
   was just those A records. Fix worth filing: move the DNS step before the
   health check (or make health check hit the IP) for cold standups.

### Key decisions made during the port
- **Web = full Connect participant** (user-chosen): plays locally, controls
  others, *and* is a transfer target. PlayerProvider hydrates audio on every
  client (paused for non-active) so transfers are instant.
- **Close/dismiss send `stop`** (park the shared session) — matches main's old
  close/clear semantics; any device can then resume.
- **`PlaybackPositionV3.updatedAt`** is required on main now; connect upserts
  stamp `Date.now()` (the server overwrites with `unixepoch()` anyway).
- REST position reporting kept (cross-platform store); real-time sync rides the
  WS `position` command (~5s) on top.

### Known follow-ups / not-yet-done (web)
- **Multi-tab presence**: deviceId is in `localStorage` (shared across tabs of
  one browser) → a second tab re-registers and the server closes the first
  tab's socket. Decide per-tab id (sessionStorage) vs. single-connection.
- **No remote-command spinner**: `pendingCommand`/`pendingTransfer` exist in
  context but HeaderPlayer doesn't yet show a spinner / "Reconnecting…" (v2 did).
  Wire `pendingCommand !== null` + `!connected` into the transport UI.
- **Parked rail track-pick**: tapping a track while parked sends `seek` (moves
  the pointer) but doesn't auto-activate+play; user then hits play. Acceptable
  for v1; revisit if it feels wrong.
- Provider nesting left as main's (`ConnectProvider` already wraps
  `PlayerProvider`); v2's cosmetic reorder + `UserMenu` `mt-2` not applied.

### Known follow-ups / not-yet-done (iOS)

All client-side and **reversible in a later App Store update** (no protocol /
server / data change; old app versions degrade gracefully — they keep working,
just don't broadcast in the background). Deferred deliberately; revisit after the
two-device happy-path smoke test.

- **WS dropped whenever backgrounded** (inherited from the reference:
  `deadlyApp` calls `connectService.stop()` on `scenePhase .background`). The
  sharp edge: even when iOS is the *active* device playing with the screen
  locked — the normal listening mode — the phone goes invisible to the session
  and stops broadcasting position/transport until the app is foregrounded.
  Tractable fix: keep the socket alive while iOS is the one playing (just don't
  `stop()` during background *playback*). Note the platform wall: a *pure
  controller* iOS app that isn't playing audio can't hold a socket in the
  background anyway (iOS suspends it ~30s; would need push/VoIP) — not our
  decision to make.
- **Lock-screen / Now Playing controls don't drive a remote device.**
  `MPRemoteCommandCenter` callbacks are wired straight to the local
  `StreamPlayer` (`StreamPlayer.swift` ~527), bypassing the Connect-aware
  `MiniPlayerService`; and Now Playing is only populated when this device is the
  audio source. So while remote-controlling the web, the lock screen can't
  pause/skip it. Full Spotify parity = route those callbacks through
  `MiniPlayerService` + populate Now Playing from `ConnectState`. Server already
  accepts the commands (web sends them today), so this is client-only.
- Carry-over from the web list that also applies to iOS: no explicit
  "Reconnecting…" affordance beyond `isPendingCommand` spinners.

### Pre-ship gate (the only one-way door) — verify before any App Store build

A shipped build can't be force-updated, so the **wire protocol is the only
irreversible commitment**. **Decided (2026-06-06):** we are **not** pre-designing
the social protocol as a gate (superseded — see "Ship checklist" above). Presence
is largely server-side already (`userStates: Map<userId, ConnectState>`;
`deviceId`/`deviceType` already on the wire), so the ROADMAP §3 social feature
will be built **additively + version-gated** later rather than designed up front.

What remains as the actual standing rule (not a one-time gate): **once shipped,
never change / retype / remove an existing wire field — only add.** Adding optional
fields / new message types is always safe (old clients ignore them); breaking an
existing field breaks the *current* feature on un-updatable installs (cf. the
`version: Int → Long` near-miss, and the 64-bit-field rule in
`connect-v2-android-debugging.md`). The background-WS and lock-screen items above
are two-way doors and can land later.

## TL;DR

- **Do not `git rebase`.** A vanilla replay of 51 commits onto a moved `main`
  re-resolves the same web/iOS/Android player conflicts dozens of times against
  a moving target, with no working checkpoint until the very end.
- **Do a layered re-integration** on a fresh branch off `main`, using
  `connect-v2` as the *spec + reference implementation*, not commits to replay.
- **Order: API → Web → iOS → Android.** API is nearly isolated (port it
  cleanly). The clients must be reconciled against today's player code, which
  has changed substantially since the branch point.

## Why a rebase loses

| Fact | Value |
|---|---|
| Branch point (merge-base) | `5dfbcc20` (2026-05-10) |
| `connect-v2` ahead of base | 51 commits |
| `main` ahead of base | 46 commits |
| Files changed on **both** sides | **34 of connect-v2's 78** |

The decisive change on `main` is one PR:

> **`fca679ba` — "cross-platform user-data sync + full web client (#48)"**

It rewrote the web player (`HeaderPlayer.tsx` **+1020/-144**), rebuilt
`PlayerProvider.tsx`, added the cross-platform sync layer, and touched iOS,
Android, **and** the API in one shot. Connect-v2's web commits were authored
against a web player that **no longer exists** on `main`. Replaying them is not
a merge — it's a rewrite disguised as conflict resolution.

Hot-file churn (connect-v2 vs main, since base):

| File | connect-v2 | main |
|---|---|---|
| `ui/components/player/HeaderPlayer.tsx` | +100/-228 | **+1020/-144** |
| `ui/components/player/PlayerProvider.tsx` | +240/-367 | +250/-3 |
| `iosApp/.../MiniPlayerServiceImpl.swift` | +224/-9 | +71/-14 |
| `androidApp/.../DeadlyMediaSessionService.kt` | +25/-1 | **+199/-3** |
| `api/src/connect/*` | replaces dir | **+12 total** |

Note the asymmetry: the **API connect surface is essentially untouched on main**
(12 lines), while the **clients were heavily rewritten on main**. That gradient
is exactly why we port API-first and re-implement the clients.

## The four layers

### Layer 0 — Branch + docs (prep)

1. Cut `feat/connect-v2` off current `main`.
2. Bring in the reference docs (connect-v2's copies are newest — `640283e1`,
   May 10, newer than the `docs/connect-v2-architecture` branch's Apr-6 copy):
   - `docs/connect-v2-architecture.md` — the contract. ✅ **Done:** decisions
     recorded in `docs/adr/0006-connect-v2.md`; the spec is amended to match the
     shipped system (client-resolve, epoch, ms-seeded version, additive-only wire).
   - `docs/connect-volume.md`, `docs/dead-276-connect-ui-breakdown.md`.
3. Keep `connect-v2` checked out alongside as the reference implementation.

### Layer 1 — API server (LOW risk — port cleanly) — ✅ DONE (`55629cf8`)

`main` added only 12 lines to `api/src/connect/`; connect-v2 owns the directory.

- **Delete** v1 `registry.ts`; **add** `state.ts` (the authoritative state
  machine: `mutateState()`, monotonic version, full-snapshot broadcasts).
- **Port** the rewritten `routes.ts` and `types.ts`.
- **Re-apply** the wiring in `server.ts` / `app.ts` (WS upgrade, route mount)
  and the 1-line `auth/middleware.ts` change (session-cookie auth for WS, not
  token query param — see `59fd5e46`).
- Drop the v1 `userdata.ts` connect fields connect-v2 removed (reconcile
  carefully — main heavily rewrote `userdata.ts` for the sync PR).
- **Exit check:** `api` builds; WS handshake + heartbeat works; a raw client can
  connect, get a state snapshot, and see version increment on a mutation.

### Layer 2 — Web client (HIGH risk — re-implement, don't patch) — ✅ BUILT (`0e8a014a`), runtime unverified

Target is the **new** `HeaderPlayer.tsx` / `PlayerProvider.tsx`. Treat
connect-v2's web files as reference for *behavior*, not lines to apply.

- New WS client `lib/connectWs.ts` + `contexts/connect.ts` + `ConnectProvider`.
- `DeviceList` (icon-based, lives in header player) replacing v1 `DevicePicker`.
- Wire into the new player: remote control state, transfer UI, next/prev/seek,
  stop, volume, reconnecting state, position interpolation with clock sync.
- **Revisit the state model with web as a first-class participant** (ROADMAP §2):
  a browser tab is now a controller *and* a target — confirm device identity,
  presence, and reconnect semantics hold for tabs.
- **Exit check:** `make ui-build` + `make docker-redeploy`; two browsers can see
  each other, transfer playback, and stay position-synced.

### Layer 3 — iOS (MEDIUM risk — reconcile) — ✅ BUILT (`connect-v2-ios`), runtime unverified

connect-v2 adds `ConnectService.swift`, `ConnectModels.swift`, `ConnectSheet`,
`ConnectScreen` (mostly additive), then edits the player surfaces that `main`
also moved (`MiniPlayerServiceImpl`, `PlayerScreen`, `MiniPlayerOverlay`,
`AppContainer`, `deadlyApp`).

- Port the additive Connect files first.
- Reconcile the player-surface edits against main's current code (mind the
  DEAD-335 race/auto-advance fix `479f58dd` already on main).
- Include cast button, "Playing on…" bubble, transfer, volume + hardware-key
  interception, stop, interpolated sync, instant reconnect.
- **Exit check:** builds on the remote Mac (`make ios-remote-install`); can
  control web/Android from device and vice versa.

### Layer 4 — Android (MEDIUM risk — reconcile) — ✅ IN PR #52 (`connect-v2-android`)

Working branch **`connect-v2-android`** off `main` (post-#51). Reference:
`origin/connect-v2`. **~2,125 lines / 36 files** on the reference.

**Scope (from `git diff 5dfbcc20 origin/connect-v2 -- androidApp/**`):**

*Additive — port cleanly (new `core/connect/` module):*
- `core/connect/ConnectServiceImpl.kt` (**704 lines** — WS register/heartbeat,
  clock-sync, state reconciliation, transport/transfer/volume, reconnect),
  `ConnectService.kt`, `di/ConnectModule.kt`, `core/model/ConnectModels.kt`,
  `core/model/AppLaunchState.kt`; module Gradle + `settings.gradle.kts` wiring.
- Settings `screens/connect/ConnectScreen.kt` / `ConnectSheet.kt` (295) /
  `ConnectViewModel.kt` + `SettingsNavigation` entry.
- `MainActivity.kt` (+34, lifecycle start/stop), `MainNavigation.kt`.

**Starting notes (de-risked 2026-06-06 — don't re-discover):**
- Module layout did **not** shift — all reference paths above match `main`
  (full paths are `androidApp/<mod>/src/main/java/com/grateful/deadly/...`; the
  `git diff --stat` display abbreviates them).
- `androidApp/core/connect/` on `main` is **only stale `build/` artifacts**
  (not git-tracked, leftover from an old connect branch) — the module genuinely
  needs creating; ignore/`git clean` the junk.
- All connect-module deps exist on `main`: `:core:model`, `:core:database`,
  `:core:api:auth`, `:core:media`, `:core:network`. Auth token for the WS Bearer
  comes from `core/api/auth` `AuthService` (impl `core/auth/AuthServiceImpl`).
  Server `resolveUser` checks `Authorization: Bearer` first, so Bearer is fine.
- **Concrete first chunk:** create `core/connect` (build.gradle.kts +
  ConnectService/ConnectServiceImpl/di/ConnectModule near-verbatim) +
  `core/model/ConnectModels.kt` + `settings.gradle.kts` `include(":core:connect")`,
  then `make android-install` to confirm the additive layer compiles before
  touching player surfaces. Then do the reconcile targets below.

**✅ Additive first chunk DONE (2026-06-06):** `core/connect/` module created
(`build.gradle.kts`, `consumer-rules.pro`, `ConnectService`/`ConnectServiceImpl`/
`di/ConnectModule` byte-identical to reference) + `core/model/ConnectModels.kt`
+ `AppLaunchState.kt`; `include(":core:connect")` wired. `:core:connect:assembleDebug`
and full `:app:assembleDebug` both green. Findings worth keeping:
- **`ConnectServiceImpl` needs 4 media APIs that `main` lacks** — these are the
  *minimal additive slice* of the media reconcile and had to land now for the
  module to compile (added to `MediaControllerRepository.kt`, all purely additive):
  `trackAutoAdvanced: SharedFlow<Int>` (emitted in `onMediaItemTransition` on
  `MEDIA_ITEM_TRANSITION_REASON_AUTO`), `seekToMediaItemIndex(index, posMs)`,
  `setVolume(Int)`/`getVolume()` + a `_volume` StateFlow. The wire `ConnectState`
  (API `types.ts` + iOS `ConnectModels`) **does** still carry
  `tracks`/`date`/`venue`/`location`, so the reference `ConnectModels.kt` matches
  the shipped protocol verbatim — client-resolve is a *behavior* rule, not a
  trimmed wire shape.
- **Do NOT apply the reference `MediaControllerRepository` diff wholesale** — it
  *removes* main's DEAD-360 "seed state flows on attach" block + `hasActiveQueue()`
  (the reference predates DEAD-360) and adds `controller.pause()` calls that may
  collide with main's paused-load path. Only the 4 additive APIs above were taken.
- **Fixed `androidApp/Makefile` `build:`/`release:` to use `./gradlew`** — they
  called bare `gradle`, which fails in any shell without a system gradle install
  (e.g. the non-interactive tool shell); `install` already used the wrapper. Now
  all three are environment-independent.
- App doesn't depend on `:core:connect` yet, so `ConnectModule` isn't in the Hilt
  graph — that arrives with the MainActivity lifecycle + UI wiring (next).

**✅ Lifecycle wired (2026-06-06):** `:app` now depends on `:core:connect`;
`MainActivity` injects `ConnectService` and calls `startIfAuthenticated()` in
`onStart`, `stop()` in `onStop`, plus `dispatchKeyEvent` routes hardware
volume keys through `handleHardwareVolumeKey` (remote-device volume, Spotify
behavior). Reference base matched main verbatim — applied cleanly. Full
`make android-build` green (Hilt resolves `ConnectService`). **This is the first
installable milestone:** once signed in, the phone opens the WS on foreground,
registers as an `android` device, appears in web/iOS device lists, and works as
a **transfer target** (responds to remote play/pause/seek/next/load via
`reactToState`). Known inherited edge: `onStop → stop()` drops the socket on any
background (matches iOS; reversible follow-up).

**✅ FULL PORT DONE (2026-06-06) — builds clean, awaiting device smoke test.**
36 files / +2087. Key realization that unblocked it: most of the "reconcile"
scope was mis-sized. The number that matters is *how much main changed each file
since the branch point* (`git diff $BASE:f main:f`), **not** how many lines the
reference added. By that measure the entire player/miniplayer surface
(`PlayerServiceImpl`, `MiniPlayerServiceImpl`, `MiniPlayerScreen/ViewModel`,
`PlayerScreen` + components, `MainNavigation`, `SettingsNavigation`) is
**main-untouched** → a clean `git checkout origin/connect-v2 -- <file>` port.

Only these needed hand-merging (main moved them too):
- **`DeadlyMediaSessionService.kt` — left as main's, untouched.** The reference's
  `commandInterceptor` hook is **dead scaffolding** (nothing in the reference ever
  *sets* it → always null → pass-through; lock-screen→remote routing was never
  actually wired on Android, same gap as iOS). Porting the reference file would
  have deleted main's Android Auto work (`onPlaybackResumption`/DEAD-360, ±10s
  notification buttons, `ControlStyleFilteringPlayer`/PlayerControlsStyle, the AA
  scroll workaround). So we dropped the orphan `PlaybackCommandInterceptor.kt` and
  kept main's session service whole. **Follow-up (two-way door):** wire a real
  interceptor later for lock-screen control of a remote device.
- **`MediaControllerRepository.kt`** — took only the 4 additive APIs
  (`trackAutoAdvanced`, `seekToMediaItemIndex`, `setVolume`/`getVolume`); kept
  main's DEAD-360 seed block + `hasActiveQueue()` (reference would've deleted them).
- **`LastPlayedTrackService.kt`** — kept *both* restore guards: skip if Connect has
  a recording **and** main's skip-if-AA-has-a-queue (DEAD-229).
- **`PlaylistViewModel.kt`** — ported reference (sends load/seek/play to Connect),
  re-applied main's reactive `getShowReviewFlow` review observation.
- **`PlayerViewModel.kt`** — took reference's `connectRemoteDeviceName` +
  service-`isPlaying` wiring, kept main's 3-arg `isSongFavoriteFlow`.
- **`SettingsScreen.kt`** — added the "Connected Devices" `PreferenceRow` +
  `onNavigateToConnect` to main's redesigned screen.
- **`feature/settings/build.gradle.kts`** — added `:core:connect`, kept
  `:core:api:usersync` (reference had *replaced* it).

**Known gaps carried (all two-way doors, post-smoke-test):** lock-screen/notif
controls don't drive a remote device (interceptor unwired, == iOS); WS drops on
background (`onStop → stop()`, == iOS). **Next:** real two-device smoke test
(`make android-install` on a dev's device ↔ web/iOS), then address the gaps.

*Reconcile against main's rewrites (the real work — confirm main's current
line-counts/paths when starting):*
- `core/media/service/DeadlyMediaSessionService.kt`,
  `core/media/repository/MediaControllerRepository.kt`,
  `core/media/service/PlaybackCommandInterceptor.kt` — session/notification
  command interception + hardware volume keys vs main's rewritten media session
  and the Android Auto work (DEAD-336/337/342/360).
- `core/player/service/PlayerServiceImpl.kt` (+175),
  `core/miniplayer/service/MiniPlayerServiceImpl.kt` (+95),
  `core/miniplayer/LastPlayedTrackService.kt` (restore-skip when Connect has a
  session — mirror iOS `PlaybackRestorationService`),
  `feature/miniplayer/MiniPlayerScreen.kt`/`MiniPlayerViewModel.kt`,
  `feature/player/PlayerScreen.kt` + secondary controls,
  `feature/playlist/PlaylistViewModel.kt` (+229 — sends load/seek to Connect).

**Hard rules carried from iOS (do NOT relitigate):**
1. **Client-resolve display metadata** (see decision above) — Android reads
   title/duration/date/venue from the show it loads locally, indexed by
   `connectState.trackIndex`. `ConnectState` is transport-only. Do **not** add a
   server track cache (we built and reverted one).
2. **Transfer-in position**: make sure becoming-active lands at the server
   position even if the receiver was paused/just-hydrated (iOS needed
   `pendingSeekOnFirstPlay`; find the Media3/ExoPlayer equivalent — seeking a
   prepared-but-not-playing player, or seek-on-ready).
3. **Now-playing-when-remote**: the show/track list "playing" indicator must
   reflect `connectState` (index + playing), not just the local player.
4. **Pending-command spinner** only while genuinely remote-controlling — never
   strand the UI when the socket is down.

- **Exit check:** builds locally (`make android-install` — Android builds run on
  this machine, never remote); two-device parity with iOS/web.

## Optional: a rebase spike (cheap reality check, throwaway)

If you want to *see* the conflicts before committing to the port, run a
time-boxed spike and throw it away:

```bash
git checkout -b spike/connect-v2-rebase origin/connect-v2
git config rerere.enabled true          # reuse resolutions if you retry
git rebase origin/main                   # expect heavy web-player conflicts
git rebase --abort                       # when it confirms the thesis
```

Expectation: it stalls hard on `HeaderPlayer.tsx` / `PlayerProvider.tsx`. Use it
to validate the decision, not to produce the branch.

## Open questions

- **Web as first-class device** — does the v2 state model (authored when web was
  a bolt-on) need device-identity/presence changes now that a tab is a target?
- **userdata.ts reconciliation** — what connect state, if any, still lives in
  REST userdata after main's sync rewrite, vs. moving entirely to the WS state
  machine.
- **Presence layer** — `/me` social "hear what they're playing" (ROADMAP §3)
  depends on this; confirm the v2 state machine exposes the presence hooks it
  needs, or note it as follow-up.

## References

- Branch: `origin/connect-v2` @ `56ef9519` · base `5dfbcc20`
- Docs branch: `origin/docs/connect-v2-architecture` (older spec; superseded by
  connect-v2's in-branch copy)
- ROADMAP §2 (Connect-v2 / real-time)
