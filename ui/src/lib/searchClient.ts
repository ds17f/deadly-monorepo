// Client-side catalog search: lazy-load the prebuilt index once, cache it in
// IndexedDB keyed by data version, and query in-browser with MiniSearch.
//
// Matches the mobile FTS feel — song, member, venue, location, and date search,
// AND-combined and prefix-matched — with zero server cost (one static file,
// downloaded once per data release).

import MiniSearch, { type SearchResult } from "minisearch";

const VERSION = process.env.NEXT_PUBLIC_DATA_VERSION ?? "dev";
// Bump when the indexed field schema changes so stale IndexedDB caches (which
// serialize the field config) are invalidated even on the same data version.
const SCHEMA = "2";
const CACHE_KEY = `${VERSION}::s${SCHEMA}`;

// The compact, dictionary-encoded artifact emitted by scripts/build-search-index.mjs.
interface RawDoc {
  i: string; // show_id
  d: string; // date YYYY-MM-DD
  v: string; // venue
  c: string; // city
  s: string; // state
  g: number[]; // setlist song ids
  m: number[]; // lineup member ids
  t: string[]; // source-type tags (SBD/AUD/…)
}
interface RawIndex {
  version: string;
  songs: string[];
  members: string[];
  docs: RawDoc[];
}

// What MiniSearch indexes/stores per show. Stored fields let results render
// from any route (the home showIndex isn't loaded elsewhere).
interface SearchDoc {
  id: string;
  date: string;
  year: string;
  venue: string;
  city: string;
  state: string;
  songs: string;
  members: string;
  source: string;
  dates: string; // free-text date variants (5/8/77, may 8, etc.)
}

export interface ShowSearchHit {
  showId: string;
  date: string;
  venue: string;
  city: string;
  state: string;
  /** Which field drove the match, for a small hint in the UI. */
  matchType: "song" | "member" | "venue" | "location" | "date" | "general";
}

const SOURCE_SYNONYMS: Record<string, string> = {
  SBD: "soundboard sbd",
  AUD: "audience aud",
  FM: "fm broadcast",
  MATRIX: "matrix",
};

const FIELDS = ["date", "year", "venue", "city", "state", "songs", "members", "source", "dates"];

const MONTH_NAMES = [
  "", "january", "february", "march", "april", "may", "june",
  "july", "august", "september", "october", "november", "december",
];

// The stored ISO date ("1977-05-08") only tokenizes to 1977/05/08, so leading-
// zero-less and 2-digit-year forms ("5/8/77", "may 8") never match. Emit those
// variants as loose tokens; MiniSearch tokenizes on the separators, so order/
// delimiter don't matter (AND across the numbers + year still narrows).
function dateVariants(iso: string): string {
  const [y, m, d] = iso.split("-");
  if (!y || !m || !d) return "";
  const mNum = String(parseInt(m, 10)); // "05" -> "5"
  const dNum = String(parseInt(d, 10)); // "08" -> "8"
  const y2 = y.slice(2); // "1977" -> "77"
  const monthName = MONTH_NAMES[parseInt(m, 10)] ?? "";
  return `${mNum} ${dNum} ${y2} ${monthName}`;
}
const MINISEARCH_OPTIONS = {
  idField: "id",
  fields: FIELDS,
  storeFields: ["date", "venue", "city", "state"],
  searchOptions: {
    prefix: true,
    // Fuzzy helps misspelled names/venues, but on a numeric token (year or
    // date part) edit-distance 1 turns "1982" into 1980/1983/1992/1882 — which
    // silently widened "Brent 1982" to ~all Brent-era years. Match digits exactly.
    fuzzy: (term: string) => (/^\d+$/.test(term) ? false : 0.2),
    combineWith: "AND" as const,
    boost: { venue: 2, songs: 2, members: 2, year: 1.5 },
  },
};

