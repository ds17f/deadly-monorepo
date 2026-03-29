/**
 * GD importer — downloads data.zip from GitHub Releases, extracts it,
 * and upserts shows/recordings/collections into catalog.db.
 *
 * Data source: pre-processed JSON from the data pipeline
 *   shows/{show_id}.json      — show metadata, setlists, lineups
 *   recordings/{identifier}.json — recording metadata + ratings
 *   collections.json           — curated show groupings
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import { createWriteStream } from "node:fs";
import { inflateRawSync } from "node:zlib";
import { getCatalogDb } from "../db/catalog.js";
import type { ImportResult, ImportProgress, ArtistImporter } from "./types.js";

const GITHUB_REPO = "ds17f/deadly-monorepo";
const DATA_VERSION_FILE = path.join(process.cwd(), "..", "data", "version");
const RELEASE_URL = (version: string) =>
  `https://github.com/${GITHUB_REPO}/releases/download/data-v${version}/data.zip`;

const SOURCE_PRIORITY: Record<string, number> = {
  SBD: 0, MATRIX: 1, FM: 2, AUD: 3, REMASTER: 4,
};

const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function slugify(text: string, maxLen = 120): string {
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

function extractSongList(setlist: unknown[] | null): string {
  if (!Array.isArray(setlist)) return "";
  const songs: string[] = [];
  for (const set of setlist) {
    const s = set as { songs?: { name?: string }[] };
    for (const song of s.songs ?? []) {
      if (song.name) songs.push(song.name);
    }
  }
  return songs.join(" ");
}

function extractMemberList(lineup: unknown[] | null): string {
  if (!Array.isArray(lineup)) return "";
  return (lineup as { name?: string }[])
    .map((m) => m.name).filter(Boolean).join(" ");
}

/**
 * Pure-JS zip extraction using only Node built-ins.
 * Handles STORE (0) and DEFLATE (8) compression methods.
 */
function extractZip(zipPath: string, destDir: string): void {
  const buf = fs.readFileSync(zipPath);
  let offset = 0;

  while (offset < buf.length - 4) {
    const sig = buf.readUInt32LE(offset);
    if (sig !== 0x04034b50) break; // not a local file header

    const method = buf.readUInt16LE(offset + 8);
    const compSize = buf.readUInt32LE(offset + 18);
    const uncompSize = buf.readUInt32LE(offset + 22);
    const nameLen = buf.readUInt16LE(offset + 26);
    const extraLen = buf.readUInt16LE(offset + 28);
    const fileName = buf.toString("utf8", offset + 30, offset + 30 + nameLen);
    const dataStart = offset + 30 + nameLen + extraLen;

    const outPath = path.join(destDir, fileName);

    if (fileName.endsWith("/")) {
      // Directory entry
      fs.mkdirSync(outPath, { recursive: true });
    } else {
      // File entry
      fs.mkdirSync(path.dirname(outPath), { recursive: true });
      const compressed = buf.subarray(dataStart, dataStart + compSize);

      if (method === 0) {
        // STORE — no compression
        fs.writeFileSync(outPath, compressed);
      } else if (method === 8) {
        // DEFLATE
        const decompressed = inflateRawSync(compressed);
        if (decompressed.length !== uncompSize && uncompSize > 0) {
          throw new Error(`Size mismatch for ${fileName}: expected ${uncompSize}, got ${decompressed.length}`);
        }
        fs.writeFileSync(outPath, decompressed);
      } else {
        throw new Error(`Unsupported compression method ${method} for ${fileName}`);
      }
    }

    offset = dataStart + compSize;
  }
}

/** Read the pinned data version from data/version. */
function getDataVersion(): string {
  // Check env override first, then version file
  if (process.env.GD_DATA_VERSION) return process.env.GD_DATA_VERSION;
  try {
    return fs.readFileSync(DATA_VERSION_FILE, "utf-8").trim();
  } catch {
    return "2.3.0"; // fallback
  }
}

/**
 * Download data.zip from GitHub Releases and extract to a temp directory.
 * Returns the path to the extracted data directory.
 */
async function downloadAndExtractData(
  version: string,
  onProgress?: ImportProgress,
): Promise<string> {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "deadly-data-"));
  const zipPath = path.join(tmpDir, "data.zip");
  const extractDir = path.join(tmpDir, "data");
  fs.mkdirSync(extractDir, { recursive: true });

  const url = RELEASE_URL(version);
  onProgress?.(`Downloading data.zip v${version}...`);

  // Follow redirects (GitHub releases redirect to S3)
  const response = await fetch(url, { redirect: "follow" });
  if (!response.ok || !response.body) {
    throw new Error(`Failed to download data.zip: HTTP ${response.status} from ${url}`);
  }

  // Stream to disk
  const fileStream = createWriteStream(zipPath);
  await pipeline(response.body!, fileStream);

  const stats = fs.statSync(zipPath);
  onProgress?.(`Downloaded ${(stats.size / 1024 / 1024).toFixed(1)} MB. Extracting...`);

  extractZip(zipPath, extractDir);

  // Verify expected structure exists
  if (!fs.existsSync(path.join(extractDir, "shows"))) {
    throw new Error(`Extracted data.zip but shows/ directory not found in ${extractDir}`);
  }

  onProgress?.("Extraction complete.");
  return extractDir;
}

