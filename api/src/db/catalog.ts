import Database from "better-sqlite3";
import path from "node:path";

const DB_PATH = process.env.CATALOG_DB_PATH ?? path.join(process.cwd(), "data", "catalog.db");

let db: Database.Database | null = null;

export function getCatalogDb(): Database.Database {
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
    CREATE TABLE IF NOT EXISTS artists (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      sort_name TEXT NOT NULL,
      short_name TEXT,
      ia_collection TEXT,
      musicbrainz_id TEXT,
      active_from INTEGER,
      active_to INTEGER,
      is_active INTEGER NOT NULL DEFAULT 0,
      description TEXT,
      image_url TEXT,
      website_url TEXT,
      data_sources TEXT NOT NULL DEFAULT '{}',
      created_at INTEGER NOT NULL DEFAULT (unixepoch()),
      updated_at INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE TABLE IF NOT EXISTS shows (
      id TEXT PRIMARY KEY,
      artist_id TEXT NOT NULL REFERENCES artists(id),
      date TEXT NOT NULL,
      year INTEGER NOT NULL,
      month INTEGER NOT NULL,
      day_of_year INTEGER NOT NULL,
      show_sequence INTEGER NOT NULL DEFAULT 1,
      venue_name TEXT,
      city TEXT,
      state TEXT,
      country TEXT NOT NULL DEFAULT 'US',
      primary_source TEXT,
      is_future INTEGER NOT NULL DEFAULT 0,
      setlist_status TEXT,
      setlist_raw TEXT,
      song_list TEXT,
      lineup_status TEXT,
      lineup_raw TEXT,
      recording_count INTEGER NOT NULL DEFAULT 0,
      best_recording_id TEXT,
      best_source_type TEXT,
      avg_rating REAL,
      total_reviews INTEGER NOT NULL DEFAULT 0,
      cover_image_url TEXT,
      notes TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch()),
      updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
      UNIQUE(artist_id, date, venue_name, show_sequence)
    );

    CREATE TABLE IF NOT EXISTS show_reviews (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      show_id TEXT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
      type TEXT NOT NULL,
      author TEXT,
      summary TEXT,
      content TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE INDEX IF NOT EXISTS idx_show_reviews_show ON show_reviews(show_id);

    CREATE INDEX IF NOT EXISTS idx_shows_artist_date ON shows(artist_id, date);
    CREATE INDEX IF NOT EXISTS idx_shows_year ON shows(artist_id, year);
    CREATE INDEX IF NOT EXISTS idx_shows_day_of_year ON shows(day_of_year);

    CREATE TABLE IF NOT EXISTS recordings (
      id TEXT PRIMARY KEY,
      show_id TEXT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
      source_type TEXT,
      rating REAL NOT NULL DEFAULT 0.0,
      raw_rating REAL NOT NULL DEFAULT 0.0,
      review_count INTEGER NOT NULL DEFAULT 0,
      confidence REAL NOT NULL DEFAULT 0.0,
      high_ratings INTEGER NOT NULL DEFAULT 0,
      low_ratings INTEGER NOT NULL DEFAULT 0,
      taper TEXT,
      source TEXT,
      lineage TEXT,
      image_url TEXT,
      ai_review_summary TEXT,
      ai_review_rating REAL,
      collection_timestamp INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE INDEX IF NOT EXISTS idx_recordings_show ON recordings(show_id);
    CREATE INDEX IF NOT EXISTS idx_recordings_rating ON recordings(show_id, rating DESC);

    CREATE TABLE IF NOT EXISTS collections (
      id TEXT PRIMARY KEY,
      artist_id TEXT NOT NULL REFERENCES artists(id),
      name TEXT NOT NULL,
      description TEXT NOT NULL,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE TABLE IF NOT EXISTS collection_shows (
      collection_id TEXT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
      show_id TEXT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
      PRIMARY KEY (collection_id, show_id)
    );

    CREATE TABLE IF NOT EXISTS pipeline_runs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      artist_id TEXT NOT NULL REFERENCES artists(id),
      collector_type TEXT NOT NULL,
      started_at INTEGER NOT NULL,
      completed_at INTEGER,
      status TEXT NOT NULL,
      records_processed INTEGER DEFAULT 0,
      records_created INTEGER DEFAULT 0,
      error_message TEXT
    );
  `);

  // ── Migrations for existing databases ──────────────────────────
  // Add cover_image_url column if missing (existing DBs won't have it from CREATE TABLE)
  const showCols = db.prepare("PRAGMA table_info(shows)").all() as { name: string }[];
  if (!showCols.some((c) => c.name === "cover_image_url")) {
    db.exec("ALTER TABLE shows ADD COLUMN cover_image_url TEXT");
  }

  // FTS5 virtual table — separate because CREATE VIRTUAL TABLE IF NOT EXISTS
  // is supported in SQLite 3.37+ but we check manually for safety
  const ftsExists = db.prepare(
    "SELECT name FROM sqlite_master WHERE type='table' AND name='shows_fts'"
  ).get();

  if (!ftsExists) {
    // Try preferred tokenizer with tokenchars first, fall back to plain unicode61
    const ftsColumns = `
        show_id UNINDEXED,
        artist_name,
        date_text,
        venue_name,
        city,
        state,
        song_list,
        member_list`;
    try {
      db.exec(`CREATE VIRTUAL TABLE shows_fts USING fts5(${ftsColumns},
        tokenize='unicode61 tokenchars "-./"');`);
    } catch {
      db.exec(`CREATE VIRTUAL TABLE shows_fts USING fts5(${ftsColumns},
        tokenize='unicode61');`);
    }
  }
}

// ── Query helpers ──────────────────────────────────────────────

export interface ArtistRow {
  id: string;
  name: string;
  sort_name: string;
  short_name: string | null;
  ia_collection: string | null;
  musicbrainz_id: string | null;
  active_from: number | null;
  active_to: number | null;
  is_active: number;
  description: string | null;
  image_url: string | null;
  website_url: string | null;
  data_sources: string;
  created_at: number;
  updated_at: number;
}

export interface ShowRow {
  id: string;
  artist_id: string;
  date: string;
  year: number;
  month: number;
  day_of_year: number;
  show_sequence: number;
  venue_name: string | null;
  city: string | null;
  state: string | null;
  country: string;
  primary_source: string | null;
  is_future: number;
  setlist_status: string | null;
  setlist_raw: string | null;
  song_list: string | null;
  lineup_status: string | null;
  lineup_raw: string | null;
  recording_count: number;
  best_recording_id: string | null;
  best_source_type: string | null;
  avg_rating: number | null;
  total_reviews: number;
  cover_image_url: string | null;
  notes: string | null;
  created_at: number;
  updated_at: number;
}

export interface RecordingRow {
  id: string;
  show_id: string;
  source_type: string | null;
  rating: number;
  raw_rating: number;
  review_count: number;
  confidence: number;
  high_ratings: number;
  low_ratings: number;
  taper: string | null;
  source: string | null;
  lineage: string | null;
  image_url: string | null;
  ai_review_summary: string | null;
  ai_review_rating: number | null;
  collection_timestamp: number;
}

export interface CollectionRow {
  id: string;
  artist_id: string;
  name: string;
  description: string;
  created_at: number;
}

export interface PipelineRunRow {
  id: number;
  artist_id: string;
  collector_type: string;
  started_at: number;
  completed_at: number | null;
  status: string;
  records_processed: number;
  records_created: number;
  error_message: string | null;
}

export interface ShowReviewRow {
  id: number;
  show_id: string;
  type: string;
  author: string | null;
  summary: string | null;
  content: string | null;
  created_at: number;
}

// ── Artists ─────────────────────────────────────────────────────

export function listArtists(): (ArtistRow & { show_count: number; recording_count: number })[] {
  const db = getCatalogDb();
  return db.prepare(`
    SELECT a.*,
      (SELECT COUNT(*) FROM shows WHERE artist_id = a.id) AS show_count,
      (SELECT COUNT(*) FROM recordings r JOIN shows s ON r.show_id = s.id WHERE s.artist_id = a.id) AS recording_count
    FROM artists a
    ORDER BY a.sort_name
  `).all() as (ArtistRow & { show_count: number; recording_count: number })[];
}

export function getArtist(id: string): (ArtistRow & { show_count: number; recording_count: number }) | undefined {
  const db = getCatalogDb();
  return db.prepare(`
    SELECT a.*,
      (SELECT COUNT(*) FROM shows WHERE artist_id = a.id) AS show_count,
      (SELECT COUNT(*) FROM recordings r JOIN shows s ON r.show_id = s.id WHERE s.artist_id = a.id) AS recording_count
    FROM artists a WHERE a.id = ?
  `).get(id) as (ArtistRow & { show_count: number; recording_count: number }) | undefined;
}

export function createArtist(artist: Omit<ArtistRow, "created_at" | "updated_at">): void {
  const db = getCatalogDb();
  db.prepare(`
    INSERT INTO artists (id, name, sort_name, short_name, ia_collection, musicbrainz_id,
      active_from, active_to, is_active, description, image_url, website_url, data_sources)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    artist.id, artist.name, artist.sort_name, artist.short_name,
    artist.ia_collection, artist.musicbrainz_id, artist.active_from, artist.active_to,
    artist.is_active, artist.description, artist.image_url, artist.website_url,
    artist.data_sources
  );
}

export function updateArtist(id: string, fields: Partial<Omit<ArtistRow, "id" | "created_at">>): void {
  const db = getCatalogDb();
  const sets: string[] = [];
  const values: unknown[] = [];
  for (const [key, value] of Object.entries(fields)) {
    if (key === "updated_at") continue;
    sets.push(`${key} = ?`);
    values.push(value);
  }
  sets.push("updated_at = unixepoch()");
  values.push(id);
  db.prepare(`UPDATE artists SET ${sets.join(", ")} WHERE id = ?`).run(...values);
}

export function deleteArtist(id: string): void {
  const db = getCatalogDb();
  // Cascade will handle shows/recordings/collections
  db.prepare("DELETE FROM artists WHERE id = ?").run(id);
}

// ── Shows ───────────────────────────────────────────────────────

export interface ShowListOptions {
  artistId: string;
  year?: number;
  month?: number;
  hasRecordings?: boolean;
  future?: boolean;
  sort?: "date_asc" | "date_desc" | "rating_desc";
  cursor?: string;
  limit?: number;
}

export function listShows(opts: ShowListOptions): { shows: ShowRow[]; nextCursor: string | null } {
  const db = getCatalogDb();
  const conditions: string[] = ["s.artist_id = ?"];
  const params: unknown[] = [opts.artistId];

  if (opts.year != null) { conditions.push("s.year = ?"); params.push(opts.year); }
  if (opts.month != null) { conditions.push("s.month = ?"); params.push(opts.month); }
  if (opts.hasRecordings) { conditions.push("s.recording_count > 0"); }
  if (opts.future != null) { conditions.push("s.is_future = ?"); params.push(opts.future ? 1 : 0); }

  const sort = opts.sort ?? "date_desc";
  const orderBy = sort === "date_asc" ? "s.date ASC, s.show_sequence ASC"
    : sort === "rating_desc" ? "s.avg_rating DESC NULLS LAST, s.date DESC"
    : "s.date DESC, s.show_sequence ASC";

  if (opts.cursor) {
    // Cursor is base64-encoded "date|id"
    const decoded = Buffer.from(opts.cursor, "base64url").toString();
    const [cursorDate, cursorId] = decoded.split("|");
    if (sort === "date_asc") {
      conditions.push("(s.date > ? OR (s.date = ? AND s.id > ?))");
      params.push(cursorDate, cursorDate, cursorId);
    } else {
      conditions.push("(s.date < ? OR (s.date = ? AND s.id > ?))");
      params.push(cursorDate, cursorDate, cursorId);
    }
  }

  const limit = Math.min(opts.limit ?? 50, 200);
  const where = conditions.join(" AND ");

  const rows = db.prepare(`
    SELECT s.* FROM shows s WHERE ${where} ORDER BY ${orderBy} LIMIT ?
  `).all(...params, limit + 1) as ShowRow[];

  let nextCursor: string | null = null;
  if (rows.length > limit) {
    rows.pop();
    const last = rows[rows.length - 1];
    nextCursor = Buffer.from(`${last.date}|${last.id}`).toString("base64url");
  }

  return { shows: rows, nextCursor };
}

export function getShow(id: string): ShowRow | undefined {
  const db = getCatalogDb();
  return db.prepare("SELECT * FROM shows WHERE id = ?").get(id) as ShowRow | undefined;
}

export function getShowsByDayOfYear(dayOfYear: number): (ShowRow & { artist_name: string })[] {
  const db = getCatalogDb();
  return db.prepare(`
    SELECT s.*, a.name AS artist_name
    FROM shows s JOIN artists a ON s.artist_id = a.id
    WHERE s.day_of_year = ? AND s.recording_count > 0
    ORDER BY s.date DESC
  `).all(dayOfYear) as (ShowRow & { artist_name: string })[];
}

export function getAdjacentShows(showId: string): { prev: { id: string; date: string; venue_name: string | null; artist_id: string } | null; next: { id: string; date: string; venue_name: string | null; artist_id: string } | null } {
  const db = getCatalogDb();
  const show = db.prepare("SELECT artist_id, date, show_sequence FROM shows WHERE id = ?").get(showId) as
    { artist_id: string; date: string; show_sequence: number } | undefined;
  if (!show) return { prev: null, next: null };

  const prev = db.prepare(`
    SELECT id, date, venue_name, artist_id FROM shows
    WHERE artist_id = ? AND (date < ? OR (date = ? AND show_sequence < ?))
    ORDER BY date DESC, show_sequence DESC LIMIT 1
  `).get(show.artist_id, show.date, show.date, show.show_sequence) as
    { id: string; date: string; venue_name: string | null; artist_id: string } | undefined;

  const next = db.prepare(`
    SELECT id, date, venue_name, artist_id FROM shows
    WHERE artist_id = ? AND (date > ? OR (date = ? AND show_sequence > ?))
    ORDER BY date ASC, show_sequence ASC LIMIT 1
  `).get(show.artist_id, show.date, show.date, show.show_sequence) as
    { id: string; date: string; venue_name: string | null; artist_id: string } | undefined;

  return { prev: prev ?? null, next: next ?? null };
}

// ── Show Reviews ────────────────────────────────────────────────

export function getReviewsForShow(showId: string): ShowReviewRow[] {
  const db = getCatalogDb();
  return db.prepare(
    "SELECT * FROM show_reviews WHERE show_id = ? ORDER BY created_at DESC"
  ).all(showId) as ShowReviewRow[];
}

// ── Recordings ──────────────────────────────────────────────────

export function getRecordingsForShow(showId: string): RecordingRow[] {
  const db = getCatalogDb();
  return db.prepare(
    "SELECT * FROM recordings WHERE show_id = ? ORDER BY rating DESC"
  ).all(showId) as RecordingRow[];
}

export function getRecording(id: string): RecordingRow | undefined {
  const db = getCatalogDb();
  return db.prepare("SELECT * FROM recordings WHERE id = ?").get(id) as RecordingRow | undefined;
}

// ── Collections ─────────────────────────────────────────────────

export function getCollectionsForArtist(artistId: string): CollectionRow[] {
  const db = getCatalogDb();
  return db.prepare(
    "SELECT * FROM collections WHERE artist_id = ? ORDER BY name"
  ).all(artistId) as CollectionRow[];
}

export function getShowIdsForCollection(collectionId: string): string[] {
  const db = getCatalogDb();
  const rows = db.prepare(
    "SELECT show_id FROM collection_shows WHERE collection_id = ?"
  ).all(collectionId) as { show_id: string }[];
  return rows.map((r) => r.show_id);
}

// ── Search ──────────────────────────────────────────────────────

export function searchShows(query: string, artistId?: string, limit = 50): (ShowRow & { artist_name: string })[] {
  const db = getCatalogDb();
  // FTS5 match syntax: append * for prefix matching
  const ftsQuery = query.trim().split(/\s+/).map((t) => `"${t}"*`).join(" ");

  if (artistId) {
    return db.prepare(`
      SELECT s.*, a.name AS artist_name
      FROM shows_fts f
      JOIN shows s ON f.show_id = s.id
      JOIN artists a ON s.artist_id = a.id
      WHERE shows_fts MATCH ? AND s.artist_id = ?
      ORDER BY rank
      LIMIT ?
    `).all(ftsQuery, artistId, limit) as (ShowRow & { artist_name: string })[];
  }

  return db.prepare(`
    SELECT s.*, a.name AS artist_name
    FROM shows_fts f
    JOIN shows s ON f.show_id = s.id
    JOIN artists a ON s.artist_id = a.id
    WHERE shows_fts MATCH ?
    ORDER BY rank
    LIMIT ?
  `).all(ftsQuery, limit) as (ShowRow & { artist_name: string })[];
}

// ── Pipeline Runs ───────────────────────────────────────────────

export function createPipelineRun(artistId: string, collectorType: string): number {
  const db = getCatalogDb();
  const result = db.prepare(`
    INSERT INTO pipeline_runs (artist_id, collector_type, started_at, status)
    VALUES (?, ?, unixepoch(), 'running')
  `).run(artistId, collectorType);
  return Number(result.lastInsertRowid);
}

export function completePipelineRun(
  runId: number,
  status: "completed" | "failed",
  processed: number,
  created: number,
  errorMessage?: string
): void {
  const db = getCatalogDb();
  db.prepare(`
    UPDATE pipeline_runs
    SET completed_at = unixepoch(), status = ?, records_processed = ?, records_created = ?, error_message = ?
    WHERE id = ?
  `).run(status, processed, created, errorMessage ?? null, runId);
}

export function listPipelineRuns(artistId?: string, limit = 50): PipelineRunRow[] {
  const db = getCatalogDb();
  if (artistId) {
    return db.prepare(
      "SELECT * FROM pipeline_runs WHERE artist_id = ? ORDER BY started_at DESC LIMIT ?"
    ).all(artistId, limit) as PipelineRunRow[];
  }
  return db.prepare(
    "SELECT * FROM pipeline_runs ORDER BY started_at DESC LIMIT ?"
  ).all(limit) as PipelineRunRow[];
}

export function getPipelineRun(id: number): PipelineRunRow | undefined {
  const db = getCatalogDb();
  return db.prepare("SELECT * FROM pipeline_runs WHERE id = ?").get(id) as PipelineRunRow | undefined;
}


// ── Stats ───────────────────────────────────────────────────────

export interface CatalogStats {
  total_artists: number;
  total_shows: number;
  total_recordings: number;
  total_collections: number;
  db_size_bytes: number;
  per_artist: {
    artist_id: string;
    artist_name: string;
    show_count: number;
    recording_count: number;
    last_updated: number | null;
  }[];
}

export function getCatalogStats(): CatalogStats {
  const db = getCatalogDb();

  const counts = db.prepare(`
    SELECT
      (SELECT COUNT(*) FROM artists) AS total_artists,
      (SELECT COUNT(*) FROM shows) AS total_shows,
      (SELECT COUNT(*) FROM recordings) AS total_recordings,
      (SELECT COUNT(*) FROM collections) AS total_collections
  `).get() as { total_artists: number; total_shows: number; total_recordings: number; total_collections: number };

  const perArtist = db.prepare(`
    SELECT
      a.id AS artist_id,
      a.name AS artist_name,
      (SELECT COUNT(*) FROM shows WHERE artist_id = a.id) AS show_count,
      (SELECT COUNT(*) FROM recordings r JOIN shows s ON r.show_id = s.id WHERE s.artist_id = a.id) AS recording_count,
      (SELECT MAX(completed_at) FROM pipeline_runs WHERE artist_id = a.id AND status = 'completed') AS last_updated
    FROM artists a ORDER BY a.sort_name
  `).all() as CatalogStats["per_artist"];

  // Get DB file size
  const pageCount = (db.prepare("PRAGMA page_count").get() as { page_count: number }).page_count;
  const pageSize = (db.prepare("PRAGMA page_size").get() as { page_size: number }).page_size;

  return {
    ...counts,
    db_size_bytes: pageCount * pageSize,
    per_artist: perArtist,
  };
}
