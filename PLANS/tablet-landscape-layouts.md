# Tablet + Landscape Layouts (Responsive Native) — Full Master-Detail

> **Status (2026-06-12): PROPOSED — NOT ACCEPTED. Needs review.** This is a
> draft design captured as a starting point for discussion, **not** an approved
> plan and **not** something to build or ship as-is. The scope here (full
> master-detail on both platforms) is one option on the table, not a decision.
> Treat every "Decision" / "user-confirmed" note below as a *proposal* to be
> re-litigated, not settled. No code has been written; nothing in `iosApp/` or
> `androidApp/` has changed. **Do not start implementation off this doc** until
> the approach is reviewed and explicitly accepted.
>
> Open questions to resolve before this is actionable:
> - Is full master-detail the right ambition, or is adaptive chrome (bars→rail)
>   enough for a first pass?
> - Is the effort justified vs. other roadmap items?
> - Does the three-tier breakpoint model hold up on real devices?
>
> - Roadmap item: [`ROADMAP.md`](ROADMAP.md) §6 "Tablet + landscape layouts"
> - The original worktree/branch (`worktree-tablet-landscape-layouts`) has been
>   removed; this doc was rescued from it and committed to `main` for review.

## Context

Both native apps render a **stretched phone UI** when wide: iOS is **iPhone-only**
(`TARGETED_DEVICE_FAMILY = 1`, iPad runs in compat scaling); Android has **zero**
window-size-class adaptivity (it rotates, just stretched). The web shell already
solved the shape — persistent left **nav rail** · **content (master list)** ·
contextual **right rail**, collapsing to a bottom tab bar when narrow
(`ui/src/components/shell/AppShell.tsx` + `RightRail.tsx`).

Decision (user-confirmed): **go the distance — full master-detail**, both
platforms, in this worktree. Not just adaptive chrome (bars→rail): the **content
itself becomes multi-pane** on wide screens. Tapping a show in a list opens it in
a **detail pane beside the list** (not a full-screen push), with a **contextual
right pane** (recordings / setlist / reviews) — the iPad Mail / web-shell model.
Width-driven so it covers tablet portrait *and* landscape from one mechanism. The
phone (compact) experience must stay **byte-for-byte today's**.

## Breakpoint model (THREE tiers — not width-binary)

Triggering full master-detail on raw width is wrong: a **phone in landscape is
wide but short**, so 3 panes would squish to ~300pt each with no height. Gate on
*room for two real panes*, which is a three-tier model:

| Tier | Devices | Layout |
|---|---|---|
| **Compact** | phones portrait; most phones landscape | **Today's UI** — bottom bar, full-width content, tap-to-push. Unchanged. |
| **Medium** (~600–840dp) | big phones landscape, small tablets portrait, foldables | **Nav rail + single content pane.** Rail replaces bottom bar; content stays one column; selecting a show pushes/replaces full-width. **No side-by-side.** |
| **Expanded** (≥840dp) | tablets landscape, large tablets | **Full master-detail** — rail · list · detail (· context). |

```
Expanded (≥840dp)                         Medium (phone landscape / small tablet)
┌────┬──────────┬──────────┬──────────┐   ┌────┬───────────────────────────────┐
│nav │ master   │ detail   │ context  │   │nav │ single content pane           │
│rail│ (list)   │(ShowDet/ │(recs/    │   │rail│ (list; show pushes full-width)│
│    │          │ playlist)│ setlist) │   │    │                               │
└────┴──────────┴──────────┴──────────┘   └────┴───────────────────────────────┘
Compact (phone portrait / normal phone landscape): unchanged — bottom bar + push.
```

**Why this avoids the squish.** *iOS:* key off `horizontalSizeClass`, NOT raw
points — Apple reports a normal iPhone in landscape as **compact**, so it keeps
the phone UI for free; only Plus/Max phones report `.regular` in landscape (they
have the room), and iPads are always regular. *Android:* `ListDetailPaneScaffold`'s
default adaptive directive shows **two panes only at Expanded** on its own, so
Medium (phone landscape) stays single-pane automatically — the dual pane can't
appear below ~840dp.

---

## iOS

The big enabler: master rows **already** use `NavigationLink(value: show.id)`
(`HomeScreen.swift:135…`, `SearchScreen`, `FavoritesScreen:462`). Inside a
`NavigationSplitView`, a content-column `NavigationLink(value:)` **automatically
populates the detail column** — so the section screens need minimal change.

