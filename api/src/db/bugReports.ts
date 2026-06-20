import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { getUsersDb } from "./users.js";

// User-submitted bug reports. Metadata lives in users.db so the admin can list,
// filter, and identify the reporter; the raw log text is stored as a sidecar
// .txt file in a sibling `bugreports/` directory under the data volume. The
// files are never served statically (see Caddyfile) — they're only reachable
// through the admin-gated download route, so logs can't leak.

const DATA_DIR = path.dirname(
  process.env.USERS_DB_PATH ?? path.join(process.cwd(), "data", "users.db"),
);
const REPORTS_DIR = path.join(DATA_DIR, "bugreports");

/** Max size of an uploaded log body we'll persist (bytes). */
export const MAX_LOG_BYTES = 5 * 1024 * 1024; // 5 MB
export const NOTE_MAX = 2000;

export interface BugReport {
  id: string;
  user_id: string | null;
  user_email: string | null;
  note: string | null;
  platform: string | null;
  app_version: string | null;
  os_version: string | null;
  device: string | null;
  install_id: string | null;
  ip: string | null;
  size_bytes: number;
  created_at: number;
}

let schemaReady = false;

function ensureSchema(): void {
  if (schemaReady) return;
  const db = getUsersDb();
  db.exec(`
    CREATE TABLE IF NOT EXISTS bug_reports (
      id TEXT PRIMARY KEY,
      user_id TEXT REFERENCES accounts(id) ON DELETE SET NULL,
      user_email TEXT,
      note TEXT,
      platform TEXT,
      app_version TEXT,
      os_version TEXT,
      device TEXT,
      install_id TEXT,
      ip TEXT,
      size_bytes INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
    );
    CREATE INDEX IF NOT EXISTS idx_bug_reports_created
      ON bug_reports(created_at DESC);
  `);
  fs.mkdirSync(REPORTS_DIR, { recursive: true });
  schemaReady = true;
}

/** Absolute path of the stored log file for a report id. */
export function bugReportFilePath(id: string): string {
  return path.join(REPORTS_DIR, `${id}.txt`);
}

export interface CreateBugReportInput {
  logs: string;
  note?: string | null;
  platform?: string | null;
  appVersion?: string | null;
  osVersion?: string | null;
  device?: string | null;
  installId?: string | null;
  userId?: string | null;
  userEmail?: string | null;
  ip?: string | null;
}

/** Persist a report's logs to disk and its metadata to the DB. Returns the row. */
export function createBugReport(input: CreateBugReportInput): BugReport {
  ensureSchema();
  const db = getUsersDb();
  const id = crypto.randomUUID();

  const body = input.logs ?? "";
  fs.writeFileSync(bugReportFilePath(id), body, "utf8");
  const sizeBytes = Buffer.byteLength(body, "utf8");

  const note = input.note?.trim() ? input.note.trim().slice(0, NOTE_MAX) : null;

  db.prepare(
    `INSERT INTO bug_reports
       (id, user_id, user_email, note, platform, app_version, os_version,
        device, install_id, ip, size_bytes)
     VALUES (@id, @user_id, @user_email, @note, @platform, @app_version,
             @os_version, @device, @install_id, @ip, @size_bytes)`,
  ).run({
    id,
    user_id: input.userId ?? null,
    user_email: input.userEmail ?? null,
    note,
    platform: input.platform ?? null,
    app_version: input.appVersion ?? null,
    os_version: input.osVersion ?? null,
    device: input.device ?? null,
    install_id: input.installId ?? null,
    ip: input.ip ?? null,
    size_bytes: sizeBytes,
  });

  return getBugReport(id)!;
}

/** Newest-first metadata list for the admin screen. */
export function listBugReports(): BugReport[] {
  ensureSchema();
  return getUsersDb()
    .prepare(`SELECT * FROM bug_reports ORDER BY created_at DESC`)
    .all() as BugReport[];
}

export function getBugReport(id: string): BugReport | null {
  ensureSchema();
  const row = getUsersDb()
    .prepare(`SELECT * FROM bug_reports WHERE id = ?`)
    .get(id) as BugReport | undefined;
  return row ?? null;
}

/** Read the stored log text, or null if the row/file is missing. */
export function readBugReportLogs(id: string): string | null {
  if (!getBugReport(id)) return null;
  const file = bugReportFilePath(id);
  if (!fs.existsSync(file)) return null;
  return fs.readFileSync(file, "utf8");
}

/** Delete the row and its log file. Returns true if a row was removed. */
export function deleteBugReport(id: string): boolean {
  ensureSchema();
  const file = bugReportFilePath(id);
  try {
    if (fs.existsSync(file)) fs.unlinkSync(file);
  } catch {
    // Best-effort file cleanup; still drop the row.
  }
  const info = getUsersDb().prepare(`DELETE FROM bug_reports WHERE id = ?`).run(id);
  return info.changes > 0;
}
