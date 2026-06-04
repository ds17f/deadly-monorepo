# Web profile and personalized home

Build a real reason to be signed in on the web app. Today the signed-in
experience on TheDeadly.app is essentially identical to the anonymous
one — the only difference is a name + Sign out dropdown
(`ui/src/components/auth/UserMenu.tsx`). This epic adds a `/me` profile
area, surfaces the user-data the server already tracks (favorites,
recent), and personalizes the home page for authenticated users.

**Depends on**: [mobile-server-sync.md](./mobile-server-sync.md). The web
client can be built first against an empty server state, but the epic's
*value* depends on phones actually pushing data to the server. Without
sync, signed-in users see empty lists.

## Why this exists

A user contacted us about the web player, which surfaced that people are
actually using the website. Today there's nothing to keep them signed in
or coming back. Meanwhile the server already has the entire user-data
surface built (`api/src/routes/user.ts`) — favorites/shows,
favorites/songs, recent, reviews, recording preferences, position,
settings. iOS has a feature-rich library experience
(`iosApp/deadly/Feature/{Home,Favorites,Collections,...}`); the web has
none of it.

So this isn't "design a profile system." It's "build the web UI on top of
endpoints that already work."

## Decisions already made

- **Route name**: `/me` (short, conventional, matches GitHub/Spotify-ish).
- **Home strategy**: at `/`, replace the marketing/landing home with a
  personalized home for signed-in users. Signed-out users see the
  current home unchanged. Matches Spotify/YouTube Music behavior.
- **`/me` is the user's profile, not a library index.** (Added
  2026-05-31.) `/me` = "Me": identity + social. Top level is the person —
  avatar/profile picture, screen name, and **social** (friends/contacts,
  and privacy controls over who can see and hear what you're listening
  to). The library surfaces — Recent, Shows, Favorites — live *underneath*
  the profile as sub-sections, not as the primary thing. Route shape:
  `/me` (profile landing) · `/me/recent` · `/me/favorites` · `/me/settings`.
  Social/contacts have **no backend yet** — scaffold the profile shell
  with clearly-labeled "coming soon" placeholders; design that domain
  (friend model, presence/listening-privacy model) as its own effort.
- **Dynamic, endpoint-hydrated shells — not static rendering.** (Revised
  2026-05-31.) The original "no new server endpoints / consume only the
  existing surface" rule is dropped. `/me` is inherently dynamic per-user
  data; the right model is a client-side shell that fetches fresh data
  from the API on interaction and hydrates, rather than being rigid about
  where/when data is downloaded. Static rendering was chosen for the
  *show catalog* (which barely changes) — that rationale does not apply
  to a personal library. Add server endpoints where they're the best
  solution. Choose correctness/freshness over design purity.
- **Show-metadata enrichment is a real gap.** `GET /api/user/recent` (and
  the favorites endpoints) return bare records keyed by `showId`. Show
  *display* metadata (venue/city/date/rating) currently lives ONLY in the
  static site's server-side JSON (`ui/src/lib/shows.ts`, `fs`-based) —
  neither the API nor the client can resolve an arbitrary `showId` → show
  at runtime. Recent v1 (landed) shows date (parsed from the showId slug)
  + a link to the show page. Proper venue/city needs a **show-metadata
  source in the API** (e.g. load a compact index from the data pipeline,
  expose enriched recents/favorites or a generic show lookup). This is
  shared infrastructure for Recent *and* Favorites — build it before the
  Favorites list needs nice display.
  - **Implemented 2026-05-31.** `api/src/showCatalog.ts` loads a compact
    index (`make api-show-index` distills `ui/data/shows/*.json` →
    `api-data/show-index.json`, ~414 KB / 2313 shows) into an in-memory
    Map at boot; `GET /api/user/recent` is enriched with
    date/venue/city/state. The catalog is the reusable source — favorites
    enrichment will reuse `getShowMeta`.
  - **Deploy wiring: done.** The index is **baked into the API image**
    and loaded via `SHOW_INDEX_PATH=/app/show-index.json`. Local:
    `docker-up` depends on `api-show-index` (regenerates into the build
    context before `--build`). CI: `build-images.yml` generates it right
    after the data download, before the API image build — so it
    auto-refreshes whenever `data/version` bumps (that path already
    triggers the workflow). No runtime volume/deploy coordination; the
    generator is non-fatal if data is absent (empty index → bare records).
  - **Remaining (optional):** a generic batch lookup
    (`GET /api/shows?ids=`) if a consumer ever needs arbitrary showIds
    rather than a pre-joined list.
