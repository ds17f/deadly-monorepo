# Landscape & Tablet Layouts — Icon Rail + Contextual Side Player

> **Status (2026-06-13): ACCEPTED — building.** Branch `tablet-landscape-layouts`.
> This replaces the earlier "full master-detail" draft, which was discarded.
> Roadmap: [`ROADMAP.md`](ROADMAP.md) §6.

## The problem

Both apps render a **stretched phone UI** when wide. Rotate a phone today and it
falls apart: the bottom tab bar eats ~1/5 of the (now short) height with 4 buttons
spread across it; the mini-player sits on top of content and clips it (Home's
second Recents row gets cut off); every list is one absurdly-wide column. iOS is
iPhone-only (`TARGETED_DEVICE_FAMILY = 1`, iPad runs scaled); Android has no
window-size-class adaptivity. Spotify dodges this by **locking to portrait** —
that's the industry floor and our safety net, but we can do better.

## The design — one rule, three zones

**Rule:** key off **width**, never device model. Narrow → today's UI, byte-for-byte
unchanged. Wide → a three-zone layout:

```
WIDE (phone landscape, tablets)              NARROW (phone portrait) — unchanged
┌──┬─────────────────────┬──────────┐        ┌─────────────────────┐
│⌂ │  current screen      │  player  │        │  current screen     │
│🔍│  (full height)       │  (side)  │        │                     │
│♥ │                     │  ▶Ⅱ      │        ├─────────────────────┤
│▦ │                     │          │        │ ▢ Title        ▶Ⅱ ⏭ │ mini
└──┴─────────────────────┴──────────┘        ├──┬──┬──┬──┬─────────┤
 icon rail   browse content   side player    │⌂ │🔍│♥ │▦ │         │ bottom bar
```

- **Icon-only nav rail (left).** The bottom tab bar stood up vertically, labels
  dropped → thin. Reclaims the wasted vertical height. **Global** — fixes every
  screen at once. Cheapest, safest piece; ships alone.
- **Center = the current screen, full height.** With the rail and player off to
  the sides, content stops being clipped by the mini-player.
- **Side player (right) — contextual.** Appears **only when something is playing**;
  idle, the center takes full width (no empty column). See player sizes below.

Because the trigger is **width**, this same layout covers **tablets for free** —
a tablet is just a roomier wide screen. The only tablet-specific work is one line
on iOS (`TARGETED_DEVICE_FAMILY = 1,2`) so iPad renders natively instead of
pixel-doubling. Android tablets, foldables, and DeX get it with zero extra code.
"Fix phone landscape" and "support tablets" are the **same work**.

## The player has three sizes (not mini-vs-full)

```
MINI (narrow, today)       SIDE (wide, docked — NEW)   FULL-WIDE (wide expand)
┌──────────────────┐       ┌──────┬────────┐           ┌──────────────┬──────┐
│ list             │       │ list │ ▢ art  │           │   big cover  │ ▢art │
├──────────────────┤       │      │ Title  │           │   ▢▢▢▢▢▢     │ Title│
│ ▢ Title    ▶Ⅱ ⏭ │       │      │ ──●──  │           │   ▢▢▢▢▢▢     │ ──●─ │
└──────────────────┘       │      │ ◀ ▶Ⅱ ▶ │           │              │ ◀▶Ⅱ▶ │
                           └──────┴────────┘           └──────────────┴──────┘
```

1. **Mini** — today's bottom strip (narrow only). Art + title + play/pause.
2. **Side** — a **rotated mini plus a scrubber**: art thumbnail, title, scrubber,
   transport, stacked vertically in the right column. "More than mini, less than
   full." **This is the keystone** — it's what makes landscape look designed.
3. **Full-wide** — expanding the player in landscape does **not** go full-screen.
   The **big cover + scrubber take over the center** (where the list was); the
   **controls stay in the right column, identical to side mode**. Collapse →
   list returns. It's a *mode swap of the center pane*, not a new screen — so
   full-wide is nearly free once side exists. (No full-screen `fullScreenCover`
   in landscape.)

## Behaviors

