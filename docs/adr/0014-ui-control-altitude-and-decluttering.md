# ADR-0014: UI control altitude — decluttering Settings, player, and playlist

## Status

Accepted (2026-06-12). Design settled across all surfaces; not yet implemented.
Working notes and current-state inventory (with file paths) live in
`PLANS/ui-decluttering.md`. Implementation is iOS + Android in parity, with a
light-touch web pass and a web Settings parity follow-up.

Related: [ADR-0010](0010-playback-auto-advance-and-show-queue.md) (auto-advance +
queue — the `∞ Autoplay` toggle and the future `Queue` entry point this ADR
re-homes both originate there).

## Context

As features shipped, controls accreted onto surfaces faster than anyone pruned
them. Three messes resulted:

1. **Settings is a flat scroll-forever `List`.** iOS and Android are near-
   identical; one section ("Home Screen") alone carries ~11 controls — a trending
   window, two toggles, a recent-rows count, **three** card-size pickers, a
   Fan-Favorites toggle, a decade picker, a fourth card-size picker, and a reset.
   Almost all of it is set-once home-layout config sitting in one undifferentiated
   list. Web Settings, by contrast, is three cards (Account / Community / Delete)
   — not cleaner, just **missing** the rest.
2. **The player's hot row is overloaded.** The `∞ Autoplay` addition (ADR-0010)
   landed *in* the inline action cluster — a 5-icon strip (Autoplay · EQ ·
   Favorite · Share · Queue) — and was *also* duplicated into the `⋯` overflow.
   Android additionally carries two dead stubs (a `＋` "Playlists are coming soon"
   and a `≣ Queue` stub) that iOS lacks, so the two platforms have drifted.
3. **The playlist `⋯` menu re-lists almost everything inline.** Six inline icons
   (Favorite · Download · Setlist · Collections · Autoplay · ⋯) and an overflow
   sheet that repeats five of them. iOS's Collections button is a dead
   `// TODO Phase 5` placeholder.

The through-line: there is no shared rule for **where a control belongs**, so
every new control defaults to "another icon in the row" and then "also in the
menu, to be safe."

## Decision

### 1. First principles (the rule every placement obeys)

1. **A control earns its altitude by frequency × reversibility.** Set-once config
   (card sizes, decade, EQ, control-style) must never sit adjacent to per-use
   actions (Favorite, Queue, Share).
2. **Configure where you consume.** A home rail's options belong near the rail,
   not mirrored into a distant mega-list (tempered for Settings — see §7).
3. **One control, one home.** Nothing appears both inline *and* in an overflow.
4. **The player/playlist surface is transport + "this show."** Modes and config
   live in the `⋯` menu; per-show actions stay inline.
5. **Progressive disclosure beats a flat list.** Settings is a short landing of
   stable categories, each its own screen.
6. **Parity is the spec.** iOS and Android share one information architecture
   (and web for Settings). Density may differ; structure may not.

### 2. Full player (iOS + Android, identical)

