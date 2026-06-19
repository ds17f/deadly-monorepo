import { getUsersDb } from "./users.js";

// Global, admin-controlled feature flags persisted in the `app_settings` table.
// A missing row falls back to the code-side default below, so a fresh database
// (or a key an admin has never touched) behaves predictably across restarts.

const CONNECT_ENABLED_KEY = "connect_enabled";

// Connect (cross-device playback sync) ships OFF by default. An admin must
// explicitly enable it; until then the server refuses ws/connect upgrades.
const CONNECT_ENABLED_DEFAULT = false;

function getBool(key: string, fallback: boolean): boolean {
  const db = getUsersDb();
  const row = db.prepare(`SELECT value FROM app_settings WHERE key = ?`).get(key) as
    | { value: string }
    | undefined;
  if (!row) return fallback;
  return row.value === "1" || row.value === "true";
}

function setBool(key: string, value: boolean): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO app_settings (key, value) VALUES (?, ?)
     ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
  ).run(key, value ? "1" : "0");
}

export function getConnectEnabled(): boolean {
  return getBool(CONNECT_ENABLED_KEY, CONNECT_ENABLED_DEFAULT);
}

export function setConnectEnabled(enabled: boolean): void {
  setBool(CONNECT_ENABLED_KEY, enabled);
}
