import Database from "better-sqlite3";
import crypto from "node:crypto";
import path from "node:path";

const DB_PATH = process.env.USERS_DB_PATH ?? path.join(process.cwd(), "data", "users.db");

let db: Database.Database | null = null;

export function getUsersDb(): Database.Database {
  if (db) return db;

  db = new Database(DB_PATH);
  db.pragma("journal_mode = WAL");
  db.pragma("busy_timeout = 5000");
  db.pragma("foreign_keys = ON");

  initSchema(db);
  return db;
}

function initSchema(db: Database.Database): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS auth_users (
      id TEXT PRIMARY KEY,
      name TEXT,
      email TEXT UNIQUE,
      "emailVerified" TIMESTAMP,
      image TEXT
    );

    CREATE TABLE IF NOT EXISTS auth_accounts (
      id TEXT PRIMARY KEY,
      "userId" TEXT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
      type TEXT NOT NULL,
      provider TEXT NOT NULL,
      "providerAccountId" TEXT NOT NULL,
      refresh_token TEXT,
      access_token TEXT,
      expires_at INTEGER,
      token_type TEXT,
      scope TEXT,
      id_token TEXT,
      session_state TEXT,
      UNIQUE(provider, "providerAccountId")
    );

    CREATE TABLE IF NOT EXISTS accounts (
      id TEXT PRIMARY KEY,
      auth_user_id TEXT UNIQUE REFERENCES auth_users(id) ON DELETE SET NULL,
      email TEXT UNIQUE NOT NULL,
      name TEXT,
      provider TEXT NOT NULL,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE TABLE IF NOT EXISTS favorite_shows (
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      added_at INTEGER NOT NULL DEFAULT (unixepoch()),
      is_pinned INTEGER NOT NULL DEFAULT 0,
      notes TEXT,
      preferred_recording_id TEXT,
      downloaded_recording_id TEXT,
      downloaded_format TEXT,
      recording_quality INTEGER,
      playing_quality INTEGER,
      custom_rating REAL,
      last_accessed_at INTEGER,
      tags TEXT,
      updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
      deleted_at INTEGER,
      PRIMARY KEY (user_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS favorite_songs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      track_title TEXT NOT NULL,
      track_number INTEGER,
      recording_id TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch()),
      updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
      deleted_at INTEGER
    );

    CREATE TABLE IF NOT EXISTS show_reviews (
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      notes TEXT,
      overall_rating REAL,
      recording_quality INTEGER,
      playing_quality INTEGER,
      reviewed_recording_id TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch()),
      updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
      deleted_at INTEGER,
      PRIMARY KEY (user_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS show_player_tags (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      player_name TEXT NOT NULL,
      instruments TEXT,
      is_standout INTEGER NOT NULL DEFAULT 0,
      notes TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE TABLE IF NOT EXISTS recording_preferences (
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      recording_id TEXT NOT NULL,
      updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
      deleted_at INTEGER,
      PRIMARY KEY (user_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS recent_shows (
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      last_played_at INTEGER NOT NULL,
      first_played_at INTEGER NOT NULL,
      total_play_count INTEGER NOT NULL DEFAULT 1,
      deleted_at INTEGER,
      PRIMARY KEY (user_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS backlog (
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      position INTEGER NOT NULL,
      added_at INTEGER NOT NULL DEFAULT (unixepoch()),
      updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
      deleted_at INTEGER,
      PRIMARY KEY (user_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS playback_position (
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      recording_id TEXT NOT NULL,
      track_index INTEGER NOT NULL,
      position_ms INTEGER NOT NULL,
      date TEXT,
      venue TEXT,
      location TEXT,
      updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
      PRIMARY KEY (user_id)
    );

    CREATE TABLE IF NOT EXISTS user_settings (
      user_id TEXT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
      include_shows_without_recordings INTEGER NOT NULL DEFAULT 0,
      favorites_display_mode TEXT NOT NULL DEFAULT 'LIST',
      force_online INTEGER NOT NULL DEFAULT 0,
      source_badge_style TEXT NOT NULL DEFAULT 'LONG',
      share_attach_image INTEGER NOT NULL DEFAULT 0,
      eq_enabled INTEGER NOT NULL DEFAULT 0,
      eq_preset TEXT,
      eq_band_levels TEXT,
      updated_at INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE TABLE IF NOT EXISTS beta_applicants (
      id TEXT PRIMARY KEY,
      email TEXT UNIQUE NOT NULL COLLATE NOCASE,
      first_name TEXT,
      last_name TEXT,
      status TEXT NOT NULL,
      asc_invitation_id TEXT,
      asc_user_id TEXT,
      last_error TEXT,
      ip_address TEXT,
      user_agent TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch()),
      invited_at INTEGER,
      member_at INTEGER,
      installed_at INTEGER,
      removed_at INTEGER
    );

    CREATE TABLE IF NOT EXISTS beta_settings (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );

    -- In-app messaging: the server is a dumb publisher of a flat message list.
    -- The MESSAGE feed carries no per-user state — a global broadcast is one
    -- row, never one-per-account, and the monotonic integer id doubles as the
    -- cursor clients track (?since=<id>). Per-user seen/dismissed lives on each
    -- client AND, for signed-in users, in notification_state below (ADR-0015).
    -- See PLANS/in-app-messaging.md.
    CREATE TABLE IF NOT EXISTS notifications (
      id             INTEGER PRIMARY KEY AUTOINCREMENT,
      author_id      TEXT NOT NULL REFERENCES accounts(id),
      scope          TEXT NOT NULL DEFAULT 'global',  -- 'global' | 'direct' (future)
      target_user_id TEXT REFERENCES accounts(id) ON DELETE CASCADE, -- null for global
      title          TEXT NOT NULL,
      body           TEXT NOT NULL,
      level          TEXT NOT NULL DEFAULT 'info',     -- info | warn (severity/color)
      category       TEXT NOT NULL DEFAULT 'general',  -- general | release | feature | outage (cosmetic glyph)
      min_version    TEXT,                             -- semver lower bound; clients filter locally
      max_version    TEXT,                             -- semver upper bound; clients filter locally
      platforms      TEXT,                             -- JSON array e.g. ["ios","android"]; null = all
      created_at     INTEGER NOT NULL DEFAULT (unixepoch()),
      expires_at     INTEGER,                          -- optional auto-retire; drives cold-start filter
      deleted_at     INTEGER                           -- admin tombstone / unsend
    );

    CREATE INDEX IF NOT EXISTS idx_notifications_scope_id
      ON notifications(scope, id);

    -- Per-user notification read/dismiss overlay (ADR-0015). Synced through the
    -- authed /api/user/sync path, NEVER the public consume feed. seen_at and
    -- dismissed_at are monotonic (null -> timestamp, once) so merge is a
    -- conflict-free union (earliest non-null wins) — no tombstones needed.
    -- CASCADE on notification_id reaps the overlay when an admin unsends.
    CREATE TABLE IF NOT EXISTS notification_state (
      user_id         TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      notification_id INTEGER NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
      seen_at         INTEGER,            -- unix seconds; null = unread
      dismissed_at    INTEGER,            -- unix seconds; null = active (not archived)
      updated_at      INTEGER NOT NULL DEFAULT (unixepoch()),
      PRIMARY KEY (user_id, notification_id)
    );
  `);

  db.exec(`
    INSERT OR IGNORE INTO beta_settings (key, value) VALUES ('auto_approve', 'false');
    INSERT OR IGNORE INTO beta_settings (key, value) VALUES ('slot_cap', '100');
    INSERT OR IGNORE INTO beta_settings (key, value) VALUES ('accepting_applications', 'false');
    INSERT OR IGNORE INTO beta_settings (key, value) VALUES ('sync_enabled', 'false');
  `);

  // Migrations: add columns to existing tables
  const accountCols = db.prepare(
    `SELECT name FROM pragma_table_info('accounts')`
  ).all() as { name: string }[];
  const accountColNames = new Set(accountCols.map((c) => c.name));
  if (!accountColNames.has("is_admin")) {
    db.exec(`ALTER TABLE accounts ADD COLUMN is_admin INTEGER NOT NULL DEFAULT 0`);
  }
  // Custom profile picture: a small (client-downscaled) image stored inline as
  // a BLOB. avatar_updated_at versions the public GET URL so it's immutably
  // cacheable. NULL avatar = fall back to the OAuth picture.
  if (!accountColNames.has("avatar")) {
    db.exec(`ALTER TABLE accounts ADD COLUMN avatar BLOB`);
  }
  if (!accountColNames.has("avatar_mime")) {
    db.exec(`ALTER TABLE accounts ADD COLUMN avatar_mime TEXT`);
  }
  if (!accountColNames.has("avatar_updated_at")) {
    db.exec(`ALTER TABLE accounts ADD COLUMN avatar_updated_at INTEGER`);
  }

  const cols = db.prepare(
    `SELECT name FROM pragma_table_info('playback_position')`
  ).all() as { name: string }[];
  const colNames = new Set(cols.map((c) => c.name));
  if (!colNames.has("date")) {
    db.exec(`ALTER TABLE playback_position ADD COLUMN date TEXT`);
  }
  if (!colNames.has("venue")) {
    db.exec(`ALTER TABLE playback_position ADD COLUMN venue TEXT`);
  }
  if (!colNames.has("location")) {
    db.exec(`ALTER TABLE playback_position ADD COLUMN location TEXT`);
  }

  // ── Sync support: per-row updated_at and deleted_at (tombstones) ────
  // Each user-data table needs an LWW comparator and a way to communicate
  // deletions across devices. Singleton tables (playback_position,
  // user_settings) don't need tombstones because the row is replaced
  // wholesale. show_player_tags doesn't need them — tags travel with
  // their parent review.
  //
  // SQLite forbids non-constant defaults on ALTER TABLE ADD COLUMN, so
  // updated_at columns are added with DEFAULT 0 then backfilled.
  const addColumnIfMissing = (table: string, column: string, ddl: string) => {
    const c = db!.prepare(`SELECT name FROM pragma_table_info('${table}')`).all() as { name: string }[];
    if (!c.some((r) => r.name === column)) {
      db!.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${ddl}`);
    }
  };

  addColumnIfMissing("favorite_shows", "updated_at", "INTEGER NOT NULL DEFAULT 0");
  addColumnIfMissing("favorite_shows", "deleted_at", "INTEGER");
  addColumnIfMissing("favorite_songs", "updated_at", "INTEGER NOT NULL DEFAULT 0");
  addColumnIfMissing("favorite_songs", "deleted_at", "INTEGER");
  addColumnIfMissing("show_reviews", "deleted_at", "INTEGER");
  addColumnIfMissing("recording_preferences", "deleted_at", "INTEGER");
  addColumnIfMissing("recent_shows", "deleted_at", "INTEGER");

  // Account tombstone: a deleted account keeps its row (and orphaned data)
  // but is treated as gone — the getters below filter deleted_at IS NULL, so
  // every auth path rejects it. Re-signing in reactivates (see adapter).
  addColumnIfMissing("accounts", "deleted_at", "INTEGER");

  // Notifications v2: category (cosmetic glyph) + client-side targeting metadata
  // (semver range + platform list). All optional/additive — the server stores
  // and serves them; each client filters locally. See PLANS/in-app-messaging.md.
  addColumnIfMissing("notifications", "category", "TEXT NOT NULL DEFAULT 'general'");
  addColumnIfMissing("notifications", "min_version", "TEXT");
  addColumnIfMissing("notifications", "max_version", "TEXT");
  addColumnIfMissing("notifications", "platforms", "TEXT");

  db.exec(`UPDATE favorite_shows SET updated_at = unixepoch() WHERE updated_at = 0`);
  db.exec(`UPDATE favorite_songs SET updated_at = unixepoch() WHERE updated_at = 0`);
}

export function createAppUser(authUserId: string, email: string, name: string | null, provider: string): string {
  const db = getUsersDb();
  const id = crypto.randomUUID();
  db.prepare(
    `INSERT INTO accounts (id, auth_user_id, email, name, provider) VALUES (?, ?, ?, ?, ?)`
  ).run(id, authUserId, email, name, provider);
  return id;
}

export function getAppUserByAuthId(authUserId: string): { id: string; email: string; name: string | null; is_admin: number; avatar_updated_at: number | null } | undefined {
  const db = getUsersDb();
  return db.prepare(
    `SELECT id, email, name, is_admin, avatar_updated_at FROM accounts WHERE auth_user_id = ? AND deleted_at IS NULL`
  ).get(authUserId) as { id: string; email: string; name: string | null; is_admin: number; avatar_updated_at: number | null } | undefined;
}

export function getAppUserById(accountId: string): { id: string; email: string; name: string | null; is_admin: number } | undefined {
  const db = getUsersDb();
  return db.prepare(
    `SELECT id, email, name, is_admin FROM accounts WHERE id = ? AND deleted_at IS NULL`
  ).get(accountId) as { id: string; email: string; name: string | null; is_admin: number } | undefined;
}

export function getAppUserByEmail(email: string): { id: string; email: string; name: string | null; is_admin: number } | undefined {
  const db = getUsersDb();
  return db.prepare(
    `SELECT id, email, name, is_admin FROM accounts WHERE email = ? AND deleted_at IS NULL`
  ).get(email) as { id: string; email: string; name: string | null; is_admin: number } | undefined;
}

/** Tombstone an account. Idempotent; returns true if a live account was deleted. */
export function deleteAppUser(accountId: string): boolean {
  const db = getUsersDb();
  const res = db.prepare(
    `UPDATE accounts SET deleted_at = unixepoch() WHERE id = ? AND deleted_at IS NULL`
  ).run(accountId);
  return res.changes > 0;
}

/**
 * Update the account's display name. `accounts.name` is what the JWT callback
 * reads into the session, so this is the source of truth for the shown name.
 */
export function updateAppUserName(accountId: string, name: string): boolean {
  const db = getUsersDb();
  const res = db.prepare(
    `UPDATE accounts SET name = ? WHERE id = ? AND deleted_at IS NULL`
  ).run(name, accountId);
  return res.changes > 0;
}

/**
 * Store a custom profile picture (already downscaled by the client) inline on
 * the account. `avatar_updated_at` versions the public GET URL for caching.
 */
export function setAppUserAvatar(accountId: string, bytes: Buffer, mime: string): boolean {
  const db = getUsersDb();
  const res = db.prepare(
    `UPDATE accounts SET avatar = ?, avatar_mime = ?, avatar_updated_at = unixepoch() WHERE id = ? AND deleted_at IS NULL`
  ).run(bytes, mime, accountId);
  return res.changes > 0;
}

/** Remove a custom profile picture (reverts to the OAuth picture). */
export function clearAppUserAvatar(accountId: string): boolean {
  const db = getUsersDb();
  const res = db.prepare(
    `UPDATE accounts SET avatar = NULL, avatar_mime = NULL, avatar_updated_at = NULL WHERE id = ? AND deleted_at IS NULL`
  ).run(accountId);
  return res.changes > 0;
}

/** Read a custom profile picture's bytes + mime, or undefined if none set. */
export function getAppUserAvatar(accountId: string): { bytes: Buffer; mime: string } | undefined {
  const db = getUsersDb();
  const row = db.prepare(
    `SELECT avatar, avatar_mime FROM accounts WHERE id = ? AND deleted_at IS NULL`
  ).get(accountId) as { avatar: Buffer | null; avatar_mime: string | null } | undefined;
  if (!row?.avatar || !row.avatar_mime) return undefined;
  return { bytes: row.avatar, mime: row.avatar_mime };
}

/** The OAuth-provided profile image for an auth user, if any. */
export function getAuthUserImage(authUserId: string): string | null {
  const db = getUsersDb();
  const row = db.prepare(
    `SELECT image FROM auth_users WHERE id = ?`
  ).get(authUserId) as { image: string | null } | undefined;
  return row?.image ?? null;
}

/** Clear an account's tombstone (reactivate on re-sign-in). */
export function reactivateAppUserByAuthId(authUserId: string): void {
  const db = getUsersDb();
  db.prepare(
    `UPDATE accounts SET deleted_at = NULL WHERE auth_user_id = ? AND deleted_at IS NOT NULL`
  ).run(authUserId);
}

export function closeUsersDb(): void {
  if (db) {
    db.close();
    db = null;
  }
}
