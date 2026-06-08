# In-App Messaging Plan

A first step toward the social features on the roadmap (`ROADMAP.md` §3): an
**in-app messaging / notifications** system. This doc scopes it down to the one
slice worth building first — **admin → everybody system notifications, in-app
only** — and lays the schema/protocol so the broader social cases (1:1 between
friends, opt-in push) slot in additively later.

Working branch: **`worktree-in-app-messaging`** (off `main`). Plan-only for now;
nothing implemented. Commit the doc, decide later whether/when to build.

## Status (2026-06-06)

- ✅ **Phase 1 built — global, web end-to-end** (branch `worktree-in-app-messaging`).
  - **Server:** `notifications` table (`api/src/db/users.ts` `initSchema`), data
    access `api/src/db/notifications.ts`, routes `api/src/routes/notifications.ts`
    (public `GET /api/notifications?since=`, admin `POST`/`GET`/`DELETE
    /api/admin/notifications`), registered in `app.ts`. `make api-typecheck` +
    `make api-test` (133) green; live round trip verified (cold consume → admin
    publish 201 → delta → 401 unauth → retire 204 → empty).
  - **Web:** `ui/src/lib/notifications.ts` (localStorage cache/cursor/merge/prune),
    `NotificationBell` in the shell header, admin compose page
    `ui/src/app/admin/notifications/`. `make ui-build` green.
