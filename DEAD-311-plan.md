# DEAD-311 — Analytics Data Quality & Dashboard Clarity

Plan for driving the epic to completion. Strategy: bundle all mobile-touching work onto `feat/dead-303-show-engagement` for a single coordinated iOS+Android release; land server hardening and dashboard work as separate PRs to `main`.

## 🧭 Orientation — read this first if you've lost the thread

### What this work is

**One epic: DEAD-311 — Analytics Data Quality & Dashboard Clarity.**

The premise: the admin analytics dashboard exists, but the data flowing into it is unreliable (wrong fields, missing fields, noisy events, phantom emissions) and the dashboard itself doesn't surface what you actually need. You can't make product decisions from it. The epic fixes both ends and the middle.

It breaks into three waves:

#### Wave 1 — Mobile bundle (this branch) ← **done, awaiting ship**
Fix what the apps emit, add what they don't, and update the server schema + admin UI to accept and display it. Co-designed across mobile + api + ui because each event needs all three layers. Ships as one TestFlight + Play Internal release.

Tickets in this wave: DEAD-303, 314, 315, 316, 321, 323, 324, 325, 328, 329, 331, 332, 306, 322, plus the partial DEAD-320 (admin clarity).

**Status:** ✅ all done and committed. DEAD-322 (`b549f1f8`) included a privacy-sanitized error vocabulary for `sign_in_failure.error_reason` and a fix to the iOS deferred-emit restore mechanism (the in-session `isRestoring` bracket alone wasn't enough — gate is now in the playback-state observer too).

#### Wave 2 — Backend hardening (this branch, ahead of ship)
- ✅ DEAD-319 — strict dedup guard on analytics insert (`88a0fbf3`)
- ✅ DEAD-326 — analytics raw-event retention policy: default 180 days (`fe627733`)

Folded into this branch since they were small, additive, and de-risk the data we're about to start collecting more of.

#### Wave 3 — Dashboard / metrics (done on this branch)
- ✅ DEAD-327 — admin watershed reference (`857c11be`): typed per-version table at `/admin/analytics-versions`. Versions are seeded `TBD`; fill in at release cut.
- ✅ DEAD-318 — realistic `avg_completion_rate` (`e5eea766`): ≥1min gate, capped at 100%, surfaced as a card on the dashboard.
- ✅ DEAD-317 — distinct-listener Top Shows (`842e1067` + `2f4b7fee` + `7ac6ca04`): replaced raw playback_start count; renamed `plays` → `track_plays`; restored play count alongside listeners.
- ✅ DEAD-304 — per-show completion rate (`8e7b160b`): SUM(listened) / SUM(duration) with ≥5-play gate; rendered alongside listener + tracks-played in the Top Shows list.
- ✅ DEAD-308 — D1/D7/D30 cohort retention (`1251cc93`): weekly cohorts, hatched cells for not-yet-mature buckets.
- ✅ DEAD-309 — search quality panel (`dc21e014`): zero-result rate, abandon rate, median rank, top zero-result + successful queries.
- ✅ DEAD-330 — Plays by Source panel (`463788e6`): horizontal bars w/ humanized source labels; "(unattributed)" bucket for pre-watershed legacy rows; click-through drill.
- ✅ DEAD-320 — admin clarity polish (`99a22cdb`): unit + hint props on MetricCards, italicized "(30d)" qualifiers, "Active installs by platform" renamed from "Platform Split", Stale Installs tooltip.
- ✅ DEAD-302 — Listening Now (`a8271000` + `8e675813` + `07b8212f` + `71293a91` + `bdf15f7f` + `36c92ddc`): 45-min window, inline bitmap matching Show Listening, emoji+iid prefix.

**Plus extras layered on top of the ticket scope:**
- ✅ Growth chart of new installs by day, stacked by platform (`9344fa37` + `2db50465` + `cf3a38cd`).
- ✅ Top-Shows day-range selector (1d / 7d / 14d / 30d / 60d / 180d / 1y) + cover art on each row (`428a4511`).
- ✅ Show Listening (old bottom panel) removed since the new headline sections replace it (`c8491abc`). Component + endpoint left in place for a future history view.

All Wave-3 panels render against current data immediately and improve as new mobile events flow in post-ship. Watershed-gated fields (`playback_start.source`, `search.selected_index`, `feature_use.target_id/provider`) start populating once the mobile bundle ships and the watershed entries are filled in.

### How we reach "done"

1. ✅ Wave 1 (mobile bundle) — committed.
2. ✅ Wave 2 (dedup guard + retention) — committed.
3. ⏳ Wave 3 — DEAD-327 done; remaining dashboard tickets in progress on this branch.
4. 30-min mobile playback regression pass on both platforms (golden path: open show → play → pause → resume → skip → background → cold-launch restore → play through end).
5. Open PR → merge to `main`.
6. Cut TestFlight + Play Internal, promote to production.
7. Fill in actual versions in `api/src/analytics-watershed.ts` at release cut, commit, redeploy server.
8. Set `ANALYTICS_RAW_RETENTION_DAYS=180` in prod env (or rely on the new compose default).
9. Confirm new events are flowing in prod (one DB query).
10. Mark all DEAD-311 child tickets Done in Linear.

### Why throwing this branch out would be a mistake

22 commits spanning mobile + api + ui (~1,460 lines added across api/src and ui/src/app/admin/analytics alone). The mobile, server schema, and dashboard pieces are co-designed — splitting them across branches would mean shipping mobile that emits fields the server drops, or server schema for events nothing emits. Several fixes are subtle (phantom playback_start debounce, search dedup window, restore-track autoPlay) and not the kind of thing you'd remember to redo. DEAD-322 is 90% done — test, commit, ship.

### How we know we're not about to break something big

- **Mobile changes are analytics-only.** No business logic touched except the playback restore autoPlay fix (`edcdf8a5`), which was verified on device. The rest is "emit an extra event" or "add a field to an event."
- **Server schema additions are additive.** New fields and event names added to allowlists; no existing event was made stricter or removed. Old clients keep working.
- **UI changes are admin-only.** The admin analytics dashboard is internal. A regression there doesn't affect end users.
- **The branch has been iteratively validated.** Most commits include "verified on device against fresh local DB" notes. Phantom-play, search dedup, favorites, downloads, preferences all regression-tested (see 🧪 section below).
- **The two known gaps are filed and deferred.** DEAD-333 (force-kill event loss) and DEAD-334 (db-pull container fd issue) — neither blocks ship.
- **One real risk to address before merging:** auth `sign_in_failure.error_reason` currently passes raw `error.localizedDescription` / OkHttp body through. Could leak server-side messages (potentially user emails on "user not found" responses). Sanitize before ship if that's a concern.

The honest one-liner: **finish DEAD-322, sanitize the auth error string, ship.** Everything else is already de-risked.

## ✅ Completed on this branch

| Ticket | Commit(s) | Notes |
|---|---|---|
| **DEAD-303** Show engagement panel | `8d379932` + `10f69926` (fix) | Top favorites / downloads / reviews / shares |
| **DEAD-314** iOS `playback_end` props | `22508c43` | listened_ms, duration_ms |
| **DEAD-315** iOS phantom-track ramp | `266b8f72` | playback_start debounce |
| **DEAD-321** iOS `playback_start.source` + `search.selected_index` | `3a4e652d` | |
| **DEAD-323** Server `feature_use` schema (target_type/id, category) | `5291bfb2` | |
| **DEAD-324** Mobile `feature_use` target_id + category | `76c66526` | iOS + Android |
| **DEAD-329** `playback_end.reason` | `bbaf5c3a` + `3024eb98` (string fix) | |
| **DEAD-328** Android `playback_start.source` + `search.selected_index` | `820bc1ad` | Port of DEAD-321 |
| **DEAD-316** iOS dedup repeated search emissions | `2e587ce1` | 2s window |
| Restore phantom-play fix (mobile) | `fa10a876` (Android) + `284c7069` (iOS) | Defer `playback_start` until user actually plays the restored track |
| **DEAD-325** Drop low-signal nav events | `51d55806` | Removed `open_menu` + `view_collections` (Android only — iOS wasn't emitting them). Validated against orphan DB. |
| **DEAD-332** Mobile `feature_use(add_favorite)` for song favorites | `1d3fefed` | iOS + Android. `target_type=recording_track`, `target_id=$showId/$recordingId/$trackNumber`. Verified on both devices against fresh local DB. |
| **DEAD-306** Mobile playback errors and stalls | `23b36563` | iOS + Android. New `playback_error` (with `error_code`, `error_message`, `is_fatal`) and `playback_stall` (>3s mid-playback rebuffer). Hooked into ExoPlayer's `onPlayerError`/`onPlaybackStateChanged` (Android) and StreamPlayer's `playbackState` observer (iOS). **Note:** server schema for `playback_error`/`playback_stall` was missing — added in DEAD-322 work below. Until that lands, these events are silently dropped server-side. Local validation skipped; will rely on prod traffic post-ship. |

### Likely closed — verify scope

- **DEAD-331** — error event schema (source vs domain): `8f1e0669` covers the core fix. Confirm the "and enrich" portion of the ticket isn't outstanding.
- Possibly part of **DEAD-320** (admin clarity) via `0e30c8bd` (cards drill-down) and `a08b4090` (per-install timeline + outcome bars). Re-read ticket to decide if more is needed.

## ⏳ Remaining for this branch (mobile bundle, must land before ship)

### **DEAD-322** — Download lifecycle + auth journey events

**Status: implemented, built, installed on iOS + Android — NOT YET MANUALLY TESTED OR COMMITTED.** All edits are uncommitted in the working tree.

**What was done:**

Server (`api/src/db/analytics.ts`):
- Registered new event names: `playback_error`, `playback_stall` (DEAD-306 follow-up — schema gap that was previously dropping those events), `download_complete (target_type, target_id, duration_ms, bytes)`, `download_failed (target_type, target_id, duration_ms, error_reason)`.
- Added `provider` and `error_reason` to `feature_use` allowlist (used by auth events).
- API auto-reloads via `tsx watch`.

Mobile auth (iOS `AuthService.swift` + Android `AuthServiceImpl.kt` + DI modules):
- `feature_use(sign_in_attempt, provider=apple|google, category=account)` at the start of Apple/Google sign-in.
- `feature_use(sign_in_success, provider, category=account)` after token exchange + state update.
- `feature_use(sign_in_failure, provider, error_reason, category=account)` on credential, network, decode, or non-200 paths.
- Skipped `sign_up_completed` (user decision — value too marginal to justify a server response field).
- AppContainer (iOS) / Hilt AuthModule (Android) updated to inject `AnalyticsService`.

Mobile downloads (iOS `DownloadServiceImpl.swift` + Android `MediaDownloadManager.kt`):
- Show-level `download_complete (target_type=show, target_id=showId, duration_ms, bytes)` when all per-track tasks settle as completed.
- Show-level `download_failed (target_type=show, target_id=showId, duration_ms, error_reason)` if any track fails after all settle.
- Tracks per-show start time at `downloadShow` invocation; in-memory dedup so re-emits don't fire on stragglers.
- Android: added `analyticsService` to `MediaDownloadManager` constructor — Hilt should resolve it automatically.

**Uncommitted files (`git status`):**
- `api/src/db/analytics.ts`
- `iosApp/deadly/Core/Auth/AuthService.swift`
- `iosApp/deadly/App/AppContainer.swift`
- `iosApp/deadly/Core/Service/PlaylistServiceImpl.swift` (small fix: qualify `SwiftAudioStreamEx.PlaybackState` to disambiguate from `deadly.PlaybackState` — required by Swift type checker)
- `iosApp/deadly/Core/Service/DownloadServiceImpl.swift`
- `androidApp/core/auth/src/main/java/com/grateful/deadly/core/auth/AuthServiceImpl.kt`
- `androidApp/core/auth/src/main/java/com/grateful/deadly/core/auth/di/AuthModule.kt`
- `androidApp/core/media/src/main/java/com/grateful/deadly/core/media/download/MediaDownloadManager.kt`
- `DEAD-311-plan.md` (this file)

**Resume order on next session:**

1. Wipe local DB and restart API for clean test:
   ```
   docker stop deadly-monorepo-api-1
   rm -f api-data/analytics.db api-data/analytics.db-wal api-data/analytics.db-shm
   docker start deadly-monorepo-api-1
   ```
2. **Manual test on iOS** (already installed at last commit cycle, may need a fresh `make ios-remote-install` if device's been off):
   - Sign out → sign in with Apple → expect `sign_in_attempt` + `sign_in_success`.
   - Sign out → airplane mode → attempt sign-in → expect `sign_in_attempt` + `sign_in_failure`.
   - Trigger a show download (favorite + auto-download), wait for full completion → expect existing `feature_use(download_show)` + new `download_complete` with non-zero bytes/duration.
   - (Optional) start download, kill network mid-flight → expect `download_failed`.
   - Settings → Developer → Flush analytics.
3. Query the DB to confirm:
   ```
   sqlite3 api-data/analytics.db "SELECT datetime(ts/1000,'unixepoch'), event, substr(props,1,200) FROM analytics_events WHERE event IN ('feature_use','download_complete','download_failed') ORDER BY ts;"
   ```
4. **Manual test on Android** — same sequence.
5. If anything fails, fix and re-test. If green, commit. Two suggested commits:
   - `feat(api/analytics): add download_complete/download_failed and playback_error/_stall to schema`
   - `feat(mobile/analytics): emit auth journey and download lifecycle events (DEAD-322)`
6. Move DEAD-322 in Linear to In Review (or Done if confident in prod validation), and DEAD-306 schema gap can be noted there too.

### Other tickets after DEAD-322 ships

(none — this is the last mobile ticket. Next step is the ship wave below.)

## 🧪 Open follow-ups from this session (must close before ship)

| # | Item | Status |
|---|---|---|
| A. `open_menu` not emitted | DEAD-325 verification | ✅ pass |
| A. `view_collections` not emitted | DEAD-325 verification | ✅ pass |
| C. `add_favorite` still fires | regression | ✅ pass |
| C. `remove_favorite` still fires | regression | ✅ pass |
| C. `download_show` still fires | regression | ✅ pass |
| C. preference toggles still fire | regression | ✅ pass (`set_source_badge_style`, `toggle_shows_without_recordings`) |
| D. Restore phantom-play fix | functional | ✅ pass — committed Android fix in `edcdf8a5`, verified on device. |
| E. iOS sanity check on DEAD-325 | parity | ⏭ not run |
| iOS event persistence on force-kill | gap | Filed as **DEAD-333**. Same gap exists on Android in practice (events buffered in memory, only flushed every 30s / at 50 events / on `onStop`). Force-kill before flush loses the buffer. |
| Local dev: `make db-pull-analytics` orphans the API container's open fds | infra | Filed as **DEAD-334**. Dropped from active list — workaround is to wipe `api-data/analytics.db*` and restart `deadly-monorepo-api-1` when stale data shows up. |
| Add "Flush Analytics" dev tool button (iOS + Android) | dev tooling | ✅ shipped in `e86f421a`. Force-flushes the buffer with success/failure dialog; preserves events on failure. |
| Restore Linux→Mac rsync sync for `ios-remote-install` | infra | ✅ done in `7d79897a`. Was removed in e62f6589 when Claude was running on the Mac; needed back now that builds run from Linux. |
| Makefile `ios-remote-install` masks build errors | infra | The `xcodebuild ... 2>&1 \| tail -20` pipeline always exits 0, so a failed build still proceeds to install (whatever stale `.app` is in DerivedData gets installed). Caused us to install stale code during DEAD-322 work. Fix: pipe to `tail` via `set -o pipefail` or capture exit status separately. Small, do anytime. |
| Auth events: errors leak provider-side messages | privacy | `sign_in_failure.error_reason` currently passes through `error.localizedDescription` / OkHttp body. Could include user emails on some failure paths (e.g. server "user not found" responses). Sanitize before shipping if this is a concern. |
| iOS playback restoration is flaky (pre-existing, also on `main`) | bug | Cold launch with a saved partial track sometimes fails to restore correctly: mini-player progress shows 0, opening full player may show the right spot but pressing play immediately auto-advances to the next track. Confirmed reproducible on `main` too — independent of analytics work. Root cause is likely a race in the seek-while-playing dance in `PlaybackRestorationService.restoreIfAvailable` (mute → play → seek → 300ms wait → unmute → pause). File as separate ticket post-ship; does NOT block DEAD-311 ship since it's pre-existing. **Note:** also includes the iOS analytics fix that bracketed restore with `isRestoring` flag (replaces `suppressNextStartEmission` + deferred-emit machinery). That fix is correct on its own merits — eliminates phantom `playback_start source:"restore"` events without depending on player state proxies. |

## 🚢 Ship

Once Wave 2 is complete: TestFlight + Play Internal, then promote to production. New events start flowing.

## 🔧 Backend hardening (separate PRs to `main`, any time)

- **DEAD-319** — strict dedup guard on analytics insert
- **DEAD-326** — analytics raw-event retention policy

## 📊 Dashboard / metrics (separate PRs to `main`, post-ship)

These need real data flowing from the new mobile events to be meaningful.

- **DEAD-317** — distinct-listener Top Shows query with listen-time gate
- **DEAD-318** — realistic `avg_completion_rate` metric
- **DEAD-304** — surface completion rate per show
- **DEAD-308** — retention cohorts: D1/D7/D30 by install week
- **DEAD-309** — search quality metrics: zero-result and abandon rates
- **DEAD-330** — admin dashboard: Plays by source panel
- **DEAD-327** — admin UI: analytics event-version watershed reference
- **DEAD-320** — admin dashboard: label and section clarity pass *(verify what's left after this branch)*
- **DEAD-302** — Listening Now live activity. Read description first — may need a mobile heartbeat, in which case it joins the mobile bundle instead of the post-ship dashboard wave.