- **Track-info row:** `title / date / venue · ♥ Favorite`. Favorite takes the
  spot the dead `＋` occupies today (Spotify's "save" position) — the one frequent
  per-show action gets a dedicated, prominent home.
- **Transport row:** unchanged (`◁◁ ▶/⏸ ▷▷` + scrubber).
- **Secondary row:** `cast / Connect` (left) … `⬆ Share · ▤ EQ` (right). Two
  icons + cast.
- **`⋯` menu (top bar):** per the taxonomy in §5.
- **Dropped until functional:** `＋ Add-to-Playlist` and `≣ Queue` are removed
  from the player entirely. **Queue returns** to the secondary row when the queue
  feature ships (ADR-0010 / `show-queue-v2`); `＋` returns when playlists exist.
- **EQ stays inline** (not demoted to the menu despite being config-ish):
  discoverability matters, and it genuinely changes per show/song while playing.

The right cluster goes from **5 icons + a duplicating `⋯`** to **one icon
(Favorite up top) + `⬆ Share · ▤ EQ` + a non-duplicating `⋯`.**

### 3. Mini-player — no change

`artwork · title/subtitle · cast · ▶/⏸ · thin progress`, tap-to-expand. Already
minimal and already identical across iOS/Android. `cast` keeps its slot — it is
transport-at-a-glance and lights up to signal remote control, exactly the state a
collapsed bar should surface.

### 4. Playlist (iOS ShowDetail / Android Playlist, identical)

- **Action row:** `☰ Setlist · ♥ Favorite · ⬇ Download · ⋯ Menu` …
  `∞ Autoplay · ▶ Play`.
  - This is Spotify's album layout (`♥ · ⬇ · ⋯ … ▶`) plus a Setlist affordance.
  - **`∞ Autoplay` rides next to `▶ Play`** as a play-*mode* (the slot Spotify
    gives shuffle) — it modifies what playing does, so it sits at the play action,
    not in the generic icon strip.
- **Collections** leaves the inline row → the `⋯` menu, shown **only when the show
  is in ≥1 collection**. iOS's dead Collections placeholder is deleted.
- Six inline icons → four on the left; the `⋯` menu stops duplicating inline
  actions.

Note the deliberate asymmetry with the player: the playlist has a discrete "start
playback" affordance, so Autoplay (a play-mode) sits beside it; the player is
mid-playback (no Play button on that surface), so Autoplay lives in its `⋯` menu.
The placement follows the meaning, not a desire for pixel-identical surfaces.

### 5. One unified `⋯`-menu taxonomy (mobile only)

Both player and playlist share **one** overflow structure — same groups, same
labels, same order. Each surface simply **hides the items it already shows
inline**, so a control is never one-tap-inline *and* in the menu (principle #3).
The user learns a single mental model:

- **Playback** — Choose Recording · Equalizer · Autoplay Next Show ·
  Add to Playlist *(when it exists)*
- **This Show** — Setlist · Collections · Download
- **Share** — Share

Rendering after per-surface hiding:

- **Player `⋯`:** *Playback* → Choose Recording · Autoplay | *This Show* →
  Setlist · Collections · Download
- **Playlist `⋯`:** *Playback* → Choose Recording · Equalizer | Collections
  *(under a divider)* | Share

**When a group collapses to a single item on a surface, drop the group header and
use a plain divider** (a lone item under a bold label reads as heavier than it
is). Web has **no `⋯` menu** — this taxonomy is iOS + Android only.

### 6. Web — light-touch, treated as the experimentation surface

Web is structurally different and cheap to iterate on live, so it is deliberately
**not** over-specified here.

- **The show page is already correct.** `ui/src/app/shows/[id]/page.tsx` is
  album-style: `Play · Favorite · Review` on one line, **Setlist rendered inline
  as page content**, review + secondary actions + a right rail. It already
  embodies the principles; its inline Setlist *validates* the "Setlist/Collections
  are views of the show" call made for mobile (§4). Light tidy only.
- **No Download on web** — not a web concept ("get the app" covers mobile).
- **The web player inverts the mobile problem:** surplus space, not scarcity. The
  discipline is restraint + putting controls in the right *state*, not hiding in a
  menu. **The expanded player is web's "second tier"** in place of a `⋯` menu.
- **Persistent bar = a sparse "quick look,"** which may keep transfer-playback,
  Autoplay, and Choose Recording as quick-access — tuned by live experiment, not
  fixed here. **Autoplay gets one home** (it is currently in both the bar and the
  expanded view).
- **Idle full-screen stays as-is** (ambient art); lyrics deferred.
- Realistic mobile-action additions to the web now-playing are **Favorite** and
  **Share** (web is ideal for sharing — URLs), surfaced in the active/expanded
  state, not the bar. **EQ is likely N/A on web audio — confirm before adding.**

### 7. Settings — categorized subscreens + a consolidated Home Layout

- Replace the flat `List` with a short landing of **stable categories, each its
  own screen**: Account · Playback & Audio · **Home Layout** · Library & Data ·
  About / Support.
- **All home-layout knobs move into one dedicated "Home Layout" screen** —
  *not* scattered in-place on each rail. This empties the mega-list while keeping
  the options in a single discoverable place (the chosen middle path between "flat
  list" and "fully in-place per-rail controls").
- **Close the web Settings parity gap** as a follow-up: port the category IA for
  the settings that have web meaning. (Open: confirm whether web home even has the
  rails/card-size/decade concept; if not, web Settings is just Account + Playback
  + About.)

### 8. Build order

Per-surface, iOS and Android kept at parity by discipline (no shared module).
Mini-player needs no work. The full player and playlist share the `⋯`-menu
taxonomy, so that component is defined once per platform and reused on both
surfaces. Web and web-Settings parity are separate, lighter passes. Use the
monorepo make targets (`make ios-remote-install`, `make android-install`).

## Consequences

**Gained:**
- One rule (`control altitude`) decides every placement, so new controls have an
  obvious home instead of defaulting to "another inline icon, also in the menu."
- The player right cluster drops from 5 icons (+ a duplicating overflow) to 1
  prominent Favorite + Share/EQ; the playlist drops from 6 inline icons to 4; no
  control is both inline and in the menu.
- A single `⋯`-menu taxonomy (Playback / This Show / Share) learned once and
  reused on both mobile surfaces.
- iOS/Android drift is eliminated (the `＋` and `≣ Queue` stubs go; both platforms
  end identical), and two dead controls (iOS Collections placeholder, the `＋`
  stub) are removed.
- Settings becomes navigable (categories + a dedicated Home Layout) instead of one
  endless scroll; web parity has a defined path.

**Accepted / given up:**
- **Parallel per-platform implementations** kept at parity by discipline, not a
  shared module (consistent with the rest of the repo).
- **Deliberate player/playlist asymmetry** for Autoplay (next-to-Play on the
  playlist, in the `⋯` menu on the player) — justified by surface meaning, at the
  cost of pixel-identical surfaces.
- **Queue and Add-to-Playlist temporarily disappear** from the player rather than
  ship as stubs; Queue returns with the queue feature, `＋` with playlists.
- **Web is intentionally under-specified** (experiment live) and stays leaner than
  mobile (no Download; EQ likely absent). The missing-actions parity (Favorite /
  Share on the web player) is logged as follow-up, not mandated here.
- **Web Settings parity is a follow-up**, gated on verifying which mobile prefs
  have web meaning.

## Alternatives considered

**Keep Autoplay inline on the player** (drop it only from the overflow). Rejected:
it satisfies "one home" but leaves a set-once *mode* permanently occupying the hot
row — the exact altitude violation this ADR exists to fix. Modes belong in the
`⋯` menu (player) or beside Play (playlist).

**Move per-rail home options in-place onto each rail** (long-press a rail header
to size it, etc.) instead of a Home Layout screen. Rejected for this pass: it is
the most aggressive declutter but the most new UI, and it scatters one concern
across many surfaces. A single consolidated Home Layout screen empties the
mega-list with far less surface area to build and keep in parity. The in-place
approach remains a viable later refinement.

**A separate, hand-tuned `⋯` menu per surface.** Rejected: it optimizes each menu
locally at the cost of a learnable, parity-checkable shared structure. The unified
taxonomy with per-surface hiding gives the same end result on each screen with one
mental model and one component.

**Give the web player a `⋯` menu mirroring mobile.** Rejected: web's constraint is
surplus space, not scarcity, and it already has a natural detail tier (the expanded
player). A `⋯` menu would import a mobile solution to a problem web doesn't have.

**Bring full Favorite/Share/Download/EQ parity to the web player as part of this
ADR.** Rejected as scope: it turns a "control altitude" cleanup into a web-player
rebuild. Web declutter ships now; the missing-actions parity is a tracked
follow-up.
