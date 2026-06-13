# ADR-0015: Cross-device notification read/dismiss state

## Status

Accepted

## Context

In-app messaging (ADR-less, designed in `PLANS/in-app-messaging.md`) ships the
server as a **dumb global publisher**: it holds one flat list of messages and
nothing per user. Each client keeps its own local store (Room on Android,
GRDB/SQLite on iOS, `localStorage` on web) of the message cache, a cursor, and
per-message `seen_at` / `dismissed_at`. Read/dismiss churn stays at the edge;
a broadcast to every user is one server write and zero per-user writes.

That decision was deliberate and remains right for *delivery*. But it has one
accepted caveat that is now being felt: **seen/dismissed state is per-device.**
A different browser, a second phone, incognito, or cleared storage all start
from a fresh cold-start and re-surface the entire active backlog as unread. On
web specifically, where state lives in `localStorage` keyed per browser, a user
who reads a message on their laptop still sees it unread on their phone.

`PLANS/in-app-messaging.md` anticipated exactly this and wrote down a 3-tier
upgrade path (decision #1):

1. **v1 (shipped):** local-only. Per-device seen/dismiss. The accepted caveat.
2. **The real upgrade:** a server-side `(user_id, notification_id)` state join
   table, reconciled local ↔ server on the focus-refresh fetch. Eventually
   consistent, offline-safe, cheap.
3. **Luxury only:** push a dismiss live over the Connect-v2 WebSocket for
   instant cross-device clear — a thin layer *on top of* tier 2, never a
   substitute, and rarely worth its cost for dismiss state.

This ADR promotes **tier 2** from "written down, not built" to the decision of
record. Tier 3 stays explicitly out of scope.

## Decision

Add a per-user notification state overlay that syncs through the **existing**
authenticated user-data sync lifecycle. The message feed itself does not change.

### Server — one new table, rides `/api/user/sync`

New table in `api/src/db/users.ts` (`initSchema`):

```sql
CREATE TABLE IF NOT EXISTS notification_state (
  user_id         TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  notification_id INTEGER NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  seen_at         INTEGER,            -- unix seconds; null = unread
  dismissed_at    INTEGER,            -- unix seconds; null = active (not archived)
  updated_at      INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY (user_id, notification_id)
);
```

- `notification_id` FKs the **public** `notifications` table. `ON DELETE
  CASCADE` means an admin unsend/retire automatically reaps the per-user
  overlay; clients already prune the message itself on the next feed fetch.
- This is the **first and only** per-user notification state. It is reached
  exclusively through the authed sync path — never through the public consume
  feed (see Consequences).

State carriers in `api/src/db/userdata.ts`:

- Extend `BackupV3` with an **optional** `notificationState?: NotificationStateV3[]`
  (same additive move as `recentShows` / `playbackPosition` — **no version
  bump**, so older clients that omit the field are unaffected). This is the
  **pull/bulk** path.
- `getFullBackupV3` includes the user's full state rows (no tombstones needed —
  see merge below).
- `importFullBackupV3` upserts each row with **union merge**, not last-write-wins.

Granular **push** endpoints (the eager path — mirrors how `toggleFavorite`
fires an immediate PUT/DELETE rather than waiting for focus):

- `POST /api/user/notifications/:id/state` — body `{ seenAt?, dismissedAt? }`.
  Union-upserts one row. Backs `markRead` and `archive`.
- `POST /api/user/notifications/state` — body `{ seenAt?, dismissedAt?, ids? }`;
  omitting `ids` means "every currently-active message for this user." Backs the
  bulk `markAllRead` / `archiveAll` (favorites has no bulk equivalent, so this
  is new surface). One round-trip instead of N.

Both are `requireAuth`, never touch the public consume feed, and use the same
union-merge upsert as the bulk import.

### Merge is conflict-free union, not LWW

`seen_at` and `dismissed_at` are **monotonic**: each goes `null → timestamp`
exactly once and never back. So there is no genuine conflict to resolve — a
message is *seen* if it was seen on **any** device, *dismissed* if dismissed on
**any** device. Merge coalesces to the **earliest** non-null timestamp on each
column independently:

```sql
INSERT INTO notification_state (user_id, notification_id, seen_at, dismissed_at, updated_at)
VALUES (?, ?, ?, ?, unixepoch())
ON CONFLICT(user_id, notification_id) DO UPDATE SET
  seen_at      = MIN(COALESCE(notification_state.seen_at,      excluded.seen_at),
                     COALESCE(excluded.seen_at,      notification_state.seen_at)),
  dismissed_at = MIN(COALESCE(notification_state.dismissed_at, excluded.dismissed_at),
                     COALESCE(excluded.dismissed_at, notification_state.dismissed_at)),
  updated_at   = unixepoch();
```

(`MIN` over `COALESCE` keeps a non-null value once either side has one and
prefers the earliest read/dismiss instant.) This needs **no tombstones** and no
clock-skew arbitration — a strictly weaker requirement than the favorites
sync's LWW + tombstone scheme.

