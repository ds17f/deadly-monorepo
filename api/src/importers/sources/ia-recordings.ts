/**
 * Internet Archive recording source — bulk-fetches recording metadata
 * from an IA etree collection via the advanced search API.
 *
 * Source type classification is ported from the battle-tested Python
 * logic in data/scripts/shared/recording_utils.py.
 */
import type { ImportProgress } from "../types.js";
import type { RecordingData, RecordingSource } from "./types.js";

const SEARCH_URL = "https://archive.org/advancedsearch.php";
const ROWS_PER_PAGE = 1000;
const REQUEST_DELAY_MS = 200; // polite delay between pages

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// ── Source type classification ──────────────────────────────────

const SOURCE_PRIORITY: Record<string, number> = {
  SBD: 0, MATRIX: 1, FM: 2, AUD: 3, REMASTER: 4,
};

/**
 * Classify recording source type using a hierarchical approach:
 *   1. Identifier patterns (highest confidence)
 *   2. Lineage keywords (high confidence, priority order)
 *   3. Source + description text search (fallback)
 *   4. null if unknown
 *
 * Ported from data/scripts/shared/recording_utils.py:improve_source_type_detection()
 */
export function classifySourceType(
  identifier: string,
  source: string | null,
  lineage: string | null,
  description: string | null,
): string | null {
  const id = identifier.toUpperCase();
  const src = (source ?? "").toUpperCase();
  const lin = (lineage ?? "").toUpperCase();
  const desc = typeof description === "string"
    ? description.toUpperCase()
    : Array.isArray(description)
      ? (description as string[]).join(" ").toUpperCase()
      : "";

  // Step 1: Identifier patterns (most reliable)
  let baseType: string | null = null;

  if (id.includes(".SBD.") || id.includes(".SOUNDBOARD.") || id.endsWith(".SBD")) {
    baseType = "SBD";
  } else if (id.includes(".MTX.") || id.includes(".MATRIX.")) {
    baseType = "MATRIX";
  } else if (id.includes(".AUD.") || id.includes(".AUDIENCE.") || id.endsWith(".AUD") || id.includes(".FOB.")) {
    baseType = "AUD";
  } else if (id.includes(".FM.") || id.includes(".BROADCAST.") || id.endsWith(".FM")) {
    baseType = "FM";
  }

  // Step 2: Lineage keywords — can upgrade the classification
  const sourceLineage = lin || src; // use lineage first, fall back to source
  if (sourceLineage) {
    // MATRIX has highest priority
    if (sourceLineage.includes("MATRIX") || sourceLineage.includes("MTX")) {
      return "MATRIX";
    }
    // FM has second priority
    if (sourceLineage.startsWith("FM") || sourceLineage.includes("FM ") ||
        sourceLineage.startsWith("BROADCAST") || sourceLineage.includes("BROADCAST")) {
      return "FM";
    }
    // SBD — only if source is actually FROM the soundboard
    if ((sourceLineage.startsWith("SBD>") || sourceLineage.startsWith("SBD >") ||
         sourceLineage.includes("MASTER SOUNDBOARD") ||
         sourceLineage.includes("SOUNDBOARD>") || sourceLineage.includes("SOUNDBOARD >")) &&
        (baseType === "AUD" || baseType === null)) {
      return "SBD";
    }
    // Microphone indicators → AUD (only if currently unknown)
    if (baseType === null) {
      const micBrands = ["MICROPHONE", "SENNHEISER", "AKG", "NEUMANN", "SONY", "DPA", "SCHOEPS", "RODE"];
      if (micBrands.some((m) => sourceLineage.includes(m))) {
        return "AUD";
      }
    }
  }

  // Step 3: Return identifier-based type if we have one
  if (baseType !== null) return baseType;

  // Step 4: Text search fallback across all fields
  const text = `${id} ${src} ${lin} ${desc}`;

  if (text.includes("SBD") || text.includes("SOUNDBOARD")) return "SBD";
  if (text.includes("MATRIX")) return "MATRIX";
  if (text.includes("AUD") || text.includes("AUDIENCE")) return "AUD";
  if (text.includes("FM") || text.includes("BROADCAST") || text.includes("RADIO")) return "FM";
  if (text.includes("REMASTER")) return "REMASTER";

  return null;
}