- **V3 schema is the contract.** Web client types mirror the server's
  `FavoriteShowV3`, `RecentShowV3`, etc. No web-flavored variants.

## Feature parity target

Realistic web v1 mirrors a subset of iOS's
`iosApp/deadly/Feature/` directories:

| iOS feature | Web v1? | Notes |
|---|---|---|
| Home | Yes | Personalized when signed in |
| Favorites (shows + songs) | Yes | List + toggle on ShowDetail |
| Recent / Library | Yes | Read-only list, click to play |
| Reviews | Yes | `/me/reviews` list; bidirectional sync with mobile |
| Settings | Yes (minimal) | Display name, sign out, delete account |
| Collections | Later | Curated; lower personalization value |
| Downloads | Never | No web equivalent |
| Playlist | Later | Built-in player has queue; standalone playlists later |
| CarPlay / Siri | Never | iOS-only |
| Player | Already exists | `ui/src/components/player/` |

## Work breakdown

### 1. Profile route shell — `/me` with nav — LANDED (restructured 2026-05-31)
- Auth-gated layout at `ui/src/app/me/` (client gate mirrors `/admin`,
  redirects signed-out users to `/signin?callbackUrl=/me`).
- Identity header (avatar + display name from the session) + sub-nav:
  **Profile / Recent / Favorites / Settings**.
- `/me` is the **Profile** landing (identity + social placeholders).
  `/me/recent`, `/me/favorites`, `/me/settings` are the sub-sections.
- `UserMenu.tsx` has a "Your library" link to `/me`.

