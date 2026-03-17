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
      PRIMARY KEY (user_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS favorite_songs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      track_title TEXT NOT NULL,
      track_number INTEGER,
      recording_id TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
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
      PRIMARY KEY (user_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS recent_shows (
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      last_played_at INTEGER NOT NULL,
      first_played_at INTEGER NOT NULL,
      total_play_count INTEGER NOT NULL DEFAULT 1,
      PRIMARY KEY (user_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS playback_position (
      user_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL,
      recording_id TEXT NOT NULL,
      track_index INTEGER NOT NULL,
      position_ms INTEGER NOT NULL,
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
  `);
}

export function createAppUser(authUserId: string, email: string, name: string | null, provider: string): string {
  const db = getUsersDb();
  const id = crypto.randomUUID();
  db.prepare(
    `INSERT INTO accounts (id, auth_user_id, email, name, provider) VALUES (?, ?, ?, ?, ?)`
  ).run(id, authUserId, email, name, provider);
  return id;
}

export function getAppUserByAuthId(authUserId: string): { id: string; email: string; name: string | null } | undefined {
  const db = getUsersDb();
  return db.prepare(
    `SELECT id, email, name FROM accounts WHERE auth_user_id = ?`
  ).get(authUserId) as { id: string; email: string; name: string | null } | undefined;
}

export function closeUsersDb(): void {
  if (db) {
    db.close();
    db = null;
  }
}
