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
- Recent + Favorites now share `ShowRow` + `showFormat` helpers.
- Lives at `/me/favorites` (not `/me/favorites/shows`); favorite *songs*
  (issue 4) can become a sub-view there later.

### 4. Favorites — songs
- Same pattern as #3 applied to track rows in the player's track list
  and/or ShowDetail.
- `GET /api/user/favorites/songs`, `PUT`/`DELETE`.

### 5. Personalized signed-in home at `/`
- When `user` is present, render the personalized home; otherwise the
  current marketing/landing home.
- Sections (v1):
  - "Pick up where you left off" — top of `/api/user/recent`.
  - "Your favorites" — sample from `/api/user/favorites/shows`.
- Future sections (deferred): "More like shows you favorited",
  recommendations, etc.

### 6. Settings page
- Display name (read-only for v1 unless trivial to make editable).
- Sign out.
- "Delete my account" with a confirm. Hits whatever the existing account
  surface supports; if there's no DELETE endpoint, file a separate issue.
- Anything else lives under iOS Settings doesn't necessarily translate;
  resist scope creep.

### 7. Anonymous event tracking on `/me` surfaces
- Cheap, no PII, no cookie banner. Events worth tracking:
  - `me_visited`, `favorites_viewed`, `recent_viewed`,
    `favorite_added` (web), `signed_in_home_shown`.
- Answer the question that started this whole effort: "is anyone using
  this?" Without it, we'll ship and not know.
- localStorage-generated anonymous ID is fine; do not try to reconcile
  with mobile IID.

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

- **Empty-state handling before sync is live.** If we ship the web
  profile before mobile-sync is on by default, every signed-in user sees
  empty lists. Options: (a) gate `/me` link behind a feature flag until
  sync ships; (b) ship anyway with friendly empty-state copy
  ("Connect your phone to fill this in"). Worth a call when we get there.
- **Favorites toggle UI on track rows.** Per-track heart icons can get
  visually busy. Look at iOS for the right pattern before designing the
  web one from scratch.

## How to track progress

This doc IS the tracker. One Linear ticket points here. As work lands,
strike through the relevant section heading and link the merging PR
inline.
