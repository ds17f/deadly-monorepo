# Web UI redo — the three-pane "app shell"

Rebuild the web app's layout from a centered, single-column marketing site
into a full-bleed, three-pane application shell modeled on the Spotify web
player. The mockups at **`/mockup`** are the visual spec — this plan turns
those throwaway prototypes into the real, data-wired UI.

**Reference mockups** (committed `366209da`, route group `ui/src/app/mockup/`):

| Route | Shows |
|-------|-------|
| `/mockup` | Signed-in three-pane: Library / browse / get-the-app + transport |
| `/mockup/logged-out` | Gated library, signup banner, free browse |
| `/mockup/show` | Show detail: album hero + setlist tracklist + AI **liner-notes** rail |
| `/mockup/mobile` | Liner-notes rail collapsed into tap-to-expand sections in a phone frame |

These are self-contained, hardcoded, and **not wired into the app**. They
stay in the tree as the living spec until the real shell reaches parity,
then get deleted.

## Why this exists

Two threads converge here:

1. **People use the website.** ([web-profile.md](./web-profile.md)) The
   signed-in experience is barely distinguished from anonymous. We've been
   adding `/me` surfaces (Recent/Favorites/Reviews) as *tabs*, but tabs
   bolt a library onto a marketing page. Spotify's three-pane shell makes
   the library a *persistent rail* — it's always there, it's the point.

2. **The detail page holds content nothing else has.** Each show carries an
   AI review (blurb, long-form review, key highlights, must-listen
   sequences, per-member band performance), the full setlist with song
   highlights, the recording picker, and the lineup
   (`ui/src/components/ShowReview.tsx`, `Setlist.tsx`, `Lineup.tsx`;
   `AiShowReview` in `ui/src/types/show.ts`). Today it's stacked in a
   2/3 + 1/3 grid. In the new shell this becomes a persistent **"liner
   notes" right rail** — the thing that makes The Deadly worth opening on
   the web even after the mobile app does playback. **Spotify structurally
   cannot show this; it's our differentiator.**

## The target layout

```
┌────────────────────────────────────────────────────────────────┐
│ top bar: logo · (global search) · auth/avatar                  │
├──────────────┬───────────────────────────────┬────────────────┤
│ LEFT          │ MIDDLE                        │ RIGHT           │
│ Your Library  │ scrollable content panel:     │ context rail:   │
│ (persistent)  │  - home / browse, OR          │  - get-the-app  │
│  filter pills │  - show detail (album hero +  │  - now-playing  │
│  list of      │    setlist tracklist + prose) │  - OR liner     │
│  shows        │                               │    notes (on a  │
│               │                               │    show page)   │
├──────────────┴───────────────────────────────┴────────────────┤
│ bottom transport bar (signed in) / signup banner (signed out)  │
└────────────────────────────────────────────────────────────────┘
```

Each pane is a rounded dark panel (`bg-deadly-surface`) floating on a black
gap background — the treatment that gives the Spotify shell its look. Uses
existing design tokens (`deadly-bg #121212`, `deadly-surface`,
`deadly-accent` crimson, `deadly-star` gold) from
`ui/src/app/globals.css`.

## What changes vs. today

| Surface | Today | Target |
|---------|-------|--------|
| Root layout | `max-w-5xl` centered `<main>` + nav/footer (`ui/src/app/layout.tsx`) | Full-bleed flex shell: top bar + 3 panes + bottom bar |
| Library | `/me/*` tabs (`ui/src/app/me/layout.tsx`) | Persistent left rail, always visible |
| Home | Centered 2/3 + 1/3 grid (`HomeContent.tsx`) | Middle content panel |
| Show detail | Centered 2/3 + 1/3 grid (`shows/[id]/page.tsx`) | Album hero in middle + liner-notes right rail |
| Player | `HeaderPlayer` in nav (`components/player/HeaderPlayerWrapper.tsx`) | Bottom transport bar |
| Get-the-app | `GetTheApp` sidebar widget | Right rail (home) / footer banner (logged out) |
| Logged out | Same as signed-in, minus name | Gated left rail + signup banner + free browse |

## Decisions already made

- **Full-bleed shell, not a wider container.** The marketing `max-w-5xl`
  constraint is dropped for the app shell. The shell owns the viewport
  (the mockups use `fixed inset-0`; the real one will use a flex column
  that fills the screen with internally-scrolling panes).
