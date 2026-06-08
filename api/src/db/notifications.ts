import { getUsersDb } from "./users.js";

// In-app messaging — server-side data access. The server only stores the
// published message list; per-user seen/dismissed state lives on each client.
// See PLANS/in-app-messaging.md.

export type NotificationLevel = "info" | "warn";
export type NotificationScope = "global" | "direct";
export type NotificationCategory = "general" | "release" | "feature" | "outage";

export const CATEGORIES: NotificationCategory[] = ["general", "release", "feature", "outage"];
/** Platforms a message can target. `web` covered too; null/[] = all. */
export const PLATFORMS = ["ios", "android", "web"] as const;
export type NotificationPlatform = (typeof PLATFORMS)[number];

export interface Notification {
  id: number;
  author_id: string;
  scope: NotificationScope;
  target_user_id: string | null;
  title: string;
  body: string;
  level: NotificationLevel;
  category: NotificationCategory;
  min_version: string | null;
  max_version: string | null;
  platforms: string | null; // JSON array string as stored
  created_at: number;
  expires_at: number | null;
  deleted_at: number | null;
}

/** The public shape sent to clients — no internal/author fields. */
export interface NotificationPublic {
  id: number;
  title: string;
  body: string;
  level: NotificationLevel;
  category: NotificationCategory;
  min_version: string | null;
  max_version: string | null;
  platforms: NotificationPlatform[] | null; // parsed; null = all platforms
  created_at: number;
  expires_at: number | null;
}

export const TITLE_MAX = 120;
export const BODY_MAX = 2000;
/** Cap on the cold-start batch a brand-new client (no cursor) receives. */
export const COLD_START_LIMIT = 5;

/** Columns selected for the public/wire shape (shared by delta + cold start). */
const PUBLIC_COLUMNS =
  "id, title, body, level, category, min_version, max_version, platforms, created_at, expires_at";

/** Parse a stored `platforms` JSON string into a clean platform array or null. */
function parsePlatforms(raw: string | null): NotificationPlatform[] | null {
  if (!raw) return null;
  try {
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return null;
    const valid = arr.filter((p): p is NotificationPlatform =>
      (PLATFORMS as readonly string[]).includes(p),
    );
    return valid.length > 0 ? valid : null;
  } catch {
    return null;
  }
}

/** Map a raw DB row (with stored `platforms` string) to the public wire shape. */
function toPublic(row: Record<string, unknown>): NotificationPublic {
  return {
    id: row.id as number,
    title: row.title as string,
    body: row.body as string,
    level: row.level as NotificationLevel,
    category: (row.category as NotificationCategory) ?? "general",
    min_version: (row.min_version as string | null) ?? null,
    max_version: (row.max_version as string | null) ?? null,
    platforms: parsePlatforms((row.platforms as string | null) ?? null),
    created_at: row.created_at as number,
    expires_at: (row.expires_at as number | null) ?? null,
  };
}

export interface CreateNotificationInput {
  authorId: string;
  title: string;
  body: string;
  level?: NotificationLevel;
  category?: NotificationCategory;
  // Client-side targeting (optional). The server stores them verbatim; each
  // client filters locally against its own app version + platform.
  minVersion?: string | null;
  maxVersion?: string | null;
  platforms?: NotificationPlatform[] | null;
  expiresAt?: number | null;
  // v1 only ever writes a global message; scope/target are carried for the
  // future 1:1 (friends) case so it slots in without a migration.
  scope?: NotificationScope;
  targetUserId?: string | null;
}

export function createNotification(input: CreateNotificationInput): Notification {
  const db = getUsersDb();
  const platformsJson =
    input.platforms && input.platforms.length > 0 ? JSON.stringify(input.platforms) : null;
  const result = db.prepare(
    `INSERT INTO notifications
       (author_id, scope, target_user_id, title, body, level, category, min_version, max_version, platforms, expires_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    input.authorId,
    input.scope ?? "global",
    input.targetUserId ?? null,
    input.title,
    input.body,
    input.level ?? "info",
    input.category ?? "general",
    input.minVersion ?? null,
    input.maxVersion ?? null,
    platformsJson,
    input.expiresAt ?? null,
  );
  bumpLatestId();
  return getNotificationById(Number(result.lastInsertRowid))!;
}

export function getNotificationById(id: number): Notification | undefined {
  const db = getUsersDb();
  return db.prepare(`SELECT * FROM notifications WHERE id = ?`).get(id) as Notification | undefined;
}

/** Admin view: every global message, newest first (includes expired/tombstoned). */
export function listNotificationsForAdmin(): Notification[] {
  const db = getUsersDb();
  return db.prepare(
    `SELECT * FROM notifications WHERE scope = 'global' ORDER BY id DESC`,
  ).all() as Notification[];
}

/** Tombstone a message ("unsend"). Idempotent; true if a live row was retired. */
export function deleteNotification(id: number): boolean {
  const db = getUsersDb();
  const res = db.prepare(
    `UPDATE notifications SET deleted_at = unixepoch() WHERE id = ? AND deleted_at IS NULL`,
  ).run(id);
  if (res.changes > 0) bumpLatestId();
  return res.changes > 0;
}

/**
 * Delta fetch: global messages newer than the client's cursor. Tombstoned and
 * expired rows are filtered out so a returning client drops them from its cache.
 */
export function getNotificationsSince(cursor: number): NotificationPublic[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT ${PUBLIC_COLUMNS}
       FROM notifications
      WHERE scope = 'global'
        AND id > ?
        AND deleted_at IS NULL
        AND (expires_at IS NULL OR expires_at > unixepoch())
      ORDER BY id ASC`,
  ).all(cursor) as Record<string, unknown>[];
  return rows.map(toPublic);
}

/**
 * Cold start (no cursor): only currently-active messages, newest first, capped.
 * A fresh install sees a live notice but never the full history.
 */
export function getActiveNotifications(limit = COLD_START_LIMIT): NotificationPublic[] {
  const db = getUsersDb();
  const rows = db.prepare(
    `SELECT ${PUBLIC_COLUMNS}
       FROM notifications
      WHERE scope = 'global'
        AND deleted_at IS NULL
        AND (expires_at IS NULL OR expires_at > unixepoch())
      ORDER BY id DESC
      LIMIT ?`,
  ).all(limit) as Record<string, unknown>[];
  return rows.map(toPublic);
}

// ── In-memory high-water id (polling short-circuit) ────────────────────────
// The notifications table only changes when an admin publishes or retires, so
// the latest id is cheap to cache. A `?since=<cursor>` poll with cursor >= this
// can skip SQLite entirely and return an empty delta — empty polls (the 99%
// case under many active clients) cost an integer compare, no DB hit.
let cachedLatestId: number | null = null;

function computeLatestId(): number {
  const db = getUsersDb();
  const row = db.prepare(
    `SELECT MAX(id) AS maxId FROM notifications WHERE scope = 'global' AND deleted_at IS NULL`,
  ).get() as { maxId: number | null };
  return row.maxId ?? 0;
}

/** The newest live global message id; lazily seeded, kept warm on mutations. */
export function getLatestNotificationId(): number {
  if (cachedLatestId === null) cachedLatestId = computeLatestId();
  return cachedLatestId;
}

/** Invalidate/advance the cache after a publish or retire. */
function bumpLatestId(): void {
  cachedLatestId = computeLatestId();
}
