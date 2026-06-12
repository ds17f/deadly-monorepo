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
1. ~~Settle the 3 menu-group labels + item assignment~~ DONE — Playback / This Show /
   Share, single-item groups use a divider (mobile only; web has no ⋯ menu).
2. Write **ADR-0014** (UI decluttering / control altitude) capturing principles +
   the locked layouts + the unified menu taxonomy + the Settings direction.
3. Decide ADR scope: one ADR for everything, or split player/playlist vs Settings.
4. Implement per surface, iOS + Android in parity (+ web Settings). Use monorepo make
   targets (`make ios-remote-install`, `make android-install`). NOT committed yet.

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
