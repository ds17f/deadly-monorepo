/** Slim show entry for the homepage index (~150 bytes per show). */
export interface ShowIndexEntry {
  /** show_id */
  id: string;
  /** date (YYYY-MM-DD) */
  d: string;
  /** venue */
  v: string;
  /** city */
  c: string;
  /** state */
  s: string;
  /** ai_rating (0 if none) */
  r: number;
  /** recording_count */
  rc: number;
  /** AI summary snippet */
  sum: string;
  /** avg_rating (community) */
  ar: number;
}

export interface CollectionSummary {
  id: string;
  name: string;
  description: string;
  tags: string[];
  total_shows: number;
}

export interface YearBucket {
  year: number;
  count: number;
}
