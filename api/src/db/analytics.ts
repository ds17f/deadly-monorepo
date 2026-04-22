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
    CREATE INDEX IF NOT EXISTS idx_analytics_event_ts ON analytics_events(event, ts);

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
  search: new Set(["query", "query_length", "result_count", "selected_index"]),
  feature_use: new Set(["feature", "enabled", "value"]),
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

// ── Timeseries queries ──────────────────────────────────────────────

export type TimeseriesMetric = "dau" | "events" | "playback_starts";

export interface TimeseriesPoint {
  day: string;
  value: number;
}

export function getTimeseries(metric: TimeseriesMetric, days: number): TimeseriesPoint[] {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;

  switch (metric) {
    case "dau":
      return db.prepare(`
        SELECT date(ts / 1000, 'unixepoch') AS day, COUNT(DISTINCT iid) AS value
        FROM analytics_events WHERE event = 'app_open' AND ts > ?
        GROUP BY day ORDER BY day ASC
      `).all(cutoff) as TimeseriesPoint[];

    case "events":
      return db.prepare(`
        SELECT date(ts / 1000, 'unixepoch') AS day, COUNT(*) AS value
        FROM analytics_events WHERE ts > ?
        GROUP BY day ORDER BY day ASC
      `).all(cutoff) as TimeseriesPoint[];

    case "playback_starts":
      return db.prepare(`
        SELECT date(ts / 1000, 'unixepoch') AS day, COUNT(*) AS value
        FROM analytics_events WHERE event = 'playback_start' AND ts > ?
        GROUP BY day ORDER BY day ASC
      `).all(cutoff) as TimeseriesPoint[];

    default:
      return [];
  }
}

// ── Show-level playback queries ─────────────────────────────────────

export interface ShowListeningSession {
  show_id: string;
  iid: string;
  sessions: number;
  tracks_played: number;
  max_track_index: number;
  total_listened_ms: number;
  total_duration_ms: number;
}

export interface ShowPlaybackSummary {
  active_listeners: number;
  unique_shows: number;
  avg_tracks_per_show: number;
  avg_show_completion: number | null;
  resume_rate: number | null;
  listeners: Array<{
    show_id: string;
    iid: string;
    sessions: number;
    tracks_played: number;
    deepest_track: number;
    completion_pct: number | null;
  }>;
}

export function getShowPlaybackSummary(days: number): ShowPlaybackSummary {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;

  // Get per-listener per-show aggregates from playback_start
  const starts = db.prepare(`
    SELECT
      json_extract(props, '$.show_id') AS show_id,
      iid,
      COUNT(DISTINCT sid) AS sessions,
      COUNT(*) AS tracks_played,
      MAX(COALESCE(CAST(json_extract(props, '$.track_index') AS INTEGER), 0)) AS max_track_index
    FROM analytics_events
    WHERE event = 'playback_start' AND ts > ?
      AND json_extract(props, '$.show_id') IS NOT NULL
    GROUP BY show_id, iid
  `).all(cutoff) as Array<{
    show_id: string;
    iid: string;
    sessions: number;
    tracks_played: number;
    max_track_index: number;
  }>;

  // Get per-listener per-show listened/duration totals from playback_end
  const ends = db.prepare(`
    SELECT
      json_extract(props, '$.show_id') AS show_id,
      iid,
      SUM(CAST(json_extract(props, '$.listened_ms') AS INTEGER)) AS total_listened_ms,
      SUM(CAST(json_extract(props, '$.duration_ms') AS INTEGER)) AS total_duration_ms
    FROM analytics_events
    WHERE event = 'playback_end' AND ts > ?
      AND json_extract(props, '$.show_id') IS NOT NULL
    GROUP BY show_id, iid
  `).all(cutoff) as Array<{
    show_id: string;
    iid: string;
    total_listened_ms: number;
    total_duration_ms: number;
  }>;

  const endMap = new Map<string, { listened: number; duration: number }>();
  for (const e of ends) {
    endMap.set(`${e.iid}:${e.show_id}`, {
      listened: e.total_listened_ms ?? 0,
      duration: e.total_duration_ms ?? 0,
    });
  }

  const listeners = starts.map((s) => {
    const end = endMap.get(`${s.iid}:${s.show_id}`);
    const completion = end && end.duration > 0
      ? Math.round((end.listened / end.duration) * 100)
      : null;
    return {
      show_id: s.show_id,
      iid: s.iid,
      sessions: s.sessions,
      tracks_played: s.tracks_played,
      deepest_track: s.max_track_index,
      completion_pct: completion,
    };
  });

  const uniqueListeners = new Set(starts.map((s) => s.iid));
  const uniqueShows = new Set(starts.map((s) => s.show_id));
  const multiSession = listeners.filter((l) => l.sessions > 1).length;
  const withCompletion = listeners.filter((l) => l.completion_pct !== null);
  const avgCompletion = withCompletion.length > 0
    ? Math.round(withCompletion.reduce((a, l) => a + l.completion_pct!, 0) / withCompletion.length)
    : null;
  const totalTracks = starts.reduce((a, s) => a + s.tracks_played, 0);

  return {
    active_listeners: uniqueListeners.size,
    unique_shows: uniqueShows.size,
    avg_tracks_per_show: starts.length > 0 ? Math.round((totalTracks / starts.length) * 10) / 10 : 0,
    avg_show_completion: avgCompletion,
    resume_rate: listeners.length > 0 ? Math.round((multiSession / listeners.length) * 100) : null,
    listeners: listeners.sort((a, b) => b.tracks_played - a.tracks_played),
  };
}

// ── Detail queries ──────────────────────────────────────────────────

