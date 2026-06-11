# Deadly — Roadmap

The single source of active work. Replaces the per-epic plans
(`mobile-server-sync`, `web-profile`, `web-spotify-shell`,
`home-discovery-rails`, `DEAD-335-followups`, `DEAD-119`) — all either
shipped or folded into "What remains" below. Community feedback that informs
this list comes from r/thedeadlyapp (the "what should I build next" and
web-launch threads, 2026-06).

## Shipped (the foundation — don't re-plan it)

- **Mobile ↔ server user-data sync.** Favorites (shows + songs), recents, and
  reviews round-trip phone ↔ server: granular per-action push + first-pull
  with last-write-wins merge. Server surface in `api/src/routes/user.ts`
  (SQLite via `api/src/db/userdata.ts`). Live in **iOS 2.32.0 / Android 2.31.1**.
- **Web profile `/me`.** Recent, favorites, reviews (bidirectional), settings,
  sort/filter, installable PWA, avatar upload, editable display name,
  sync-version banner.
- **Web app shell.** Three-pane Spotify-style layout (`AppShell` in
  `ui/.../layout.tsx`), homepage discovery carousels, global search
  (desktop + mobile; song/member/venue/date aware), hierarchical library rail,
  platform-aware store badges.
- **Web analytics + discovery.** Web listens feed Trending; Fan Favorites and
  Today-in-GD-History rails.
- **Reactive favorites UI (both platforms).** The favorites screen observes the
  local DB — shows *and* songs via Room `Flow` on Android (at parity with
  iOS GRDB `ValueObservation`, `b5decdb7`) — so a change pulled from another
  device repaints live instead of going stale.