- **Tap a show while the side player is docked:** the show opens in the **center**
  (list → detail, same as today's push). Player stays put on the right. Center =
  "what I'm browsing," right = "what I'm hearing" — independent, like desktop apps.
- **Narrow stays exactly today's UX** — bottom bar, mini-player, full-screen player,
  push navigation. Regression gate: compact is byte-for-byte unchanged.
- **Portrait-lock is the escape valve.** If a specific screen fights landscape, we
  can lock just that screen to portrait rather than block the whole feature.

## Phasing (smallest win first, ship incrementally)

1. **Icon rail** when wide — global chrome change. Fixes the wasted-height eyesore
   on every screen. *Shippable alone.* — **DONE (both platforms, compiles).**
   Android width ≥600dp; iOS width ≥600pt + iPad target flipped
   (`TARGETED_DEVICE_FAMILY = 1,2`). Device verification pending.
2. **Side player** + center reclaiming full height. The real win; landscape now
   looks intentional. *Shippable here.* — **NEXT.**
3. **Full-wide expand** (big cover center + controls right). Pure polish; defer
   until 1–2 feel right on a device.
4. Per-screen landscape polish (Home rows → grid, artwork sizing, etc.).

## Phase 1 notes & follow-ups (device-verified 2026-06-13 — rail works)

Observations from the shipped rail, to revisit (most are Phase 2 fodder):

- **Toast / overlay positioning is inconsistent across platforms in wide mode.**
  Android renders the offline banner, auto-advance card, and app toast *inside*
  the content pane (right of the rail), so they're centered in the content, not
  the window. iOS attaches those overlays to the full-window `GeometryReader`, so
  they span the whole width *including over the rail*. Pick one (likely: confine
  to the content pane on both) when polishing. Low priority — both are legible.
- **Mini player is still a bottom strip** within the content pane in wide mode
  (AppScaffold renders it at the bottom). This is exactly what Phase 2's side
  player replaces — the docked right player supersedes the bottom mini when wide.
- **Rail has no Settings entry**; Settings is still reached via the logo button in
  each screen's top toolbar (unchanged from compact). Fine; revisit if the toolbar
  logo feels out of place in the wide layout.
- **Rail is visually plain** — icon + selected tint, no active-pill/indicator
  animation. Cosmetic polish deferred.
- **iOS rotation compact↔wide** preserves per-tab nav stacks (they're parent
  `@State`, shared by both layouts) — verified smooth on device.

## Platform mechanics

### iOS (builds on remote Mac — `make ios-remote-install`)
- **Width signal:** `GeometryReader` width ≥ 600pt (NOT `horizontalSizeClass`).
  Decided to gate on width so a **regular iPhone in landscape** — which reports
  `.compact` — still gets the wide rail (it reads ~844–956pt wide). Portrait
  phones (~390–430pt) stay below the threshold → today's TabView, unchanged.
- **Root (`iosApp/deadly/App/MainNavigation.swift`):** branch the body —
  compact → today's `TabView` unchanged; wide → an `HStack` of [icon rail bound to
  `selectedTab`] · [the current section screen] · [side player when a track is
  loaded]. Reuse the existing `AppTab` enum and per-tab `NavigationStack` paths.
- **Side player:** a new compact player view (reuse `PlayerScreen` subviews /
  view-model); not the `fullScreenCover`. Full-wide = swap the center for a hero
  cover view while the side controls persist.
- **iPad target:** `project.pbxproj` `TARGETED_DEVICE_FAMILY = 1,2` (all 6 configs);
  `Info.plist` allow all iPad orientations, no `UIRequiresFullScreen` lock.

### Android (builds locally — `make android-install`)
- **Width signal:** `currentWindowAdaptiveInfo().windowSizeClass` from
  `androidx.compose.material3.adaptive:adaptive` (add to `libs.versions.toml` +
  `:app`/`:core:design`). Wide = width ≥ MEDIUM (~600dp).
- **Icon rail:** extend `core/design/.../scaffold/AppScaffold.kt` with a
  `navigationRailContent` + `useSideNav` path: when wide, wrap the content in
  `Row { rail(); content }` and pass `bottomNavigationContent = null`. Add a
  `NavigationRailBar` mirroring the private `BottomNavigationBar`, iterating
  `BottomNavDestination.destinations`, icons only.
- **Side player:** in `MainNavigation.kt`, when wide and a track is loaded, render
  the side player as the right element of the `Row`. Reuse player feature UI.
- **Show nav stays the existing `playlist/{showId}` NavHost route** — don't thread
  pane navigation through feature modules (Collections/Downloads navigate by raw
  `navController.navigate("playlist/$showId")` strings). Center just hosts the
  NavHost as today.
- No manifest orientation lock exists — app already rotates.

## Verification
- **Android:** `make android-install`; phone landscape + tablet AVD: icon rail
  replaces bottom bar, content full height (Home second row not clipped), side
  player appears on play and is gone when idle, tapping a show fills center. Phone
  **portrait** identical to today.
- **iOS:** `make ios-remote-install` (keychain pw in memory); iPhone landscape +
  iPad sim: rail at regular width, side player docks right, no full-screen player
  in landscape. iPhone **portrait** unchanged.
- **Regression gate both:** narrow = byte-for-byte today's UX.

## Docs / tracking
- ADR-0011 `docs/adr/0011-landscape-tablet-layouts.md`: width-driven icon rail +
  contextual side player + three player sizes; iPad target enablement; compact
  unchanged; portrait-lock fallback.
- Update `PLANS/ROADMAP.md` §6 status as phases land.

## Commits (conventional, per repo scope rules)
- `feat(android/layout): icon nav rail when wide`
- `feat(ios/layout): icon nav rail + iPad target when wide`
- `feat(mobile/player): contextual side player in landscape`
- `docs(mobile/layout): ADR-0011 landscape + tablet layouts`