/** Rank source types for "best recording" selection. Lower = better. */
export { SOURCE_PRIORITY };

// ── IA search response shape ────────────────────────────────────

interface IADoc {
  identifier: string;
  date?: string;
  avg_rating?: number;
  num_reviews?: number;
  source?: string;
  taper?: string;
  description?: string | string[];
  lineage?: string;
}

interface IASearchResponse {
  response: {
    numFound: number;
    start: number;
    docs: IADoc[];
  };
}

// ── Helpers ─────────────────────────────────────────────────────

/** Normalize IA date strings to YYYY-MM-DD. IA dates can include time. */
function normalizeDate(raw: string): string | null {
  // Handle "YYYY-MM-DDT..." or "YYYY-MM-DD ..." or just "YYYY-MM-DD"
  const match = raw.match(/^(\d{4}-\d{2}-\d{2})/);
  return match ? match[1] : null;
}

function descriptionToString(desc: string | string[] | undefined): string | null {
  if (!desc) return null;
  return Array.isArray(desc) ? desc.join(" ") : desc;
}

// ── Implementation ──────────────────────────────────────────────

export class IARecordingSource implements RecordingSource {
  readonly name = "archive.org";

  constructor(private readonly collection: string) {}

  async fetchRecordings(onProgress?: ImportProgress): Promise<RecordingData[]> {
    const recordings: RecordingData[] = [];
    let page = 1;
    let totalFound = 0;

    const fields = "identifier,date,avg_rating,num_reviews,source,taper,description,lineage";
    const query = `collection:${this.collection} AND mediatype:etree`;

    while (true) {
      if (page > 1) await sleep(REQUEST_DELAY_MS);

      const params = new URLSearchParams({
        q: query,
        fl: fields,
        rows: String(ROWS_PER_PAGE),
        page: String(page),
        output: "json",
      });

      const res = await fetch(`${SEARCH_URL}?${params}`);
      if (!res.ok) {
        throw new Error(`IA search error: HTTP ${res.status} on page ${page}`);
      }

      const data = (await res.json()) as IASearchResponse;

      if (page === 1) {
        totalFound = data.response.numFound;
        onProgress?.(`archive.org: found ${totalFound} recordings in collection "${this.collection}"`);
      }

      if (data.response.docs.length === 0) break;

      for (const doc of data.response.docs) {
        const rec = mapRecording(doc);
        if (rec) recordings.push(rec);
      }

      onProgress?.(`archive.org: page ${page} (${recordings.length}/${totalFound} recordings)`);

      // Stop if we've fetched all results
      if (recordings.length >= totalFound) break;
      page++;
    }

    onProgress?.(`archive.org: fetched ${recordings.length} recordings.`);
    return recordings;
  }
}

function mapRecording(doc: IADoc): RecordingData | null {
  const date = doc.date ? normalizeDate(doc.date) : null;
  if (!date) return null; // skip recordings without a parseable date

  const descStr = descriptionToString(doc.description);
  const sourceType = classifySourceType(
    doc.identifier,
    doc.source ?? null,
    doc.lineage ?? null,
    descStr,
  );

  return {
    identifier: doc.identifier,
    date,
    source_type: sourceType,
    rating: doc.avg_rating ?? 0,
    raw_rating: doc.avg_rating ?? 0,
    review_count: doc.num_reviews ?? 0,
    confidence: doc.num_reviews != null && doc.num_reviews >= 5 ? 1.0 : 0.5,
    high_ratings: 0, // not available from bulk search
    low_ratings: 0,
    taper: doc.taper ?? null,
    source: doc.source ?? null,
    lineage: doc.lineage ?? null,
  };
}
