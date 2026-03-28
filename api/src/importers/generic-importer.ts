/**
 * Generic importer — composes a ShowSource + RecordingSource, merges
 * shows and recordings by date, and writes everything to catalog.db.
 *
 * Used for any artist with IA recordings and an external show source
 * (setlist.fm, spaffnerds, etc). The pairing is hardwired in the
 * importer registry — no runtime configuration needed.
 */
import { getCatalogDb, generateShowId } from "../db/catalog.js";
import type { ImportResult, ImportProgress, ArtistImporter } from "./types.js";
import type { ShowSource, RecordingSource, ShowData, RecordingData } from "./sources/types.js";
import { SOURCE_PRIORITY } from "./sources/ia-recordings.js";

// ── Helpers (shared with GD importer) ───────────────────────────

const MONTH_NAMES = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

function slugify(text: string, maxLen = 80): string {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, maxLen);
}

function dayOfYear(dateStr: string): number {
  const d = new Date(dateStr + "T00:00:00Z");
  const start = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.floor((d.getTime() - start.getTime()) / 86_400_000) + 1;
}

function formatDateVariants(dateStr: string): string {
  const [y, m, d] = dateStr.split("-");
  if (!y || !m || !d) return dateStr;
  const mi = parseInt(m, 10) - 1;
  const shortYear = y.slice(2);
  return [
    dateStr,
    `${MONTH_NAMES[mi]} ${parseInt(d, 10)} ${y}`,
    `${parseInt(m, 10)}/${parseInt(d, 10)}/${shortYear}`,
    `${parseInt(m, 10)}-${parseInt(d, 10)}-${shortYear}`,
  ].join(" ");
}

// ── Implementation ──────────────────────────────────────────────

export class GenericImporter implements ArtistImporter {
  readonly collectorType: string;

  constructor(
    private readonly showSource: ShowSource,
    private readonly recordingSource: RecordingSource,
    collectorType: string,
  ) {
    this.collectorType = collectorType;
  }

