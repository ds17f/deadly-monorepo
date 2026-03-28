/**
 * Interfaces for composable data sources used by the generic importer.
 * A ShowSource provides show/setlist data, a RecordingSource provides
 * recording metadata. The GenericImporter merges them by date.
 */
import type { ImportProgress } from "../types.js";

/** Normalized show data from any show source. */
export interface ShowData {
  date: string;             // YYYY-MM-DD
  venue: string;
  city: string | null;
  state: string | null;
  country: string;
  setlist_status: "found" | "missing";
  setlist_raw: object[];    // [{set_name, songs: [{name, segue_into_next?}]}]
  song_list: string;        // space-separated song names for FTS
  primary_source: string;   // "setlist.fm", "spaffnerds.com", etc.
}

/** Normalized recording data from any recording source. */
export interface RecordingData {
  identifier: string;
  date: string;             // YYYY-MM-DD
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

/** Fetches show/setlist data for an artist. */
export interface ShowSource {
  readonly name: string;
  fetchShows(onProgress?: ImportProgress): Promise<ShowData[]>;
}

/** Fetches recording metadata for an artist. */
export interface RecordingSource {
  readonly name: string;
  fetchRecordings(onProgress?: ImportProgress): Promise<RecordingData[]>;
}