- **Connect-v2 (cross-device playback) — MVP shipped.** Spotify-Connect-style
  control: see what's playing elsewhere, transfer playback, live
  position/transport over WebSocket. API + web client (#50), iOS (#51),
  Android + restart/transfer resilience (#52), plus external media-control
  forwarding and background keepalive (#53, #54). Live in **iOS 2.33 /
  Android 2.32**. `ConnectState` carries transport only; display metadata is
  client-resolved. Decisions in **ADR-0006**; spec in
  `docs/connect-v2-architecture.md`; strategy/status in
  [`PLANS/connect-v2-port.md`](connect-v2-port.md). **Remaining edges** moved to
  "What remains" §2.
- **Faster first launch (prebuilt catalog DB) — shipped.** First launch now
  bulk-copies a prebuilt SQLite catalog seed (`d8442651`) instead of importing
  ~20k JSON files; FTS rebuilt on-device; fixes Android's silent false-complete
  on kill mid-import. Data pinned to 2.4.0 (`fb90535d`). Decisions in
  **ADR-0007**; details in [`PLANS/prebuilt-catalog-db.md`](prebuilt-catalog-db.md).
- **Admin → user messaging + notifications.** In-app inbox (v1 + v2, #55),
  engagement funnel + per-notification admin stats (#57), admin dashboard
  Versions panel (#56). New `track()` events must be registered in
  `EVENT_SCHEMAS` (`api/src/db/analytics.ts`) or they're dropped.
- **Cold-start home rails populate on first launch — fixed (both platforms).**
  Trending and Fan Favorites no longer stay blank until restart on a fresh
  install. Both rail services now re-`refresh()` when the first-launch catalog
  import completes (Android observes `DatabaseManager.progress` →
  `phase = "COMPLETED"`; iOS re-fetches after import from `deadlyApp`), and a
  guard keeps prior content instead of caching an empty rail when API IDs
  resolve against a not-yet-populated catalog. Verified on remote simulator +
  emulator (2026-06-09).

## What remains

### 1. Playback auto-advance + show queue + shuffle (community's top ask)
The loudest, most-aligned cluster of requests. **Now in build (v2 design):**
[`docs/adr/0010-playback-auto-advance-and-show-queue.md`](../docs/adr/0010-playback-auto-advance-and-show-queue.md),
plan [`show-queue-v2.md`](show-queue-v2.md), branch `show-queue-v2`. A first
attempt (abandoned `show-queue` branch) entangled the queue with auto-advance
and fought Connect-v2's transport authority — the v2 design fixes that by making
advance an independent coordinator off a positive `onShowCompleted` event,
keeping the queue local-first (synced like Favorites), and adding one
informational Connect "park" primitive. Built chunk-by-chunk, proven on all
three platforms.
- **Auto-advance / Go-to-next-show** — ✅ **mechanism complete on all three
  platforms**, incl. cross-device end-of-show countdown over Connect and the
  full-screen "Up Next" takeover + per-device "when a show ends" setting
  (2026-06-11). Chronological today; queue-fed once the queue lands. Remaining:
  the play/pause affordance fix and an intermittent iOS spurious-`next` (seen
  once, needs a repro). *(OP idea #3; MazelTov.)*
- **Queue of shows** — a local-first "play next" list, separate from Favorites
  (which never clear and pollute Fan-Favorites stats). Shows leave the queue
  once played; reorderable; syncs as a whole-list snapshot when signed in
  (absorbs the old `show-queue-sync` effort). *(OP idea #1.)*
- **Shuffle** — both *tracks* and *shows*, with the ability to curate which
  collections feed the shuffle pool. Layers on the queue. *(MuffDiving — "greedy,
  I want both".)*

### 2. Connect-v2 — finish the remaining edges
MVP shipped (see Shipped). Open:
- **Session ownership lease + WS protocol versioning (ship ASAP).** Connect's
  `activeDeviceId` is in-memory server state that clients rebuild via a growing
  pile of cause-specific reclaim detectors — which miss the fresh-reconnect-after-
  restart case (an end-of-show advance silently died this way, 2026-06-11) and the
  disconnect-cleanup case. Replace them with one convergent **ownership lease** the
  audio-producing device renews via heartbeat, gated on a new **`protocolVersion`**
  primitive (monotonic int on `register`, stamped on the in-memory connection
  record — telemetry + soft forced-update lever). **`protocolVersion` has standalone
  value — ship it first.** Server claim-when-null already landed (`52942d45`).
  Decisions in **ADR-0011**; plan in [`PLANS/connect-ownership-lease.md`](connect-ownership-lease.md).
  Redis-persist stays deferred (ADR-0008); the lease covers what we need without it.
- **Web as a first-class controllable target ("remote").** A browser tab is now
  a controller/target — control the web player from the phone. In flight,
  promised publicly. Current constraint: the controlling app can't be
  backgrounded (phone must be on/unlocked); lock-screen persistence was
  deferred. Revisit device identity + presence with web as a participant.
- **Downstream once solid:** Alexa and Sonos apps that the phone can drive.

### 3. Source / recording picker (power-user delight, unblocked)
Surface *which* recording is playing and let users see and switch sources
easily — Matrix vs SBD vs AUD, label Charlie Miller boards, etc. *(ebash42.)*
Schema supports it: many recordings *per* show is fine (the deferred PK limit is
the reverse — one recording → two shows). This is a UI problem: where to put the
source selector on the show/now-playing surface.

### 4. "Hot track" highlights on the mobile setlist
Flag standout tracks (e.g. 🔥) on the setlist / now-playing screen, derived from
review sentiment. *(MazelTov.)* **The data already exists** — the web shows it
from the AI reviews in `stage00`. Cheap to surface on the *setlist*; the caveat
is the recording-track vs setlist-song mapping ("Tuning" et al. don't map to a
played song), so do it on the setlist where the mapping is clean.

### 5. Web profile — social (`/me` 1b)
Friends graph + listening-privacy controls. No backend yet — its own design +
API effort. Now fully unblocked: the presence layer that "hear" depends on
shipped with Connect-v2 (§ Shipped).
- **"See" before "hear":** seeing a friend's recents / reviews / favorites ships
  on plain request/response and comes first.
- **Listening Parties** (one DJ, friends listen along) ride directly on
  Connect-v2 presence/transport — the natural follow-on once friends exist.
- **Custom user collections** (build, then share by link, ultimately "publish"
  into the system) are the bridge between §1 and social — shareable curation is
  the on-ramp to the friends graph. *(OP idea #2; GrrGrrBear.)*

### 6. Tablet + landscape layouts (responsive native)
iPad and Android-tablet layouts plus a proper **landscape** layout so the apps
look right rotated, instead of stretched phone UI. Two parallel efforts (SwiftUI
size classes / Android window-size classes), same design problem — a
wider/master-detail layout echoing the web shell's rail+content. Has user pull
(Used_Bandicoot asked for iPad/native clients) on top of being a maintainer
priority. The big polish track; run it alongside the cheap wins in §1/§3, ahead
of the §5 social backbone.

### 7. Sync hardening (mobile)
Favorites are observation-driven on both platforms. Remaining:
- **Extend observation to recents / reviews / position** — the favorites path is
  the proven pattern; those surfaces aren't wired the same way yet. (Position is
  Connect-v2 territory.)
- **Android Auto browse tree** (`BrowseTreeProvider`) still reads favorite songs
  one-shot; invalidate via `notifyChildrenChanged` on sync-apply if staleness is
  noticeable.
- **Extract favorite-songs off `ReviewService`** onto `FavoritesService` (both
  platforms) — legacy mis-placement. File a Linear tech-debt ticket.

### 8. Smaller asks / integrations (backlog)
- **Reviews published to Archive.org** — if the user links their archive.org
  account, post their review there too. *(OP idea #5.)* External write.
- **In-app feature request system** — replace the Reddit thread with an in-app
  channel. *(OP idea #7.)* Partial infra exists from the admin→user inbox
  (#55/#57); this is the reverse (user→admin) direction.
- **headyversion.com integration** — show each song's headyversion score, sort
  by it, and a "best version of each song" playlist. *(ItsMichaelRay.)* External
  data import.
- **Track-level playlists** — put individual songs into a playlist (e.g.
  reconstruct an unreleased album from live tracks). *(ItsMichaelRay.)* Bumps the
  track≠setlist and one-recording-one-show limitations (§Deferred).
- **Native desktop / iPad clients** — partially covered today by the PWA and
  iOS-app-on-Mac. *(Used_Bandicoot.)* Low priority vs §6.

### 9. Known bug — Google sign-in
At least one user (GrrGrrBear) can't complete Google login; OP suspects an
**international** issue. Needs reproduction + a ticket — not a feature, but it
blocks onboarding for affected users.

### 10. Web shell — tail-end cleanup
- **Collections** (parked): box-set-icon card + `/collections/<id>` detail
  surface, then flip `COLLECTIONS_ENABLED` in `HomeDiscovery.tsx`. (Distinct
  from the *user-built* custom collections in §5.)
- **Retire the `/me` tab strip — blocked, deferred.** Still the only nav path to
  **Settings on desktop** (`UserMenu` lacks `/me/settings`; `LibraryRail` covers
  only Recent/Reviews/Favorites) and **Recent + Reviews on mobile**
  (`MobileTabBar` is Home/Favorites/Settings; the rail is `lg:hidden`). To
  retire: add Settings to `UserMenu`, surface Recent/Reviews in mobile nav, then
  drop the strip. Until that nav exists, leave it.

### 11. Settings screen reorganization (cross-platform, maintainer pain)
The settings screens (iOS `SettingsScreen.swift`, Android `SettingsScreen.kt`,
web `/me` SettingsTab) have grown overloaded and are increasingly hard to work in
— findability is poor and adding a toggle means scanning a long flat list (the
"When a show ends" / Playback section just landed into this sprawl). Regroup into
clear sections with better findability; keep it a presentation/IA change, not new
settings. Independent of any feature — the show-queue plan flags it as
"Settings cleanup (independent, anytime)." Do it before the settings list grows
again (shuffle, queue, and source-picker options are all coming).

## Deferred / explicit non-goals (sync v0)
Cross-device deletion **tombstones**, **settings sync**, and **background sync**
(WorkManager / BGTaskScheduler). Revisit tombstones before flipping sync
default-on for production — deleting on phone while editing on web is a known
last-write-wins foot-gun.

**One recording can attach to only one show.** `recordings.identifier` is the sole
PK on both platforms (lookup is `WHERE show_id = ?`), so the ~57 tapes shared by
two shows (early/late, same-date multi-venue) surface under only one. A
composite-PK / `recording_shows` join-table fix is a coordinated iOS+Android+seed
migration for a small edge case — deferred. Why + path in
[`PLANS/prebuilt-catalog-db.md`](prebuilt-catalog-db.md) "Known limitations";
decision in ADR-0007 §9.

**Out of scope entirely:** additional bands / non-Grateful-Dead content. The
Deadly is bounded by what's in the Grateful Dead's Internet Archive collection.
