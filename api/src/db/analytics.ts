import Database from "better-sqlite3";
import path from "node:path";

const DB_PATH =
  process.env.ANALYTICS_DB_PATH ??
  path.join(process.cwd(), "data", "analytics.db");

let db: Database.Database | null = null;

/** Platforms the analytics pipeline recognizes. `web` is browser usage —
 *  not an installed app — so the admin dashboard defaults to filtering it
 *  out of install/active-user counts (see platformClause + the dashboard
 *  platform toggles). */
export const KNOWN_PLATFORMS = ["ios", "android", "web"] as const;

/**
 * Build a safe ` AND <col> IN ('ios','android')` SQL fragment from an
 * admin-supplied platform allowlist. Each value is validated against
 * {@link KNOWN_PLATFORMS}, so inlining them carries no injection risk and
 * callers can splice the fragment into any WHERE without binding extra
 * params.
 *
 * Returns "" (a no-op) when the filter is absent, empty/all-invalid, or
 * selects every known platform — so an unfiltered call behaves exactly as
 * it did before platform filtering existed. `col` lets aliased queries
 * target e.g. `s.platform`.
 */
export function platformClause(
  platforms: string[] | undefined,
  col = "platform",
): string {
  if (!platforms || platforms.length === 0) return "";
  const valid = [...new Set(platforms)].filter((p) =>
    (KNOWN_PLATFORMS as readonly string[]).includes(p),
  );
  if (valid.length === 0 || valid.length === KNOWN_PLATFORMS.length) return "";
  return ` AND ${col} IN (${valid.map((p) => `'${p}'`).join(", ")})`;
}

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
 *
 * FOOTGUN: the index keys on primitive columns only — `props` is not
 * considered. Two events from the same install/session at the exact same
 * millisecond with *different* props (e.g. two `add_favorite`s for
 * different shows, two `playback_start`s for different tracks) collide and
 * one gets silently dropped. In practice clients emit events serially so
 * collisions are vanishingly rare; if we ever ship a bulk-action UI (e.g.
 * "favorite all", batch import), the client must stagger timestamps or we
 * widen the index. Surfaced during getPopularShows test setup — see the
 * popular.test.ts comments and PLANS/home-discovery-rails.md.
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

