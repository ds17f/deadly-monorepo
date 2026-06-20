import { getUsersDb } from "./users.js";

// Global, admin-controlled feature flags persisted in the `app_settings` table.
// A missing row falls back to the code-side default below, so a fresh database
// (or a key an admin has never touched) behaves predictably across restarts.

const CONNECT_ENABLED_KEY = "connect_enabled";
const CONNECT_MIN_PROTOCOL_KEY = "connect_min_protocol";

// Connect (cross-device playback sync) ships OFF by default. An admin must
// explicitly enable it; until then the server refuses ws/connect upgrades.
const CONNECT_ENABLED_DEFAULT = false;

// Minimum wire-protocol version a client must advertise on register to be
// allowed in (when Connect is enabled). 0 = allow all. Lets an admin gate out
// the old fleet (e.g. set to 2 so only proto-2+ builds connect) without a
// global off. See connect/protocol.ts and ADR-0018.
const CONNECT_MIN_PROTOCOL_DEFAULT = 0;

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

function getInt(key: string, fallback: number): number {
  const db = getUsersDb();
  const row = db.prepare(`SELECT value FROM app_settings WHERE key = ?`).get(key) as
    | { value: string }
    | undefined;
  if (!row) return fallback;
  const n = parseInt(row.value, 10);
  return Number.isFinite(n) ? n : fallback;
}

function setInt(key: string, value: number): void {
  const db = getUsersDb();
  db.prepare(
    `INSERT INTO app_settings (key, value) VALUES (?, ?)
     ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
  ).run(key, String(value));
}

export function getConnectEnabled(): boolean {
  return getBool(CONNECT_ENABLED_KEY, CONNECT_ENABLED_DEFAULT);
}

export function setConnectEnabled(enabled: boolean): void {
  setBool(CONNECT_ENABLED_KEY, enabled);
}

export function getConnectMinProtocol(): number {
  return getInt(CONNECT_MIN_PROTOCOL_KEY, CONNECT_MIN_PROTOCOL_DEFAULT);
}

export function setConnectMinProtocol(minProtocol: number): void {
  setInt(CONNECT_MIN_PROTOCOL_KEY, minProtocol);
}