- ✅ **Phase 2 built — mobile consume, both platforms** (branch `worktree-in-app-messaging`).
  - **Android:** new `core/notifications` module — `Notification.kt` (wire/cached
    models + `active`/`dismissed`/`unreadCount` helpers), `NotificationStore`
    (SharedPreferences JSON blob + `StateFlow`, merge/prune/markSeen/dismiss),
    `NotificationApiServiceImpl` (OkHttp GET, **no** auth header),
    `NotificationCoordinator` (ProcessLifecycle `ON_START` + cold start, fires
    **unconditionally**), Hilt `NotificationModule`. UI: `NotificationBell`
    (badge + `ModalBottomSheet` inbox with dismissed archive) wired into the
    **home** top-bar actions in `MainNavigation`; coordinator started in
    `DeadlyApplication.onCreate`. `./gradlew assembleDebug` green.
  - **iOS:** `Core/Notifications/` — `NotificationModels.swift`,
    `NotificationStore` (`@Observable`, `UserDefaults` JSON blob, same
    merge/prune/mark logic), `NotificationService` (reuses `APIClient`, no
    token). UI: `Feature/Notifications/NotificationBell.swift` (badge + sheet
    inbox, swipe-to-dismiss + dismissed `DisclosureGroup`) in the **Home** tab
    `topBarTrailing`. Cold-start + `willEnterForegroundNotification` refresh
    wired in `deadlyApp.swift`; store/service owned by `AppContainer`.
    `make ios-remote-build` green.
  - Both clients store local-only seen/dismiss, cold-start marks the capped
    batch seen-not-dismissed (decision #8), and render `\n`/Unicode as-is.
- ⏳ **Phase 3 (social 1:1 + push)** not started.
- ✅ **v2 — real inbox + targeting (2026-06-07, built, all platforms)** on
  `worktree-in-app-messaging`. Upgrades the shipped bell/sheet into a proper
  mail-style inbox (list + detail + archive + bulk actions), makes the unread
  badge persistent (cleared only by explicit read/archive), adds tappable links,
  client-side **targeting** (platform + app-version range) to kill stale release
  notices, a small **category** taxonomy, a subreddit link, and polling tuning.
  Full design in **[§ v2 — Real inbox + targeting](#v2--real-inbox--targeting-2026-06-07)** below; it supersedes the
  "Client surfaces" / "Phase 2 — mobile consume" sections for the inbox UI and
  the cold-start rule (decision #8). No push; still in-app only.
  - **Verified end-to-end** (user-confirmed on device) + builds green across all
    four steps: `make api-typecheck`/`make api-test` (139), `make ui-build`,
    `./gradlew assembleDebug`, `make ios-remote-build`.
  - **Pull-to-refresh** on both mobile inboxes (manual `?since` delta fetch +
    merge): Android `PullToRefreshBox` → `NotificationViewModel.refresh()`; iOS
    `.refreshable` → `NotificationService.refresh(reason:"pull")`. The empty
    inbox is still pullable (Android renders the empty state inside a `LazyColumn`
    item) so you can pull to check for new messages.
  - **Category glyphs are monochrome**, not emoji (see decision F): web inline
    outline SVG (`currentColor`, `text-zinc-500`), Android `material-icons`
    outlined tinted `onSurfaceVariant`, iOS SF Symbols tinted `.secondary`.

### Known limitation surfaced in Phase 1: retraction doesn't reach delivered copies
The consume endpoint is a **forward-only** delta (`id > since`). So retiring
(`deleted_at`) a message only hides it from clients that *hadn't pulled it yet*;
a client that already cached it never sees the tombstone (its `?since=` is past
that id) and keeps showing it until **expiry** prunes it client-side by clock.
So: **`expires_at` is the reliable "take it down" lever; `DELETE`/retire is best
understood as "stop new deliveries," not a true unsend.** Fixing real retraction
would mean a change-cursor (by mutation time, not id) or a periodic full-active
reconcile — more machinery than v1 warrants. Documented, not built.

## v2 — Real inbox + targeting (2026-06-07)

> Supersedes the **Client surfaces** + **Phase 2 — mobile consume** UI sections
> and **decision #8** (cold start). Server's "dumb global publisher, clients
> filter locally" model (decisions #1–2) is unchanged — every v2 addition is
> additive metadata the client interprets. Branch: `worktree-in-app-messaging`.
> Decided in session 2026-06-07. **No push** — still in-app only.

### A. Persistent unread badge (read-state semantics change)
Today opening the inbox auto-stamps everything `seen` (web `toggleOpen`, Android
`onClick`, iOS `Button` action all call `markAllSeen()`), so the badge clears on
a mere glance. **v2: the badge persists across opens.** It counts active & unread
and only drops via explicit user action:

| Action | Mechanism |
|---|---|
| **Tap a message → detail** | marks *that* message read (`seen_at`) on open — "tap to read" |
| **Archive** (per message) | sets `dismissed_at`; leaves the active list → no longer counted |
| **Mark all read** | `markAllSeen()` — the old on-open behavior, now behind a button |
| **Archive all** | archive every active message at once |

`seen_at` keeps its name but now means "read" (set by detail-open / mark-all-read,
**not** by opening the list). Remove `markAllSeen()` from all three open handlers.

### B. Real inbox screen (replaces dropdown/sheet)
The bell + badge stays in the top bar as the entry point; tapping it now opens a
**navigable screen**, not a popover:
- **Web:** a `/notifications` route/page. Drop the header dropdown — the bell
  navigates straight to the page (decided: consistency with mobile).
- **iOS:** push a `List` screen in the NavigationStack → `NavigationLink` detail.
  Replaces the current `.sheet`.
- **Android:** a Compose nav destination (list → detail). Replaces the
  `ModalBottomSheet`.

Layout:
```
Inbox                          [Archived]
┌─────────────────────────────────────┐
│ ● v2.4 is live              2h ago  │  unread: filled dot + bold title
│   Heads up, the new release adds…   │  body preview (1–2 lines)
├─────────────────────────────────────┤
│   Archive.org outage        Jun 3   │  read: no dot, muted
│   Playback may fail while Archive…  │
└─────────────────────────────────────┘
  [ Mark all read ]      [ Archive all ]
  More at r/thedeadlyapp →               (footer link, § I)

— tap row → detail (full body + tappable links); opening marks read
— swipe (mobile) / button (web) on a row → archive
— [Archived] tab/section lists dismissed messages (read-only)
```
Row = category glyph + unread dot + title + body preview + relative time. Detail =
title + timestamp + full body with links.

### C. Toast on arrival (transient — coexists with the inbox)
The inbox is the screen; the **toast is the "ping."** It fires when a foreground
poll merges a **new, eligible, unread** message (post-targeting filter, `cursor>0`
delta — never on the cold-start batch). Tapping it opens the inbox. Rides existing
surfaces:
- **Web:** existing `ToastProvider` (`ui/src/components/ui/ToastProvider.tsx`).
  Extend `showToast` to carry an optional tap target → navigate to
  `/notifications`. Text: `New: <title>`.
- **Android:** existing app-wide `SnackbarHostState` (already in `AppScaffold` /
  `MainNavigation`, used for offline/online). `showSnackbar(msg, actionLabel="View")`
  → action navigates to the inbox.
- **iOS:** the only gap — `OfflineBanner` is a *status* banner (shows while a
  condition holds), not transient. Add a transient `.message(text, onTap)` style
  that auto-clears after ~3–4s, reusing the existing banner styling + the
  `safeAreaInset(edge:.bottom)` slot. Tap → inbox.

### D. Links in message bodies (markdown + autolink)
Parser per platform: support `[label](url)` **and** autolink bare `http(s)://`
URLs not already inside a markdown link. **Restrict schemes to http/https** (drop
`javascript:`/`data:` etc.) even though the admin is the only author.
- **Web:** render parsed tokens to `<a target="_blank" rel="noopener noreferrer">`
  (opens a new tab). **No `dangerouslySetInnerHTML`** — build anchors from tokens.
- **iOS:** `AttributedString` with `.link` runs (`AttributedString(markdown:)` +
  `NSDataDetector` for bare URLs); `Text(attr)` opens Safari on tap.
- **Android:** `AnnotatedString` + `LinkAnnotation.Url`;
  `LocalUriHandler.current.openUri(url)` opens the system browser (hands off to
  the Reddit app for reddit.com links if installed).

### E. Targeting — client-side relevance filter (the stale-release fix)
The real fix for "don't show a v2.4 release notice to someone already on 2.4+" is
**targeting, not a type label.** The client already knows its platform + app
version, so hang optional predicates on the message and filter locally — server
stays dumb. New **optional** fields:
- `min_version` / `max_version` (semver) — show only if the client's app version
  is in range (null = unbounded).
- `platforms` (e.g. `["ios"]`; null/absent = all).

**Eligibility is computed at view/badge/toast time, NOT dropped at ingest** — a
message with `min_version: 2.5` is ineligible for a 2.4 client but must become
eligible after they update (the delta is long past their cursor by then, so the
store must still hold it). Store everything (subject to cold-start cap + prune);
filter on display using current version/platform. Ineligible messages are never
shown, counted, or toasted.
- **Web nuance:** continuously deployed → version targeting is **N/A** on web
  (treat version bounds as always-pass); platform targeting still applies.
- Needs a small semver-compare helper per platform.

### F. Category (cosmetic, small set)
Keep `level` (`info`/`warn`) as **severity/color**. Add an optional `category` for
**icon + label**, tight set: `general | release | feature | outage`. Drives a
glyph in the row/detail. Additive enum; old clients ignore unknown values.

**Glyphs are monochrome, not emoji** (emoji read as clumsy colorful stickers).
Each platform uses its native vector icon set tinted to a muted neutral so the
glyph is a quiet marker: web inline outline SVG (`currentColor`), Android
`material-icons-extended` outlined (`onSurfaceVariant`), iOS SF Symbols
(`.secondary`). Mapping: `general`→megaphone, `release`→rocket (iOS:
`shippingbox`, no rocket symbol), `feature`→sparkles, `outage`→warning triangle.

### G. Cold start — show the (targeted) backlog (supersedes decision #8)
v2 reverses #8's "pre-mark the cold batch seen." A fresh client now **sees the
active backlog as unread**, because:
- targeting (E) already prunes stale release notes (a new install on 2.5 won't
  match the 2.4 announcement's `max_version`),
- `expires_at` prunes time-bound notices,
- **Mark all read** (A) handles any residual volume.

Keep the cold-start **cap** (N active, newest-first) as a safety bound, but the
batch is now **unread**, not pre-seen.

### H. Polling tuning (no push — keep it cheap)
- **Server:** keep an in-memory `latestNotificationId` (seeded at boot, bumped on
  admin `POST`/retire). A `GET /api/notifications?since=<cursor>` with
  `cursor >= latestNotificationId` returns `{messages:[], cursor}` **without
  touching SQLite** — empty polls cost an int compare. (Per-request cost is
  dominated by routing/JSON, so this pairs with fewer requests below.)
- **Web:** drop the 5-min `setInterval`; poll on mount + focus/visibility only
  (keep the 30s focus guard), matching mobile. Idle/blurred tabs stop polling.
- **Opportunistic refresh on a real user action** ("they're looking at the phone
  and took an action"): on navigation/screen-change, call refresh **throttled to
  ~10 min** (collapses a whole session's events into a couple of polls). Pick a
  rare, user-visible event (navigation), **not** next-track. Optional/low-priority
  — the foreground trigger is the floor.

### I. Subreddit link (`r/thedeadlyapp`)
A persistent community link, **not** a message. Define `SUBREDDIT_URL`
(`https://www.reddit.com/r/thedeadlyapp`) **once per platform** (shared constant),
rendered via the § D link machinery (opens browser / Reddit app):
- **Settings:** a "Community" / "Reddit" row near About/version/legal — the
  durable home.
- **Inbox:** a quiet **footer** ("More at r/thedeadlyapp →") at the bottom of the
  list, not the top.

### Publish-side changes (server + admin web)
Targeting/category grow the authoring path too:
- **DB** (`api/src/db/users.ts`, `addColumnIfMissing`): add `category`,
  `min_version`, `max_version`, `platforms` (store `platforms` as JSON/CSV TEXT).
- **API** (`api/src/routes/notifications.ts`): accept + validate + store + serve
  the new fields on `POST` and the feed.
- **Admin page** (`ui/src/app/admin/notifications/page.tsx`): category dropdown,
  platform multiselect, optional `min/max version` inputs.

### v2 build order (extend `worktree-in-app-messaging`)
1. **Server** — schema columns + API fields + polling short-circuit.
   `make api-typecheck` + `make api-test` green.
2. **Web** (fastest loop) — link parser, targeting filter + semver, persistent
   badge, `/notifications` page, toast tap-to-open, subreddit (settings + footer),
   focus-only polling, admin compose controls. `make ui-build` + docker redeploy.
3. **Android** (builds locally) — store/model targeting + category, link renderer,
   persistent badge, inbox screen (list+detail+archive), snackbar toast, subreddit
   (settings + footer), throttled nav refresh. `make android-install`.
4. **iOS** (remote Mac) — same surface + the transient banner toast variant.
   `make ios-remote-build` / install.

### v2 exit checks (per platform)
Publish a plain message → appears unread, badge persists across opens until
read/archive/mark-all-read · open detail → marks read, links tap out to
browser/new tab · publish a `min_version` above the build → does **not** appear;
below/within range → appears · publish `platforms:["other"]` → filtered out ·
archive removes from active, stays in Archived; relaunch preserves read/archive ·
toast pings on a new delta (not cold start) and opens the inbox · subreddit link
opens from settings + inbox footer · empty polls return without a DB hit.

## The one slice worth building first

> An admin (me) writes a notification; it lands in-app on every signed-in user's
> device the next time they foreground the app.

Everything else the brainstorm raised — 1:1 messaging, friend-gating, push
notifications, opt-in toggles, compose-in-app — is **deferred** behind this. The
reasons it's the right first slice:

- **Zero new auth surface.** `accounts.is_admin` + `requireAdmin` already exist
  (`api/src/auth/middleware.ts`) and flow through the JWT/session. The author is
  always the one admin; there's no friend graph, blocklist, or per-recipient
  permission to design.
- **Zero new delivery infra.** Clients already poll `GET /api/user/sync` on
  focus/foreground (focus-refresh model — see `ROADMAP.md` Shipped). A
  notifications fetch rides the exact same trigger. No APNs/FCM, no WebSocket
  push, no background workers.
- **Zero new admin shell.** A web admin surface already exists
  (`ui/src/app/admin/{beta,analytics}`) gated on `isAdmin`. The compose form is
  one more page next to those.
- **It's genuinely useful on its own** — "heads up, v2.4 is live", "Archive.org
  is down, that's why playback is failing", "new collection added" — without any
  social graph existing yet.

## Key architectural decisions

### 1. Server is a dumb publisher; clients own seen/dismiss state (distributed)
The server holds **one flat list of published messages** and nothing per user.
It never tracks who read or dismissed what. Each client keeps its own local store
(Room on Android, SQLite/GRDB on iOS, `localStorage` on web) of:
- a **cache of messages** it has fetched,
- a **cursor** — the last message id (or `created_at`) it has seen,
- per-message **`seen_at` / `dismissed_at`**, entirely local.

The active queue ("what nags me") is computed *on the client* from its cache
minus what it has locally dismissed. This is the whole point: a broadcast to
every user is **one write** on the server (the message row) and **zero per-user
writes ever** — read/dismiss churn stays at the edge. Central load is a function
of message *volume*, not user count × message count.

> **Upgrade path if cross-device read-state is ever wanted (3 tiers):**
> 1. **v1 (here):** local-only. Per-device seen/dismiss; the accepted caveat.
> 2. **The real upgrade:** a server-side `(user_id, notification_id)` state join
>    table, reconciled local ↔ server on the focus-refresh fetch. Eventually
>    consistent, offline-safe, cheap. This is the design v1 deliberately rejects
>    (see the cross-device caveat below) — written down, not built.
> 3. **Luxury only:** push a dismiss live over the Connect-v2 WebSocket for
>    *instant* cross-device clear. A WS is **transport, not a source of truth** —
>    it only reaches devices that are online *now*, so it can't tell the device
>    that was offline at dismiss time; that always needs tier 2's durable state
>    underneath it. So WS is a thin layer *on top of* #2, never a substitute —
>    and for a dismissed announcement (vs. live playback, Connect-v2's actual
>    job) "clears next foreground" is fine, so this tier rarely earns its cost.
>    Same principle as `ConnectState`: transport only, durable state lives
>    elsewhere.

### 2. The global feed is identical for everyone → cacheable, near-zero cost
Because v1 messages are `global` and carry no per-user state, the consume
endpoint returns the **same bytes for every caller**. That makes
`GET /api/user/notifications?since=<cursor>` cacheable at the edge / behind Caddy
(short TTL, e.g. 30–60s) — the origin barely sees it. It can even be served
without auth (the content isn't user-specific). The `since` cursor keeps the
common response a tiny "nothing new" delta.

This is the floor that always works for an in-app-only model. Connect-v2's
WebSocket (`ROADMAP.md` §2) is the obvious **future** live channel — push a new
message over the open socket when it's the presence backbone — but it is *not* a
v1 dependency.

### 3. Schema carries `scope` + nullable `target_user_id` additively
v1 only ever writes `scope = 'global'`. But the message row ships with `scope`
and a nullable `target_user_id` from day one so the **1:1 / friends** case
(`ROADMAP.md` §3) is a pure additive read filter later — no migration, no
protocol break. (1:1 is the one case that *does* need per-recipient delivery, so
it reintroduces some server-side fan-out — but only for direct messages, and only
when friends exist. Global stays distributed.)

### 4. Dismiss ≠ delete; dismissed is still reviewable — all local
`seen_at` and `dismissed_at` are distinct **local** timestamps.

- **Seen** clears the unread badge/count on that device.
- **Dismiss** removes it from the *active* queue (the thing that nags you).
- Dismissed messages remain in the client's cache, shown in an **archive / "all
  notifications"** view — answering the brainstorm's "dismissable but reviewable
  after". A message leaves the cache only on local **cleanup** (see below) or when
  the server retires it (`deleted_at` / `expires_at`), which the client honors on
  its next fetch.

### 5. Client-side cleanup keeps the local cache bounded
Clients prune their local message cache on an interval / on fetch: drop messages
the server has tombstoned or expired, and optionally age out dismissed messages
older than some window (e.g. 90 days). No server involvement.

### 6. Reach/engagement via fire-and-forget analytics, not per-user rows
If we want to know how many people saw or dismissed a message, clients emit
**aggregate analytics events** ("notification_seen" / "notification_dismissed"
with the message id) through the existing analytics pipeline
(`api/src/routes/analytics.ts`). These are fire-and-forget counters for the admin
dashboard — *not* authoritative per-user state, and not what gates the client UI.
This gives reach numbers without reintroducing the central per-user store we're
avoiding. Optional; can land after the core loop.

### 7. Push is an explicit non-goal for v1
In-app only. An opt-in push channel (APNs/FCM, neither wired today) is a later
phase, behind a user setting (`user_settings` already exists as the home for such
a toggle). Don't build push infra to satisfy the first slice.

### 8. A fresh client does NOT backfill the whole history
A brand-new install has no cursor, so it must not pull years of past
announcements. The rule: **a cold client (no `since`) gets only the currently
*active* messages — unexpired, not deleted — newest-first, capped at a small N
(default ~5).** Then it records the high-water cursor and every later fetch is a
normal `?since=` delta.

This reuses `expires_at` as the "is this still relevant?" signal — which is the
reason expiry is in the schema. The effect:
- A live, unexpired notice ("Archive.org is down") *does* greet a new install —
  that's useful.
- A stale announcement from a year ago (expired or beyond the cap) does **not** —
  no flood, no confusing old context.

The client also marks that cold-start batch as **seen** (not dismissed) so a
fresh user isn't slammed with N unread badges on first launch — they appear in
the inbox but don't all nag. (Alternative considered: cold cursor = `now`, so a
new install sees *nothing* until the next message ships. Simpler, but it hides an
active announcement from the people most likely to need it — new users. Rejected
in favor of "active messages only.")

## Data model

### Server — one table, no per-user state (SQLite — `api/src/db/users.db`)
Follows the existing `CREATE TABLE IF NOT EXISTS` + `addColumnIfMissing` pattern
in `api/src/db/users.ts`. There is **no per-user state table** — that's the
distributed design's whole point.

```sql
CREATE TABLE IF NOT EXISTS notifications (
  id           INTEGER PRIMARY KEY AUTOINCREMENT, -- monotonic; doubles as the cursor
  author_id    TEXT NOT NULL REFERENCES accounts(id),
  scope        TEXT NOT NULL DEFAULT 'global', -- 'global' | 'direct' (future)
  target_user_id TEXT REFERENCES accounts(id) ON DELETE CASCADE, -- null for global
  title        TEXT NOT NULL,
  body         TEXT NOT NULL,
  level        TEXT NOT NULL DEFAULT 'info',   -- info | warn (cosmetic; reuse notify.ts vocab)
  created_at   INTEGER NOT NULL DEFAULT (unixepoch()),
  expires_at   INTEGER,                        -- optional auto-retire; drives cold-start filter
  deleted_at   INTEGER                         -- admin tombstone / unsend
);
```

A monotonic integer `id` doubles as the **cursor** clients track (`?since=<id>`),
so the common fetch is `WHERE id > ? AND scope='global' AND deleted_at IS NULL`.
A **cold client** (no `since`) instead gets active messages only:

```sql
-- cold start: active, newest-first, capped
SELECT * FROM notifications
WHERE scope = 'global' AND deleted_at IS NULL
  AND (expires_at IS NULL OR expires_at > unixepoch())
ORDER BY id DESC LIMIT 5;
```

### Client — the only place read/dismiss lives
Per platform local store (Room / GRDB-SQLite / `localStorage`):

```
message_cache(id, title, body, level, created_at, expires_at, seen_at, dismissed_at)
meta(last_seen_cursor)   -- high-water id from the last fetch
```

`seen_at` / `dismissed_at` are set locally and **never sent to the server**
(except as optional fire-and-forget analytics, decision #6). The active queue =
`message_cache WHERE dismissed_at IS NULL AND not expired`; the archive = the
whole cache. Cleanup (decision #5) prunes expired/tombstoned and aged-out rows.

### Length limits (server-enforced)
- `title` ≤ **120 chars**
- `body` ≤ **2000 chars**, plain text (or a tiny markdown subset — links/bold —
  decided at build time; start plain).

These are recommendations to close the brainstorm's "Length: ??" — generous
enough for a real announcement, bounded enough to render in a banner/inbox row.

## API surface

**Admin (compose / manage)** — all `preHandler: requireAdmin`:
- `POST   /api/admin/notifications` — create (body: title, body, level, scope,
  optional expires_at). v1 forces `scope: 'global'`.
- `GET    /api/admin/notifications` — list sent (with simple read/dismiss counts).
- `DELETE /api/admin/notifications/:id` — tombstone / "unsend".

**Consume** — one read-only, cacheable endpoint (auth optional — global content
isn't user-specific; see decision #2):
- `GET /api/notifications?since=<id>` — messages after the cursor. With no
  `since`, returns the cold-start batch (active only, capped — decision #8).
  Edge/Caddy-cacheable with a short TTL; fetched on foreground.

There are **no** server read/dismiss endpoints — that state is client-local.
The archive view is just the client's own cache; no extra call.

**Analytics (optional, decision #6)** — fire-and-forget, aggregate only:
- reuse `api/src/routes/analytics.ts` to record `notification_seen` /
  `notification_dismissed` events keyed by message id, for an admin reach count.

## Client surfaces

Minimal, in-app only. Resolve display from the payload (title/body/level/ts);
no extra fetch needed.

- **Web** (`ui/`): a bell + unread badge in the header; click → a dropdown/inbox
  list; per-item read-on-open, dismiss action; an "all notifications" archive
  route. Web is the fastest loop, so it leads (Phase 1).
- **iOS / Android**: an inbox surface (likely under Settings or a header bell to
  match web), fed by the same `GET /api/notifications`. Foreground fetch hooks
  into the same lifecycle the user-data sync already uses
  (`willEnterForeground` / `ProcessLifecycle ON_START`). Phase 2 — see the
  detailed integration points below.
- **Admin web** (`ui/src/app/admin/`): a `notifications/` page with a compose
  form (title, body, level, optional expiry) + a sent-list with counts. This is
  the only authoring surface for v1 — **compose-in-app on mobile is deferred**
  (the brainstorm's "write in app or on web" → web/admin first).

## Phasing

- **Phase 0 — this doc.** ✅ (commit it)
- **Phase 1 — global, web end-to-end.** `notifications` table + migration in
  `users.ts`; admin POST/GET/DELETE; the single cacheable consume GET; admin
  compose page; web `localStorage` cache + cursor + seen/dismiss + header bell +
  inbox + archive. Shippable and useful with only the web client.
- **Phase 2 — mobile consume.** iOS (GRDB) + Android (Room) local cache + cursor
  + seen/dismiss, inbox surface, foreground fetch against the same endpoint. No
  server change.
- **Phase 3 — social + push (depends on others).**
  - **1:1 direct messages**, gated on the **friends graph** (`ROADMAP.md` §3 —
    no backend yet). Flip on `scope: 'direct'` + `target_user_id`; the schema
    already carries it. Compose targets a friend.
  - **Opt-in push** (APNs/FCM) behind a `user_settings` toggle. Optionally
    **live delivery over the Connect-v2 WebSocket** once §2's presence backbone
    is in place.

## Phase 2 — mobile consume (design, not yet built)

Both apps are pure **consumers** of the Phase-1 endpoint — no server or protocol
change. Each re-implements what `ui/src/lib/notifications.ts` + `NotificationBell`
do on web: fetch on foreground, keep a local cache + cursor, own seen/dismiss
locally, show an inbox. The endpoint needs **no auth** (global content), so the
fetch fires regardless of sign-in state.

### Shared shape (both platforms)
- **Model** mirroring the wire shape: `id, title, body, level, created_at,
  expires_at` + local `seen_at` / `dismissed_at`.
- **Cursor** = high-water `id` pulled so far. `GET {apiBaseUrl}/api/notifications?since=<cursor>`;
  omit `since` on a fresh client → capped cold-start batch, **mark that batch
  seen-not-dismissed** (decision #8) so a new install isn't flooded with badges.
- **Merge/prune** identical to web: preserve local state across re-fetches; drop
  expired (`expires_at`) and long-dismissed messages.
- **Active queue** = not dismissed; **archive** = the rest. Unread badge = active
  & unseen.

### Encoding & rendering (verified — applies to web too)
Storing messages as JSON is safe for newlines, emoji, and arbitrary Unicode —
this was tested end-to-end, not assumed. A deliberately nasty payload (multiple
newlines + a blank line, `😀🎶`, a **ZWJ** emoji `👨‍👩‍👧‍👦`, `café`/`naïve`,
`日本語`, a tab, both quote types) round-tripped **byte-identical** through the
real path: `createNotification` → SQLite `TEXT` → `getActiveNotifications`, and
again through a client `JSON.stringify`/`parse` blob. JSON escapes `\n`, encodes
emoji as UTF-8, and `JSONEncoder` / `kotlinx.serialization` / `UserDefaults` /
DataStore all preserve them losslessly. **Storage is not the risk.** What the
implementer must get right:
- **Render newlines.** Web does (`whitespace-pre-wrap`); SwiftUI `Text` and
  Compose `Text` honor `\n` by default — just don't strip/normalize it.
- **Decode as UTF-8** (the default on both platforms).
- **Length-limit counting differs by platform (nuance, not a bug).** The server
  caps with JS `String.length` = **UTF-16 code units**, so an emoji/ZWJ sequence
  consumes more of the 120/2000 budget than its visible glyph count (the test
  body was 129 UTF-16 units / 123 code points / 160 bytes). Swift's
  `String.count` (grapheme clusters) would disagree — so a client-side counter,
  if shown, won't match the server's. The **server is the authority**; clients
  display only. Heavy-emoji titles just fit slightly fewer characters.

### Local store decision (recommended): a JSON blob in platform prefs, NOT the DB
Web uses `localStorage`. The mobile-faithful equivalent is a serialized JSON blob
in **`AppPreferences`** (Android DataStore / iOS `UserDefaults`) plus an in-memory
reactive holder (`StateFlow` / `@Published`) for the UI. Volume is tiny and the
access pattern is "load all, filter in memory", so this avoids the heavier path:
- **Android Room** would need a new entity + DAO + a **DB version bump 25 → 26 +
  an explicit `MIGRATION_25_26`** (`DeadlyDatabase` is at v25 and pointedly does
  **not** use `fallbackToDestructiveMigration` — `core/database/DeadlyDatabase.kt`).
- **iOS GRDB** would need a new `vNN-notifications` `registerMigration` in
  `Core/Database/AppDatabase.swift`.

Use the DB-table path only if we later want reactive observation à la favorites;
for v1, prefs-blob is lighter and matches web. (Pick one rule for both platforms.)

### Android integration points (builds locally on this machine — never remote)
- **HTTP:** new `NotificationApiService` + impl modeled on
  `core/usersync/UserSyncServiceImpl.kt` (OkHttp + `kotlinx.serialization`, base
  URL from `AppPreferences.apiBaseUrl`). **Omit** the `Authorization` header —
  the endpoint is public.
- **Foreground trigger:** mirror `core/usersync/UserSyncCoordinator.kt`, which
  already observes `ProcessLifecycleOwner` `ON_START` (+ cold start). Either add a
  parallel `NotificationCoordinator` (preferred — fires **unconditionally**, not
  gated on `AuthState.SignedIn`) or branch inside the existing one. Wire its
  `start()` from `DeadlyApplication.onCreate` like `userSyncCoordinator.start()`.
- **Store:** `NotificationStore` over DataStore (`AppPreferences`), exposing a
  `StateFlow<List<CachedNotification>>` for the UI.
- **UI:** an inbox under `feature/settings` (a "Notifications" `PreferenceRow` →
  a list screen with dismiss + a dismissed archive), and/or a top-bar bell.
  Settings is the lower-friction first surface.

### iOS integration points (builds on the remote Mac — `make ios-remote-install`)
- **HTTP:** reuse `Core/Network/APIClient.swift` (`get(path:)`, base URL from
  `AppPreferences.apiBaseUrl`); no token needed. A thin `NotificationsAPIClient`
  like `UserSyncAPIClient.swift`.
- **Foreground trigger:** in `App/deadlyApp.swift`, ride the existing
  **`UIApplication.willEnterForegroundNotification`** `onReceive` (already used
  there because scene-phase `.active` doesn't fire reliably under the
  `UIApplicationDelegateAdaptor` — same finding as Connect-v2) + a cold-start
  fetch like the existing sign-in/cold-start sync path.
- **Store:** a `NotificationStore` (`@Observable` / `ObservableObject`) persisting
  the JSON blob to `UserDefaults`.
- **UI:** an inbox in `Feature/Settings` (SwiftUI list, swipe-to-dismiss, a
  dismissed section) and/or a toolbar bell.

### Exit check (per platform)
Foreground a signed-out **and** signed-in build → publish from `/admin/notifications`
→ message appears on next foreground; dismiss removes it from the active list but
it stays in the archive; relaunch preserves seen/dismissed; cold install gets only
the capped active batch (not the whole history), pre-marked seen.

## Open questions

- **Authoring on mobile?** v1 says admin/web only. Is a mobile compose ever
  wanted, or is the admin always at a keyboard? (Leaning: web/admin only,
  permanently — keep mobile consume-only.)
- **Markdown vs plain text** in the body. Start plain; add a link/bold subset if
  announcements need it.
- **Per-user mute** even for global admin messages? (i.e. can a user opt out of
  *all* notifications?) Probably no for true system/operational notices; yes once
  it's social 1:1. Revisit with the §3 privacy controls.
- **Cross-device read-state (the accepted v1 caveat).** Because seen/dismiss is
  client-local, dismissing a message on your phone does **not** clear it on web —
  you may see the same notice once per device. Accepted for now (the brainstorm's
  explicit "I'm okay with that"). The documented upgrade is the rejected
  server-side state join table (decision #1); revisit alongside §3 social if it
  starts to annoy.
- **Cold-start cap + window.** N=5 active messages and the "active = unexpired"
  rule are starting values (decision #8) — tune once there's real message volume.
  Should the cold batch also have a max age independent of `expires_at`?

## References

- Admin auth: `api/src/auth/middleware.ts` (`requireAdmin`, `isAdmin`).
- Existing admin endpoints to mirror: `api/src/routes/beta.ts` (`/api/admin/...`).
- Existing admin web pages: `ui/src/app/admin/{beta,analytics}`.
- Focus-refresh delivery model: `ROADMAP.md` (Shipped — mobile↔server sync);
  `api/src/routes/user.ts` (`GET /api/user/sync`).
- Migration pattern: `api/src/db/users.ts` (`initSchema`, `addColumnIfMissing`).
- `notify.ts` is **Slack/internal only** — not a user-facing channel; level
  vocab (`info`/`warn`/`error`) is worth reusing for consistency.
- Social roadmap: `ROADMAP.md` §3 (friends graph, listening privacy);
  real-time backbone: §2 (Connect-v2).
</content>
</invoke>