export function getSummary(platforms?: string[]): AnalyticsSummary {
  const db = getAnalyticsDb();
  const now = Date.now();
  const dayAgo = now - 24 * 3600 * 1000;
  const weekAgo = now - 7 * 24 * 3600 * 1000;
  const monthAgo = now - 30 * 24 * 3600 * 1000;
  // Platform filter spliced into each WHERE. `pc` targets the bare
  // `platform` column; `pcS` targets the `s.`-aliased playback_start rows
  // in the top_shows CTE.
  const pc = platformClause(platforms);
  const pcS = platformClause(platforms, "s.platform");

  // DAU/WAU/MAU from app_open events
  const dau =
    (
      db
        .prepare(
          `SELECT COUNT(DISTINCT iid) AS c FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc}`,
        )
        .get(dayAgo) as { c: number }
    )?.c ?? 0;

  const wau =
    (
      db
        .prepare(
          `SELECT COUNT(DISTINCT iid) AS c FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc}`,
        )
        .get(weekAgo) as { c: number }
    )?.c ?? 0;

  const mau =
    (
      db
        .prepare(
          `SELECT COUNT(DISTINCT iid) AS c FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc}`,
        )
        .get(monthAgo) as { c: number }
    )?.c ?? 0;

  // Total unique installs ever
  const total_installs =
    (
      db
        .prepare(`SELECT COUNT(DISTINCT iid) AS c FROM analytics_events WHERE 1=1${pc}`)
        .get() as { c: number }
    )?.c ?? 0;

  // Stale installs: seen ever but not in last 30 days (both bounds honor
  // the platform filter, so "stale" means stale on the selected platforms).
  const stale_installs_30d =
    (
      db
        .prepare(
          `SELECT COUNT(DISTINCT iid) AS c FROM analytics_events
       WHERE iid NOT IN (SELECT DISTINCT iid FROM analytics_events WHERE ts > ?${pc})${pc}`,
        )
        .get(monthAgo) as { c: number }
    )?.c ?? 0;

  // Platform split (last 30 days)
  const platformRows = db
    .prepare(
      `SELECT platform, COUNT(DISTINCT iid) AS c FROM analytics_events
     WHERE event = 'app_open' AND ts > ?${pc} GROUP BY platform`,
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
         WHERE s.event = 'playback_start' AND s.ts > ?${pcS}
       ),
       track_plays AS (
         SELECT
           json_extract(props, '$.show_id') AS show_id,
           COUNT(*) AS track_plays
         FROM analytics_events
         WHERE event = 'playback_start' AND ts > ?${pc}
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
         WHERE event = 'playback_end' AND ts > ?${pc}
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
     WHERE event = 'feature_use' AND ts > ?${pc}
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
           AND ts > ?${pc}
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
         AND ts > ?${pc}
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
       WHERE event = 'playback_start' AND ts > ?${pc}
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
          `SELECT COUNT(*) AS c FROM analytics_events WHERE ts >= ?${pc}`,
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
export function getRetentionCohorts(
  weeks = 12,
  platforms?: string[],
): RetentionCohort[] {
  const db = getAnalyticsDb();
  const oldestWeekStart = Date.now() - weeks * 7 * 24 * 3600 * 1000;
  const todayDay = new Date().toISOString().slice(0, 10);
  const pc = platformClause(platforms);

  return db
    .prepare(
      `WITH first_open AS (
         SELECT iid,
                MIN(ts) AS install_ts,
                date(MIN(ts) / 1000, 'unixepoch') AS install_day
         FROM analytics_events
         WHERE event = 'app_open'${pc}
         GROUP BY iid
         HAVING install_ts >= ?
       ),
       active AS (
         SELECT DISTINCT iid, date(ts / 1000, 'unixepoch') AS active_day
         FROM analytics_events
         WHERE event = 'app_open'${pc}
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
export function getTopShows(
  days: number,
  limit = 20,
  platforms?: string[],
): TopShowRow[] {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;
  const pc = platformClause(platforms);
  const pcS = platformClause(platforms, "s.platform");
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
         WHERE s.event = 'playback_start' AND s.ts > ?${pcS}
       ),
       track_plays AS (
         SELECT
           json_extract(props, '$.show_id') AS show_id,
           COUNT(*) AS track_plays
         FROM analytics_events
         WHERE event = 'playback_start' AND ts > ?${pc}
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
         WHERE event = 'playback_end' AND ts > ?${pc}
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
 * `playback_start` keeps a listener live for a long time (mid-track
 * keepalive — Grateful Dead jams routinely run 25–35 min with no
 * intervening events) while a recent `playback_end` only keeps them
 * live for a short window (between-tracks autoplay gap). Together
 * they cover the three real cases:
 *   - mid-track:      latest event is a start within START window → live
 *   - between tracks: latest is an end within END window           → live
 *   - finished:       latest is an end past END window              → not live
 *
 * `playback_end` with reason `app_backgrounded` is excluded from the
 * latest-event picker: backgrounding the app does not stop audio
 * (locked phone in pocket is the modal listening posture). Treating
 * those as real ends dropped ~25% of finishes off Live within 2 min
 * even though playback continued. The preceding `playback_start`
 * remains the keepalive anchor.
 *
 * Replaced the earlier single-window approach: a 45-min "unmatched
 * start" rule mis-classified between-tracks users as Recent, and a
 * 90-second soft window dropped mid-jam users out of Live.
 */
const LIVE_START_WINDOW_MS = 45 * 60 * 1000;
const LIVE_END_WINDOW_MS = 2 * 60 * 1000;

/** Reasons on `playback_end` that do NOT actually stop audio — exclude
 *  these from the "latest event" calculation so a backgrounded user
 *  stays Live until their `playback_start` ages out of the 45-min
 *  window. */
const NON_TERMINAL_END_REASONS_SQL = `('app_backgrounded')`;

export function getLiveListeners(platforms?: string[]): LiveListener[] {
  const db = getAnalyticsDb();
  const now = Date.now();
  const startWindowStart = now - LIVE_START_WINDOW_MS;
  const endWindowStart = now - LIVE_END_WINDOW_MS;
  const lookbackStart = now - 6 * 3600 * 1000;
  const pc = platformClause(platforms);

  // Pick the latest playback event per iid (start or end), then keep
  // only those whose latest event qualifies under the dual-window rule.
  // `started_at` is resolved separately as the most recent
  // `playback_start` for this (iid, show_id) so the UI can render
  // "started X ago" correctly even when the keepalive event is an end.
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
           AND ts >= ?${pc}
           AND json_extract(props, '$.show_id') IS NOT NULL
           AND NOT (
             event = 'playback_end'
             AND json_extract(props, '$.reason') IN ${NON_TERMINAL_END_REASONS_SQL}
           )
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
         AND (
           (l.event = 'playback_start' AND l.ts >= ?)
           OR (l.event = 'playback_end' AND l.ts >= ?)
         )
       ORDER BY l.ts DESC`,
    )
    .all(lookbackStart, lookbackStart, startWindowStart, endWindowStart) as Array<{
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

export function getSearchQuality(
  days = 30,
  platforms?: string[],
): SearchQuality {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;
  const pc = platformClause(platforms);

  const overview = db
    .prepare(
      `SELECT
         COUNT(*) AS total_searches,
         COUNT(CASE WHEN json_extract(props, '$.result_count') = 0 THEN 1 END) AS zero_result_count,
         COUNT(CASE WHEN json_extract(props, '$.selected_index') IS NULL THEN 1 END) AS abandon_count
       FROM analytics_events
       WHERE event = 'search' AND ts > ?${pc}`,
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
       WHERE event = 'search' AND ts > ?${pc}
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
       WHERE event = 'search' AND ts > ?${pc}
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
       WHERE event = 'search' AND ts > ?${pc}
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
export function getGrowthByPlatform(
  days = 60,
  platforms?: string[],
): GrowthDay[] {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;
  // Each iid is attributed to its first event's platform, so the platform
  // filter is applied after that attribution: a deselected platform is
  // dropped from both its own series and the daily total. null = show all.
  const selected =
    platforms && platforms.length
      ? new Set(
          platforms.filter((p) =>
            (KNOWN_PLATFORMS as readonly string[]).includes(p),
          ),
        )
      : null;

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
    if (selected && !selected.has(r.platform)) continue;
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

export function getTimeseries(
  metric: TimeseriesMetric,
  days: number,
  platforms?: string[],
): TimeseriesPoint[] {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;
  const pc = platformClause(platforms);

  switch (metric) {
    case "dau":
      return db.prepare(`
        SELECT date(ts / 1000, 'unixepoch') AS day, COUNT(DISTINCT iid) AS value
        FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc}
        GROUP BY day ORDER BY day ASC
      `).all(cutoff) as TimeseriesPoint[];

    case "events":
      return db.prepare(`
        SELECT date(ts / 1000, 'unixepoch') AS day, COUNT(*) AS value
        FROM analytics_events WHERE ts > ?${pc}
        GROUP BY day ORDER BY day ASC
      `).all(cutoff) as TimeseriesPoint[];

    case "playback_starts":
      return db.prepare(`
        SELECT date(ts / 1000, 'unixepoch') AS day, COUNT(*) AS value
        FROM analytics_events WHERE event = 'playback_start' AND ts > ?${pc}
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
          WHERE 1=1${pc}
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

// ── Version distribution & adoption ─────────────────────────────────

export interface VersionDistributionRow {
  version: string;
  /** Installs whose most recent event was within 7 days. */
  active: number;
  /** Most recent event 8–30 days ago. */
  idle: number;
  /** Most recent event > 30 days ago. */
  stale: number;
  total: number;
}

/**
 * Snapshot of installs by their *current* app version (view A of the
 * Versions panel). Each install is attributed to the `app_version` on its
 * most recent event — not a lexical MAX, which would sort "2.9.0" above
 * "2.10.0" — and bucketed by how recently that install was last seen,
 * reusing the dashboard's active(≤7d)/idle(8–30d)/stale(>30d) thresholds.
 * One row per iid via a window function (same pattern as getLiveListeners).
 */
export function getVersionDistribution(
  platforms?: string[],
): VersionDistributionRow[] {
  const db = getAnalyticsDb();
  const now = Date.now();
  const weekAgo = now - 7 * 24 * 3600 * 1000;
  const monthAgo = now - 30 * 24 * 3600 * 1000;
  const pc = platformClause(platforms);

  return db
    .prepare(
      `WITH latest AS (
         SELECT iid, app_version, ts,
                ROW_NUMBER() OVER (PARTITION BY iid ORDER BY ts DESC) AS rn
         FROM analytics_events
         WHERE 1=1${pc}
       )
       SELECT
         app_version AS version,
         SUM(CASE WHEN ts > ? THEN 1 ELSE 0 END) AS active,
         SUM(CASE WHEN ts <= ? AND ts > ? THEN 1 ELSE 0 END) AS idle,
         SUM(CASE WHEN ts <= ? THEN 1 ELSE 0 END) AS stale,
         COUNT(*) AS total
       FROM latest
       -- Hide local dev/debug builds; they aren't a released version.
       WHERE rn = 1 AND app_version != 'dev'
       GROUP BY app_version
       ORDER BY total DESC`,
    )
    .all(weekAgo, weekAgo, monthAgo, monthAgo) as VersionDistributionRow[];
}

export interface VersionDay {
  day: string;
  version: string;
  /** Distinct installs active that day running this version. */
  count: number;
}

/**
 * Version mix over time (view B of the Versions panel). For each day, counts
 * the distinct installs that opened the app, grouped by the version they ran
 * *that day*. An install that updated mid-day is counted once under its newer
 * version (latest app_open that day wins), so daily counts never double-count
 * an iid. The component normalizes each day to 100% share to draw the fading
 * stacked-area rollout curve. Window is the last `days` days.
 */
export function getVersionTimeseries(
  days = 90,
  platforms?: string[],
): VersionDay[] {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;
  const pc = platformClause(platforms);

  return db
    .prepare(
      `WITH opens AS (
         SELECT
           date(ts / 1000, 'unixepoch') AS day,
           iid,
           app_version,
           ROW_NUMBER() OVER (
             PARTITION BY date(ts / 1000, 'unixepoch'), iid
             ORDER BY ts DESC
           ) AS rn
         FROM analytics_events
         -- Hide local dev/debug builds; they aren't a released version.
         WHERE event = 'app_open' AND ts > ? AND app_version != 'dev'${pc}
       )
       SELECT day, app_version AS version, COUNT(*) AS count
       FROM opens
       WHERE rn = 1
       GROUP BY day, version
       ORDER BY day ASC`,
    )
    .all(cutoff) as VersionDay[];
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
  platforms?: string[],
): RecentListeningSession[] {
  const db = getAnalyticsDb();
  const now = Date.now();
  const windowStart = now - hours * 3600 * 1000;
  const liveStartWindow = now - LIVE_START_WINDOW_MS;
  const liveEndWindow = now - LIVE_END_WINDOW_MS;
  const pc = platformClause(platforms);
  // Inner lookback for "what's this iid's most recent event overall" —
  // must be at least as wide as the longer of the two live windows.
  const liveLookbackStart = liveStartWindow;

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
           AND ts >= ?${pc}
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
       -- Mirror the dual-window live definition in getLiveListeners:
       -- exclude (iid, show_id) pairs where this iid's most recent event
       -- describes this show AND qualifies as live under either
       -- (start within START window) OR (end within END window).
       WHERE NOT EXISTS (
         SELECT 1 FROM analytics_events latest
         WHERE latest.event IN ('playback_start', 'playback_end')
           AND latest.iid = a.iid
           AND json_extract(latest.props, '$.show_id') = a.show_id
           AND NOT (
             latest.event = 'playback_end'
             AND json_extract(latest.props, '$.reason') IN ${NON_TERMINAL_END_REASONS_SQL}
           )
           AND latest.ts = (
             SELECT MAX(x.ts) FROM analytics_events x
             WHERE x.event IN ('playback_start', 'playback_end')
               AND x.iid = a.iid
               AND x.ts >= ?
               AND NOT (
                 x.event = 'playback_end'
                 AND json_extract(x.props, '$.reason') IN ${NON_TERMINAL_END_REASONS_SQL}
               )
           )
           AND (
             (latest.event = 'playback_start' AND latest.ts >= ?)
             OR (latest.event = 'playback_end' AND latest.ts >= ?)
           )
       )
       ORDER BY a.last_event_at DESC
       LIMIT ?`,
    )
    .all(
      windowStart,
      liveLookbackStart,
      liveStartWindow,
      liveEndWindow,
      limit,
    ) as Array<{
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

// ── Network error outcomes ────────────────────────────────────────────

export type NetworkErrorOutcome =
  | "recover_same_track"
  | "skipped_to_next"
  | "skipped_ahead"
  | "jumped_back"
  | "next_event_is_end"
  | "no_followup";

export interface NetworkErrorSummary {
  /** Window inspected, in days. */
  days: number;
  /** Platform filter applied, or null for all platforms. */
  platform: string | null;
  /** Total network_error events in the window. */
  total: number;
  /** Count by outcome. */
  outcomes: Array<{ outcome: NetworkErrorOutcome; count: number; avg_gap_s: number | null }>;
  /** Most recent N errors with their classified outcome. */
  recent: Array<{
    ts: number;
    iid: string;
    platform: string;
    app_version: string;
    show_id: string | null;
    recording_id: string | null;
    track_index: number | null;
    outcome: NetworkErrorOutcome;
    /** Seconds until the next playback event for this iid+show, if any. */
    gap_s: number | null;
    next_track_index: number | null;
  }>;
}

export function getNetworkErrorOutcomes(
  days = 30,
  platform: string | null = null,
  recentLimit = 50,
): NetworkErrorSummary {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;

  // Each network_error end is classified by the next playback_start /
  // playback_end for the same (iid, show_id) — same logic as the
  // exploratory query that surfaced the 75%-skip pattern.
  const classifySql = `
    WITH ne AS (
      SELECT id, iid, ts, platform, app_version,
             json_extract(props, '$.show_id') AS show_id,
             json_extract(props, '$.recording_id') AS recording_id,
             CAST(json_extract(props, '$.track_index') AS INTEGER) AS track_index
      FROM analytics_events
      WHERE event = 'playback_end'
        AND json_extract(props, '$.reason') = 'network_error'
        AND ts >= ?
        ${platform ? "AND platform = ?" : ""}
    ),
    classified AS (
      SELECT
        ne.id, ne.iid, ne.ts, ne.platform, ne.app_version,
        ne.show_id, ne.recording_id, ne.track_index,
        (SELECT n.event FROM analytics_events n
           WHERE n.iid = ne.iid AND n.ts > ne.ts
             AND n.event IN ('playback_start','playback_end')
             AND json_extract(n.props, '$.show_id') = ne.show_id
           ORDER BY n.ts LIMIT 1) AS next_event,
        (SELECT CAST(json_extract(n.props, '$.track_index') AS INTEGER) FROM analytics_events n
           WHERE n.iid = ne.iid AND n.ts > ne.ts
             AND n.event IN ('playback_start','playback_end')
             AND json_extract(n.props, '$.show_id') = ne.show_id
           ORDER BY n.ts LIMIT 1) AS next_trk,
        (SELECT (n.ts - ne.ts) / 1000.0 FROM analytics_events n
           WHERE n.iid = ne.iid AND n.ts > ne.ts
             AND n.event IN ('playback_start','playback_end')
             AND json_extract(n.props, '$.show_id') = ne.show_id
           ORDER BY n.ts LIMIT 1) AS gap_s
      FROM ne
    )
    SELECT
      id, iid, ts, platform, app_version, show_id, recording_id, track_index,
      gap_s, next_trk,
      CASE
        WHEN next_event IS NULL THEN 'no_followup'
        WHEN next_event = 'playback_end' THEN 'next_event_is_end'
        WHEN next_event = 'playback_start' AND next_trk = track_index THEN 'recover_same_track'
        WHEN next_event = 'playback_start' AND next_trk = track_index + 1 THEN 'skipped_to_next'
        WHEN next_event = 'playback_start' AND next_trk > track_index + 1 THEN 'skipped_ahead'
        WHEN next_event = 'playback_start' AND next_trk < track_index THEN 'jumped_back'
        ELSE 'no_followup'
      END AS outcome
    FROM classified
  `;

  const params: Array<number | string> = [cutoff];
  if (platform) params.push(platform);

  const all = db.prepare(classifySql).all(...params) as Array<{
    id: number;
    iid: string;
    ts: number;
    platform: string;
    app_version: string;
    show_id: string | null;
    recording_id: string | null;
    track_index: number | null;
    gap_s: number | null;
    next_trk: number | null;
    outcome: NetworkErrorOutcome;
  }>;

  const buckets = new Map<NetworkErrorOutcome, { count: number; gapSum: number; gapN: number }>();
  for (const r of all) {
    const b = buckets.get(r.outcome) ?? { count: 0, gapSum: 0, gapN: 0 };
    b.count++;
    if (r.gap_s !== null) {
      b.gapSum += r.gap_s;
      b.gapN++;
    }
    buckets.set(r.outcome, b);
  }
  const outcomes = Array.from(buckets.entries())
    .map(([outcome, b]) => ({
      outcome,
      count: b.count,
      avg_gap_s: b.gapN > 0 ? Math.round((b.gapSum / b.gapN) * 10) / 10 : null,
    }))
    .sort((a, b) => b.count - a.count);

  const recent = all
    .slice()
    .sort((a, b) => b.ts - a.ts)
    .slice(0, recentLimit)
    .map((r) => ({
      ts: r.ts,
      iid: r.iid,
      platform: r.platform,
      app_version: r.app_version,
      show_id: r.show_id,
      recording_id: r.recording_id,
      track_index: r.track_index,
      outcome: r.outcome,
      gap_s: r.gap_s,
      next_track_index: r.next_trk,
    }));

  return {
    days,
    platform,
    total: all.length,
    outcomes,
    recent,
  };
}

export function getShowPlaybackSummary(
  days: number,
  platforms?: string[],
): ShowPlaybackSummary {
  const db = getAnalyticsDb();
  const cutoff = Date.now() - days * 24 * 3600 * 1000;
  const pc = platformClause(platforms);

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
      AND ts > ?${pc}
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

export function getDetail(
  metric: DetailMetric,
  filter?: string,
  platforms?: string[],
): DetailRow[] {
  const db = getAnalyticsDb();
  const now = Date.now();
  const dayAgo = now - 24 * 3600 * 1000;
  const weekAgo = now - 7 * 24 * 3600 * 1000;
  const monthAgo = now - 30 * 24 * 3600 * 1000;
  const todayStart = new Date(new Date().toISOString().slice(0, 10)).getTime();
  const pc = platformClause(platforms);

  switch (metric) {
    case "dau":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc}
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all(dayAgo) as DetailRow[];

    case "wau":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc}
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all(weekAgo) as DetailRow[];

    case "mau":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc}
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all(monthAgo) as DetailRow[];

    case "total_installs":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events
        WHERE 1=1${pc}
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all() as DetailRow[];

    case "stale_installs":
      return db.prepare(`
        SELECT iid, platform, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events
        WHERE iid NOT IN (SELECT DISTINCT iid FROM analytics_events WHERE ts > ?${pc})${pc}
        GROUP BY iid ORDER BY MAX(ts) DESC
      `).all(monthAgo) as DetailRow[];

    case "events_today":
      return db.prepare(`
        SELECT event AS detail, platform, app_version,
          iid,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          1 AS event_count
        FROM analytics_events WHERE ts >= ?${pc}
        ORDER BY ts DESC LIMIT 500
      `).all(todayStart) as DetailRow[];

    case "new_installs":
      // Filter is a YYYY-MM-DD day. List the iids whose first-ever event
      // landed on that day. Without filter, list the most recent
      // first-seen iids overall.
      if (filter) {
        return db.prepare(`
          WITH first_seen AS (
            SELECT iid, MIN(ts) AS first_ts FROM analytics_events WHERE 1=1${pc} GROUP BY iid
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
          SELECT iid, MIN(ts) AS first_ts FROM analytics_events WHERE 1=1${pc} GROUP BY iid
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
          WHERE event = 'playback_start' AND ts > ?${pc} AND ${filterClause}
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
        WHERE event = 'playback_start' AND ts > ?${pc}
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
          WHERE event = 'playback_start' AND ts > ?${pc} AND json_extract(props, '$.show_id') = ?
          ORDER BY ts DESC LIMIT 500
        `).all(monthAgo, filter) as DetailRow[];
      }
      return db.prepare(`
        SELECT json_extract(props, '$.show_id') AS detail,
          iid, platform, app_version,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          1 AS event_count
        FROM analytics_events
        WHERE event = 'playback_start' AND ts > ?${pc}
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
          WHERE event = 'feature_use' AND ts > ?${pc} AND json_extract(props, '$.feature') = ?
          ORDER BY ts DESC LIMIT 500
        `).all(monthAgo, filter) as DetailRow[];
      }
      return db.prepare(`
        SELECT json_extract(props, '$.feature') AS detail,
          iid, platform, app_version,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          1 AS event_count
        FROM analytics_events
        WHERE event = 'feature_use' AND ts > ?${pc}
        ORDER BY ts DESC LIMIT 500
      `).all(monthAgo) as DetailRow[];

    case "platform_split":
      if (filter) {
        return db.prepare(`
          SELECT platform, iid, app_version,
            datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
            COUNT(*) AS event_count
          FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc} AND platform = ?
          GROUP BY iid ORDER BY MAX(ts) DESC
        `).all(monthAgo, filter) as DetailRow[];
      }
      return db.prepare(`
        SELECT platform, iid, app_version,
          datetime(MAX(ts)/1000, 'unixepoch') AS last_seen,
          COUNT(*) AS event_count
        FROM analytics_events WHERE event = 'app_open' AND ts > ?${pc}
        GROUP BY iid ORDER BY platform, MAX(ts) DESC
      `).all(monthAgo) as DetailRow[];

    case "playback":
      return db.prepare(`
        SELECT json_extract(props, '$.show_id') AS detail,
          iid, platform, app_version,
          datetime(ts/1000, 'unixepoch') AS last_seen,
          CAST(json_extract(props, '$.listened_ms') AS INTEGER) AS event_count
        FROM analytics_events
        WHERE event = 'playback_end' AND ts > ?${pc}
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

// ── Popular shows (favorites-driven) ──────────────────────────────────

export interface PopularShow {
  show_id: string;
  /** Distinct installs whose latest favorite action on this show is `add`
   *  (i.e. currently favorited — adds minus subsequent removes). */
  favorites: number;
  /** All-time logical listens from show_listens_rollup. */
  listens: number;
  /**
   * favorites / max(listens, 1). The differentiator from Trending: surfaces
   * shows people *kept*, not shows people *played a lot*. A high ratio
   * means a high fraction of people who heard it favorited it.
   */
  ratio: number;
}

export type PopularDecade = "60s" | "70s" | "80s" | "90s";

export interface PopularDecadeBuckets {
  "60s": PopularShow[];
  "70s": PopularShow[];
  "80s": PopularShow[];
  "90s": PopularShow[];
}

export interface PopularResponse {
  generated_at: string;
  decades: PopularDecadeBuckets;
}

export interface PopularOptions {
  /** Override Date.now() for deterministic tests. */
  nowMs?: number;
  /** Minimum distinct favoriters required to surface a show in the pool. */
  minFavorites?: number;
  /** Target sample size drawn from each decade's qualifying pool. */
  perDecade?: number;
  /** Rotation window in hours — the time bucket that seeds the shuffle. */
  rotationHours?: number;
}

function envNum(name: string, fallback: number): number {
  const raw = process.env[name];
  if (!raw) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

// Server-tunable so we don't need an app release to retune the rail.
// Floor stays at 1 in the early stage — current prod data has most shows
// sitting at 1–2 favoriters, so floor=2 produces empty buckets. Raise via
// env var once volume grows.
const POPULAR_MIN_FAVORITES = envNum("POPULAR_MIN_FAVORITES", 1);
/** Per-decade pool size returned to the client. The client picks 4
 *  display shows from these pools and re-rolls on "Show more" without
 *  another round trip, so we want a healthy pool but not the whole tail. */
const POPULAR_PER_DECADE = envNum("POPULAR_PER_DECADE", 20);
const POPULAR_ROTATION_HOURS = envNum("POPULAR_ROTATION_HOURS", 4);

const DECADE_KEYS = ["60s", "70s", "80s", "90s"] as const;
type DecadeKey = (typeof DECADE_KEYS)[number];

function decadeOf(showId: string): DecadeKey | null {
  // Two show_id formats in the wild:
  //   - slug:   "1977-05-11-st-paul-civic-center-..." (prod canonical)
  //   - legacy: "gd1977-05-11.sbd.miller..." (older / test fixtures)
  // Pull the first 4-digit run regardless of any leading prefix.
  const m = showId.match(/(\d{4})/);
  if (!m) return null;
  const year = parseInt(m[1]!, 10);
  if (year >= 1960 && year < 1970) return "60s";
  if (year >= 1970 && year < 1980) return "70s";
  if (year >= 1980 && year < 1990) return "80s";
  if (year >= 1990 && year < 2000) return "90s";
  return null;
}

/** Sortable date key (`YYYY-MM-DD`) extracted from either show_id format. */
function dateKey(showId: string): string {
  const m = showId.match(/(\d{4}-\d{2}-\d{2})/);
  return m ? m[1]! : showId;
}

// mulberry32 — small, fast, deterministic PRNG. Good enough for shuffle.
function mulberry32(seed: number): () => number {
  let s = seed | 0;
  return function () {
    s = (s + 0x6d2b79f5) | 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function seededShuffle<T>(arr: readonly T[], seed: number): T[] {
  const rng = mulberry32(seed);
  const out = arr.slice();
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    const tmp = out[i]!;
    out[i] = out[j]!;
    out[j] = tmp;
  }
  return out;
}

/** Hash the (bucket index, decade) pair into a 32-bit seed. */
function decadeSeed(bucket: number, decade: string): number {
  let h = (bucket * 0x01000193) | 0;
  for (let i = 0; i < decade.length; i++) {
    h = Math.imul(h ^ decade.charCodeAt(i), 0x01000193) | 0;
  }
  return h >>> 0;
}

/**
 * "Popular" shows surfaced as four decade *pools* (60s/70s/80s/90s). Each
 * pool is `perDecade` shows drawn via a uniform shuffle seeded by the
 * current rotation window, so the underlying pool is stable for
 * ~`rotationHours` and rotates on its own. The client picks the 4-show
 * display set from these pools and re-rolls on "Show more" without
 * another round trip — that's why the server returns pools, not a
 * pre-picked display set.
 *
 * `target_type` filter:
 *   - 'show' → this surface (Unit 2).
 *   - 'recording_track' → reserved for the future "Best …" rail (Unit 3).
 *   - NULL → legacy events emitted before target_type was added, which
 *     also have no target_id. Unrecoverable; filtered out implicitly by
 *     the IS NOT NULL on target_id.
 */
export function getPopularShows(options: PopularOptions = {}): PopularResponse {
  const db = getAnalyticsDb();
  const nowMs = options.nowMs ?? Date.now();
  const minFavorites = options.minFavorites ?? POPULAR_MIN_FAVORITES;
  const perDecade = options.perDecade ?? POPULAR_PER_DECADE;
  const rotationHours = options.rotationHours ?? POPULAR_ROTATION_HOURS;

  // Listens are computed inline (not from show_listens_rollup) because the
  // rollup only retains the top-50 per window — favorited shows outside
  // the top-50 listened set would otherwise read `listens = 0` and skew
  // the ratio. The CTE mirrors rebuildShowListensRollup's logical-listen
  // definition: count distinct "listen runs" per (iid, show_id), where a
  // run is broken either by a different show or a gap > LISTEN_GAP_MS.
  const pool = db
    .prepare(
      `WITH actions AS (
         SELECT
           iid,
           json_extract(props, '$.target_id') AS show_id,
           json_extract(props, '$.feature')   AS feature,
           ts
         FROM analytics_events
         WHERE event = 'feature_use'
           AND json_extract(props, '$.target_type') = 'show'
           AND json_extract(props, '$.feature') IN ('add_favorite', 'remove_favorite')
           AND json_extract(props, '$.target_id') IS NOT NULL
       ),
       latest AS (
         SELECT iid, show_id, feature, ts
         FROM actions a
         WHERE a.ts = (
           SELECT MAX(a2.ts) FROM actions a2
           WHERE a2.iid = a.iid AND a2.show_id = a.show_id
         )
       ),
       favs AS (
         SELECT
           show_id,
           COUNT(DISTINCT iid) AS favorites
         FROM latest
         WHERE feature = 'add_favorite'
         GROUP BY show_id
         HAVING favorites >= ?
       ),
       plays AS (
         SELECT iid,
                json_extract(props, '$.show_id') AS show_id,
                ts,
                LAG(json_extract(props, '$.show_id')) OVER w AS prev_show,
                LAG(ts)                              OVER w AS prev_ts
         FROM analytics_events
         WHERE event = 'playback_start'
           AND json_extract(props, '$.show_id') IN (SELECT show_id FROM favs)
         WINDOW w AS (PARTITION BY iid ORDER BY ts)
       ),
       listen_runs AS (
         SELECT show_id,
                CASE
                  WHEN prev_show IS NULL
                    OR prev_show != show_id
                    OR (ts - prev_ts) > ?
                  THEN 1 ELSE 0
                END AS is_new
         FROM plays
       ),
       show_listens AS (
         SELECT show_id, SUM(is_new) AS listens
         FROM listen_runs
         GROUP BY show_id
       )
       SELECT
         f.show_id,
         f.favorites,
         COALESCE(sl.listens, 0) AS listens,
         CAST(f.favorites AS REAL) /
           CASE WHEN COALESCE(sl.listens, 0) > 0 THEN sl.listens ELSE 1 END
           AS ratio
       FROM favs f
       LEFT JOIN show_listens sl ON sl.show_id = f.show_id`,
    )
    .all(minFavorites, LISTEN_GAP_MS) as PopularShow[];

  const byDecade: Record<DecadeKey, PopularShow[]> = {
    "60s": [],
    "70s": [],
    "80s": [],
    "90s": [],
  };
  for (const row of pool) {
    const d = decadeOf(row.show_id);
    if (d) byDecade[d].push(row);
  }

  const bucket = Math.floor(nowMs / (rotationHours * 3600 * 1000));
  const buckets: PopularDecadeBuckets = {
    "60s": [],
    "70s": [],
    "80s": [],
    "90s": [],
  };
  for (const d of DECADE_KEYS) {
    // Stable per-rotation shuffle, then take the per-decade pool. The
    // client picks the 4-show display set from these pools and re-rolls
    // on "Show more" without another round trip.
    const shuffled = seededShuffle(byDecade[d], decadeSeed(bucket, d));
    buckets[d] = shuffled.slice(0, perDecade);
  }

  return {
    generated_at: new Date(nowMs).toISOString(),
    decades: buckets,
  };
}

// ── Cleanup ──────────────────────────────────────────────────────────

export function closeAnalyticsDb(): void {
  if (db) {
    db.close();
    db = null;
  }
}
