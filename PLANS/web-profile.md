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
- **No new server endpoints.** Everything consumed from existing
  `/api/user/*` surface. If something's missing, file a separate issue;
  don't grow this epic.
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

### 1. Profile route shell — `/me` with nav
- Auth-gated layout at `ui/src/app/me/`.
- Tabs: Recent / Favorites / Settings. Pure layout, no data wiring.
- `UserMenu.tsx` gets a "Your library" (or similar) link to `/me`.
- Redirect signed-out users to `/signin?callbackUrl=/me`.

### 2. Recent shows on web
- `GET /api/user/recent` → list of recent shows.
- Read-only list, click row to play (reuses existing `playShow()` from
  `PlayerProvider`).
- Empty state copy explains: "Play something on any device to fill this
  in" (acknowledges the mobile-sync dependency).
- First real API client wiring — establishes the pattern.

### 3. Favorites — shows
- `GET /api/user/favorites/shows` → list view at `/me/favorites/shows`.
- Heart icon on `/show/[id]` (`ShowDetail`) toggles favorite via
  `PUT`/`DELETE`.
- Optimistic update on click; reconcile from server response.

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
