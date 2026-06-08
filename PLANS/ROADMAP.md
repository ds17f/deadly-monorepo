# Deadly — Roadmap

The single source of active work. Replaces the per-epic plans
(`mobile-server-sync`, `web-profile`, `web-spotify-shell`,
`home-discovery-rails`, `DEAD-335-followups`, `DEAD-119`) — all either
shipped or folded into "What remains" below.

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
  local DB — shows *and* songs via Room `Flow` on Android (now at parity with
  iOS GRDB `ValueObservation`, `b5decdb7`) — so a change pulled from another
  device repaints live instead of going stale. Vestigial imperative
  song-refresh removed from `FavoritesViewModel`.

## What remains

### 1. Sync hardening (mobile)
Favorites are now observation-driven on both platforms (see Shipped). Remaining:
- **Extend observation to recents / reviews / position** on both platforms — the
  favorites path is the proven pattern; recents/reviews surfaces aren't wired
  the same way yet. (Position is slated for Connect-v2 §2, not REST observation.)
- **Android Auto browse tree** (`BrowseTreeProvider`) still reads favorite songs
  one-shot; invalidate via `notifyChildrenChanged` when sync-apply writes, if the
  staleness there proves noticeable.
- **Extract favorite-songs off `ReviewService`** onto `FavoritesService` (both
  platforms) — legacy mis-placement with call-site churn. File a Linear
  tech-debt ticket.

### 2. Connect-v2 / real-time (the next backbone)
Spotify-Connect-style cross-device control: see what's playing elsewhere,
transfer playback, live position/transport over WebSocket. This is the correct
home for **playback-position sync** (deliberately cut from REST) and the
**presence layer** the social "hear" feature depends on.
- **Layered re-integration** (no rebase — PR #48 rewrote the player surfaces the
  branch built on), using `connect-v2` as reference. **API (Layer 1) + web
  client (Layer 2) shipped in PR #50; iOS (Layer 3) merged in PR #51; Android
  (Layer 4) in PR #52** (`connect-v2-android`). #52 also hardens the shared
  protocol so a session survives a **server restart** and a **device transfer**
  on all clients, via an explicit `epoch` (boot id) that replaces the implicit
  null-active / empty-tracks heuristics — verified on a Pixel 6 + device iOS.
  Full strategy + status in [`PLANS/connect-v2-port.md`](connect-v2-port.md);
  the epoch decision is in
  [`PLANS/connect-v2-android-debugging.md`](connect-v2-android-debugging.md).
  Decisions recorded in **ADR-0006** (`docs/adr/0006-connect-v2.md`); the
  detailed spec (`docs/connect-v2-architecture.md`) is amended to match the
  shipped system.
- **Display metadata is client-resolved**, not server state: `ConnectState`
  carries live transport only (ids/index/position/playing/active); each client
  derives title/duration/date/venue from the show it already loads. A
  server-side track cache was tried and reverted. Android must follow this.
- **Revisit the state model with web as a first-class participant** — a browser
  tab is now a controller/target, which changes device identity and presence.
- **Pre-ship rule (mobile):** the social protocol is **not** pre-designed as a
  gate (decided 2026-06-06) — presence is mostly server-side already and will be
  added *additively + version-gated* later. The standing rule that does survive:
  once shipped, **never change/retype/remove an existing wire field — only add**;
  and numeric wire fields must be **64-bit safe** (`version`/`epoch` are ms — a
  32-bit `Int` silently drops the whole state on Kotlin). See
  [`PLANS/connect-v2-port.md`](connect-v2-port.md) "Ship checklist".

### 3. Web profile — social (`/me` 1b)
Friends graph + listening-privacy controls. No backend yet — its own design +
API effort.
- **"See" before "hear":** seeing a friend's activity ships on plain
  request/response and comes first; live "what they're playing right now" is
  presence and depends on Connect-v2 (§2).

### 4. Web shell — tail-end cleanup
- **Collections** (parked): box-set-icon card + `/collections/<id>` detail
  surface, then flip `COLLECTIONS_ENABLED` in `HomeDiscovery.tsx`.
- **Retire the `/me` tab strip — blocked, deferred.** It looks superseded by the
  shell, but the audit found it's still the *only* nav path to two things:
  **Settings on desktop** (`UserMenu` links `/me` but not `/me/settings`, and
  `LibraryRail` only covers Recent/Reviews/Favorites) and **Recent + Reviews on
  mobile** (`MobileTabBar` is Home/Favorites/Settings only; the rail is
  `lg:hidden`). To retire it: add a Settings entry to `UserMenu` for the desktop
  gap, surface Recent/Reviews in mobile nav (a "Library" view or extra tabs),
  *then* drop the strip. Until that nav exists, leave it in place.
- **Done:** `/mockup` prototype route group removed (`3bfdc8f2`).

### 5. Faster first launch (prebuilt catalog DB)
First launch downloads a 25 MB `data.zip` and imports ~20k JSON files on-device
(minutes on venue cell). Replace it with a **prebuilt SQLite catalog seed**
built in CI (~2.2 MB gzip), bulk-copied into each app's DB (sub-second), FTS
rebuilt on-device. Also fixes Android's silent false-complete on kill
mid-import.
- Decisions in **ADR-0007** (`docs/adr/0007-prebuilt-catalog-db.md`); phased
  plan + findings + schema contract in
  [`PLANS/prebuilt-catalog-db.md`](prebuilt-catalog-db.md).
- Order: pipeline producer (build + publish `catalog.db.zip`) → Android consumer
  → iOS consumer → `data.zip` fallback both.
- **Progress:** ✅ Phase 1 (pipeline) done (not yet tagged/published) · 🟡 Phase 2
  (Android consumer + JSON fallback) code-complete, pending local build + device
  run · ⬜ Phase 3/4 (iOS) not started.

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