### A. Adaptive root (`iosApp/deadly/App/MainNavigation.swift`)
- iOS has effectively **two** size-class tiers (compact / regular); the Medium
  tier is implicit — `NavigationSplitView` self-balances column count by
  available width (shows the sidebar as an overlay / collapses to 2 columns when
  cramped, e.g. Plus/Max landscape), so we don't hand-roll a Medium breakpoint.
- Introduce an `AdaptiveRoot` that branches on `@Environment(\.horizontalSizeClass)`:
  - **compact** → today's `TabView` (the entire current body), unchanged. (A
    normal iPhone in landscape is compact → no squish.)
  - **regular** → a `NavigationSplitView` (three columns):
    - **sidebar**: nav list of `AppTab` cases + a Settings row, bound to
      `selectedTab` (reuse the existing `AppTab` enum; the `settingsLogoButton`
      affordance becomes a sidebar row).
    - **content**: `switch selectedTab` → `HomeScreen` / `SearchScreen` /
      `FavoritesScreen` / `CollectionsScreen` (the *same* screens).
    - **detail**: a `NavigationStack(path:)` declaring
      `.navigationDestination(for: String.self) { ShowDetailScreen(showId:) }`
      (and `CollectionRoute`). Reuse the existing per-tab path state
      (`homeStack`/`searchStack`/…) as the **detail** column's stack.
- Keep `.miniPlayer(...)` + `.offlineBanner(...)` applied around the split view.

### B. Contextual right pane (inspector)
- Add `.inspector(isPresented: $showInspector)` on the **detail** column showing
  the show's recordings / setlist / reviews. **Reuse existing sheet bodies** —
  `RecordingPicker.swift`, `SetlistSheet.swift`, `ReviewDetailsSheet.swift` — as
  inspector panels instead of sheets when regular width. A detail-toolbar button
  toggles it. (Compact keeps them as sheets.)

### C. Player + content width
- `PlayerScreen` (`fullScreenCover`): at regular width, present centered with a
  max content width instead of a stretched full screen.
- Add a `.readableContentFrame()` helper (`Core/Design`) capping master/detail
  content width so rails/lists don't stretch; apply to the four section screens
  and `ShowDetailScreen`.

### D. iPad target
- `iosApp/deadly.xcodeproj/project.pbxproj`: `TARGETED_DEVICE_FAMILY = 1,2`
  (Debug + Release).
- `iosApp/Info.plist`: add `UISupportedInterfaceOrientations~ipad` (all four);
  ensure no `UIRequiresFullScreen` force. iPhone orientations unchanged.

---

## Android

Multi-module (`core/design`, `feature/*`). Show detail is the
`playlist/{showId}` destination (`feature/playlist/.../PlaylistNavigation.kt`),
reached via `navController.navigateToPlaylist(...)`.