  async run(artistId: string, onProgress?: ImportProgress): Promise<ImportResult> {
    const result: ImportResult = {
      showsProcessed: 0, showsCreated: 0,
      recordingsProcessed: 0, recordingsCreated: 0,
      collectionsProcessed: 0,
    };

    // Fetch shows and recordings in parallel — they're independent
    onProgress?.("Fetching shows and recordings...");
    const [shows, recordings] = await Promise.all([
      this.showSource.fetchShows(onProgress),
      this.recordingSource.fetchRecordings(onProgress),
    ]);

    const db = getCatalogDb();

    // Look up artist name for slugs/FTS
    const artist = db.prepare("SELECT name FROM artists WHERE id = ?").get(artistId) as
      { name: string } | undefined;
    const artistName = artist?.name ?? artistId;

    // ── Phase 1: Import shows ─────────────────────────────────

    onProgress?.(`Importing ${shows.length} shows...`);

    // Build a map of existing shows to avoid duplicates on re-import
    const existingShows = new Map<string, string>();
    const existingRows = db.prepare(
      "SELECT id, date, venue_name FROM shows WHERE artist_id = ?",
    ).all(artistId) as { id: string; date: string; venue_name: string | null }[];
    for (const row of existingRows) {
      existingShows.set(`${row.date}|${row.venue_name ?? ""}`, row.id);
    }

    const dateVenueCounts = new Map<string, number>();
    const dateToShowIds = new Map<string, string[]>();

    const insertShow = db.prepare(`
      INSERT INTO shows (id, slug, artist_id, date, year, month, day_of_year, show_sequence,
        venue_name, city, state, country, primary_source, is_future,
        setlist_status, setlist_raw, song_list, lineup_status, lineup_raw,
        recording_count, best_recording_id, best_source_type, avg_rating, total_reviews,
        cover_image_url, notes)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const insertFts = db.prepare(`
      INSERT INTO shows_fts (show_id, artist_name, date_text, venue_name, city, state, song_list, member_list)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const importShows = db.transaction(() => {
      for (const show of shows) {
        result.showsProcessed++;
        const dvKey = `${show.date}|${show.venue}`;

        // Skip duplicate date+venue
        if (existingShows.has(dvKey)) {
          const existingId = existingShows.get(dvKey)!;
          const ids = dateToShowIds.get(show.date) ?? [];
          ids.push(existingId);
          dateToShowIds.set(show.date, ids);
          continue;
        }

        const seq = (dateVenueCounts.get(dvKey) ?? 0) + 1;
        dateVenueCounts.set(dvKey, seq);

        const shortId = generateShowId();
        const slug = slugify(`${artistName}-${show.date}-${show.venue}`);
        const parts = show.date.split("-");
        const year = parseInt(parts[0] ?? "0", 10);
        const month = parseInt(parts[1] ?? "0", 10);

        const setlistRaw = show.setlist_raw.length > 0 ? JSON.stringify(show.setlist_raw) : null;

        insertShow.run(
          shortId, slug, artistId, show.date, year, month, dayOfYear(show.date), seq,
          show.venue || null, show.city, show.state, show.country,
          show.primary_source, 0,
          show.setlist_status, setlistRaw, show.song_list || null,
          null, null, // lineup_status, lineup_raw
          0, null, null, null, 0, // recording aggregates (updated later)
          null, // cover_image_url (set in phase 3)
          null, // notes
        );

        insertFts.run(
          shortId, artistName, formatDateVariants(show.date),
          show.venue, show.city ?? "", show.state ?? "", show.song_list, "",
        );

        existingShows.set(dvKey, shortId);
        const ids = dateToShowIds.get(show.date) ?? [];
        ids.push(shortId);
        dateToShowIds.set(show.date, ids);
        result.showsCreated++;
      }
    });

    importShows();
    onProgress?.(`Imported ${result.showsCreated} shows (${result.showsProcessed} processed).`);

    // ── Phase 2: Import recordings ────────────────────────────

    onProgress?.(`Importing ${recordings.length} recordings...`);

    const insertRec = db.prepare(`
      INSERT OR IGNORE INTO recordings (id, show_id, source_type, rating, raw_rating,
        review_count, confidence, high_ratings, low_ratings, taper, source, lineage)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    // Create stub shows for recordings with no matching setlist show
    const stubShowInsert = db.prepare(`
      INSERT INTO shows (id, slug, artist_id, date, year, month, day_of_year, show_sequence,
        venue_name, city, state, country, primary_source, is_future,
        setlist_status, setlist_raw, song_list, lineup_status, lineup_raw,
        recording_count, best_recording_id, best_source_type, avg_rating, total_reviews,
        cover_image_url, notes)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const stubFtsInsert = db.prepare(`
      INSERT INTO shows_fts (show_id, artist_name, date_text, venue_name, city, state, song_list, member_list)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const importRecordings = db.transaction(() => {
      for (const rec of recordings) {
        result.recordingsProcessed++;

        let showIds = dateToShowIds.get(rec.date);

        // No show for this date — create a stub
        if (!showIds?.length) {
          const stubId = generateShowId();
          const stubSlug = slugify(`${artistName}-${rec.date}-unknown-venue`);
          const parts = rec.date.split("-");
          const year = parseInt(parts[0] ?? "0", 10);
          const month = parseInt(parts[1] ?? "0", 10);

          stubShowInsert.run(
            stubId, stubSlug, artistId, rec.date, year, month, dayOfYear(rec.date), 1,
            null, null, null, "US",
            "archive.org", 0,
            "missing", null, null,
            null, null,
            0, null, null, null, 0,
            null, // cover_image_url
            null, // notes
          );

          stubFtsInsert.run(
            stubId, artistName, formatDateVariants(rec.date),
            "", "", "", "", "",
          );

          showIds = [stubId];
          dateToShowIds.set(rec.date, showIds);
          result.showsCreated++;
        }

        insertRec.run(
          rec.identifier, showIds[0],
          rec.source_type,
          rec.rating, rec.raw_rating,
          rec.review_count, rec.confidence,
          rec.high_ratings, rec.low_ratings,
          rec.taper, rec.source, rec.lineage,
        );
        result.recordingsCreated++;
      }
    });

    importRecordings();
    onProgress?.(`Imported ${result.recordingsCreated} recordings (${result.recordingsProcessed} processed).`);

    // ── Phase 3: Update show aggregates ───────────────────────

    onProgress?.("Computing show aggregates (recording counts, best recordings)...");

    const updateAggregates = db.prepare(`
      UPDATE shows SET
        recording_count = (SELECT COUNT(*) FROM recordings WHERE show_id = shows.id),
        avg_rating = (SELECT AVG(rating) FROM recordings WHERE show_id = shows.id AND rating > 0),
        total_reviews = (SELECT COALESCE(SUM(review_count), 0) FROM recordings WHERE show_id = shows.id),
        best_recording_id = (
          SELECT id FROM recordings WHERE show_id = shows.id
          ORDER BY
            CASE source_type
              WHEN 'SBD' THEN 0
              WHEN 'MATRIX' THEN 1
              WHEN 'FM' THEN 2
              WHEN 'AUD' THEN 3
              WHEN 'REMASTER' THEN 4
              ELSE 5
            END,
            rating DESC
          LIMIT 1
        ),
        best_source_type = (
          SELECT source_type FROM recordings WHERE show_id = shows.id
          ORDER BY
            CASE source_type
              WHEN 'SBD' THEN 0
              WHEN 'MATRIX' THEN 1
              WHEN 'FM' THEN 2
              WHEN 'AUD' THEN 3
              WHEN 'REMASTER' THEN 4
              ELSE 5
            END,
            rating DESC
          LIMIT 1
        ),
        updated_at = unixepoch()
      WHERE artist_id = ?
    `);

    updateAggregates.run(artistId);

    // Set cover_image_url from best recording's IA thumbnail
    db.prepare(`
      UPDATE shows SET cover_image_url = 'https://archive.org/services/img/' || best_recording_id
      WHERE artist_id = ? AND best_recording_id IS NOT NULL AND cover_image_url IS NULL
    `).run(artistId);

    onProgress?.("Done.");

    return result;
  }
}
