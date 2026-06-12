# UI Decluttering — Settings + Player/Playlist Controls

**Status:** Design in progress (not yet an ADR, not yet built). 2026-06-12.
**Goal:** Reduce UI clutter that's accreted as features shipped. Settings is a
scroll-forever flat list (nightmare on mobile); the ∞ Autoplay addition crowded
the player; playlist action rows duplicate everything into the overflow. Be
deliberate — establish first principles, capture them as an **ADR**, then use it
to guide implementation across iOS + Android (+ web for Settings parity).

This doc is the recovery point. When the design is settled, it becomes
**ADR-0014** (next free number in `docs/adr/`; ADR-0010 = auto-advance/queue is
the relevant neighbor for Queue's eventual return).

---

## First principles (agreed)

1. **A control earns its altitude by frequency × reversibility.** Set-once config
   (card sizes, decade, EQ, control-style) must not sit adjacent to per-use
   actions (Favorite, Queue, Share).
2. **Configure where you consume.** A rail's size/window/decade belongs on/near
   the rail, not mirrored into a distant mega-list. (Tempered by the Settings
   decision below — we chose a consolidated Home Layout screen, not fully
   in-place rail controls.)
3. **One control, one home.** Nothing appears both inline and in an overflow.
   (The ∞ violates this today; the playlist overflow re-lists ~5 inline icons.)
4. **The player/playlist surface is transport + "this show."** Modes and config
   live in the ⋯ menu; per-show actions stay inline.
5. **Progressive disclosure beats a flat list.** Settings → a short landing of
   stable categories, each its own screen.
6. **Parity is the spec.** Same information architecture on iOS/Android (+ web
   for Settings). Density can differ; structure can't. Web Settings being
   "sparse" is a *missing* IA, not a clean one.

---

## Current-state inventory (as built on `main`, 2026-06-12)

### Full player
- **iOS** `iosApp/deadly/Feature/Player/PlayerScreen.swift`
  - `header` (~L290): ⌄ back · "Now Playing"/album (tap→show) · ⋯ (`playerMenuSheet` ~L415)
  - track-info row: NO ＋ stub on iOS
  - transport: ⏮ ◁◁ ▶/⏸ ▷▷ ⏭
  - `actionButtons` (~L337): cast(airplay) left · right cluster = ∞ Autoplay · EQ · ♥ Fav · Share. NO Queue.
  - `playerMenuSheet` ⋯: Autoplay · Favorite · EQ · Share (duplicates inline)
  - `toggleAutoAdvance` ~L475
- **Android** `androidApp/feature/player/.../screens/main/PlayerScreen.kt`
  - top bar `PlayerTopBar`: ← · "Playing from Show" (tap→playlist) · ⋯ → `PlayerTrackActionsSheet`
  - `PlayerTrackInfoRow.kt`: title/date/venue + **＋ onAddToPlaylist** → "Playlists are coming soon" toast (DEAD STUB)
  - `PlayerEnhancedControls.kt`: transport
  - `PlayerSecondaryControls.kt`: cast left · right cluster = ∞ Autoplay · EQ · ♥ Fav · Share · ≣ Queue (STUB)
  - `PlayerTrackActionsSheet` ⋯: Share · Add to Playlist(soon) · Download · Favorite · Autoplay · EQ · Queue (duplicates inline)
- **Drift:** Android has ＋ and ≣ Queue stubs; iOS has neither.

### Mini-player — ALREADY FINE, leave as-is
- iOS `iosApp/deadly/Feature/Player/MiniPlayerOverlay.swift`
- Android `androidApp/feature/miniplayer/.../MiniPlayerScreen.kt`
- Both: artwork · title/subtitle · cast · ▶/⏸ · thin progress · tap→expand. Already minimal + consistent. **No changes.**

### Playlist (iOS=ShowDetail, Android=Playlist) — worst offender
- **iOS** `iosApp/deadly/Feature/ShowDetail/ShowDetailScreen.swift`
  - header: artwork · date/venue · ◁ ▷ prev/next-show · rating card
  - action row (~L146): ♥ Fav · ⬇ Download · ☰ Setlist · ▦ Collections(**dead `// TODO Phase 5`** ~L176) · ∞ Autoplay · ⋯ Menu ... ▶ Play
  - `menuSheet` (~L270)
- **Android** `androidApp/feature/playlist/.../components/`
  - `PlaylistActionRow.kt`: ♥ Fav · ⬇ Download · ☰ Setlist · ▦ Collections · ∞ Autoplay · ⋮ Menu ... ▶ Play (big)
  - `PlaylistMenuSheet.kt` ⋮: Favorites · Download · Setlist · Collections · Share · Choose Recording · ∞ Autoplay · EQ (re-lists 5 inline icons)

### Settings — flat scroll-forever List
- iOS `iosApp/deadly/Feature/Settings/SettingsScreen.swift`
- Android `androidApp/feature/settings/.../screens/main/SettingsScreen.kt` (mirrors iOS)
- Web `ui/src/app/me/_components/SettingsTab.tsx` (only Account/Community/Delete — missing the rest = parity gap)
- The "Home Screen" section alone is ~11 controls (trending window, 2 toggles, recent rows, 3 card-size pickers, fan-fav toggle, decade, 4th card size, reset). This is the bulk of the clutter.

---

## DECISIONS (locked unless noted)

### Full player — LOCKED
- **Track-info row:** `title/date/venue · ♥ Favorite` (Favorite takes the dead ＋'s spot — Spotify save position).
- **Transport row:** unchanged (◁◁ ▶/⏸ ▷▷ + jog).
- **Secondary row:** `cast` (left) ............ `⬆ Share · ▤ EQ` (right).
- **⋯ menu:** `Autoplay ∞ · ⬇ Download` (see unified taxonomy below for final grouped form).
- **Dropped until real:** `＋ Add-to-Playlist`, `≣ Queue`. Queue returns to inline cluster when the queue feature ships (ADR-0010 / `show-queue-v2`); ＋ when playlists exist.
- **EQ stays inline** (discoverability; genuinely changes per show/song while playing).
- **Parity:** iOS + Android become identical.
- **OPEN:** Queue's eventual home (Spotify puts it bottom-right; decide at ship).
- **OPEN/noted:** player Autoplay default = ⋯ menu, but could flank the transport row like Spotify shuffle/repeat. Decide at build. Default = menu.

### Mini-player — LOCKED: no changes.

### Playlist — LOCKED
- **Action row:** `☰ Setlist · ♥ Favorite · ⬇ Download · ⋯ Menu` ............ `∞ Autoplay · ▶ Play`
  - Autoplay rides next to Play as a play-mode (Spotify shuffle slot).
- **Collections** leaves the inline row → into the ⋯ menu, shown only when the show is in ≥1 collection. Delete iOS's dead Collections placeholder.
- **⋯ menu:** per unified taxonomy below.

### Unified ⋯ menu taxonomy — BOTH surfaces share ONE structure
Same order, same labels, same groups on both screens; each surface hides items it
already shows inline. User learns one taxonomy: **Listen / This Show / Share & More**.

| Group | Item | Player ⋯ | Playlist ⋯ |
|---|---|---|---|
| **Listen** | Choose Recording | ✅ | ✅ |
| | Equalizer | inline→hidden | ✅ |
| | Autoplay Next Show *(toggle)* | ✅ | inline→hidden |
| **This Show** | Setlist | ✅ | inline→hidden |
| | Collections *(if any)* | ✅ | ✅ |
| | Download | ✅ | inline→hidden |
| **Share** | Share | inline→hidden | ✅ |

**LOCKED group labels + assignment (mobile iOS+Android only; web has NO ⋯ menu):**
- **Playback** — Choose Recording · Equalizer · Autoplay Next Show · Add to Playlist *(future)*
- **This Show** — Setlist · Collections · Download
- **Share** — Share
- **Single-item groups on a surface → drop the header, use a plain divider.**

Renders as:
- **Player ⋯:** *Playback* → Choose Recording · Autoplay · | *This Show* → Setlist · Collections · Download
- **Playlist ⋯:** *Playback* → Choose Recording · Equalizer · | (Collections under a divider) · | Share

### Web — DECIDED (light-touch; web is the experimentation surface)
Web is structurally different from mobile and **cheap to iterate on live** — so
declutter-only, don't over-specify in the ADR.
- **Web show/playlist page** `ui/src/app/shows/[id]/page.tsx` is ALREADY album-style:
  `HeroActions` = Play · Favorite · Review on one line; **Setlist rendered inline as
  page content**; Review inline; secondary `ShowActions` (archive.org / get-the-app);
  right rail = app CTA + liner notes. This page largely follows the principles already
  (and its inline Setlist *validates* the mobile "Setlist/Collections are views" call).
  Light tidy only.
- **Download dropped from web entirely** — not a web concept; "get the app" covers mobile.
- **Web player** is a fundamentally different surface (`ui/src/components/player/`):
  - persistent header/mini **"quick look" bar** (`HeaderPlayer.tsx`): transport · seek ·
    ∞ Autoplay · Recording · Queue · Devices · Fullscreen · Clear · volume
  - immersive **full-screen with two states**: `idle` (chrome fades to ambient art ~L701)
    vs `active` (`isActive` ~L544)
  - rail panels: Queue, Devices
  - **Web inverts the mobile problem:** surplus space, not scarcity → discipline is
    restraint + state-appropriateness, NOT hiding. **The expanded player is web's
    "second tier" instead of a ⋯ menu.**
- **Persistent bar = sparse "quick look,"** but may KEEP transfer-playback (Connect),
  Autoplay, Choose Recording as quick-access — **experiment live**, don't over-spec.
- **One home for Autoplay** still applies (currently in BOTH bar and expanded). Lean =
  expanded only; open to keeping on bar per experimentation.
- **Idle full-screen: leave as-is** (ambient art). Lyrics = nice-to-have, DEFERRED.
- Realistic mobile-action additions to the web now-playing: **Favorite + Share** (web is
  ideal for share = URLs). **EQ likely N/A on web audio — confirm before adding.** Surface
  in active/expanded, not the bar.
- **Web Settings** (`ui/src/app/me/_components/SettingsTab.tsx`) still needs categorized-IA
  parity. OPEN: verify whether web home even has the rails/card-size/decade concept before
  scoping a web "Home Layout" screen — if not, web Settings just needs Account + Playback +
  About.

### Settings — DECIDED (direction), details TBD
- Replace the flat List with **categorized subscreens**: Account · Playback & Audio ·
  **Home Layout** · Library & Data · About/Support. Mobile = short landing list; web =
  same categories.
- **Home prefs → a consolidated "Home Layout" screen** (USER CHOSE this over fully
  in-place per-rail controls). Pulls all the home knobs out of the main list.
- **Close the web parity gap** (web Settings currently missing most of this).
- Detailed Settings IA not yet drawn — do after the player/playlist ADR, or fold both
  into one ADR. (Decide scope when resuming.)

---

## Remaining work
0. Web is covered (light-touch, experimentation surface — see Web section). Verify the
   one open web item (does web home have rails? → web Settings scope) when convenient.
1. ~~Settle the 3 menu-group labels + item assignment~~ DONE.
2. ~~Write **ADR-0014**~~ DONE — `docs/adr/0014-ui-control-altitude-and-decluttering.md`.
3. ~~Decide ADR scope~~ DONE — one ADR for everything.
4. ~~Implement player + playlist, iOS + Android in parity~~ **DONE (Phase 1, built +
   installed both platforms, NOT committed).** See "Phase 1 — implemented" below.

### Phase 1 — implemented (player + playlist, iOS + Android)
- **Shared `⋯` component:** Android `core/design/.../ShowActionsMenuSheet.kt`; iOS
  `Core/Design/ShowActionsMenuSheet.swift`. Taxonomy Playback / This Show / Share,
  hide-inline-via-null, single-item group → no header. Reused on both surfaces.
- **Player:** Favorite → track-info row; `＋`/`≣ Queue` stubs deleted (Android
  `PlayerTrackActionsSheet.kt` + `PlayerQueueSheet.kt` removed); secondary row =
  cast … Share·EQ; `⋯` = Choose Recording·Autoplay │ Setlist·Collections·Download.
  Player has no Setlist/Collections/recording state, so those + Choose Recording
  **navigate to the playlist** and deep-link the matching sheet (Android: `openSheet`
  query param on the playlist route; iOS: `ShowDetailSheet` hint via
  `pendingShowSheet` binding). Download + Autoplay act in place. Collections shown
  only when in ≥1 (Android `PlayerViewModel.showCollectionsCount` over new
  `core:api:playlist` dep; iOS `collectionsContaining` count).
- **Playlist:** action row → Setlist·Favorite·Download·⋯ … Autoplay·Play; Collections
  out of inline row → menu (only if ≥1). Android `PlaylistMenuSheet.kt` removed;
  iOS dead `// TODO Phase 5` Collections placeholders deleted, new per-show
  `ShowCollectionsSheet.swift` + `CollectionsService.collectionsContaining(showId:)`.
- **Discoverability — reusable toast (follow-up, 2nd commit):** the bare `∞` Autoplay
  icon had no label. Rejected adding inline text / re-mirroring the menu / long-press.
  Chose a **transient confirmation toast** on the Autoplay toggle — it doubles as the
  teaching moment ("learn by doing once") and only fires where there's no other visible
  feedback. Built as a **generic, reusable** bus so any surface can use it:
  - Android: `core/database/ToastController.kt` (`@Singleton`, `MutableSharedFlow`)
    → `AppViewModel.toasts` → collected at the root and rendered as `AppToast`
    (`core/design/component/AppToast.kt`, surfaceVariant pill) in the global content
    `Box` (top of z-stack, above the mini player). NOT the scaffold `SnackbarHost` —
    that's occluded by the bottom bar + mini player on every screen except the player.
  - iOS: `Core/Design/ToastPresenter.swift` (`@Observable` on `AppContainer`,
    auto-dismiss ~2.5s) → `ActionToastView` overlay in `MainNavigation`.
  - Shared copy helper `autoplayToastMessage(enabled)` on each platform (matched text).
    Wired into both Player + Playlist `toggleAutoAdvance`.

### Phase 2 — Web — DONE (2026-06-12)
- **Web player** (`ui/src/components/player/HeaderPlayer.tsx`): Autoplay given ONE
  home (the expanded player) — removed from the persistent bar; added to the desktop
  immersive docked controls (which had none) so desktop keeps the toggle. Favorite +
  Share added to the expanded/active state on both the mobile sheet and desktop docked
  controls, reusing `useUserData` (favorite) + `useShareLink` (copy-link + toast). No
  Download, no EQ on web (intentional). Three small local helpers: `FavoriteAction`,
  `ShareAction`, `AutoplayIcon`.
- **Web Settings** (`ui/src/app/me/_components/SettingsTab.tsx`): added **Playback**
  (Autoplay Next Show toggle, wired to `usePlayer().toggleAutoAdvance`) + **About**
  (data version + Privacy link). Web-home-rails question RESOLVED: web home is a static
  shell with NO rails/card-size/decade concept, so the mobile "Home Layout" category
  has no web equivalent — dropped. Web Settings = Account + Playback + Community + About.
- **Web show page:** already album-style; no change needed.

### Phase 2 — Settings (mobile) — DONE (2026-06-12, branch `mobile-settings-rework`)
- Flat `List`/`LazyColumn` replaced with a short landing of 5 categories, each its
  own subscreen: **Account · Playback & Audio · Home Layout · Library & Data ·
  About & Support**. All home-layout knobs gathered onto the one Home Layout screen.
- iOS (`SettingsScreen.swift`): landing `List` of `NavigationLink`s → 5 subscreen
  views; every binding/analytics call preserved verbatim. Compiles (`make ios-remote-build`).
- Android (`SettingsScreen.kt`): Settings lives in the nav drawer, so landing →
  subscreen is a local state drill-down with a back header + `BackHandler`; leaf
  screens (Equalizer/Connect/Legal/…) stay full-screen routes. `SettingsScreen`
  signature unchanged, so `SettingsNavigation.kt`/`MainNavigation.kt` untouched.
  Compiles (`:feature:settings` + `:app`).
- Knob→category map: Account=sign-in; Playback&Audio=controls+source badge+EQ+devices;
  Home Layout=all trending/rails/fan-favorites/card-size knobs+reset; Library&Data=
  include-no-recordings+downloads+import/export+migration; About&Support=version+
  release notes+donate+community+mission+legal+privacy+developer.

---

## RESUME PROMPT (paste into a fresh session)

> We're mid-design on a UI decluttering effort for the deadly-monorepo (iOS +
> Android, + web for Settings). Read `PLANS/ui-decluttering.md` — it has the first
> principles, the current-state inventory with file paths, and all locked decisions.
> Do NOT re-derive from scratch; pick up from "Remaining work."
>
> State: full player, mini-player, and playlist action layouts are LOCKED. The
> unified ⋯-menu taxonomy (Listen / This Show / Share & More, shared across both
> surfaces, hiding inline items) is agreed in shape — the only open design question
> is confirming the 3 group labels and which group each menu item belongs to.
> Settings direction is decided (categorized subscreens + a consolidated "Home
> Layout" screen + close the web parity gap) but its detailed IA isn't drawn yet.
>
> Next: (1) settle the menu groups with me, (2) write it all up as ADR-0014 in
> `docs/adr/` following the house ADR style (see ADR-0010), (3) then we implement.
> Nothing is built or committed yet. Discuss/confirm before writing the ADR.
