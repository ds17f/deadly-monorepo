import Database from "better-sqlite3";
import path from "node:path";

const DB_PATH =
  process.env.ANALYTICS_DB_PATH ??
  path.join(process.cwd(), "data", "analytics.db");

let db: Database.Database | null = null;

export function getAnalyticsDb(): Database.Database {
  if (db) return db;

  db = new Database(DB_PATH);
  db.pragma("journal_mode = WAL");
  db.pragma("busy_timeout = 5000");

  initSchema(db);
  return db;
}

function initSchema(db: Database.Database): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS analytics_events (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      event TEXT NOT NULL,
      ts INTEGER NOT NULL,
      received_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
      iid TEXT NOT NULL,
      sid TEXT NOT NULL,
      platform TEXT NOT NULL,
      app_version TEXT NOT NULL,
      props TEXT
    );

    CREATE INDEX IF NOT EXISTS idx_analytics_event ON analytics_events(event);
    CREATE INDEX IF NOT EXISTS idx_analytics_ts ON analytics_events(ts);
    CREATE INDEX IF NOT EXISTS idx_analytics_platform ON analytics_events(platform);
    CREATE INDEX IF NOT EXISTS idx_analytics_iid ON analytics_events(iid);

    CREATE TABLE IF NOT EXISTS analytics_daily_rollup (
      day TEXT NOT NULL,
      event TEXT NOT NULL,
      platform TEXT NOT NULL,
      app_version TEXT NOT NULL,
      event_count INTEGER NOT NULL,
      unique_installs INTEGER NOT NULL,
      unique_sessions INTEGER NOT NULL,
      props_summary TEXT,
      PRIMARY KEY (day, event, platform, app_version)
    );
  `);
}

// ── Valid events and their allowed prop keys ─────────────────────────

const EVENT_SCHEMAS: Record<string, Set<string>> = {
  app_open: new Set(["os_version"]),
  playback_start: new Set([
    "show_id",
    "recording_id",
    "track_index",
    "source",
  ]),
  playback_end: new Set([
    "show_id",
    "recording_id",
    "track_index",
    "duration_ms",
    "listened_ms",
    "reason",
  ]),
  search: new Set(["query_length", "result_count", "selected_index"]),
  feature_use: new Set(["feature"]),
  error: new Set(["domain", "message", "is_fatal"]),
  cold_start: new Set(["duration_ms"]),
};

export const VALID_EVENTS = new Set(Object.keys(EVENT_SCHEMAS));

export function allowedPropsFor(event: string): Set<string> | undefined {
  return EVENT_SCHEMAS[event];
}

// ── Insert ───────────────────────────────────────────────────────────

export interface AnalyticsEvent {
  event: string;
  ts: number;
  iid: string;
  sid: string;
  platform: string;
  app_version: string;
  props?: Record<string, unknown>;
}

export function insertEvents(events: AnalyticsEvent[]): void {
  const db = getAnalyticsDb();
  const stmt = db.prepare(`
    INSERT INTO analytics_events (event, ts, iid, sid, platform, app_version, props)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `);

  const tx = db.transaction((rows: AnalyticsEvent[]) => {
    for (const e of rows) {
      stmt.run(
        e.event,
        e.ts,
        e.iid,
        e.sid,
        e.platform,
        e.app_version,
        e.props ? JSON.stringify(e.props) : null,
      );
    }
  });

  tx(events);
}

// ── Rollup ───────────────────────────────────────────────────────────

export function rollupDay(day: string): void {
  const db = getAnalyticsDb();
  db.prepare(`
    INSERT OR REPLACE INTO analytics_daily_rollup
      (day, event, platform, app_version, event_count, unique_installs, unique_sessions)
    SELECT
      date(ts / 1000, 'unixepoch') AS day,
      event,
      platform,
      app_version,
      COUNT(*) AS event_count,
      COUNT(DISTINCT iid) AS unique_installs,
      COUNT(DISTINCT sid) AS unique_sessions
    FROM analytics_events
    WHERE date(ts / 1000, 'unixepoch') = ?
    GROUP BY day, event, platform, app_version
  `).run(day);
}

// ── Pruning (optional, controlled by env var) ────────────────────────

export function pruneOldEvents(): void {
  const retentionDays = process.env.ANALYTICS_RAW_RETENTION_DAYS;
  if (!retentionDays) return;

  const days = parseInt(retentionDays, 10);
  if (isNaN(days) || days <= 0) return;

  const cutoff = Date.now() - days * 24 * 3600 * 1000;
  const db = getAnalyticsDb();
  db.prepare("DELETE FROM analytics_events WHERE ts < ?").run(cutoff);
}

// ── Summary queries ──────────────────────────────────────────────────

export interface AnalyticsSummary {
  dau: number;
  wau: number;
  mau: number;
  total_installs: number;
  stale_installs_30d: number;
  platform_split: Record<string, number>;
  top_shows: Array<{ show_id: string; plays: number }>;
  feature_adoption: Record<string, number>;
  avg_completion_rate: number | null;
  events_today: number;
}

export function getSummary(): AnalyticsSummary {
  const db = getAnalyticsDb();
  const now = Date.now();
  const dayAgo = now - 24 * 3600 * 1000;
  const weekAgo = now - 7 * 24 * 3600 * 1000;
  const monthAgo = now - 30 * 24 * 3600 * 1000;

  // DAU/WAU/MAU from app_open events
  const dau =
    (
      db
        .prepare(
          `SELECT COUNT(DISTINCT iid) AS c FROM analytics_events WHERE event = 'app_open' AND ts > ?`,
        )
        .get(dayAgo) as { c: number }
    )?.c ?? 0;

  const wau =
    (
      db
        .prepare(
          `SELECT COUNT(DISTINCT iid) AS c FROM analytics_events WHERE event = 'app_open' AND ts > ?`,
        )
        .get(weekAgo) as { c: number }
    )?.c ?? 0;

  const mau =
    (
      db
        .prepare(
          `SELECT COUNT(DISTINCT iid) AS c FROM analytics_events WHERE event = 'app_open' AND ts > ?`,
        )
        .get(monthAgo) as { c: number }
    )?.c ?? 0;

  // Total unique installs ever
  const total_installs =
    (
      db
        .prepare(`SELECT COUNT(DISTINCT iid) AS c FROM analytics_events`)
        .get() as { c: number }
    )?.c ?? 0;

  // Stale installs: seen ever but not in last 30 days
  const stale_installs_30d =
    (
      db
        .prepare(
          `SELECT COUNT(DISTINCT iid) AS c FROM analytics_events
       WHERE iid NOT IN (SELECT DISTINCT iid FROM analytics_events WHERE ts > ?)`,
        )
        .get(monthAgo) as { c: number }
    )?.c ?? 0;

  // Platform split (last 30 days)
  const platformRows = db
    .prepare(
      `SELECT platform, COUNT(DISTINCT iid) AS c FROM analytics_events
     WHERE event = 'app_open' AND ts > ? GROUP BY platform`,
    )
    .all(monthAgo) as Array<{ platform: string; c: number }>;
  const platform_split: Record<string, number> = {};
  for (const r of platformRows) platform_split[r.platform] = r.c;

  // Top 10 shows (last 30 days)
  const top_shows = db
    .prepare(
      `SELECT json_extract(props, '$.show_id') AS show_id, COUNT(*) AS plays
     FROM analytics_events
     WHERE event = 'playback_start' AND ts > ?
     GROUP BY show_id ORDER BY plays DESC LIMIT 10`,
    )
    .all(monthAgo) as Array<{ show_id: string; plays: number }>;

  // Feature adoption (last 30 days)
  const featureRows = db
    .prepare(
      `SELECT json_extract(props, '$.feature') AS feature, COUNT(*) AS uses
     FROM analytics_events
     WHERE event = 'feature_use' AND ts > ? GROUP BY feature ORDER BY uses DESC`,
    )
    .all(monthAgo) as Array<{ feature: string; uses: number }>;
  const feature_adoption: Record<string, number> = {};
  for (const r of featureRows) feature_adoption[r.feature] = r.uses;

  // Average completion rate (last 30 days)
  const completion = db
    .prepare(
      `SELECT AVG(
      CAST(json_extract(props, '$.listened_ms') AS REAL) /
      NULLIF(CAST(json_extract(props, '$.duration_ms') AS REAL), 0)
    ) AS avg_rate
    FROM analytics_events WHERE event = 'playback_end' AND ts > ?`,
    )
    .get(monthAgo) as { avg_rate: number | null };
  const avg_completion_rate = completion?.avg_rate
    ? Math.round(completion.avg_rate * 1000) / 1000
    : null;

  // Events today
  const todayStart =
    new Date(new Date().toISOString().slice(0, 10)).getTime();
  const events_today =
    (
      db
        .prepare(
          `SELECT COUNT(*) AS c FROM analytics_events WHERE ts >= ?`,
        )
        .get(todayStart) as { c: number }
    )?.c ?? 0;

  return {
    dau,
    wau,
    mau,
    total_installs,
    stale_installs_30d,
    platform_split,
    top_shows,
    feature_adoption,
    avg_completion_rate,
    events_today,
  };
}

// ── Cleanup ──────────────────────────────────────────────────────────

export function closeAnalyticsDb(): void {
  if (db) {
    db.close();
    db = null;
  }
}