### 1b. Profile & social (NEW — backend to design)
- Profile landing content: profile picture upload, editable screen name,
  and social — friends/contacts (add/remove) and listening-privacy
  controls (who can see / hear what you're playing).
- None of this has a backend yet. v1 = honest "coming soon" placeholders
  showing the real session avatar + name. The social domain (friend
  graph, presence, privacy model) is its own design + API effort.
- **"See" before "hear".** The privacy control splits in two: *seeing*
  what a friend is/was playing (friends + basic activity) can ship on
  plain request/response and comes first. *Hearing* — live, real-time
  "what they're playing right now" — is presence and **requires
  connect-v2 / WebSocket as a prerequisite**; it comes later, after that
  backbone exists. Connect-v2 is effectively the presence layer for the
  social "hear" feature.

### 2. Recent shows on web — v1 LANDED (d50b7ea6)
- `GET /api/user/recent` → list of recent shows. `fetchRecentShows()` in
  `ui/src/lib/userDataApi.ts`; `RecentTab` client shell hydrates on mount
  with loading / error / empty states.
- v1 rows show date (parsed from the showId slug) + play count and **link
  to `/shows/[id]`** (the user's "show a little header info, then link to
  the show page"). Inline `playShow()` and real venue/city are deferred —
  see the show-metadata-enrichment decision above.
- Empty state: "Play something on any device to fill this in."
- First real API client wiring — establishes the pattern for Favorites.
- **Remaining**: enriched display (venue/city) once the API has a
  show-metadata source; optional inline play.

### 3. Favorites — shows — LANDED
- **Toggle already existed**: `FavoriteButton` on `/shows/[id]` uses
  `UserDataProvider.toggleFavorite` — optimistic update + `PUT`/`DELETE`,
  with cross-tab refetch from `/api/user/sync` on focus.
- **List added** at `/me/favorites`: `FavoritesTab` fetches the enriched
  `GET /api/user/favorites/shows` (reuses `getShowMeta`), pinned-first then
  most-recently-added, rows link to the show page. Read-only — the toggle
  lives on the show page, per this issue.
- Recent + Favorites now share `ShowRow` + `ShowArtwork` + `showFormat`
  helpers, modeled on the mobile show cards: square **ticket artwork**
  (ticket stub / photo → **Deadly logo**), then **date (primary) · city
  (secondary) · venue (tertiary)** — date + city are emphasized over the
  venue name, per design. Responsive grid (1/2/3 columns). The catalog
  index carries `image` + `bestRecordingId` (`make api-show-index`).
  - **Artwork fallback fixed (2f8a4c9b).** The chain was image →
    Archive.org thumbnail → logo, but `archive.org/services/img/<id>`
    returns a generic grey placeholder with HTTP 200 for art-less
    recordings, so `onError` never fired and the logo was never reached.
    Dropped the Archive.org source: real catalog image or the logo
    (rendered `object-contain` so it reads as an intentional mark),
    nothing in between.
- Lives at `/me/favorites` (not `/me/favorites/shows`); favorite *songs*
  (issue 4) can become a sub-view there later.

### 4. Favorites — songs — LANDED
- **Toggle** lives on track rows in the player's `TrackList` (used by the
  rail panel, the queue popover, and the fullscreen player — all three call
  sites pass `showId` + `selectedRecording`). A per-row heart appears on
  hover/focus and stays filled when favorited, so dense track lists don't
  look busy — resolves the open question below, mirroring the iOS passive
  indicator + player toggle. Identity is the `(showId, trackTitle)` tuple.
- **Context plumbing**: `UserDataContext` gains `isFavoriteTrack(showId,
  trackTitle)` + `toggleFavoriteTrack(track)`, implemented in
  `UserDataProvider` against `data.favorites.tracks` (optimistic local
  update + `PUT`/`DELETE`, same shape as `toggleFavorite`).
- **List** added as a **Shows / Songs segmented toggle** inside
  `/me/favorites` (`FavoritesTab` → `FavoriteSongsList`), per the issue-3
  note ("favorite songs can become a sub-view there"). Songs render as a
  flat list — track title (primary), show date · venue (secondary) — each
  row linking to `/shows/[id]`, with an inline unfavorite. Read-only beyond
  remove, like the shows list.
- **API**: `GET /api/user/favorites/songs` now enriched with `getShowMeta`
  (date/venue/city/image) like recent/favorites/reviews; `PUT` /
  `DELETE ?showId=&trackTitle=` unchanged. Round-trip verified against real
  synced data.
- **Song cards + play-and-jump** (`f7073f4b`): the songs list renders as
  ticket-art cards matching the other tabs (`ShowArtwork` + date/venue), with
  Play / Go-to-show / remove actions. A new player action
  `playShowTrack(show, title, number)` loads the show and jumps to the track
  once its tracks arrive (matches by title, then track number) — used by the
  Play action.
- **`?tab` persistence** (`f822a8e0`): the Shows/Songs sub-view is held in the
  URL (`?tab=songs`) via `useSearchParams`, so leaving and returning (e.g.
  opening a song's show page) keeps you on Songs. Page wraps the tab in
  `Suspense` (static-export requirement).

### Player — fullscreen playlist + interaction polish — LANDED
Not a numbered issue, but shipped alongside the favorites work since `/me`
leans on the player:
- **Click-to-expand** (`3bbd3391`, `c7943b0e`): clicking anywhere on the
  bottom bar that isn't an actual control (button / link / volume input / seek
  bar, tagged `data-no-expand`) opens the full player. Done with a single
  target-aware handler — an earlier per-cluster `stopPropagation` swallowed the
  empty space around the controls, leaving only the gap between clusters
  clickable.
- **Playlist in fullscreen** (`3bbd3391`, `9391c936`): desktop fullscreen
  gained a right-side playlist rail (mobile fullscreen already had the list).
  Shown whenever a *loaded* show is present (playing or parked) — a parked show
  loads its tracks without auto-playing via a new `ensureTracks()`, and picking
  a track while parked claims the session and starts there. The rail is chrome:
  when idle it fades **and** collapses its width so the ambient art re-centers.
- **Escape** collapses the expanded player.

### 5. Personalized signed-in home at `/` — REVERTED, NEEDS DESIGN
- A first cut (`PersonalizedHome` strip above the catalog: recents +
  favorites) was built and **reverted** — the layout didn't look good
  bolted on top of the existing home. The plumbing is sound (reused the
  enriched endpoints + shared cards), but the **home page wants real
  design work** before we reintroduce personalization: how the
  signed-in home is composed (replace vs. augment the catalog, hero
  treatment, section layout/density) is a design question, not just a
  data-wiring one.
- Kept from that attempt: shared card components moved to
  `components/show/` (used by `/me`); they'll be reusable when we redo this.
- **TODO: design pass on the signed-in home** before rebuilding. Sections
  still wanted (v1): "Pick up where you left off" (recents), "Your
  favorites". Future: recommendations, "more like…".

### 6. Settings page — LANDED
- `/me/settings` (`SettingsTab`): Account section (display name + email,
  read-only from session) with **Sign out**, and a **Delete account**
  danger zone.
- **Delete account** is confirmation-gated: a "Delete account…" button
  opens an inline confirm where you must **type `DELETE`** before the
  destructive button enables — not one-click. On confirm it calls
  `DELETE /api/user/account`, signs out, and returns home.
- **Tombstone, not hard-delete** (per decision): `accounts.deleted_at` is
  set; the row + user-data are retained but every auth getter filters
  `deleted_at IS NULL`, so a deleted account is rejected everywhere (401).
  **Signing back in reactivates** the account (adapter clears the
  tombstone) — avoids the UNIQUE-email collision and is a friendly
  soft-delete. Verified the create→delete→reject→reactivate lifecycle.
- **Follow-ups:** (1) a TTL/cron purge of long-tombstoned accounts and
  their orphaned user-data (hard delete). (2) editable display name. (3)
  the dev (credentials) sign-in path doesn't run the adapter's
  `linkAccount`, so dev reactivation isn't wired — dev-only, fine for now.

### 6b. Sync-version banner on `/me` — LANDED (50b3db5a, 30c1ce41)
- Resolves the empty-state open question (see below) via option (b) + a
  version floor. A dismissible banner across the whole `/me` section
  (`SyncVersionBanner`, in the layout) explains that favorites / recents /
  reviews sync from the app and require a minimum version.
- The floor lives in one constant, `ui/src/lib/syncVersions.ts`
  (`MIN_SYNC_VERSION` = iOS 2.32 / Android 2.31 — the first releases
  carrying the sync-push code). **Update it when the real release is cut.**
- Context: the apps are live, but App Store / Play review takes ~a week, so
  during rollout *every* existing app user is below the floor until the new
  build propagates — the banner is written for that, not as an edge case.

### 8. Reviews — tab + bidirectional sync — LANDED (30c1ce41, 7d584844, b35ae703, b0d00ad9)
- **Web tab** (`30c1ce41`): `/me/reviews` (`ReviewsTab`) fetches
  `GET /api/user/reviews`, now enriched with show display metadata
  (date/venue/city) server-side like recent/favorites. Each review renders
  as a show card with the overall rating + notes. Nav tab added to the
  `/me` layout.
- **Mobile sync** (`7d584844` Android, `b35ae703` iOS): reviews now sync
  **both directions**, mirroring the favorite-song stack — a `review`
  outbox kind enqueued at every write site (rating/notes/qualities +
  player-tag edits), flushed to `PUT /api/user/reviews/:showId`; deletes
  tombstone (soft-delete) and flush as `DELETE`. Pull merges with LWW by
  `updatedAt`. **Player tags travel with the review** (the server replaces
  all tags on every upsert, so omitting them would wipe them) — gathered on
  push, replaced on apply. Review reads filter tombstones; backfill
  includes reviews. See [mobile-server-sync.md](./mobile-server-sync.md).
- **Reactive indicator** (`b0d00ad9`): the playlist "you have a review"
  marker was a one-shot read, so a review arriving via sync didn't flip it.
  Now observed live — Android `getShowReviewFlow`, iOS `observeShowReview`.

### 9. Sorting / filtering controls on `/me` — LANDED (811069fd)
- Grew past plain sort/filter into a shared `<LibraryView>` backing all
  three tabs (Recent / Favorites / Reviews): hierarchical decade→year
  cascade filter, sort, and a list/grid toggle.
- Per-row actions ride along: list rows show inline icon buttons (Share,
  Pin/Unpin, Remove); the grid keeps the compact ⋯ overflow menu. Recent
  rows are Share-only.
- Resolved the TBD scope: **client-side** over the already-fetched lists
  (they're small), one **shared control component** rather than per-tab,
  no server support needed. `LibraryRail` was refactored onto the now-
  shared `DecadeCascadeFilter` so the rail and the views filter alike.
- Related share work shipped alongside: a QR / copy / native-share path
  on both `/me` rows and the show hero (7c514e1f, f180f217).

### 7. Anonymous event tracking on `/me` surfaces — IN PROGRESS (design)
- Cheap, no PII, no cookie banner. Answer the question that started this
  whole effort: "is anyone using this?" Without it, we ship blind.
- **The server pipeline already exists and already supports web.**
  `POST /api/analytics` (`api/src/routes/analytics.ts`) ingests batched
  `{event, ts, iid, sid, platform, app_version, props}`;
  `VALID_PLATFORMS` is `{ios, android, web}`. So issue 7 is mostly a *web
  client* + registering the `/me` events — not new server analytics.
- **Web is its own source via `platform: "web"`.** This is the platform
  dimension the admin dashboard already splits on (DAU/installs/growth by
  platform). We do **not** reuse mobile's `iid`; the web mints its own
  localStorage anonymous id, and `app_version` carries the web build id.
  Do not try to reconcile web ids with mobile IIDs.
- **Key handling (the one real design question).** Mobile sends a secret
  `X-Analytics-Key`; a browser bundle can't keep a secret. Plan: a
  dedicated `handle /api/analytics` block in the Caddyfile injects the key
  server-side (`header_up X-Analytics-Key {$ANALYTICS_API_KEY}`), so the
  browser never sees it and the API keeps requiring it. Rate-limiting +
  same-origin remain the spam gate (unchanged).
- **Events.** Lean on the existing generic `feature_use`
  (`feature`/`category`/`target_*` props, already bucketed by the summary)
  plus `app_open` for the visit, rather than inventing bespoke event types:
  - `app_open` on first `/me` load (platform=web) — feeds DAU/installs.
  - `feature_use { feature: "me_visited" | "favorites_viewed" |
    "recent_viewed" | "reviews_viewed", category: "navigation" }`.
  - `feature_use { feature: "favorite_added", category: "action",
    target_type: "show"|"song", target_id }` on a web favorite toggle.
  - (later) `signed_in_home_shown` once issue 5 lands.
  Any genuinely new event names must also be registered in
  `EVENT_SCHEMAS` (`api/src/db/analytics.ts`) and the watershed table with
  a `web:` reliable-from version.
- **Client shape.** Small `ui/src/lib/analytics.ts`: localStorage `iid`
  (+ per-tab `sid`), a `track(event, props)` that buffers and flushes a
  batch to `POST /api/analytics` on a timer / `visibilitychange`. Fire it
  from the `/me` layout + tabs and the favorite toggles. No cookie banner
  (anonymous, no PII).

## Out of scope (explicit non-goals)

- **Connect-v2 finishing.** Tracked separately on the `connect-v2`
  branch.
- **The reported pause bug on the web player.** Separate small ticket;
  awaiting repro details (browser, OS, in-page vs lock-screen).
- **Broader usability pass on the unsigned site.** Separate small epic
  after this one ships.
- **Downloads / offline / playlists.** Not part of v1.
- **Cross-device IID reconciliation.** Anonymous web ID stays anonymous.

## Open questions

- ~~**Empty-state handling before sync is live.**~~ **RESOLVED** via
  option (b) + a version floor — see issue 6b. Shipped with the
  `SyncVersionBanner` explaining favorites/recents/reviews sync from the
  app and the minimum version required.
- ~~**Favorites toggle UI on track rows.**~~ **RESOLVED** (issue 4). The
  per-row heart is hidden until row hover/focus and only stays visible when
  the track is favorited, so it doesn't clutter dense lists — the web take
  on iOS's passive indicator + player-level toggle.

## How to track progress

This doc IS the tracker. One Linear ticket points here. As work lands,
strike through the relevant section heading and link the merging PR
inline.