export class GratefulDeadImporter implements ArtistImporter {
  readonly collectorType = "stage02-json";

  private explicitDataDir?: string;

  constructor(dataDir?: string) {
    this.explicitDataDir = dataDir ?? process.env.GD_DATA_DIR ?? undefined;
  }

  async run(artistId: string, onProgress?: ImportProgress): Promise<ImportResult> {
    // Resolve data directory: explicit path, or download from GitHub
    let dataDir = this.explicitDataDir;
    let tempDir: string | null = null;

    if (dataDir && fs.existsSync(path.join(dataDir, "shows"))) {
      onProgress?.(`Using local data directory: ${dataDir}`);
    } else {
      // Download data.zip
      const version = getDataVersion();
      dataDir = await downloadAndExtractData(version, onProgress);
      tempDir = path.dirname(dataDir); // parent tmpDir for cleanup
    }

    try {
      return this.importFromDir(artistId, dataDir, onProgress);
    } finally {
      // Clean up temp directory if we downloaded
      if (tempDir) {
        onProgress?.("Cleaning up temp files...");
        fs.rmSync(tempDir, { recursive: true, force: true });
      }
    }
  }

  private importFromDir(
    artistId: string,
    dataDir: string,
    onProgress?: ImportProgress,
  ): ImportResult {
    const showsDir = path.join(dataDir, "shows");
    const recordingsDir = path.join(dataDir, "recordings");
    const collectionsFile = path.join(dataDir, "collections.json");

    if (!fs.existsSync(showsDir)) {
      throw new Error(`Shows directory not found: ${showsDir}`);
    }

    const db = getCatalogDb();

    // Look up artist name for slugs/FTS
    const artist = db.prepare("SELECT name FROM artists WHERE id = ?").get(artistId) as
      { name: string } | undefined;
    const artistName = artist?.name ?? artistId;

    const result: ImportResult = {
      showsProcessed: 0, showsCreated: 0,
      recordingsProcessed: 0, recordingsCreated: 0,
      collectionsProcessed: 0,
    };

    // ── Shows ──────────────────────────────────────────────────

    const showFiles = fs.readdirSync(showsDir)
      .filter((f) => f.endsWith(".json")).sort();

    onProgress?.(`Processing ${showFiles.length} shows...`);

    // Build a map of existing shows for this artist so we can skip duplicates
    const existingShows = new Map<string, string>();
    const existingRows = db.prepare(
      "SELECT id, date, venue_name FROM shows WHERE artist_id = ?"
    ).all(artistId) as { id: string; date: string; venue_name: string | null }[];
    for (const row of existingRows) {
      existingShows.set(`${row.date}|${row.venue_name ?? ""}`, row.id);
    }

    const dateVenueCounts = new Map<string, number>();
    const oldIdToNewId = new Map<string, string>();
    const dateToShowIds = new Map<string, string[]>();

    const insertShow = db.prepare(`
      INSERT INTO shows (id, artist_id, date, year, month, day_of_year, show_sequence,
        venue_name, city, state, country, primary_source, is_future,
        setlist_status, setlist_raw, song_list, lineup_status, lineup_raw,
        recording_count, best_recording_id, best_source_type, avg_rating, total_reviews,
        cover_image_url, notes)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const insertReview = db.prepare(`
      INSERT INTO show_reviews (show_id, type, author, summary, content)
      VALUES (?, ?, ?, ?, ?)
    `);

    const insertFts = db.prepare(`
      INSERT INTO shows_fts (show_id, artist_name, date_text, venue_name, city, state, song_list, member_list)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const insertMany = db.transaction(() => {
      for (const file of showFiles) {
        const show = JSON.parse(fs.readFileSync(path.join(showsDir, file), "utf-8"));

        const oldShowId: string = show.show_id ?? "";
        const date: string = show.date ?? "";
        const venue: string = show.venue ?? "";
        const city: string | null = show.city ?? null;
        const state: string | null = show.state ?? null;
        const country: string = show.country ?? "US";

        result.showsProcessed++;

        // Dedup: skip if show already exists for this date+venue
        const dvKey = `${date}|${venue}`;
        if (existingShows.has(dvKey)) {
          const existingId = existingShows.get(dvKey)!;
          oldIdToNewId.set(oldShowId, existingId);
          const ids = dateToShowIds.get(date) ?? [];
          ids.push(existingId);
          dateToShowIds.set(date, ids);
          continue;
        }

        const seq = (dateVenueCounts.get(dvKey) ?? 0) + 1;
        dateVenueCounts.set(dvKey, seq);

        const showId = slugify(`${date}-${venue}-${city ?? ""}-${state ?? ""}-${country}`);
        const parts = date.split("-");
        const year = parseInt(parts[0] ?? "0", 10);
        const month = parseInt(parts[1] ?? "0", 10);

        const setlistRaw = show.setlist ? JSON.stringify(show.setlist) : null;
        const lineupRaw = show.lineup ? JSON.stringify(show.lineup) : null;
        const songList = extractSongList(show.setlist);
        const memberList = extractMemberList(show.lineup);

        const sourceTypes: Record<string, unknown> = show.source_types ?? {};
        const bestSource = Object.keys(sourceTypes).length > 0
          ? Object.keys(sourceTypes).reduce((a, b) =>
            (SOURCE_PRIORITY[a] ?? 99) < (SOURCE_PRIORITY[b] ?? 99) ? a : b)
          : null;

        const totalReviews = (show.total_high_ratings ?? 0) + (show.total_low_ratings ?? 0);

        // Extract cover image from ticket_images: prefer front, then unknown, then first
        let coverImageUrl: string | null = null;
        const ticketImages: { url: string; side?: string }[] = show.ticket_images ?? [];
        if (ticketImages.length > 0) {
          const front = ticketImages.find((t) => t.side === "front");
          const unknown = ticketImages.find((t) => t.side === "unknown");
          coverImageUrl = (front ?? unknown ?? ticketImages[0]).url;
        }

        insertShow.run(
          showId, artistId, date, year, month, dayOfYear(date), seq,
          venue || null, city, state, country,
          "jerrygarcia.com", 0,
          show.setlist_status ?? null, setlistRaw, songList,
          show.lineup_status ?? null, lineupRaw,
          show.recording_count ?? 0,
          show.best_recording ?? null,
          bestSource,
          show.avg_rating ?? null,
          totalReviews,
          coverImageUrl,
          null, // notes
        );

        // Insert AI review if present
        const aiReview = show.ai_show_review;
        if (aiReview && (aiReview.summary || aiReview.review || aiReview.blurb)) {
          insertReview.run(
            showId,
            "ai",
            "claude",
            aiReview.summary ?? null,
            JSON.stringify(aiReview),
          );
        }

        insertFts.run(
          showId, artistName, formatDateVariants(date),
          venue, city ?? "", state ?? "", songList, memberList,
        );

        oldIdToNewId.set(oldShowId, showId);
        const ids = dateToShowIds.get(date) ?? [];
        ids.push(showId);
        dateToShowIds.set(date, ids);
        result.showsCreated++;
      }
    });

    insertMany();
    onProgress?.(`Imported ${result.showsCreated} shows (${result.showsProcessed} processed).`);

    // ── Recordings ──────────────────────────────────────────────

    if (fs.existsSync(recordingsDir)) {
      const recFiles = fs.readdirSync(recordingsDir)
        .filter((f) => f.endsWith(".json")).sort();

      onProgress?.(`Processing ${recFiles.length} recordings...`);

      const insertRec = db.prepare(`
        INSERT OR IGNORE INTO recordings (id, show_id, source_type, rating, raw_rating,
          review_count, confidence, high_ratings, low_ratings, taper, source, lineage)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);

      const insertRecs = db.transaction(() => {
        for (const file of recFiles) {
          const rec = JSON.parse(fs.readFileSync(path.join(recordingsDir, file), "utf-8"));
          const identifier = file.replace(".json", "");
          const date: string = rec.date ?? "";

          result.recordingsProcessed++;

          const showIds = dateToShowIds.get(date);
          if (!showIds?.length) continue;

          insertRec.run(
            identifier, showIds[0],
            rec.source_type ?? null,
            rec.rating ?? 0,
            rec.raw_rating ?? 0,
            rec.review_count ?? 0,
            rec.confidence ?? 0,
            rec.high_ratings ?? 0,
            rec.low_ratings ?? 0,
            rec.taper ?? null,
            rec.source ?? null,
            rec.lineage ?? null,
          );
          result.recordingsCreated++;
        }
      });

      insertRecs();
      onProgress?.(`Imported ${result.recordingsCreated} recordings (${result.recordingsProcessed} processed).`);
    }

    // ── Collections ─────────────────────────────────────────────

    if (fs.existsSync(collectionsFile)) {
      const data = JSON.parse(fs.readFileSync(collectionsFile, "utf-8"));
      const collections: {
        id: string;
        name: string;
        description: string;
        show_ids?: string[];
      }[] = data.collections ?? [];

      onProgress?.(`Processing ${collections.length} collections...`);

      const insertCol = db.prepare(
        "INSERT OR IGNORE INTO collections (id, artist_id, name, description) VALUES (?, ?, ?, ?)"
      );
      const insertColShow = db.prepare(
        "INSERT OR IGNORE INTO collection_shows (collection_id, show_id) VALUES (?, ?)"
      );

      const insertCols = db.transaction(() => {
        for (const col of collections) {
          insertCol.run(col.id, artistId, col.name, col.description);
          for (const oldShowId of col.show_ids ?? []) {
            const newId = oldIdToNewId.get(oldShowId);
            if (newId) insertColShow.run(col.id, newId);
          }
          result.collectionsProcessed++;
        }
      });

      insertCols();
      onProgress?.(`Imported ${result.collectionsProcessed} collections.`);
    }

    return result;
  }
}
