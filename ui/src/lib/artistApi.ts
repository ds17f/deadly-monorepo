import type { Artist } from "@/types/artist";
import type { ShowIndexEntry, CollectionSummary, YearBucket } from "@/types/homepage";
import type { Recording } from "@/types/recording";
import type { Show } from "@/types/show";

interface ShowRow {
  id: string;
  slug: string;
  artist_id: string;
  date: string;
  year: number;
  venue_name: string | null;
  city: string | null;
  state: string | null;
  country: string;
  recording_count: number;
  best_recording_id: string | null;
  best_source_type: string | null;
  avg_rating: number | null;
  total_reviews: number;
  cover_image_url: string | null;
  setlist_raw: string | null;
  song_list: string | null;
  lineup_raw: string | null;
  notes: string | null;
}

interface RecordingRow {
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
}

interface CollectionRow {
  id: string;
  artist_id: string;
  name: string;
  description: string;
}

export async function fetchArtists(): Promise<Artist[]> {
  const res = await fetch("/api/artists");
  if (!res.ok) throw new Error("Failed to fetch artists");
  return res.json();
}

export async function fetchArtist(id: string): Promise<Artist> {
  const res = await fetch(`/api/artists/${encodeURIComponent(id)}`);
  if (!res.ok) throw new Error("Artist not found");
  return res.json();
}

export async function fetchArtistShows(
  artistId: string,
  opts?: { year?: number; hasRecordings?: boolean; sort?: string; cursor?: string; limit?: number }
): Promise<{ shows: ShowRow[]; nextCursor: string | null }> {
  const params = new URLSearchParams();
  if (opts?.year != null) params.set("year", String(opts.year));
  if (opts?.hasRecordings) params.set("has_recordings", "true");
  if (opts?.sort) params.set("sort", opts.sort);
  if (opts?.cursor) params.set("cursor", opts.cursor);
  if (opts?.limit != null) params.set("limit", String(opts.limit));
  const qs = params.toString();
  const res = await fetch(`/api/artists/${encodeURIComponent(artistId)}/shows${qs ? `?${qs}` : ""}`);
  if (!res.ok) throw new Error("Failed to fetch shows");
  return res.json();
}

export async function fetchArtistCollections(artistId: string): Promise<CollectionRow[]> {
  const res = await fetch(`/api/artists/${encodeURIComponent(artistId)}/collections`);
  if (!res.ok) return [];
  return res.json();
}

export async function fetchShow(id: string): Promise<ShowRow | null> {
  const res = await fetch(`/api/shows/${encodeURIComponent(id)}`);
  if (!res.ok) return null;
  return res.json();
}

export async function fetchShowRecordings(showId: string): Promise<RecordingRow[]> {
  const res = await fetch(`/api/shows/${encodeURIComponent(showId)}/recordings`);
  if (!res.ok) return [];
  return res.json();
}

export interface AdjacentShows {
  prev: { id: string; date: string; venue_name: string | null } | null;
  next: { id: string; date: string; venue_name: string | null } | null;
}

export interface ShowReviewRow {
  id: number;
  show_id: string;
  type: string;
  author: string | null;
  summary: string | null;
  content: Record<string, unknown> | null;
  created_at: number;
}

export async function fetchShowAdjacent(showId: string): Promise<AdjacentShows> {
  const res = await fetch(`/api/shows/${encodeURIComponent(showId)}/adjacent`);
  if (!res.ok) return { prev: null, next: null };
  return res.json();
}

export async function fetchShowReviews(showId: string): Promise<ShowReviewRow[]> {
  const res = await fetch(`/api/shows/${encodeURIComponent(showId)}/reviews`);
  if (!res.ok) return [];
  return res.json();
}

export async function fetchArtistSearch(
  query: string,
  artistId?: string
): Promise<ShowRow[]> {
  const params = new URLSearchParams({ q: query, limit: "50" });
  if (artistId) params.set("artist_id", artistId);
  const res = await fetch(`/api/search?${params}`);
  if (!res.ok) return [];
  return res.json();
}