### A. Dependency
- Add `androidx.compose.material3.adaptive:adaptive`, `:adaptive-layout`,
  `:adaptive-navigation` (versions matching the project's Compose BOM) to
  `androidApp/gradle/libs.versions.toml` + the `:app` and `:core:design`
  `build.gradle.kts`. Provides `currentWindowAdaptiveInfo()`,
  `NavigableListDetailPaneScaffold`, and `rememberListDetailPaneScaffoldNavigator`.

### B. Nav rail (chrome) in the shared scaffold
- `androidApp/core/design/.../scaffold/AppScaffold.kt` (the BarConfiguration
  overload used by `MainNavigation`): add
  `navigationRailContent: (@Composable () -> Unit)? = null` + `useSideNav: Boolean`.
  When `useSideNav`, wrap the inner `Box` in
  `Row { navigationRailContent(); Box { Scaffold … } }`; caller passes
  `bottomNavigationContent = null`. The existing bottom mini-player `Column`
  stays (mini-player remains a bottom bar).
- `androidApp/.../MainNavigation.kt`: compute `useSideNav` from
  `currentWindowAdaptiveInfo().windowSizeClass` width ≥ **MEDIUM** (~600dp) — so
  the rail appears at Medium (phone landscape / small tablet) while the **panes
  do not** (see C: the pane directive gates dual-pane to Expanded). Add a
  `NavigationRailBar` mirroring the existing private `BottomNavigationBar` /
  `BottomNavItem`, iterating `BottomNavDestination.destinations`, reusing
  `onNavigateToDestination` + offline-redirect logic.

### C. List-detail panes (content)
- In `MainNavigation.kt`, host the show-browsing flow in
  `NavigableListDetailPaneScaffold`:
  - **listPane**: the current top-level NavHost destinations
    (home/search/favorites/collections) — keep the NavHost for these.
  - **detailPane**: the existing playlist/show screen composable, hosted in the
    pane instead of a full-screen route.
  - **extraPane**: recordings / setlist (contextual rail).
- Selecting a show calls `scaffoldNavigator.navigateTo(Detail, showId)` instead
  of `navigateToPlaylist`. Keep the **default** `calculatePaneScaffoldDirective`
  so dual-pane appears **only at Expanded (≥840dp)**; at Compact/Medium the
  scaffold is single-pane (selecting a show shows a full-width detail, back
  returns to the list — today's behavior). The extra (context) pane is gated to
  Expanded with sufficient room.
- Keep the existing `playlist/{showId}` NavHost route for **compact** /
  deep-link / back-compat; the pane scaffold path is the wide one. (Reuse the
  same screen composable in both — no fork.)
- The detail pane's recordings/setlist UI already exists in the playlist feature;
  surface it in the extraPane when expanded.

### D. App already rotates (no manifest orientation lock found) — no change needed.

---

## Sequencing (land + verify incrementally)
1. **Chrome**: iOS sidebar branch + Android `NavigationRail` (the frame).
2. **iPad target** enablement (iOS) so wide layouts render natively.
3. **List-detail panes**: iOS `NavigationSplitView` detail column + Android
   `ListDetailPaneScaffold` — wire one section first (**Search**), then
   Favorites/Home/Collections.
4. **Contextual right pane**: iOS `.inspector` + Android `extraPane`
   (recordings/setlist/reviews — reuse existing UI).
5. **Player + content width** adaptation.
6. **Docs**: ADR-0011, this file, ROADMAP §6 status.

## Docs / tracking
- **ADR-0011** `docs/adr/0011-tablet-landscape-layouts.md` (0010 = unmerged
  show-queue branch): width-driven master-detail; `NavigationSplitView` +
  `.inspector` (iOS) and `ListDetailPaneScaffold` (Android); iPad-target
  enablement; reuse of existing detail/sheet UI; compact path unchanged.
- This file = handoff/status (mirrors the other `PLANS/*.md`).
- Update `PLANS/ROADMAP.md` §6 status when phases land.

## Commits (conventional, per repo scope rules)
- `build(android): add material3-adaptive (window size class + list-detail)`
- `feat(ios/layout): adaptive split-view master-detail + iPad target (ADR-0011)`
- `feat(ios/layout): contextual inspector for recordings/setlist/reviews`
- `feat(android/layout): nav rail + list-detail pane scaffold (ADR-0011)`
- `feat(android/layout): contextual extra pane for recordings/setlist`
- `docs(mobile/layout): ADR-0011 + roadmap for tablet/landscape master-detail`

## Verification
- **Android**: `make android-install` (build locally per repo convention — the
  remote agent is iOS-only). On a tablet AVD (Pixel Tablet) + phone landscape:
  confirm rail replaces bottom bar; list+detail side-by-side at Expanded;
  selecting a show fills the detail pane (not a full-screen push); extra pane
  shows recordings/setlist; system back collapses panes correctly; mini-player +
  offline redirect intact. Phone **portrait**: identical to today.
- **iOS**: `make ios-remote-install` (remote Mac; keychain pw note in memory).
  iPad simulator (portrait + landscape) + iPhone (portrait + landscape): sidebar
  at regular width; tapping a show fills the detail column; inspector toggles
  recordings/setlist/reviews; player centered not stretched. iPhone **portrait**:
  today's TabView + push nav, unchanged.
- Regression gate on both: **compact must be byte-for-byte the current UX.**

## Risk / notes
- Largest risk is the **content restructure** (panes), not the chrome. Mitigated
  by keeping compact on the existing code paths and reusing the same screen
  composables/views in both modes (no forks).
- iOS: `NavigationLink(value:)` driving the split-view detail column is the
  load-bearing assumption — **verify early on one section** before fanning out.
- Android: hosting the playlist screen in both a NavHost route (compact) and a
  pane (wide) — keep it one composable to avoid divergence.
- Worktree build gotchas (per memory): copy gitignored `Secrets.swift` for iOS;
  older Makefile may use bare `gradle` — run `./gradlew` directly if so.