- **Browse stays public; saving needs auth.** (From the logged-out mockup.)
  Anonymous users get the full catalog, search, top-rated, and playback.
  The left rail and per-card favorite/heart prompt login; the bottom bar
  becomes a signup banner. This is the Spotify conversion model and it's
  already how our data model works (favorites/reviews are user-scoped).
- **The AI liner-notes rail is the differentiator.** The right rail on a
  show page surfaces the existing `AiShowReview` content. On mobile it
  collapses to tap-to-expand sections (`/mockup/mobile`).
- **Reuse, don't rewrite, the data layer.** `ShowRow`, `ShowArtwork`,
  `HeaderPlayer`/`PlayerProvider`, `UserDataProvider`, the `/me` fetchers
  (`ui/src/lib/userDataApi.ts`), and the show JSON loaders (`lib/shows.ts`)
  all carry over. This is a **layout** redo, not a data redo.

## Parity requirements (don't lose content in the move)

- **Full long-form review must come along.** The mockups show the AI
  *blurb*/summary but drop the long-form `review` field
  (`AiShowReview.review`). The real liner-notes "About this show" section
  must render the full review, not just the blurb. (Noted by Damian on the
  mockup review — "easy to add back in.")
- **Band performance, must-listen sequences, key highlights, best-recording
  pick, lineup** all carry over (they're in the mockup rail already).
- **Setlist song highlights** (`song_highlights`) keep their treatment as
  the tracklist HIGHLIGHT markers.
- **Recording picker** (`RecordingSelector`/`ShowActions`) must remain
  reachable from the album hero's source chip.

## Hard constraints to respect

- **Static export.** The site is Next `output: export`
  (`generateStaticParams` over 2,300+ shows). The shell must stay
  compatible with static prerender + client hydration. Per-user panes
  (left library, now-playing) are client components that hydrate from the
  API — exactly the model [web-profile.md](./web-profile.md) already
  committed to.
- **Player already exists.** `PlayerProvider` + `HeaderPlayer` work today.
  The bottom transport bar is a *re-skin/reposition* of `HeaderPlayer`, not
  a new player. Don't fork playback state.
- **Responsive collapse is required.** Three panes only work on wide
  viewports. Below `lg`, panes must collapse: left rail → drawer/bottom
  tab, right rail → below content (show page) or hidden (home). The mobile
  mockup is the phone-width end state.

## Open questions

- **Left rail content when the library is empty / pre-sync.** Most web
  users have no synced data yet (depends on mobile-server-sync rollout).
  Empty-state = the logged-out gated prompts? Or "open the app to sync"?
- **Does the middle panel route, or swap in-place?** Spotify keeps the
  shell mounted and swaps only the middle pane (SPA feel). With Next
  static export + app router, do we get that via client navigation, or
  accept full-page transitions per route? Affects whether the player
  survives navigation without a global provider already in the layout (it
  is — `PlayerProvider` wraps everything).
- **Right rail on non-show pages.** On home, it's get-the-app + now-playing.
  On a show, it's liner notes. On `/me`, it's…? (profile/social, per
  web-profile.md). Needs a per-route slot strategy.
- **Search placement.** Mockup puts a global search in the middle's sticky
  top bar; today search is a homepage filter (`SearchFilter.tsx`). Promote
  to a global shell search, or keep per-page?
- **Do we keep `/me` tabs at all,** or does the left rail fully replace
  them? (Likely: left rail *is* the library; `/me` keeps profile/social.)

## Proposed phasing

Each phase should be independently shippable and not regress the current
site. Recommend building behind a route or flag until the shell reaches
parity, rather than swapping `layout.tsx` on day one.

1. **Shell skeleton.** Build the full-bleed flex shell as a layout
   (top bar + 3 empty panes + bottom bar) with responsive collapse.
   Wire the existing `HeaderPlayer` into the bottom bar. No content yet.
2. **Middle = home.** Move `HomeContent` into the middle pane. Verify
   browse/search/top-rated/year-timeline still work full-bleed.
3. **Left rail = library.** Render Recent/Favorites/Reviews/Pinned from
   the existing `/me` fetchers as the persistent rail with filter pills.
   Empty + logged-out states (resolve the open question first).
4. **Right rail = get-the-app + now-playing** on home.
5. **Show detail in-shell.** Album hero (cover, date/venue, rating + AI
   rating + source badges, play + actions), setlist-as-tracklist, full
   long-form review inline. **Right rail = liner notes** (all AI content,
   incl. full review — parity requirement above).
6. **Logged-out conversion.** Gated left rail, per-card heart→login,
   signup banner in place of the transport bar.
7. **Responsive / mobile.** Pane collapse; liner-notes → collapsible
   sections; verify the phone-width end state matches `/mockup/mobile`.
8. **Cutover + cleanup.** Replace the marketing layout, delete the
   `/mockup` route group, retire the old `/me` tab chrome where the rail
   supersedes it.

## Collections — parked (revisit)

Collections matter, but they're **hidden on the home for now**
(`COLLECTIONS_ENABLED = false` in `HomeDiscovery.tsx`; data stays wired).
Two things block doing them justice:

1. **No card treatment.** Collections aren't shows — they have no cover
   art, so they render as bare chips that read as "lost" next to the
   cover-art carousels. Want a **"box set" icon** to use as the card art so
   a Collections carousel matches the others visually.
2. **No landing page.** There's no per-collection detail/route on web yet
   (mobile has `CollectionRoute.detail`), so a collection card has nowhere
   good to link — the temporary `/?collection=<id>` hook is a no-op.

When picked up: add a box-set-icon collection card, a `/collections/<id>`
(or in-shell) detail surface listing the collection's shows, then
re-enable the home carousel and wire the cards to it.

## Relationship to other plans

- **[web-profile.md](./web-profile.md)** — the `/me` surfaces, endpoint
  hydration, and show-metadata enrichment are prerequisites/inputs. This
  plan is the *layout* those surfaces live in. The "issue 9: sorting/
  filtering controls" work folds into the left-rail filter pills here.
- **[mobile-server-sync.md](./mobile-server-sync.md)** — gates the *value*
  of the left rail (empty until phones push data). Same dependency
  web-profile already documents.
- **Mobile app** — the liner-notes content proves out a future additive
  feature for the apps (AI review sections under the show screen). Not in
  scope here, but `/mockup/mobile` is the reference if/when we do it.

## Status

- **2026-06-01** — Mockups built and committed (`366209da`). This plan
  written. No shell implementation started yet. Next: decide phase-1
  scope (shell skeleton behind a route vs. flagged in `layout.tsx`) and
  resolve the empty-left-rail open question.
- **2026-06-02** — Shell is **built and live**. The empty-rail and
  behind-a-flag open questions were resolved in the building: the shell
  ships directly in `layout.tsx` (`AppShell` wraps everything — no flag),
  and the empty left rail shows gated prompts when logged out / an
  "open the app to sync" hint when empty. Phase-by-phase:

  | # | Phase | State |
  |---|-------|-------|
  | 1 | Shell skeleton (top bar · 3 panes · bottom transport, responsive) | ✅ done & live — `components/shell/AppShell.tsx` |
  | 2 | Middle = home | ✅ done — `HomeContent` in the middle pane |
  | 3 | Left rail = library | ✅ done — live `/me` fetchers, hierarchical decade→year cascade filter, labelled sections (Recently Played / My Reviews / Favorites), gated/loading/empty/error states (`LibraryRail.tsx`); see "Library-rail rework" on the board |
  | 4 | Right rail on home (get-the-app + now-playing) | ❌ not done — `GetTheApp` is still inline in `HomeContent` (~line 175); home renders no right pane |
  | 5 | Show detail in-shell + liner-notes rail | ✅ mostly — `shows/[id]/page.tsx` uses `RightRailSlot` → `ShowLinerNotes`; ⚠️ verify full long-form `review` parity |
  | 6 | Logged-out conversion | 🟡 partial — gated rail done; signup-banner-replacing-transport not done |
  | 7 | Responsive / mobile | ✅ done — pane stacking; liner-notes stay always-expanded on mobile (collapse explicitly not wanted) |
  | 8 | Cutover + cleanup | 🟡 cutover done & live, but `/mockup` still in tree and old `/me` tabs still present |

  Cross-cutting gap: **global search isn't wired** — the top-bar search
  box is a placeholder `Link` to `/#browse` (see `AppShell.tsx`).

  Right-rail infra (`RightRail.tsx`) shipped with two layers: a page slot
  (`RightRailSlot`, used by the show page for liner notes) and a player
  override (`useRightRailOverride`, used by `HeaderPlayer` for the
  queue/device picker) — the player layer wins when present.

  **Remaining work:** phase 4 (home right rail), global search, logged-out
  signup banner, cleanup (delete `/mockup`, retire superseded `/me` tabs),
  and the two parity verifications (long-form review, mobile liner-notes
  collapse). Most self-contained next step: phase 4.

- **2026-06-02 (phase 4 design — in progress)** — The home right rail is
  not "get-the-app + now-playing"; it becomes the **dynamic discovery
  column**, porting the mobile home rails (`HomeScreen.swift`): Today in
  Grateful Dead History, Trending, Fan Favorites. Driving principle that
  resolves the SEO tension: **static/build-time content → middle window
  (the SEO surface); dynamic/analytics/personal content → rails.** So:

  - **Middle (static, SEO):** show catalog/browse, Top Rated, Featured
    Collections — `TopRatedShows` + `CollectionsGrid` move OUT of the old
    nested `lg:grid-cols-3` sidebar inside `HomeContent` into the middle
    content flow (dissolves the sidebar-inside-a-pane).
  - **Right rail (dynamic, client-hydrated, non-SEO):** new
    `DiscoveryRail` — TIGDH (client-side date match on the in-memory
    `showIndex`, "this week" fallback when empty), Trending
    (`/api/trending` `now`/24h window), Fan Favorites (`/api/popular`
    decade pools), demoted get-the-app card at the bottom. Web is the
    FIRST web consumer of `/api/trending` + `/api/popular` (both
    return bare `show_id`s; resolved against the `showIndex` already
    hydrated into `HomeContent` — no enriched endpoint needed for v1).
  - **Now-playing card deferred** — bottom transport + player's
    `useRightRailOverride` (queue/devices) already cover it.

  **Rail behavior across states (the priority stack already in
  `RightRail.tsx`: player override > page slot > empty):**

  | State | Left rail | Right rail |
  |-------|-----------|------------|
  | Home, logged out | gated prompts | DiscoveryRail + demoted get-the-app |
  | Home, logged in | library | DiscoveryRail (now-playing later) |
  | Show page | unchanged | liner notes (built) — replaces discovery |
  | Playing + queue open | unchanged | player override wins (built) |

  Left rail is **persistent nav** — constant across routes; only its
  content swaps by auth. Right rail is route-driven page slot.

  **Mobile lead:** on mobile web the discovery rail must *lead*, so the
  slot carries a **placement hint** (`above` | `below`, default `below`);
  `AppShell` applies flex `order` on mobile only. Home opts into `above`;
  show pages keep the default (liner notes below content).

  **Card treatment (revised after first cut):** the rail is narrow (280–
  360px), so each unit is a **vertical list of `ShowRow` cards** (the
  shared library card — cover · date · location), NOT a carousel. The
  first cut used horizontal carousels mimicking mobile; they crammed to
  ~2 cards in the rail. *Pinned follow-up:* carousels would suit a future
  **main-window** discovery treatment (more horizontal room).

  **Pane scroll:** the shell is ALWAYS a fixed `h-[100dvh]` column — the
  document itself never scrolls, on any width. Desktop: each pane scrolls
  internally (`lg:min-h-0 lg:overflow-y-auto`, the flexbox
  `min-height:auto` gotcha). Mobile: the stacked panes are ONE scroll
  region (`overflow-y-auto` on the panes container, `lg:overflow-hidden`
  to hand scrolling back to the panes on desktop). Because header +
  transport sit OUTSIDE the scroll region, they're pinned without
  `sticky`, and **wheeling over the player scrolls nothing** — the
  earlier whole-document mobile scroll let the player area page the view.
  Verified with Playwright at desktop + sub-`lg` widths.

  **Rail rows:** borderless entries (ticket tile · date · location, hover
  highlight) matching the left library rail — NOT the bordered `ShowRow`
  card, which crowded the narrow rail.

- **2026-06-02 (global search — shipped)** — The open "search placement"
  question is resolved: a real global shell search, client-side, song- and
  member-aware (mobile FTS parity). Architecture, all client/no server:
  - **Prebuilt artifact:** `scripts/build-search-index.mjs` (a `prebuild`
    hook) emits `public/search-index.<dataVersion>.json` from the catalog —
    dictionary-encoded songs + lineup members + source tags + date/venue/
    city/state. ~542 KB raw / ~93 KB gzip. Gitignored (rebuilt each build).
  - **Load/cache:** `lib/searchClient.ts` lazy-loads on first search focus,
    builds a MiniSearch index, and caches the serialized index in IndexedDB
    keyed by `NEXT_PUBLIC_DATA_VERSION` (exposed via `next.config.ts` from
    `data/version`). Verified: fetched once, **zero refetch on reload**;
    new data version invalidates.
  - **Query:** AND-combined, prefix + light fuzzy, boosted venue/song/
    member; source-tag synonyms (soundboard→SBD). "Brent Mydland Dark Star"
    → the 8 shows with both (all within Brent's 1979–90 tenure).
  - **UI:** `components/shell/SearchBox.tsx` in the top bar — live dropdown,
    keyboard-nav, song/member match get a hint chip, links to the show.
  - **Follow-ups:** ~~mobile search entry~~ (done — fills the top bar on
    phones); ~~richer date-format variants~~ (done — `dates` field emits
    "5-8-77"/"may 8" tokens); lineup is core members only (12) — no guest
    sit-ins (same as mobile).

  **Status: shipped on `feat/mobile-server-sync`** (v1). Verified desktop
  + mobile via Playwright screenshots (`~/.cache/ms-playwright` chromium).

  **Known follow-up:** the proper fix for ID→metadata is an enriched
  trending/popular response (like the `/me` fetchers) rather than leaning
  on the home-page `showIndex` — the "show-metadata API source is the
  shared gap" called out in [web-profile.md](./web-profile.md).

---

### ==== CURRENT STATE — 2026-06-02 session end (authoritative) ====

Supersedes the phase table and earlier dated entries where they conflict.
The shell is live; the homepage was substantially rebuilt this session.
Commits: `7c6c6b7e..e2092eed` (~21 commits) on branch
`feat/mobile-server-sync`.

**Homepage** (`components/home/HomeContent.tsx`), MIDDLE pane top→bottom:
1. `HeroSection` — trimmed to headline + one paragraph ("what this site
   is"). The 3 feature blocks were removed (that story is now the rail's
   mission). Still a touch tall; trimming further is optional.
2. `HomeDiscovery` — the discovery **carousels** (the big change: moved
   OUT of the right rail INTO the wide middle as cover-art horizontal
   scrollers): Recently Played (signed-in) · Today in GD History ·
   Trending · Fan Favorites · **Collections (parked/hidden)**.
   - `ShowCarousel.tsx` = reusable carousel: hidden scrollbar, native
     horizontal wheel, full-height fade-in ‹ › gutter arrows (on hover,
     only when overflowing, gated by scroll position). Also exports
     `showToCarouselItem`/`fmtCarouselDate` (shared item mapping).
   - Trending/Fan-Favorites/Recently-Played hydrate from
     `/api/trending`,`/api/popular`,`/me`; TIGDH from the
     in-memory `showIndex`; bare `show_id`s resolved against it.
3. "Browse all 2,313 shows" = year graph (`YearTimeline`) → **Top Rated
   carousel** → `SearchFilter` → `ShowList` (the static **SEO surface**).
   - **Top Rated moved here** (was a `HomeDiscovery` carousel): it lives
     under the graph because it tracks the active search + year/decade/
     source filters (top 15 of `filtered`), so it belongs *with* the
     controls that drive it. Verified it re-ranks on filter (search "1977"
     → all-1977).
   - In-page text search is now **song/member/venue/date aware** via the
     shared `searchClient` MiniSearch index (>=2 chars; substring fallback
     otherwise). Searching anchors the section to the top of the pane.

**Home RIGHT rail** = `components/home/HomeRightRail.tsx` (via
`RightRailSlot mobilePlacement="above"` so it leads on mobile): Now
Playing (when active) · Get the app (badges + desktop QR) · **Our
Mission** (real app copy) · **Donate to the Internet Archive**. The
"discovery in the right rail" idea is DEAD — discovery is carousels now.
`DiscoveryRail.tsx` was deleted. **Mobile (2026-06-02):** the rail leads
the homepage and is kept focused on conversion — **Our Mission + Donate
are `hidden lg:block`** (desktop only), so phones see Get-the-app at the
top, then the hero/carousels.

**Store badges are platform-aware** (`components/home/StoreBadges.tsx` +
`lib/usePlatform.ts`): a phone sees only its store (iOS → App Store,
Android → Google Play); desktop/unknown sees both. `usePlatform` returns
`null` until mounted (server + first client render show both → no hydration
mismatch), then narrows by UA. `GetTheApp` (home rail + `ShowAppCta` show
rail) and the show-page `ShowNav` all render through it.

**Show page mobile nav** (`ShowNav.tsx`): the compact `‹ Listen Now ›`
center button is gone — web playback is the hero Play button now, so the
center slot is the **relevant store badge** (`‹ [App Store] ›`). Prev/next
chevrons unchanged.

**Mobile header-loss fix** (`AppShell.tsx`): tapping a show from the home
on a phone dropped the whole top nav (logo/search/profile) every time. Root
cause: the shell was an **in-flow** `h-[100dvh]` flex column, so a mobile
browser could scroll the entire shell under the URL bar on navigation (the
`html{overflow:hidden}` lock doesn't stop body/visual-viewport scroll on
iOS). Fix: the shell is now **`fixed inset-x-0 top-0 h-[100dvh]`** — pinned
to the viewport so the header/transport physically can't scroll away, while
`h-[100dvh]` still tracks the dynamic (URL-bar) viewport. (Matches the
`fixed inset-0` the mockups used.) **Confirmed fixed on-device** (the top
nav no longer drops when tapping a show from the home); wasn't reproducible
headless (treats `dvh==vh`). Also kept a `scrollTo(top:0)` on `pathname`
change so shows open at the top after a scrolled-down home.

**Global search** (`SearchBox.tsx` + `lib/searchClient.ts` + prebuilt
`public/search-index.<ver>.json` via `scripts/build-search-index.mjs`
`prebuild` hook): shipped, song/member/venue/date aware, IndexedDB-cached
keyed by `NEXT_PUBLIC_DATA_VERSION`, viewport-centered, dropdown caps at
20 with a "Showing N of TOTAL — keep typing to narrow" footer. **Now on
mobile too:** the box grows to fill the top bar on phones (`flex-1`,
`sm:flex-none`); the "The Deadly" wordmark is hidden under `sm` and the
header spacers are `sm:flex-1` (desktop-only centering) to make room.

**Cover-art fallback** is consistent everywhere: square stealie
`public/cover-fallback.png` (vendored from iOS `deadly_logo_square`),
`object-cover` + rounded — carousels, both rails, `ShowArtwork`, show
hero, player (mini + fullscreen). Round `/logo.png` is reserved for the
brand wordmark and the OG/Twitter **share image only** (`resolveCoverImageUrl`
in `shows/[id]/page.tsx` still returns the logo for OG, by design).

**Store badges:** `public/google-play-badge.png` was trimmed of its 41px
transparent padding to size-match Apple; both render `h-10 w-auto`.
`GetTheApp.tsx` renders both inline.

**Show page** (`shows/[id]/page.tsx`): app CTA moved to the TOP of the
right rail above Key highlights — `ShowAppCta.tsx` (badges only; the QR is
lower, in `ShowLinerNotes`'s "Open in App"). Bottom `ShowActions` lost its
badges (keeps in-app deep link + Archive.org).

**Shell scroll model (app-wide, important):** `AppShell` is a fixed
`h-[100dvh]` column; the document never scrolls. Desktop = 3 panes scroll
internally; mobile = stacked panes are ONE scroll region. Header +
transport sit outside it (pinned, no `sticky`). `AppShell` sets `html{
overflow:hidden }` on shell routes via `useEffect` (restored on bare
routes `/signin`,`/auth`,`/admin`) — that's what stops wheeling over the
player from paging the document. VERIFY viewport/scroll in a real
browser, not just headless — headless Chromium treats `dvh==vh`.

**Volume:** defaults mid-range (`DEFAULT_VOLUME = 0.5` in
`PlayerProvider.tsx`; stored `0`/missing treated as unset, recovering
users the old `Number(null)===0` bug muted).

**Verification:** Python Playwright + cached Chromium available
(`~/.cache/ms-playwright`); stack on `localhost:8080` after `make
ui-build && make docker-redeploy`. Gotcha: an invalid `/shows/<bad-slug>/`
serves the home shell (not 404) — confirm the slug renders real content.

**The board (next):**
- ~~**Logged-out signup bar** (phase 6)~~ — **cut (2026-06-02).** Decided
  against the bottom conversion bar; logged-out users keep the normal
  transport. Phase 6 is dropped from scope.
- **Collections** (parked) — see section above; needs a box-set-icon card
  + a `/collections/<id>` detail surface, then flip `COLLECTIONS_ENABLED`
  in `HomeDiscovery.tsx`.
- ~~**Mobile search entry**~~ — **done (2026-06-02).** `SearchBox` now fills
  the top bar on phones (wordmark hidden under `sm`); same live dropdown.
- ~~**Browse-search song/member upgrade**~~ — **done (2026-06-02).** The
  "Browse all" filter now resolves queries (>=2 chars) through the shared
  `searchClient` MiniSearch index (`HomeContent.tsx`: async `searchHits`
  set; substring fallback for single-char / index-loading / load-failure so
  the list never flashes empty). Placeholder updated to "Search shows,
  songs, or band members...". Verified via Playwright: Scarlet Begonias→315,
  Brent Mydland→812, Dark Star→216 (all 0 under the old substring filter);
  Fillmore→81 venue match unregressed. **Scroll anchor:** searching now
  smooth-scrolls the Browse-all `<section>` (ref + `scroll-mt-4`) to the top
  of the pane so a shrinking result list no longer jumps the viewport. Done
  as an **instant** `scrollIntoView({block:"start"})` from an effect keyed on
  `[searchQuery, searchHits]` (NOT a smooth scroll in the change handler — that
  animated against stale layout and drifted, re-anchoring after the async hits
  resolve). The section also takes `min-h-[100dvh]` *while a query is active* —
  without it a short/empty result set leaves the column too short to scroll the
  heading to the top (it stranded mid-screen at y≈395 on 0 results). Verified:
  heading pins to y≈73 (top, under the bar) at 0 / many / member queries, even
  with char-by-char typing.
  - **Numeric-token fix (shared `searchClient`):** `fuzzy` is now disabled for
    all-digit tokens — edit-distance-1 fuzz had turned "1982" into
    1980/1983/1992/1882, so "Brent 1982" matched ~717 shows (all Brent-era
    years). The top-bar search hid this behind its 20-row cap; browse exposed
    it. Now "Brent 1982" → 61 = "1982" → 61 (AND-correct). Both surfaces share
    the options, so they're consistent.
  - **Free-text date variants (shared `searchClient`):** the stored ISO date
    only tokenized to 1977/05/08, so "5/8/77", "5-8-77", "may 8 1977" missed.
    Added a `dates` field derived client-side in `toSearchDocs` (no artifact
    change): emits leading-zero-less month/day, 2-digit year, and month name as
    loose tokens (MiniSearch splits on the separators, so delimiter/order don't
    matter). Bumped a `SCHEMA` tag in the IndexedDB cache key to invalidate the
    old serialized index. Verified: 5/8/77 = 5-8-77 = may 8 1977 →
    1977-05-08 Barton Hall (Cornell); 8/27/72 → Veneta OR.
- ~~**Library-rail rework**~~ — **done (2026-06-03, `1530a343`).** Replaced
  the flat kind-toggle pills with the native Search screen's hierarchical
  cascade (`HierarchicalFilterChips.swift`): `All` → `60s/70s/80s/90s` →
  (70s/80s) `Early/Late` → individual year, drilling on tap and popping back
  when the selected node is tapped; a leaf collapses to a `70s > Late 70s >
  1977` breadcrumb chip. Chips **wrap across rows** (not horizontal-scroll) to
  fit the 280px rail; active chip is `deadly-accent` red. The merged list is
  now three labelled `<section>`s — **Recently Played / My Reviews /
  Favorites** — and the filter narrows all three at once (year parsed from
  `date ?? showId`). Privacy + GitHub links moved off the rail footer into the
  `UserMenu` dropdown (`UserMenu.tsx`). Note: section is "Favorites", not
  "Library", to avoid doubling the "Your Library" rail title.
- **Cleanup** (phase 8) — delete `/mockup`; retire superseded `/me` tabs.
- **Verify** — long-form review parity ✅ met (`ShowReview` "About this show"
  shows the blurb + the full `review.review` behind "Show more"). Mobile
  liner-notes: **decision — keep the panels always-expanded** on mobile (it
  reads fine; do NOT add tap-to-expand collapse — tried it, not wanted).
