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
- Don't design from scratch — rebase/port the `connect-v2` branch (draft impl +
  `api/src/connect/` surface) onto head; promote
  `docs/connect-v2-architecture.md` into a numbered ADR (`docs/adr/`).
- **Revisit the state model with web as a first-class participant** — a browser
  tab is now a controller/target, which changes device identity and presence.

### 3. Web profile — social (`/me` 1b)
Friends graph + listening-privacy controls. No backend yet — its own design +
API effort.
- **"See" before "hear":** seeing a friend's activity ships on plain
  request/response and comes first; live "what they're playing right now" is
  presence and depends on Connect-v2 (§2).

### 4. Web shell — tail-end cleanup
- **Collections** (parked): box-set-icon card + `/collections/<id>` detail
  surface, then flip `COLLECTIONS_ENABLED` in `HomeDiscovery.tsx`.
- **Cleanup:** delete the `/mockup` prototypes (`ui/src/app/mockup`) and retire
  the superseded old `/me` tabs.

## Deferred / explicit non-goals (sync v0)
Cross-device deletion **tombstones**, **settings sync**, and **background sync**
(WorkManager / BGTaskScheduler). Revisit tombstones before flipping sync
default-on for production — deleting on phone while editing on web is a known
last-write-wins foot-gun.
