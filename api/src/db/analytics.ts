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

    -- Pre-computed top-N per window for the trending endpoint. Fully
    -- rebuilt by rebuildShowListensRollup() each hour from analytics_events,
    -- so the endpoint never queries raw events. Replaces the day-keyed
    -- show_plays_daily table — see ADR-0004 for why session-based dedup was
    -- replaced with logical-listen aggregation.
    CREATE TABLE IF NOT EXISTS show_listens_rollup (
      window TEXT NOT NULL,         -- 'now' | 'week' | 'month' | 'all'
      show_id TEXT NOT NULL,
      listens INTEGER NOT NULL,     -- logical listens (dedup-aware)
      plays INTEGER NOT NULL,       -- raw playback_start count in window
      installs INTEGER NOT NULL,    -- distinct iids contributing
      PRIMARY KEY (window, show_id)
    );
    CREATE INDEX IF NOT EXISTS idx_show_listens_window ON show_listens_rollup(window);

    -- One-shot migration: the old day-keyed table is no longer load-bearing.
    -- Listings are now rebuilt from raw events on every rollup tick.
    DROP TABLE IF EXISTS show_plays_daily;

    CREATE TABLE IF NOT EXISTS watched_installs (
      iid TEXT PRIMARY KEY,
      name TEXT,
      notes TEXT,
      watched_at INTEGER NOT NULL
    );
  `);
}

// ── Watched installs ──────────────────────────────────────────────────

export interface WatchedInstall {
  iid: string;
  name: string | null;
  notes: string | null;
  watched_at: number;
}

export function getWatchedInstalls(): WatchedInstall[] {
  const db = getAnalyticsDb();
  return db
    .prepare(
      `SELECT iid, name, notes, watched_at
       FROM watched_installs
       ORDER BY watched_at DESC`,
    )
    .all() as WatchedInstall[];
}

export function setWatchedInstall(
  iid: string,
  name: string | null,
  notes: string | null,
): WatchedInstall {
  const db = getAnalyticsDb();
  const now = Date.now();
  db.prepare(
    `INSERT INTO watched_installs (iid, name, notes, watched_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(iid) DO UPDATE SET
       name = excluded.name,
       notes = excluded.notes`,
  ).run(iid, name, notes, now);
  return db
    .prepare(
      `SELECT iid, name, notes, watched_at FROM watched_installs WHERE iid = ?`,
    )
    .get(iid) as WatchedInstall;
}

export function removeWatchedInstall(iid: string): void {
  const db = getAnalyticsDb();
  db.prepare(`DELETE FROM watched_installs WHERE iid = ?`).run(iid);
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
  top_shows: Array<{
    show_id: string;
    listeners: number;
    track_plays: number;
    completion_rate: number | null;
  }>;
  top_shows_by_action: Record<ActionShowsBucket, ActionShowsRow[]>;
  /** playback_start grouped by `source`. Pre-watershed rows have null source
   *  and are surfaced as "(unattributed)" so the legacy bucket is visible
   *  rather than silently disappearing. */
  plays_by_source: Array<{
    source: string;
    plays: number;
    distinct_listeners: number;
  }>;
  feature_adoption: FeatureAdoption;
  avg_completion_rate: number | null;
  avg_completion_sample_count: number;
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

  // Top 10 shows by distinct listeners (last 30 days). Counts each install
  // once per show, only when a matching playback_end recorded ≥30s of
  // listening — filters out previews and accidental relaunches.
  // Pairs each start to the same install/session/show within 4h.
  const TOP_SHOWS_MIN_LISTEN_MS = 30_000;
  const TOP_SHOWS_MAX_PAIR_WINDOW_MS = 4 * 3600 * 1000;
  // Per-show completion rate (SUM listened / SUM duration), gated to shows
  // with ≥5 playback_end events to avoid noise from one-off plays. Null
  // below the threshold so the UI can render a "—" instead of a misleading
  // small-sample number.
  const TOP_SHOWS_COMPLETION_MIN_ENDS = 5;
  const top_shows = db
    .prepare(
      `WITH listens AS (
         SELECT DISTINCT
           json_extract(s.props, '$.show_id') AS show_id,
           s.iid
         FROM analytics_events s
         JOIN analytics_events e
           ON e.event = 'playback_end'
           AND e.iid = s.iid
           AND e.sid = s.sid
           AND json_extract(e.props, '$.show_id') = json_extract(s.props, '$.show_id')
           AND e.ts BETWEEN s.ts AND s.ts + ?
           AND CAST(json_extract(e.props, '$.listened_ms') AS REAL) >= ?
         WHERE s.event = 'playback_start' AND s.ts > ?
       ),
       track_plays AS (
         SELECT
           json_extract(props, '$.show_id') AS show_id,
           COUNT(*) AS track_plays
         FROM analytics_events
         WHERE event = 'playback_start' AND ts > ?
         GROUP BY show_id
       ),
       completions AS (
         SELECT
           json_extract(props, '$.show_id') AS show_id,
           SUM(
             MIN(
               CAST(json_extract(props, '$.listened_ms') AS REAL),
               CAST(json_extract(props, '$.duration_ms') AS REAL)
             )
           ) AS sum_listened,
           SUM(CAST(json_extract(props, '$.duration_ms') AS REAL)) AS sum_duration,
           COUNT(*) AS end_count
         FROM analytics_events
         WHERE event = 'playback_end' AND ts > ?
           AND CAST(json_extract(props, '$.duration_ms') AS REAL) > 0
         GROUP BY show_id
       )
       SELECT
         l.show_id,
         COUNT(*) AS listeners,
         COALESCE(p.track_plays, 0) AS track_plays,
         CASE
           WHEN c.end_count >= ? AND c.sum_duration > 0
           THEN c.sum_listened / c.sum_duration
           ELSE NULL
         END AS completion_rate
       FROM listens l
       LEFT JOIN track_plays p ON p.show_id = l.show_id
       LEFT JOIN completions c ON c.show_id = l.show_id
       GROUP BY l.show_id, p.track_plays, c.end_count, c.sum_duration, c.sum_listened
       ORDER BY listeners DESC, track_plays DESC
       LIMIT 10`,
    )
    .all(
      TOP_SHOWS_MAX_PAIR_WINDOW_MS,
      TOP_SHOWS_MIN_LISTEN_MS,
      monthAgo,
      monthAgo,
      monthAgo,
      TOP_SHOWS_COMPLETION_MIN_ENDS,
    ) as Array<{
      show_id: string;
      listeners: number;
      track_plays: number;
      completion_rate: number | null;
    }>;

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

  // Average completion rate (last 30 days), restricted to "real listens":
  // both listened_ms and duration_ms ≥ 60s. Short previews and 1-second
  // skips would otherwise drag the average toward zero.
  // listened_ms is capped at duration_ms so replays / over-runs don't
  // produce ratios > 1.
  const MIN_LISTEN_MS = 60_000;
  const completion = db
    .prepare(
      `SELECT
         AVG(
           MIN(
             CAST(json_extract(props, '$.listened_ms') AS REAL),
             CAST(json_extract(props, '$.duration_ms') AS REAL)
           ) /
           CAST(json_extract(props, '$.duration_ms') AS REAL)
         ) AS avg_rate,
         COUNT(*) AS sample_count
       FROM analytics_events
       WHERE event = 'playback_end'
         AND ts > ?
         AND CAST(json_extract(props, '$.listened_ms') AS REAL) >= ?
         AND CAST(json_extract(props, '$.duration_ms') AS REAL) >= ?`,
    )
    .get(monthAgo, MIN_LISTEN_MS, MIN_LISTEN_MS) as {
    avg_rate: number | null;
    sample_count: number;
  };
  const avg_completion_rate = completion?.avg_rate
    ? Math.round(completion.avg_rate * 1000) / 1000
    : null;
  const avg_completion_sample_count = completion?.sample_count ?? 0;

  // Plays by source (last 30d). Null source bucketed as "(unattributed)" so
  // pre-watershed rows from clients that didn't yet emit `source` are
  // visible rather than silently dropped.
  const plays_by_source = db
    .prepare(
      `SELECT
         COALESCE(json_extract(props, '$.source'), '(unattributed)') AS source,
         COUNT(*) AS plays,
         COUNT(DISTINCT iid) AS distinct_listeners
       FROM analytics_events
       WHERE event = 'playback_start' AND ts > ?
       GROUP BY source
       ORDER BY plays DESC`,
    )
    .all(monthAgo) as Array<{
      source: string;
      plays: number;
      distinct_listeners: number;
    }>;

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
    plays_by_source,
    feature_adoption,
    avg_completion_rate,
    avg_completion_sample_count,
    events_today,
  };
}

// ── Timeseries queries ──────────────────────────────────────────────

// ── Retention cohorts ──────────────────────────────────────────────

export interface RetentionCohort {
  /** ISO-style "YYYY-Www" — week the install first opened the app. */
  cohort_week: string;
  /** Distinct installs first seen during this week. */
  cohort_size: number;
  /** Distinct installs with an app_open exactly N days after install_day.
   *  Null when the cohort is younger than N days (not enough time elapsed). */
  d1: number | null;
  d7: number | null;
  d30: number | null;
}

/**
 * Weekly cohort retention: for each install-week, how many returned on
 * D1, D7, and D30. "Returned on day N" means an `app_open` exists on the
 * calendar day install_day + N (strict). Cohorts younger than N days
 * report null for that bucket so the UI can render a hatched / "—" cell
 * instead of misleading 0%. Defaults to the last 12 weeks of cohorts.
 */
export function getRetentionCohorts(weeks = 12): RetentionCohort[] {
  const db = getAnalyticsDb();
  const oldestWeekStart = Date.now() - weeks * 7 * 24 * 3600 * 1000;
  const todayDay = new Date().toISOString().slice(0, 10);

  return db
    .prepare(
      `WITH first_open AS (
         SELECT iid,
                MIN(ts) AS install_ts,
                date(MIN(ts) / 1000, 'unixepoch') AS install_day
         FROM analytics_events
         WHERE event = 'app_open'
         GROUP BY iid
         HAVING install_ts >= ?
       ),
       active AS (
         SELECT DISTINCT iid, date(ts / 1000, 'unixepoch') AS active_day
         FROM analytics_events
         WHERE event = 'app_open'
       )
       SELECT
         strftime('%Y-W%W', f.install_day) AS cohort_week,
         COUNT(DISTINCT f.iid) AS cohort_size,
         CASE WHEN date(?, '-1 day') >= MAX(f.install_day)
              THEN COUNT(DISTINCT CASE WHEN a1.iid IS NOT NULL THEN f.iid END)
              ELSE NULL END AS d1,
         CASE WHEN date(?, '-7 day') >= MAX(f.install_day)
              THEN COUNT(DISTINCT CASE WHEN a7.iid IS NOT NULL THEN f.iid END)
              ELSE NULL END AS d7,
         CASE WHEN date(?, '-30 day') >= MAX(f.install_day)
              THEN COUNT(DISTINCT CASE WHEN a30.iid IS NOT NULL THEN f.iid END)
              ELSE NULL END AS d30
       FROM first_open f
       LEFT JOIN active a1
         ON a1.iid = f.iid AND a1.active_day = date(f.install_day, '+1 day')
       LEFT JOIN active a7
         ON a7.iid = f.iid AND a7.active_day = date(f.install_day, '+7 day')
       LEFT JOIN active a30
         ON a30.iid = f.iid AND a30.active_day = date(f.install_day, '+30 day')
       GROUP BY cohort_week
       ORDER BY cohort_week DESC`,
    )
    .all(oldestWeekStart, todayDay, todayDay, todayDay) as RetentionCohort[];
}

// ── Top Shows (parameterized by window) ────────────────────────────

export interface TopShowRow {
  show_id: string;
  listeners: number;
  track_plays: number;
  completion_rate: number | null;
}

const TOP_SHOWS_MIN_LISTEN_MS_PARAM = 30_000;
const TOP_SHOWS_MAX_PAIR_WINDOW_MS_PARAM = 4 * 3600 * 1000;
const TOP_SHOWS_COMPLETION_MIN_ENDS_PARAM = 5;

/**
 * Same query as the summary's top_shows but with a configurable window.
 * Limits to top 20 (UI may render fewer) and filters tiny-sample shows
 * out of the completion column the same way the summary does.
 */
export function getTopShows(days: number, limit = 20): TopShowRow[] {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;
  return db
    .prepare(
      `WITH listens AS (
         SELECT DISTINCT
           json_extract(s.props, '$.show_id') AS show_id,
           s.iid
         FROM analytics_events s
         JOIN analytics_events e
           ON e.event = 'playback_end'
           AND e.iid = s.iid
           AND e.sid = s.sid
           AND json_extract(e.props, '$.show_id') = json_extract(s.props, '$.show_id')
           AND e.ts BETWEEN s.ts AND s.ts + ?
           AND CAST(json_extract(e.props, '$.listened_ms') AS REAL) >= ?
         WHERE s.event = 'playback_start' AND s.ts > ?
       ),
       track_plays AS (
         SELECT
           json_extract(props, '$.show_id') AS show_id,
           COUNT(*) AS track_plays
         FROM analytics_events
         WHERE event = 'playback_start' AND ts > ?
         GROUP BY show_id
       ),
       completions AS (
         SELECT
           json_extract(props, '$.show_id') AS show_id,
           SUM(
             MIN(
               CAST(json_extract(props, '$.listened_ms') AS REAL),
               CAST(json_extract(props, '$.duration_ms') AS REAL)
             )
           ) AS sum_listened,
           SUM(CAST(json_extract(props, '$.duration_ms') AS REAL)) AS sum_duration,
           COUNT(*) AS end_count
         FROM analytics_events
         WHERE event = 'playback_end' AND ts > ?
           AND CAST(json_extract(props, '$.duration_ms') AS REAL) > 0
         GROUP BY show_id
       )
       SELECT
         l.show_id,
         COUNT(*) AS listeners,
         COALESCE(p.track_plays, 0) AS track_plays,
         CASE
           WHEN c.end_count >= ? AND c.sum_duration > 0
           THEN c.sum_listened / c.sum_duration
           ELSE NULL
         END AS completion_rate
       FROM listens l
       LEFT JOIN track_plays p ON p.show_id = l.show_id
       LEFT JOIN completions c ON c.show_id = l.show_id
       GROUP BY l.show_id, p.track_plays, c.end_count, c.sum_duration, c.sum_listened
       ORDER BY listeners DESC, track_plays DESC
       LIMIT ?`,
    )
    .all(
      TOP_SHOWS_MAX_PAIR_WINDOW_MS_PARAM,
      TOP_SHOWS_MIN_LISTEN_MS_PARAM,
      cutoff,
      cutoff,
      cutoff,
      TOP_SHOWS_COMPLETION_MIN_ENDS_PARAM,
      limit,
    ) as TopShowRow[];
}

// ── Listening Now ──────────────────────────────────────────────────

export interface LiveListener {
  iid: string;
  platform: string;
  app_version: string;
  /** Wall-clock ms when the playback_start fired. */
  started_at: number;
  show_id: string | null;
  recording_id: string | null;
  track_index: number | null;
  source: string | null;
  /** Per-track outcomes for this session of this show, same shape as the
   *  Show Listening bitmap. Empty when show_id is null. */
  tracks: TrackPlay[];
}

/**
 * "Currently listening" set, defined as: each install's most recent
 * `playback_start` / `playback_end` event whose timestamp is within
 * LIVE_WINDOW_MS. The "soft" boundary (events of *either* kind count)
 * keeps a listener live across the autoplay gap between tracks: when
 * track N ends naturally, the brief moment before track N+1's
 * `playback_start` fires would otherwise drop them into Recent
 * Listening and they'd show as "36s ago" while still actively
 * listening. Sessions that crash or get force-killed without emitting
 * `playback_end` still linger for up to LIVE_WINDOW_MS — same trade
 * as before, just shorter than the old 45-minute window. The
 * shorter window is fine because the new definition no longer
 * relies on "unmatched start" semantics.
 */
const LIVE_WINDOW_MS = 90 * 1000;

export function getLiveListeners(): LiveListener[] {
  const db = getAnalyticsDb();
  const windowStart = Date.now() - LIVE_WINDOW_MS;

  // Pick the latest playback event per iid (start or end), then keep
  // only those whose latest event is inside the live window AND
  // describes a show. `started_at` is resolved separately as the most
  // recent `playback_start` for this (iid, show_id) within a generous
  // lookback so the UI can render "started X ago" correctly even when
  // the live-keepalive event is an end.
  const rows = db
    .prepare(
      `WITH latest AS (
         SELECT
           iid,
           sid,
           ts,
           event,
           platform,
           app_version,
           json_extract(props, '$.show_id') AS show_id,
           json_extract(props, '$.recording_id') AS recording_id,
           CAST(json_extract(props, '$.track_index') AS INTEGER) AS track_index,
           json_extract(props, '$.source') AS source,
           ROW_NUMBER() OVER (PARTITION BY iid ORDER BY ts DESC) AS rn
         FROM analytics_events
         WHERE event IN ('playback_start', 'playback_end')
           AND ts >= ?
           AND json_extract(props, '$.show_id') IS NOT NULL
       )
       SELECT
         l.iid,
         l.sid,
         l.platform,
         l.app_version,
         COALESCE(
           (SELECT MAX(s.ts) FROM analytics_events s
              WHERE s.event = 'playback_start'
                AND s.iid = l.iid
                AND json_extract(s.props, '$.show_id') = l.show_id
                AND s.ts >= ?),
           l.ts
         ) AS started_at,
         l.show_id,
         l.recording_id,
         l.track_index,
         l.source
       FROM latest l
       WHERE l.rn = 1
         AND l.ts >= ?
       ORDER BY l.ts DESC`,
    )
    .all(
      // Latest-event lookback: a bit wider than LIVE_WINDOW_MS so the
      // ROW_NUMBER pick is robust against clock skew / batch flushes.
      Date.now() - 6 * 3600 * 1000,
      // started_at lookup: look back 6h to find the original session start.
      Date.now() - 6 * 3600 * 1000,
      windowStart,
    ) as Array<{
      iid: string;
      sid: string;
      platform: string;
      app_version: string;
      started_at: number;
      show_id: string | null;
      recording_id: string | null;
      track_index: number | null;
      source: string | null;
    }>;

  // For each live listener, materialize the bitmap for their current
  // (iid, sid, show_id). Look back 6h so a long jam session earlier in
  // the same sid still contributes to the bar.
  const sessionLookback = Date.now() - 6 * 3600 * 1000;

  return rows.map((r) => {
    const tracks = r.show_id
      ? buildTrackBitmap(r.iid, r.sid, r.show_id, sessionLookback)
      : [];
    return {
      iid: r.iid,
      platform: r.platform,
      app_version: r.app_version,
      started_at: r.started_at,
      show_id: r.show_id,
      recording_id: r.recording_id,
      track_index: r.track_index,
      source: r.source,
      tracks,
    };
  });
}

// ── Search quality ─────────────────────────────────────────────────

export interface SearchQuality {
  total_searches: number;
  /** Searches whose result_count was 0. */
  zero_result_count: number;
  /** Searches with no selected_index (user didn't tap a result). */
  abandon_count: number;
  /** Median position of the selected result. Lower is better. */
  median_selected_index: number | null;
  top_zero_result: Array<{ query: string; count: number }>;
  top_successful: Array<{ query: string; count: number }>;
}

export function getSearchQuality(days = 30): SearchQuality {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;

  const overview = db
    .prepare(
      `SELECT
         COUNT(*) AS total_searches,
         COUNT(CASE WHEN json_extract(props, '$.result_count') = 0 THEN 1 END) AS zero_result_count,
         COUNT(CASE WHEN json_extract(props, '$.selected_index') IS NULL THEN 1 END) AS abandon_count
       FROM analytics_events
       WHERE event = 'search' AND ts > ?`,
    )
    .get(cutoff) as {
    total_searches: number;
    zero_result_count: number;
    abandon_count: number;
  };

  // Pull all selected_index values to compute median in JS — SQLite has no
  // MEDIAN. Cheap at this volume; revisit if search events ever get to
  // millions/day.
  const selectedRows = db
    .prepare(
      `SELECT CAST(json_extract(props, '$.selected_index') AS INTEGER) AS si
       FROM analytics_events
       WHERE event = 'search' AND ts > ?
         AND json_extract(props, '$.selected_index') IS NOT NULL`,
    )
    .all(cutoff) as Array<{ si: number }>;
  const selectedIndices = selectedRows
    .map((r) => r.si)
    .filter((n) => Number.isFinite(n))
    .sort((a, b) => a - b);
  const median_selected_index =
    selectedIndices.length === 0
      ? null
      : selectedIndices.length % 2 === 1
        ? selectedIndices[(selectedIndices.length - 1) / 2]
        : (selectedIndices[selectedIndices.length / 2 - 1] +
            selectedIndices[selectedIndices.length / 2]) /
          2;

  // Top 20 zero-result queries. Filtered to length ≥ 3 to skip the noise
  // of users still typing — single chars dominate raw counts otherwise.
  const top_zero_result = db
    .prepare(
      `SELECT json_extract(props, '$.query') AS query, COUNT(*) AS count
       FROM analytics_events
       WHERE event = 'search' AND ts > ?
         AND json_extract(props, '$.result_count') = 0
         AND length(TRIM(json_extract(props, '$.query'))) >= 3
       GROUP BY query
       ORDER BY count DESC, query ASC
       LIMIT 20`,
    )
    .all(cutoff) as Array<{ query: string; count: number }>;

  const top_successful = db
    .prepare(
      `SELECT json_extract(props, '$.query') AS query, COUNT(*) AS count
       FROM analytics_events
       WHERE event = 'search' AND ts > ?
         AND CAST(json_extract(props, '$.result_count') AS INTEGER) > 0
         AND length(TRIM(json_extract(props, '$.query'))) >= 3
       GROUP BY query
       ORDER BY count DESC, query ASC
       LIMIT 20`,
    )
    .all(cutoff) as Array<{ query: string; count: number }>;

  return {
    total_searches: overview.total_searches,
    zero_result_count: overview.zero_result_count,
    abandon_count: overview.abandon_count,
    median_selected_index,
    top_zero_result,
    top_successful,
  };
}

// ── Growth (per-platform new installs by day) ──────────────────────

export interface GrowthDay {
  day: string;
  ios: number;
  android: number;
  web: number;
  total: number;
}

/**
 * New installs per day, broken out by platform. An iid's platform is
 * the platform of its first event. Window is the last `days` days
 * inclusive.
 */
export function getGrowthByPlatform(days = 60): GrowthDay[] {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;

  const rows = db
    .prepare(
      `WITH first_seen AS (
         SELECT iid, MIN(ts) AS first_ts FROM analytics_events GROUP BY iid
       )
       SELECT
         date(f.first_ts/1000, 'unixepoch') AS day,
         (SELECT platform FROM analytics_events WHERE iid = f.iid ORDER BY ts ASC LIMIT 1) AS platform,
         COUNT(*) AS value
       FROM first_seen f
       WHERE f.first_ts > ?
       GROUP BY day, platform
       ORDER BY day ASC`,
    )
    .all(cutoff) as Array<{ day: string; platform: string; value: number }>;

  const byDay = new Map<string, GrowthDay>();
  for (const r of rows) {
    const slot =
      byDay.get(r.day) ??
      ({ day: r.day, ios: 0, android: 0, web: 0, total: 0 } as GrowthDay);
    if (r.platform === "ios") slot.ios += r.value;
    else if (r.platform === "android") slot.android += r.value;
    else if (r.platform === "web") slot.web += r.value;
    slot.total += r.value;
    byDay.set(r.day, slot);
  }
  return Array.from(byDay.values()).sort((a, b) => a.day.localeCompare(b.day));
}

export type TimeseriesMetric =
  | "dau"
  | "events"
  | "playback_starts"
  | "new_installs";

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

    case "new_installs":
      // Each iid's first-ever timestamp = first-seen day; group by that
      // day. We allow first_ts to fall outside the lookback window only
      // if filtering to it would hide installs that first appeared
      // before the window — which is what we want here.
      return db.prepare(`
        WITH first_seen AS (
          SELECT iid, MIN(ts) AS first_ts
          FROM analytics_events
          GROUP BY iid
        )
        SELECT date(first_ts / 1000, 'unixepoch') AS day,
               COUNT(*) AS value
        FROM first_seen
        WHERE first_ts > ?
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

/**
 * Replay playback_start / playback_end events for an (iid, show_id) into a
 * per-track outcome bitmap. When `sid` is provided, only that session is
 * considered (Listening Now — single live session). When `sid` is null, all
 * sessions for this user/show are merged (Recent Listening — cumulative
 * listening across app launches).
 */
function buildTrackBitmap(
  iid: string,
  sid: string | null,
  showId: string,
  sinceMs: number,
): TrackPlay[] {
  const db = getAnalyticsDb();
  const sql = `SELECT
         event, ts,
         COALESCE(CAST(json_extract(props, '$.track_index') AS INTEGER), 0) AS track_index,
         json_extract(props, '$.reason') AS reason
       FROM analytics_events
       WHERE iid = ?${sid != null ? " AND sid = ?" : ""}
         AND json_extract(props, '$.show_id') = ?
         AND event IN ('playback_start', 'playback_end')
         AND ts >= ?
       ORDER BY ts ASC`;
  const params = sid != null ? [iid, sid, showId, sinceMs] : [iid, showId, sinceMs];
  const events = db.prepare(sql).all(...params) as Array<{
    event: string;
    ts: number;
    track_index: number;
    reason: string | null;
  }>;

  const acc = new Map<number, { ts: number; outcome: TrackOutcome }>();
  for (const e of events) {
    const prior = acc.get(e.track_index);
    if (e.event === "playback_start") {
      if (!prior) acc.set(e.track_index, { ts: e.ts, outcome: "partial" });
      continue;
    }
    if (!prior || e.ts >= prior.ts) {
      acc.set(e.track_index, { ts: e.ts, outcome: reasonToOutcome(e.reason) });
    }
  }
  return Array.from(acc.entries())
    .map(([index, v]) => ({ index, outcome: v.outcome }))
    .sort((a, b) => a.index - b.index);
}

// ── Recent Listening ───────────────────────────────────────────────

export interface RecentListeningSession {
  iid: string;
  platform: string;
  app_version: string;
  /** Earliest playback_start across all sessions of this (iid, show_id). */
  started_at: number;
  /** Most recent playback event across all sessions of this (iid, show_id). */
  last_event_at: number;
  show_id: string | null;
  recording_id: string | null;
  /** track_index of the most recent playback event. */
  track_index: number | null;
  /** source from the first playback_start across all sessions. */
  source: string | null;
  /** Per-track outcomes merged across all sessions of this (iid, show_id). */
  tracks: TrackPlay[];
  /** Number of distinct sids (app sessions) the user used for this show. */
  session_count: number;
  /** True if any session has a playback_end. */
  ended: boolean;
}

/**
 * Recent listening, deduped by (iid, show_id). One row per user-show pair —
 * if the same install listened to the same show across multiple app sessions
 * (sids) in the window, those are merged into a single row with cumulative
 * track bitmap and earliest/latest timestamps. Excludes any (iid, show_id)
 * still considered live by getLiveListeners() to avoid double-counting.
 */
export function getRecentListening(
  hours = 24,
  limit = 100,
): RecentListeningSession[] {
  const db = getAnalyticsDb();
  const now = Date.now();
  const windowStart = now - hours * 3600 * 1000;
  const liveWindowStart = now - LIVE_WINDOW_MS;

  const rows = db
    .prepare(
      `WITH show_events AS (
         SELECT
           iid,
           sid,
           json_extract(props, '$.show_id') AS show_id,
           event,
           ts,
           platform,
           app_version,
           json_extract(props, '$.recording_id') AS recording_id,
           CAST(json_extract(props, '$.track_index') AS INTEGER) AS track_index,
           json_extract(props, '$.source') AS source
         FROM analytics_events
         WHERE event IN ('playback_start', 'playback_end')
           AND ts >= ?
           AND json_extract(props, '$.show_id') IS NOT NULL
       ),
       agg AS (
         SELECT
           iid,
           show_id,
           MIN(ts) AS started_at,
           MAX(ts) AS last_event_at,
           COUNT(DISTINCT sid) AS session_count,
           MAX(CASE WHEN event = 'playback_end' THEN 1 ELSE 0 END) AS has_end,
           MAX(CASE WHEN event = 'playback_start' THEN ts ELSE 0 END) AS last_start_ts
         FROM show_events
         GROUP BY iid, show_id
       )
       SELECT
         a.iid,
         a.show_id,
         a.started_at,
         a.last_event_at,
         a.session_count,
         a.has_end,
         (SELECT platform FROM show_events
            WHERE iid = a.iid AND show_id = a.show_id
            ORDER BY ts DESC LIMIT 1) AS platform,
         (SELECT app_version FROM show_events
            WHERE iid = a.iid AND show_id = a.show_id
            ORDER BY ts DESC LIMIT 1) AS app_version,
         (SELECT recording_id FROM show_events
            WHERE iid = a.iid AND show_id = a.show_id
              AND event = 'playback_start'
            ORDER BY ts ASC LIMIT 1) AS recording_id,
         (SELECT track_index FROM show_events
            WHERE iid = a.iid AND show_id = a.show_id
            ORDER BY ts DESC LIMIT 1) AS track_index,
         (SELECT source FROM show_events
            WHERE iid = a.iid AND show_id = a.show_id
              AND event = 'playback_start'
            ORDER BY ts ASC LIMIT 1) AS source
       FROM agg a
       -- Mirror the live definition in getLiveListeners: exclude
       -- (iid, show_id) pairs where this iid's most recent playback
       -- event is within LIVE_WINDOW_MS AND describes this show.
       WHERE NOT EXISTS (
         SELECT 1 FROM analytics_events latest
         WHERE latest.event IN ('playback_start', 'playback_end')
           AND latest.iid = a.iid
           AND latest.ts >= ?
           AND json_extract(latest.props, '$.show_id') = a.show_id
           AND latest.ts = (
             SELECT MAX(x.ts) FROM analytics_events x
             WHERE x.event IN ('playback_start', 'playback_end')
               AND x.iid = a.iid
               AND x.ts >= ?
           )
       )
       ORDER BY a.last_event_at DESC
       LIMIT ?`,
    )
    .all(windowStart, liveWindowStart, liveWindowStart, limit) as Array<{
      iid: string;
      show_id: string;
      started_at: number;
      last_event_at: number;
      session_count: number;
      has_end: number;
      platform: string;
      app_version: string;
      recording_id: string | null;
      track_index: number | null;
      source: string | null;
    }>;

  return rows.map((r) => ({
    iid: r.iid,
    platform: r.platform,
    app_version: r.app_version,
    started_at: r.started_at,
    last_event_at: r.last_event_at,
    show_id: r.show_id,
    recording_id: r.recording_id,
    track_index: r.track_index,
    source: r.source,
    tracks: buildTrackBitmap(r.iid, null, r.show_id, r.started_at),
    session_count: r.session_count,
    ended: r.has_end === 1,
  }));
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
  | "playback"
  | "playback_source"
  | "new_installs";

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

    case "new_installs":
      // Filter is a YYYY-MM-DD day. List the iids whose first-ever event
      // landed on that day. Without filter, list the most recent
      // first-seen iids overall.
      if (filter) {
        return db.prepare(`
          WITH first_seen AS (
            SELECT iid, MIN(ts) AS first_ts FROM analytics_events GROUP BY iid
          )
          SELECT
            f.iid,
            (SELECT platform FROM analytics_events WHERE iid = f.iid ORDER BY ts ASC LIMIT 1) AS platform,
            (SELECT app_version FROM analytics_events WHERE iid = f.iid ORDER BY ts ASC LIMIT 1) AS app_version,
            datetime(f.first_ts/1000, 'unixepoch') AS last_seen,
            (SELECT COUNT(*) FROM analytics_events WHERE iid = f.iid) AS event_count,
            date(f.first_ts/1000, 'unixepoch') AS detail
          FROM first_seen f
          WHERE date(f.first_ts/1000, 'unixepoch') = ?
          ORDER BY f.first_ts DESC LIMIT 500
        `).all(filter) as DetailRow[];
      }
      return db.prepare(`
        WITH first_seen AS (
          SELECT iid, MIN(ts) AS first_ts FROM analytics_events GROUP BY iid
        )
        SELECT
          f.iid,
          (SELECT platform FROM analytics_events WHERE iid = f.iid ORDER BY ts ASC LIMIT 1) AS platform,
          (SELECT app_version FROM analytics_events WHERE iid = f.iid ORDER BY ts ASC LIMIT 1) AS app_version,
          datetime(f.first_ts/1000, 'unixepoch') AS last_seen,
          (SELECT COUNT(*) FROM analytics_events WHERE iid = f.iid) AS event_count,
          date(f.first_ts/1000, 'unixepoch') AS detail
        FROM first_seen f
        ORDER BY f.first_ts DESC LIMIT 500
      `).all() as DetailRow[];

    case "playback_source":
      if (filter) {
        // Drill into a specific source: list recent plays from it. The "(unattributed)"
        // bucket maps to legacy rows where source was never emitted.
        const filterClause =
          filter === "(unattributed)"
            ? "json_extract(props, '$.source') IS NULL"
            : "json_extract(props, '$.source') = ?";
        const params: (string | number)[] = [monthAgo];
        if (filter !== "(unattributed)") params.push(filter);
        return db.prepare(`
          SELECT json_extract(props, '$.show_id') AS detail,
            iid, platform, app_version,
            datetime(ts/1000, 'unixepoch') AS last_seen,
            1 AS event_count
          FROM analytics_events
          WHERE event = 'playback_start' AND ts > ? AND ${filterClause}
          ORDER BY ts DESC LIMIT 500
        `).all(...params) as DetailRow[];
      }
      return db.prepare(`
        SELECT
          COALESCE(json_extract(props, '$.source'), '(unattributed)') AS detail,
          iid, platform, app_version,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          1 AS event_count
        FROM analytics_events
        WHERE event = 'playback_start' AND ts > ?
        ORDER BY ts DESC LIMIT 500
      `).all(monthAgo) as DetailRow[];

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

// ── Trending shows ──────────────────────────────────────────────────

export interface TrendingShow {
  show_id: string;
  /** Logical listens — runs of plays from one install on one show, deduped
   *  across app restarts. Primary ranking signal. See ADR-0004. */
  listens: number;
  /** Raw playback_start count in the window. Engagement-weighted secondary. */
  plays: number;
  /** Distinct installs that contributed any play to this show in the window. */
  installs: number;
}

export interface TrendingResponse {
  generated_at: string;
  windows: {
    now: TrendingShow[];
    week: TrendingShow[];
    month: TrendingShow[];
    all: TrendingShow[];
  };
}

/**
 * Logical-listen rollup window definitions. `sinceMs` is computed against
 * Date.now() at rebuild time; "all" uses 0 to mean "since the epoch".
 */
const TRENDING_WINDOWS = ["now", "week", "month", "all"] as const;
type TrendingWindowName = (typeof TRENDING_WINDOWS)[number];

function windowSinceMs(name: TrendingWindowName, now: number): number {
  switch (name) {
    case "now":   return now - 24 * 3600 * 1000;
    case "week":  return now -  7 * 24 * 3600 * 1000;
    case "month": return now - 30 * 24 * 3600 * 1000;
    case "all":   return 0;
  }
}

/** Gap that splits two same-show plays into separate logical listens. */
const LISTEN_GAP_MS = 6 * 3600 * 1000;

/** How many shows we cache per window. UI serves a smaller `limit` slice. */
const ROLLUP_PER_WINDOW = 50;

/**
 * Rebuild `show_listens_rollup` from raw events for all four windows.
 *
 * Ranking primitive is a **logical listen** — a maximal run of
 * `playback_start` events from one install on one show, broken by either
 * (a) a play of a *different* show in between, or (b) a gap of more than
 * `LISTEN_GAP_MS`. This dedupes the case where the same listener stops
 * and restarts the same show within one sitting (which the previous
 * session-based primitive counted as multiple votes — see ADR-0004).
 *
 * Runs hourly in a background job. The endpoint never touches raw events;
 * it just reads precomputed top-N rows from this table.
 */
export function rebuildShowListensRollup(): void {
  const db = getAnalyticsDb();
  const now = Date.now();

  // SQL inserts the top-N for one window, given (gap, windowName, sinceMs, limit).
  // The LAG scan runs over the full event history (no `ts >` filter), so
  // listen-start detection is correct even when a listen straddles the
  // window boundary; the WHERE on `ts > sinceMs` filters which listen-start
  // events count toward this window.
  const insertWindow = db.prepare(`
    INSERT INTO show_listens_rollup (window, show_id, listens, plays, installs)
    WITH plays AS (
      SELECT iid,
             json_extract(props, '$.show_id') AS show_id,
             ts,
             LAG(json_extract(props, '$.show_id')) OVER w AS prev_show,
             LAG(ts)                              OVER w AS prev_ts
      FROM analytics_events
      WHERE event = 'playback_start'
        AND json_extract(props, '$.show_id') IS NOT NULL
      WINDOW w AS (PARTITION BY iid ORDER BY ts)
    ),
    listens AS (
      SELECT iid, show_id, ts,
             CASE
               WHEN prev_show IS NULL
                 OR prev_show != show_id
                 OR (ts - prev_ts) > ?
               THEN 1 ELSE 0
             END AS is_new
      FROM plays
    )
    SELECT
      ?         AS window,
      show_id,
      SUM(is_new)         AS listens,
      COUNT(*)            AS plays,
      COUNT(DISTINCT iid) AS installs
    FROM listens
    WHERE ts > ?
    GROUP BY show_id
    HAVING SUM(is_new) > 0
    ORDER BY listens DESC, plays DESC
    LIMIT ?
  `);

  const tx = db.transaction(() => {
    db.exec(`DELETE FROM show_listens_rollup`);
    for (const name of TRENDING_WINDOWS) {
      insertWindow.run(LISTEN_GAP_MS, name, windowSinceMs(name, now), ROLLUP_PER_WINDOW);
    }
  });
  tx();
}

/**
 * MM-DD strings for today and ±1 day, used to exclude "Today in Grateful
 * Dead History" shows from the `now` window of trending. The 24h window
 * straddles two calendar dates, and the OTD home rail promotes the
 * matching anniversary shows, so without this filter the `now` ranking
 * is dominated by anniversary plays rather than organic listening.
 * ±1 covers the calendar boundary (yesterday's anniversary plays from
 * 23:00 are still inside the 24h window at 22:00 today).
 */
function anniversaryMonthDays(now: Date): string[] {
  const fmt = (d: Date): string =>
    `${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}`;
  const today = new Date(now);
  const yesterday = new Date(now.getTime() - 24 * 3600 * 1000);
  const tomorrow = new Date(now.getTime() + 24 * 3600 * 1000);
  return [fmt(yesterday), fmt(today), fmt(tomorrow)];
}

/**
 * Trending shows across four time windows. Reads precomputed rows from
 * `show_listens_rollup` — no event scan, no Redis round-trip — so it's
 * cheap even under traffic spikes.
 *
 * `includeAnniversaries` controls whether shows whose date matches
 * today (MM-DD ±1) are filtered out of the `now` window. Defaults to
 * false so trending surfaces organic momentum rather than echoing the
 * OTD home rail. Other windows are never filtered — week/month/all are
 * long enough that anniversary spikes wash out.
 */
export function getTrending(
  limit = 10,
  includeAnniversaries = false,
): TrendingResponse {
  const db = getAnalyticsDb();

  const plainStmt = db.prepare(
    `SELECT show_id, listens, plays, installs
     FROM show_listens_rollup
     WHERE window = ?
     ORDER BY listens DESC, plays DESC
     LIMIT ?`,
  );

  // show_id is `YYYY-MM-DD-...`; matching on substr(show_id, 6, 5) lets
  // SQLite use the WHERE clause without pulling rows through JS.
  const filteredNowStmt = db.prepare(
    `SELECT show_id, listens, plays, installs
     FROM show_listens_rollup
     WHERE window = 'now'
       AND substr(show_id, 6, 5) NOT IN (?, ?, ?)
     ORDER BY listens DESC, plays DESC
     LIMIT ?`,
  );

  const fetchPlain = (name: TrendingWindowName): TrendingShow[] =>
    plainStmt.all(name, limit) as TrendingShow[];

  let nowRows: TrendingShow[];
  if (includeAnniversaries) {
    nowRows = fetchPlain("now");
  } else {
    const [yest, today, tmrw] = anniversaryMonthDays(new Date());
    nowRows = filteredNowStmt.all(yest, today, tmrw, limit) as TrendingShow[];
  }

  return {
    generated_at: new Date().toISOString(),
    windows: {
      now: nowRows,
      week: fetchPlain("week"),
      month: fetchPlain("month"),
      all: fetchPlain("all"),
    },
  };
}

// ── Cleanup ──────────────────────────────────────────────────────────

export function closeAnalyticsDb(): void {
  if (db) {
    db.close();
    db = null;
  }
}
