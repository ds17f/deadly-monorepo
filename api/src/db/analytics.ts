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
  ensureUniqueEventIndex(db);
  return db;
}

/**
 * Strict-dedup guard: a unique index on (iid, sid, event, ts) plus
 * `INSERT OR IGNORE` in `insertEvents` neutralizes client-retry duplicates.
 * Existing dupes block index creation, so we pre-dedupe on first run only —
 * the check on `sqlite_master` keeps subsequent startups O(1) instead of
 * scanning the table.
 */
function ensureUniqueEventIndex(db: Database.Database): void {
  const exists = db
    .prepare(
      `SELECT 1 FROM sqlite_master
       WHERE type = 'index' AND name = 'uniq_analytics_event'`,
    )
    .get();
  if (exists) return;

  db.exec(`
    DELETE FROM analytics_events
    WHERE id NOT IN (
      SELECT MIN(id) FROM analytics_events
      GROUP BY iid, sid, event, ts
    );
    CREATE UNIQUE INDEX uniq_analytics_event
      ON analytics_events(iid, sid, event, ts);
  `);
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
  playback_error: new Set([
    "show_id",
    "recording_id",
    "track_index",
    "error_code",
    "error_message",
    "is_fatal",
  ]),
  playback_stall: new Set([
    "show_id",
    "recording_id",
    "track_index",
    "stall_duration_ms",
  ]),
  search: new Set(["query", "query_length", "result_count", "selected_index"]),
  feature_use: new Set([
    "feature",
    "enabled",
    "value",
    "target_type",
    "target_id",
    "category",
    "provider",
    "error_reason",
  ]),
  download_complete: new Set([
    "target_type",
    "target_id",
    "duration_ms",
    "bytes",
  ]),
  download_failed: new Set([
    "target_type",
    "target_id",
    "duration_ms",
    "error_reason",
  ]),
  error: new Set(["source", "message", "is_fatal"]),
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
  // OR IGNORE pairs with the uniq_analytics_event unique index to swallow
  // client-retry duplicates (same iid+sid+event+ts) without erroring the batch.
  const stmt = db.prepare(`
    INSERT OR IGNORE INTO analytics_events (event, ts, iid, sid, platform, app_version, props)
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
  const result = db
    .prepare("DELETE FROM analytics_events WHERE ts < ?")
    .run(cutoff);
  if (result.changes > 0) {
    console.log(
      `[analytics] pruned ${result.changes} raw event(s) older than ${days} days`,
    );
  }
}

// ── Summary queries ──────────────────────────────────────────────────

export type FeatureCategory =
  | "action"
  | "preference"
  | "navigation"
  | "uncategorized";

export interface FeatureAdoptionEntry {
  feature: string;
  uses: number;
}

export type FeatureAdoption = Record<FeatureCategory, FeatureAdoptionEntry[]>;

export type ActionShowsBucket = "favorited" | "downloaded" | "reviewed" | "shared";

export type ActionShowsRow = { show_id: string; users: number };

export interface AnalyticsSummary {
  dau: number;
  wau: number;
  mau: number;
  total_installs: number;
  stale_installs_30d: number;
  platform_split: Record<string, number>;
  top_shows: Array<{ show_id: string; plays: number }>;
  top_shows_by_action: Record<ActionShowsBucket, ActionShowsRow[]>;
  feature_adoption: FeatureAdoption;
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

  // Feature adoption (last 30 days), bucketed by category
  const featureRows = db
    .prepare(
      `SELECT
       json_extract(props, '$.feature') AS feature,
       json_extract(props, '$.category') AS category,
       COUNT(*) AS uses
     FROM analytics_events
     WHERE event = 'feature_use' AND ts > ?
     GROUP BY feature, category ORDER BY uses DESC`,
    )
    .all(monthAgo) as Array<{
    feature: string;
    category: string | null;
    uses: number;
  }>;
  const feature_adoption: FeatureAdoption = {
    action: [],
    preference: [],
    navigation: [],
    uncategorized: [],
  };
  for (const r of featureRows) {
    if (!r.feature) continue;
    const bucket: FeatureCategory =
      r.category === "action" ||
      r.category === "preference" ||
      r.category === "navigation"
        ? r.category
        : "uncategorized";
    feature_adoption[bucket].push({ feature: r.feature, uses: r.uses });
  }

  // Top show targets per action feature (last 30 days). Uses target_id with
  // a show_id fallback for any pre-DEAD-324 events that still carry only
  // show_id. Counts distinct iids — "popularity" measured in users, not raw
  // taps.
  function topShowsForFeatures(features: string[], limit: number): ActionShowsRow[] {
    const placeholders = features.map(() => "?").join(", ");
    return db
      .prepare(
        `SELECT
           COALESCE(json_extract(props, '$.target_id'), json_extract(props, '$.show_id')) AS show_id,
           COUNT(DISTINCT iid) AS users
         FROM analytics_events
         WHERE event = 'feature_use'
           AND ts > ?
           AND json_extract(props, '$.feature') IN (${placeholders})
           AND COALESCE(json_extract(props, '$.target_id'), json_extract(props, '$.show_id')) IS NOT NULL
         GROUP BY show_id
         ORDER BY users DESC, show_id ASC
         LIMIT ?`,
      )
      .all(monthAgo, ...features, limit) as ActionShowsRow[];
  }

  const top_shows_by_action: Record<ActionShowsBucket, ActionShowsRow[]> = {
    favorited: topShowsForFeatures(["add_favorite"], 10),
    downloaded: topShowsForFeatures(["download_show"], 10),
    reviewed: topShowsForFeatures(
      ["write_review", "edit_review", "save_review"],
      10,
    ),
    shared: topShowsForFeatures(["share_show"], 10),
  };

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
    top_shows_by_action,
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

export type TrackOutcome = "complete" | "skipped" | "error" | "partial";

export interface TrackPlay {
  index: number;
  outcome: TrackOutcome;
}

export interface ShowPlaybackSummary {
  active_listeners: number;
  unique_shows: number;
  resumed_count: number;
  listeners: Array<{
    show_id: string;
    iid: string;
    tracks: TrackPlay[];
    last_seen: string;
    resumed: boolean;
  }>;
}

/**
 * Map a `playback_end.reason` to a track outcome. Unknown reasons fall through
 * to "partial" so the track still shows as "heard something" rather than
 * disappearing from the bar.
 */
function reasonToOutcome(reason: string | null): TrackOutcome {
  switch (reason) {
    case "completed":
      return "complete";
    case "skipped_next":
    case "skipped_prev":
      return "skipped";
    case "network_error":
      return "error";
    default:
      // app_backgrounded, null (legacy), or anything unknown: the user heard
      // some of the track but didn't finish it.
      return "partial";
  }
}

export function getShowPlaybackSummary(days: number): ShowPlaybackSummary {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;

  // Pull every playback_start / playback_end in the window. Track outcomes
  // require both sides — playback_end carries the reason, playback_start is
  // the only signal when a track was started but never ended.
  const rows = db.prepare(`
    SELECT
      iid,
      sid,
      ts,
      event,
      json_extract(props, '$.show_id') AS show_id,
      COALESCE(CAST(json_extract(props, '$.track_index') AS INTEGER), 0) AS track_index,
      json_extract(props, '$.reason') AS reason
    FROM analytics_events
    WHERE event IN ('playback_start', 'playback_end')
      AND ts > ?
      AND json_extract(props, '$.show_id') IS NOT NULL
    ORDER BY ts ASC
  `).all(cutoff) as Array<{
    iid: string;
    sid: string;
    ts: number;
    event: string;
    show_id: string;
    track_index: number;
    reason: string | null;
  }>;

  // Group by (iid, show_id) and within that by track_index. The latest event
  // wins — playback_end overrides playback_start at the same index, and a
  // newer skip overrides an older complete (e.g. user replayed and skipped).
  interface Group {
    iid: string;
    show_id: string;
    sessions: Set<string>;
    lastTs: number;
    tracks: Map<number, { ts: number; outcome: TrackOutcome }>;
  }

  const groups = new Map<string, Group>();
  for (const r of rows) {
    const key = `${r.iid}::${r.show_id}`;
    let g = groups.get(key);
    if (!g) {
      g = {
        iid: r.iid,
        show_id: r.show_id,
        sessions: new Set(),
        lastTs: 0,
        tracks: new Map(),
      };
      groups.set(key, g);
    }
    g.sessions.add(r.sid);
    if (r.ts > g.lastTs) g.lastTs = r.ts;

    const prior = g.tracks.get(r.track_index);
    // playback_start without a later end → "partial" (still in progress / not
    // finalised). It only wins if there's no event yet for this track.
    if (r.event === "playback_start") {
      if (!prior) g.tracks.set(r.track_index, { ts: r.ts, outcome: "partial" });
      continue;
    }
    // playback_end: latest wins.
    if (!prior || r.ts >= prior.ts) {
      g.tracks.set(r.track_index, { ts: r.ts, outcome: reasonToOutcome(r.reason) });
    }
  }

  const listeners = Array.from(groups.values()).map((g) => {
    const tracks: TrackPlay[] = Array.from(g.tracks.entries())
      .map(([index, v]) => ({ index, outcome: v.outcome }))
      .sort((a, b) => a.index - b.index);
    return {
      show_id: g.show_id,
      iid: g.iid,
      tracks,
      last_seen: new Date(g.lastTs).toISOString().replace("T", " ").slice(0, 19),
      resumed: g.sessions.size > 1,
    };
  });

  const uniqueListeners = new Set(listeners.map((l) => l.iid));
  const uniqueShows = new Set(listeners.map((l) => l.show_id));
  const resumedCount = listeners.filter((l) => l.resumed).length;

  return {
    active_listeners: uniqueListeners.size,
    unique_shows: uniqueShows.size,
    resumed_count: resumedCount,
    listeners: listeners.sort((a, b) => b.last_seen.localeCompare(a.last_seen)),
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
        // When drilling into a specific feature, surface target_id (when
        // present) so per-target counts are visible. Falls back to feature
        // name for events without a target_id.
        return db.prepare(`
          SELECT
            COALESCE(
              json_extract(props, '$.target_id'),
              json_extract(props, '$.feature')
            ) AS detail,
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
