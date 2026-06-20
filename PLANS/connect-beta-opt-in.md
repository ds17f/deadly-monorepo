# Connect beta opt-in + server-gated UI — implementation plan

Tracks [ADR-0018](../docs/adr/0018-connect-beta-opt-in-and-server-gated-ui.md).
Goal: Connect stays default-OFF and server-gated, but becomes a discoverable
user-facing beta opt-in (icon always present, greyed when server-off, enable
sheet when server-on-but-device-off, full UI when on).

Status legend: ⬜ not started · 🟡 in progress · ✅ done

## Part 0 — Server: public flag read

- ✅ `GET /api/connect/enabled` → `{ connectEnabled }`, **no auth**
  (`api/src/routes/connectPublic.ts`, registered in `app.ts`). Reuses
  `getConnectEnabled()`.
- ✅ Test `api/src/routes/__tests__/connectPublic.test.ts`: reachable with no
  auth, reflects the flag. Passes; tsc clean.

This is the dependency for every client part — do it first.

## Shared client state model (all three platforms)

Two booleans drive all Connect UI:
- `serverConnectEnabled` — from `GET /api/connect/enabled`. Fetch **at startup**
  (short ~2–3s timeout so it never blocks launch), **fall back to a foreground
  fetch** on timeout/failure, and **refresh on the existing focus/navigation
  trigger** used for userdata sync. Also set `false` when a live session receives
  close code **4005**. Until the first read resolves, use the last cached value,
  defaulting to `false` (greyed) on a fresh install — fail safe to "not offered".
- `connectOptedIn` — the local per-device/per-install toggle (default **false**).

Icon rendering:
- `serverConnectEnabled == false` → icon **greyed**, tap → "Connect unavailable".
- `true && connectOptedIn == false` → icon enabled, tap → **enable sheet**.
- `true && connectOptedIn == true` → existing full Connect UI + "Turn off" row.

Client only opens the WS / registers when `connectOptedIn == true`.

## Part 1 — Android

Files touched in `e64b1f7c` are the map of every gate site:

- ✅ `core/connect/ConnectServiceImpl.kt` — short-timeout `restClient` fetch of
  `/api/connect/enabled` via `refreshServerConnectEnabled()`; expose
  `serverConnectEnabled`; on 4005 set it false; also tear down a live session if
  a refresh reports off. Hooked in `MainActivity.onStart` (startup + foreground).
- ✅ `core/database/AppPreferences.kt` — cached `server_connect_enabled` getter/
  setter (default false), `connectEnabled` opt-in unchanged.
- ✅ Toggle moved out of `DeveloperScreen.kt` into **Playback & Audio → Connect
  (Beta)** (`SettingsScreen.kt` / `SettingsViewModel.kt`).
- ✅ Three-state render handled by the **ConnectSheet** (unavailable / promo /
  full); the four player surfaces now always show the icon, greyed when
  `!serverConnectEnabled`:
  - `PlayerScreen.kt` (+ `PlayerViewModel`), `PlayerSecondaryControls.kt`,
    `PlayerMiniPlayer.kt`, `PlayerSidePanel.kt`
  - `MiniPlayerScreen.kt` (+ `MiniPlayerViewModel`)
- ✅ `ConnectSheet.kt` rebuilt into 3 modes: `ConnectUnavailableContent`,
  `ConnectEnablePromoContent` ("Enable Connect (Beta)" + beta/other-devices copy
  + confirm), and the full picker with a "Turn off Connect" row ("your other
  devices stay connected"). `ConnectViewModel` exposes the two flags + setter.
- ✅ Build: `./gradlew assembleDebug` succeeds (pre-existing deprecation warning
  only).

## Part 2 — iOS

- ✅ `Core/Service/ConnectService.swift` — `serverConnectEnabled` (seeded from
  cache), `refreshServerConnectEnabled()` (3s URLSession GET), 4005 →
  `setServerConnectEnabled(false)` which also tears down a live session.
- ✅ `Core/Service/AppPreferences.swift` — `serverConnectEnabledCached` (default
  false); `connectEnabled` opt-in unchanged.
- ✅ Refresh hooked in `App/deadlyApp.swift` (cold start + willEnterForeground).
- ✅ Toggle moved from `DeveloperView.swift` into **Playback & Audio → Connect
  (Beta)** in `SettingsScreen.swift`.
- ✅ Connect icon always shown, greyed when off, at `PlayerScreen.swift`
  (`connectIconColor`), `SidePlayerView.swift`, `MiniPlayerOverlay.swift`.
- ✅ `ConnectSheet.swift` rebuilt into 3 modes (`ConnectUnavailableView`,
  `ConnectEnablePromoView`, `fullDeviceList` + "Turn off Connect" footer).
- ✅ Compiles clean on the remote Mac (`generic/platform=iOS Simulator`, Debug).

## Part 3 — Web

- ✅ `ui/src/components/connect/ConnectProvider.tsx` — `refreshServerConnectEnabled`
  (no-store GET on mount + focus/visibility); per-install opt-in in
  `localStorage` (`deadly-connect-opted-in`); socket attempted only when signed
  in + opted in + server enabled; 4005 → flag false; context exports the three.
- ✅ `ui/src/contexts/ConnectContext.ts` — added `serverConnectEnabled`,
  `connectOptedIn`, `setConnectOptedIn`.
- ✅ **Settings → Connect (Beta)** toggle in `SettingsTab.tsx` (per-browser; copy
  adapts when server OFF).
- ✅ Three-mode device popover in `DeviceList.tsx` (unavailable / enable promo /
  full + "Turn off Connect on this device"); icon in `HeaderPlayer.tsx` shown
  when opted-in-or-server-on, greyed when server OFF.
- ✅ `tsc --noEmit` clean. Device/redeploy verification pending
  (`make ui-build` + `make docker-redeploy`).

## Validation

- ⬜ Server OFF: all platforms show greyed icon + "unavailable" on tap; no socket.
- ⬜ Server ON, never opted in: promo icon → enable sheet → confirm → connects.
- ⬜ Active session, admin flips server OFF: client receives 4005, session dies,
  icon goes greyed (no churn — Android `onClosing` path).
- ⬜ Disable from full UI: copy mentions other devices stay connected.