function toSearchDocs(raw: RawIndex): SearchDoc[] {
  return raw.docs.map((d) => ({
    id: d.i,
    date: d.d,
    year: d.d.slice(0, 4),
    venue: d.v,
    city: d.c,
    state: d.s,
    songs: d.g.map((id) => raw.songs[id]).join(" "),
    members: d.m.map((id) => raw.members[id]).join(" "),
    source: d.t.map((tag) => SOURCE_SYNONYMS[tag] ?? tag).join(" "),
    dates: dateVariants(d.d),
  }));
}

// ---- IndexedDB cache (best-effort; falls back to fetch+build if unavailable) ----

const DB_NAME = "deadly-search";
const STORE = "index";

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function cacheGet(version: string): Promise<string | null> {
  try {
    const db = await openDb();
    return await new Promise((resolve) => {
      const req = db.transaction(STORE, "readonly").objectStore(STORE).get(version);
      req.onsuccess = () => resolve((req.result as string) ?? null);
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

async function cachePut(version: string, json: string): Promise<void> {
  try {
    const db = await openDb();
    await new Promise<void>((resolve) => {
      const store = db.transaction(STORE, "readwrite").objectStore(STORE);
      store.clear(); // drop any prior data-version index
      store.put(json, version);
      store.transaction.oncomplete = () => resolve();
      store.transaction.onerror = () => resolve();
    });
  } catch {
    /* caching is best-effort */
  }
}

// ---- Load (memoized) ----

let loadPromise: Promise<MiniSearch<SearchDoc>> | null = null;

async function build(): Promise<MiniSearch<SearchDoc>> {
  // 1. Serialized index already in IndexedDB for this version? Load instantly.
  const cached = await cacheGet(CACHE_KEY);
  if (cached) {
    try {
      return MiniSearch.loadJSON<SearchDoc>(cached, MINISEARCH_OPTIONS);
    } catch {
      /* corrupt cache — fall through to refetch */
    }
  }

  // 2. Fetch the prebuilt artifact (once), build, then cache the serialized form.
  const res = await fetch(`/search-index.${VERSION}.json`);
  if (!res.ok) throw new Error(`search index ${res.status}`);
  const raw = (await res.json()) as RawIndex;
  const ms = new MiniSearch<SearchDoc>(MINISEARCH_OPTIONS);
  ms.addAll(toSearchDocs(raw));
  cachePut(CACHE_KEY, JSON.stringify(ms)); // fire-and-forget
  return ms;
}

/** Lazily load (and memoize) the search index. Call on first search intent. */
export function loadSearchIndex(): Promise<MiniSearch<SearchDoc>> {
  if (!loadPromise) loadPromise = build().catch((e) => {
    loadPromise = null; // allow retry on failure
    throw e;
  });
  return loadPromise;
}

function matchType(result: SearchResult): ShowSearchHit["matchType"] {
  // result.match maps term -> [fields it matched in]. Pick the most specific.
  const fields = new Set<string>();
  for (const f of Object.values(result.match)) for (const name of f) fields.add(name);
  if (fields.has("songs")) return "song";
  if (fields.has("members")) return "member";
  if (fields.has("venue")) return "venue";
  if (fields.has("city") || fields.has("state")) return "location";
  if (fields.has("date") || fields.has("year") || fields.has("dates")) return "date";
  return "general";
}

export interface SearchResults {
  hits: ShowSearchHit[];
  total: number; // total matches before the display limit
}

export async function searchShows(query: string, limit = 20): Promise<SearchResults> {
  const q = query.trim();
  if (q.length < 2) return { hits: [], total: 0 };
  const ms = await loadSearchIndex();
  const all = ms.search(q);
  const hits = all.slice(0, limit).map((r) => ({
    showId: r.id as string,
    date: r.date as string,
    venue: r.venue as string,
    city: r.city as string,
    state: r.state as string,
    matchType: matchType(r),
  }));
  return { hits, total: all.length };
}
