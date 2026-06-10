import fs from "node:fs";
import path from "node:path";

// In-memory show-metadata catalog. The API has no show data of its own —
// this is loaded once at boot from the compact index built by
// scripts/build-show-index.mjs (make api-show-index) and used to enrich
// user-data responses (recents, favorites) with display fields.
// See PLANS/web-profile.md (show-metadata source).

export interface ShowMeta {
  date: string | null;
  venue: string | null;
  city: string | null;
  state: string | null;
  country: string | null;
  rating: number;
  recordingCount: number;
  // Card "ticket" image (ticket stub / photo), null when none.
  image: string | null;
  // Archive.org recording id, for the thumbnail fallback when image is null.
  bestRecordingId: string | null;
}

let catalog = new Map<string, ShowMeta>();
let sortedIds: string[] | null = null;
let loaded = false;

function defaultIndexPath(): string {
  if (process.env.SHOW_INDEX_PATH) return process.env.SHOW_INDEX_PATH;
  // Sits alongside the SQLite DBs in the mounted data dir.
  const dbPath = process.env.USERS_DB_PATH ?? "/app/data/users.db";
  return path.join(path.dirname(dbPath), "show-index.json");
}

/**
 * Load the show index into memory. Returns the number of shows loaded.
 * A missing/unreadable index is non-fatal — user-data endpoints still work,
 * just without enrichment.
 */
export function loadShowCatalog(filePath: string = defaultIndexPath()): number {
  try {
    const raw = fs.readFileSync(filePath, "utf-8");
    const obj = JSON.parse(raw) as Record<string, ShowMeta>;
    catalog = new Map(Object.entries(obj));
  } catch {
    catalog = new Map();
  }
  sortedIds = null;
  loaded = true;
  return catalog.size;
}

export function getShowMeta(showId: string): ShowMeta | null {
  if (!loaded) loadShowCatalog();
  return catalog.get(showId) ?? null;
}

// Show ids sort chronologically (the "YYYY-MM-DD-…" prefix), matching the
// catalog's own ordering. Cached lazily; invalidated on (re)load.
function getSortedIds(): string[] {
  if (!loaded) loadShowCatalog();
  if (!sortedIds) sortedIds = Array.from(catalog.keys()).sort();
  return sortedIds;
}

/**
 * The next show chronologically after [afterShowId], or null if it's the last
 * (or unknown). Backs web auto-advance (ADR-0010): the browser has no catalog,
 * so it asks the API — which already holds it — for the next show to play.
 */
export function getNextShow(afterShowId: string): { showId: string; meta: ShowMeta } | null {
  const ids = getSortedIds();
  const idx = ids.indexOf(afterShowId);
  if (idx < 0 || idx >= ids.length - 1) return null;
  const showId = ids[idx + 1];
  const meta = catalog.get(showId);
  return meta ? { showId, meta } : null;
}
