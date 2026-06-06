import { getUsersDb } from "./users.js";

// In-app messaging — server-side data access. The server only stores the
// published message list; per-user seen/dismissed state lives on each client.
// See PLANS/in-app-messaging.md.

export type NotificationLevel = "info" | "warn";
export type NotificationScope = "global" | "direct";

export interface Notification {
  id: number;
  author_id: string;
  scope: NotificationScope;
  target_user_id: string | null;
  title: string;
  body: string;
  level: NotificationLevel;
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
  created_at: number;
  expires_at: number | null;
}

export const TITLE_MAX = 120;
export const BODY_MAX = 2000;
/** Cap on the cold-start batch a brand-new client (no cursor) receives. */
export const COLD_START_LIMIT = 5;

export interface CreateNotificationInput {
  authorId: string;
  title: string;
  body: string;
  level?: NotificationLevel;
  expiresAt?: number | null;
  // v1 only ever writes a global message; scope/target are carried for the
  // future 1:1 (friends) case so it slots in without a migration.
  scope?: NotificationScope;
  targetUserId?: string | null;
}

export function createNotification(input: CreateNotificationInput): Notification {
  const db = getUsersDb();
  const result = db.prepare(
    `INSERT INTO notifications (author_id, scope, target_user_id, title, body, level, expires_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    input.authorId,
    input.scope ?? "global",
    input.targetUserId ?? null,
    input.title,
    input.body,
    input.level ?? "info",
    input.expiresAt ?? null,
  );
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
  return res.changes > 0;
}

/**
 * Delta fetch: global messages newer than the client's cursor. Tombstoned and
 * expired rows are filtered out so a returning client drops them from its cache.
 */
export function getNotificationsSince(cursor: number): NotificationPublic[] {
  const db = getUsersDb();
  return db.prepare(
    `SELECT id, title, body, level, created_at, expires_at
       FROM notifications
      WHERE scope = 'global'
        AND id > ?
        AND deleted_at IS NULL
        AND (expires_at IS NULL OR expires_at > unixepoch())
      ORDER BY id ASC`,
  ).all(cursor) as NotificationPublic[];
}

/**
 * Cold start (no cursor): only currently-active messages, newest first, capped.
 * A fresh install sees a live notice but never the full history.
 */
export function getActiveNotifications(limit = COLD_START_LIMIT): NotificationPublic[] {
  const db = getUsersDb();
  return db.prepare(
    `SELECT id, title, body, level, created_at, expires_at
       FROM notifications
      WHERE scope = 'global'
        AND deleted_at IS NULL
        AND (expires_at IS NULL OR expires_at > unixepoch())
      ORDER BY id DESC
      LIMIT ?`,
  ).all(limit) as NotificationPublic[];
}
