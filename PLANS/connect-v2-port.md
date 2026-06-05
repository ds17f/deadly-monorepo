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
- ✅ **Layer 3 — iOS** — built green on `connect-v2-ios` (off `main` post-#50).
  Additive `ConnectService`/`ConnectModels`/`ConnectScreen`/`ConnectSheet` ported
  near-verbatim; engine (`StreamPlayer`/`AudioStreamEngine`) gained
  `skipTo(autoplay:)` + public `onTrackComplete` reconciled into main's
  DEAD-335-rewritten engine; `MiniPlayerServiceImpl` rewritten to merge main's
  skeleton/`restoredTrack` system with Connect remote-state precedence;
  DI + lifecycle wired (`AppContainer`, `deadlyApp`); player surfaces
  (`PlayerScreen`, `MiniPlayerOverlay`, `ShowDetailScreen`, `SettingsScreen`)
  routed through `MiniPlayerService`. `xcodebuild ... -destination 'generic/platform=iOS Simulator'`
  → **BUILD SUCCEEDED** on the remote Mac. Runtime two-device smoke test + device
  install (`make ios-remote-install`, needs `KEYCHAIN_PASSWORD`) still TODO.
- ⏳ **Layer 4 — Android** — not started.

**First shippable unit = Layer 1 + Layer 2 together** (atomic — shipped in #50).
Next concrete step: ship/smoke-test iOS (Layer 3), then Android.

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
   - `docs/connect-v2-architecture.md` — the contract. **Promote to a numbered
     ADR** `docs/adr/0006-connect-v2.md` (next free number; 0001–0005 exist).
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

### Layer 4 — Android (MEDIUM risk — reconcile)

connect-v2 adds a clean `core/connect/` module (`ConnectService`,
`ConnectServiceImpl`, DI) — additive. Reconcile the media/player edits against
main's heavily-rewritten `DeadlyMediaSessionService.kt` (+199 on main) and
`MediaControllerRepository.kt`, plus the new Android Auto work on main
(DEAD-336/337/342/360).

- Port `core/connect/`, `ConnectModels`, settings `ConnectScreen`/`Sheet`/VM,
  player `PlayerConnectSheet`, `PlaybackCommandInterceptor`.
- Reconcile session/notification command interception + hardware volume keys
  with main's session service rewrite.
- **Exit check:** builds locally (`make android-install` — Android builds run on
  this machine, never remote); parity with iOS/web.

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