### Clients — local-first, server-mirrored (the Favorites shape)

The local store stays the source of truth for the UI and the **only** store for
logged-out users. For authed users it becomes a cache that reconciles with the
server. Two complementary paths keep every device uniform:

- **Eager push on the action.** `markRead`, `archive`, `markAllRead`, and
  `archiveAll` each fire their granular/bulk endpoint immediately, optimistic-
  local-first, exactly like `toggleFavorite` (UserDataProvider's
  `updateFavoriteShow(...).then(...)`). The user's intent leaves the device the
  moment they act — not on the next focus event. A failed push (offline) is
  caught by the focus-refresh below.
- **Pull/merge on focus.** On focus/foreground the client unions the server's
  `notificationState` into its local per-message `seen_at` / `dismissed_at`
  (same monotonic coalesce as the server) and flushes any local state the server
  hasn't acknowledged. This is the catch-up + cross-device convergence path.

**A new device must not display already-handled messages.** Two rules make the
first paint correct, not merely self-correcting:

1. **State-before-surface on cold start.** For a signed-in client, the first
   feed fetch and the first state pull are awaited **together** before the badge
   count, the inbox list, and any toast are computed. The unread/active set is
   derived from `feed ⋈ serverState`, so a message already read or archived on
   another device renders read/archived on its **first** appearance — no
   unread-then-clear flash.
2. **No cold-start toasts** (existing decision C, retained): the
   cold-start backlog never toasts; only genuine deltas after the cursor do. So
   a new device is quiet on launch even before convergence.

Messages the user has *genuinely never touched on any device* still legitimately
show as unread — that is correct, not "old spam." "Old messages" the user has
already dealt with are what gets suppressed.

Per platform:

- **Web** — `ui/src/lib/notifications.ts` gains `toSyncState()` /
  `mergeSyncState()`. `NotificationsProvider`'s `markRead` / `archive` /
  `markAllRead` / `archiveAll` add the eager POST (optimistic-local-first, like
  `toggleFavorite`); the focus pull/merge joins the existing user-data refetch.
  Cold-start `refresh("cold_start")` awaits the state pull before computing
  `unreadCount` / `activeMessages`. `localStorage` schema unchanged.
- **iOS** — `NotificationStore` mutations call the push via `UserSyncAPIClient`;
  `UserSyncApplyService` does the union merge. The inbox's first load gates on
  the state pull.
- **Android** — `core/notifications` store mutations push via
  `core/api/usersync`; merge in the `core/usersync` apply step; first inbox load
  gates on the state pull.

## Consequences

**Gained**

- Read/dismiss state follows the user across devices and browsers. The web
  "I keep seeing it on every device" re-spam goes away once signed in.
- Reuses the entire existing sync lifecycle, auth, and offline behavior — no new
  polling, no new transport, no protocol version bump.
- Merge is simpler than every other synced collection (no tombstones, no LWW).

**Accepted**

- The acting device pushes **immediately**; *other* devices converge on their
  next focus-refresh (eventually consistent), consistent with all other user
  data. A brand-new device, by the state-before-surface rule, is correct on
  first paint — it never shows the flash. (Tier 3 / WebSocket push for *instant*
  fan-out to already-open devices is still explicitly *not* adopted; "clears next
  foreground" is fine for dismiss state.)
- Logged-out users keep per-device state — there is no anonymous identity to
  sync against. This is unchanged from today.
- We introduce the first per-user notification rows. Write volume is bounded by
  *(users who read messages) × (messages they touch)* — the central store the
  publish path deliberately avoids. It is acceptable here because it rides the
  authed, already-rate-limited sync path and never the cacheable public feed.

**Invariant preserved**

- `GET /api/notifications` stays global, identical-for-everyone, and
  edge-cacheable. Per-user state is reachable **only** through authed
  `/api/user/sync`. Mixing it into the consume feed would break the cache and is
  prohibited by this ADR.

## Alternatives considered

- **Do nothing (stay tier 1).** Cheapest, but the per-device re-spam is the
  exact pain being reported, and it worsens as a user adds devices.
- **Dedicated `/api/user/notifications/state` endpoint** instead of folding into
  `BackupV3`. Cleaner in isolation but adds a second per-user sync round-trip and
  a second coordinator on every platform. Folding into the existing V3 sync is
  one trip and reuses code already wired on all three clients.
- **Last-write-wins with `updated_at`** (mirroring favorites). Works, but is
  strictly more machinery than the data needs: monotonic null→timestamp columns
  are conflict-free under union, so LWW + tombstones would be dead weight.
- **Tier 3 WebSocket push for instant clear.** A WS reaches only devices online
  *now*, so it can never inform the device that was offline at dismiss time —
  that always needs tier 2's durable state underneath. For dismiss state the
  instant-clear payoff doesn't earn the cost; deferred indefinitely.