export type DetailMetric =
  | "dau" | "wau" | "mau"
  | "total_installs" | "stale_installs"
  | "events_today"
  | "top_shows"
  | "feature_adoption"
  | "platform_split"
  | "playback";

export interface DetailRow {
  iid: string;
  platform: string;
  app_version: string;
  last_seen: string;
  event_count: number;
  detail?: string;
}

export function getDetail(metric: DetailMetric, filter?: string): DetailRow[] {
  const db = getAnalyticsDb();
  const now = Date.now();
  const dayAgo = now - 24 * 3600 * 1000;
  const weekAgo = now - 7 * 24 * 3600 * 1000;
  const monthAgo = now - 30 * 24 * 3600 * 1000;
  const todayStart = new Date(new Date().toISOString().slice(0, 10)).getTime();

  switch (metric) {
    case "dau":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events WHERE event = 'app_open' AND ts > ?
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all(dayAgo) as DetailRow[];

    case "wau":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events WHERE event = 'app_open' AND ts > ?
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all(weekAgo) as DetailRow[];

    case "mau":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events WHERE event = 'app_open' AND ts > ?
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all(monthAgo) as DetailRow[];

    case "total_installs":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all() as DetailRow[];

    case "stale_installs":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events
        WHERE iid NOT IN (SELECT DISTINCT iid FROM analytics_events WHERE ts > ?)
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all(monthAgo) as DetailRow[];

    case "events_today":
      return db.prepare(`
        SELECT event AS detail, platform, app_version,
          iid,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          1 AS event_count
        FROM analytics_events WHERE ts >= ?
        ORDER BY ts DESC LIMIT 500
      `).all(todayStart) as DetailRow[];

    case "top_shows":
      if (filter) {
        return db.prepare(`
          SELECT json_extract(props, '$.show_id') AS detail,
            iid, platform, app_version,
            datetime(ts/1000, 'unixepoch') AS last_seen,
            1 AS event_count
          FROM analytics_events
          WHERE event = 'playback_start' AND ts > ? AND json_extract(props, '$.show_id') = ?
          ORDER BY ts DESC LIMIT 500
        `).all(monthAgo, filter) as DetailRow[];
      }
      return db.prepare(`
        SELECT json_extract(props, '$.show_id') AS detail,
          iid, platform, app_version,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          1 AS event_count
        FROM analytics_events
        WHERE event = 'playback_start' AND ts > ?
        ORDER BY ts DESC LIMIT 500
      `).all(monthAgo) as DetailRow[];

    case "feature_adoption":
      if (filter) {
        return db.prepare(`
          SELECT json_extract(props, '$.feature') AS detail,
            iid, platform, app_version,
            datetime(ts/1000, 'unixepoch') AS last_seen,
            1 AS event_count
          FROM analytics_events
          WHERE event = 'feature_use' AND ts > ? AND json_extract(props, '$.feature') = ?
          ORDER BY ts DESC LIMIT 500
        `).all(monthAgo, filter) as DetailRow[];
      }
      return db.prepare(`
        SELECT json_extract(props, '$.feature') AS detail,
          iid, platform, app_version,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          1 AS event_count
        FROM analytics_events
        WHERE event = 'feature_use' AND ts > ?
        ORDER BY ts DESC LIMIT 500
      `).all(monthAgo) as DetailRow[];

    case "platform_split":
      if (filter) {
        return db.prepare(`
          SELECT platform, iid, app_version,
            datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
            COUNT(*) AS event_count
          FROM analytics_events WHERE event = 'app_open' AND ts > ? AND platform = ?
          GROUP BY iid ORDER BY MAX(ts) DESC
        `).all(monthAgo, filter) as DetailRow[];
      }
      return db.prepare(`
        SELECT platform, iid, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events WHERE event = 'app_open' AND ts > ?
        GROUP BY iid ORDER BY platform, MAX(ts) DESC
      `).all(monthAgo) as DetailRow[];

    case "playback":
      return db.prepare(`
        SELECT json_extract(props, '$.show_id') AS detail,
          iid, platform, app_version,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          CAST(json_extract(props, '$.listened_ms') AS INTEGER) AS event_count
        FROM analytics_events
        WHERE event = 'playback_end' AND ts > ?
        ORDER BY ts DESC LIMIT 500
      `).all(monthAgo) as DetailRow[];

    default:
      return [];
  }
}

// ── Install detail ──────────────────────────────────────────────────

export interface InstallEvent {
  id: number;
  event: string;
  ts: number;
  sid: string;
  platform: string;
  app_version: string;
  props: string | null;
}

export interface InstallSummary {
  iid: string;
  platform: string;
  app_version: string;
  first_seen: string;
  last_seen: string;
  total_events: number;
  events: InstallEvent[];
}

export function getInstallEvents(iid: string): InstallSummary | null {
  const db = getAnalyticsDb();

  const summary = db.prepare(`
    SELECT iid, platform, app_version,
      datetime(MIN(ts)/1000, 'unixepoch') AS first_seen,
      datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
      COUNT(*) AS total_events
    FROM analytics_events WHERE iid = ?
    GROUP BY iid
  `).get(iid) as (Omit<InstallSummary, "events"> | undefined);

  if (!summary) return null;

  const events = db.prepare(`
    SELECT id, event, ts, sid, platform, app_version, props
    FROM analytics_events WHERE iid = ?
    ORDER BY ts DESC LIMIT 1000
  `).all(iid) as InstallEvent[];

  return { ...summary, events };
}

// ── Cleanup ──────────────────────────────────────────────────────────

export function closeAnalyticsDb(): void {
  if (db) {
    db.close();
    db = null;
  }
}