/** Map an API ShowRow to a ShowIndexEntry for reuse with existing components. */
export function showRowToIndexEntry(row: ShowRow): ShowIndexEntry {
  const sourceTypes: string[] = [];
  if (row.best_source_type) sourceTypes.push(row.best_source_type);
  return {
    id: row.id,
    d: row.date,
    v: row.venue_name ?? "Unknown Venue",
    c: row.city ?? "",
    s: row.state ?? "",
    r: row.avg_rating ?? 0,
    rc: row.recording_count,
    sum: "",
    ar: row.avg_rating ?? 0,
    st: sourceTypes,
  };
}

/** Map API ShowRow + RecordingRows to a Show type for show detail components. */
export function showRowToShow(
  row: ShowRow,
  recordings: RecordingRow[],
  artistName: string
): Show {
  let setlist = null;
  if (row.setlist_raw) {
    try {
      setlist = JSON.parse(row.setlist_raw);
    } catch { /* ignore */ }
  }

  let lineup = null;
  if (row.lineup_raw) {
    try {
      lineup = JSON.parse(row.lineup_raw);
    } catch { /* ignore */ }
  }

  return {
    show_id: row.id,
    band: artistName,
    date: row.date,
    venue: row.venue_name ?? "Unknown Venue",
    location_raw: [row.city, row.state, row.country].filter(Boolean).join(", "),
    city: row.city ?? "",
    state: row.state ?? "",
    country: row.country,
    setlist,
    lineup,
    recordings: recordings.map((r) => r.id),
    best_recording: row.best_recording_id,
    recording_count: row.recording_count,
    avg_rating: row.avg_rating ?? 0,
    raw_rating: 0,
    source_types: recordings.reduce((acc, r) => {
      if (r.source_type) acc[r.source_type] = (acc[r.source_type] ?? 0) + 1;
      return acc;
    }, {} as Record<string, number>),
    ai_show_review: null,
    cover_image_url: row.cover_image_url ?? null,
    ticket_images: [],
    photos: [],
  };
}

/** Map API RecordingRow to the frontend Recording type. */
export function recordingRowToRecording(row: RecordingRow): Recording {
  return {
    identifier: row.id,
    title: "",
    date: "",
    venue: "",
    location: "",
    source_type: row.source_type ?? "",
    lineage: row.lineage ?? "",
    taper: row.taper ?? "",
    source: row.source ?? "",
    runtime: "",
    rating: row.rating,
    raw_rating: row.raw_rating,
    review_count: row.review_count,
    confidence: row.confidence,
    high_ratings: row.high_ratings,
    low_ratings: row.low_ratings,
  };
}

/** Build year histogram from show rows. */
export function buildYearHistogramFromShows(shows: ShowRow[]): YearBucket[] {
  const counts = new Map<number, number>();
  for (const s of shows) {
    counts.set(s.year, (counts.get(s.year) ?? 0) + 1);
  }
  return Array.from(counts.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([year, count]) => ({ year, count }));
}

/** Map API CollectionRow to CollectionSummary for component reuse. */
export function collectionRowToSummary(row: CollectionRow): CollectionSummary {
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    tags: [],
    total_shows: 0,
  };
}

/** Compute decade chips from artist active years. */
export function computeDecades(
  activeFrom: number | null,
  activeTo: number | null
): { label: string; from: number; to: number }[] {
  const startYear = activeFrom ?? 1960;
  const endYear = activeTo ?? new Date().getFullYear();
  const startDecade = Math.floor(startYear / 10) * 10;
  const endDecade = Math.floor(endYear / 10) * 10;

  const decades: { label: string; from: number; to: number }[] = [];
  for (let d = startDecade; d <= endDecade; d += 10) {
    decades.push({
      label: `${String(d).slice(2)}s`,
      from: Math.max(d, startYear),
      to: Math.min(d + 9, endYear),
    });
  }
  return decades;
}
