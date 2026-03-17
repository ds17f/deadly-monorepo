import type { ArchiveTrack } from "@/types/player";

const FORMAT_PRIORITY = ["VBR MP3", "MP3", "Ogg Vorbis"];
const MAX_RETRIES = 3;
const CACHE_PREFIX = "archive_tracks_";

/** Unwrap polymorphic Archive.org field (string | string[]) to string. */
function flexString(val: unknown): string {
  if (Array.isArray(val)) return val[0] ?? "";
  if (typeof val === "string") return val;
  if (typeof val === "number") return String(val);
  return "";
}

/**
 * Parse Archive.org duration which may be "MM:SS", "H:MM:SS", or raw seconds.
 */
function parseDuration(raw: unknown): number {
  const s = flexString(raw);
  if (!s) return 0;
  const parts = s.split(":");
  if (parts.length === 3) {
    return (
      parseInt(parts[0], 10) * 3600 +
      parseInt(parts[1], 10) * 60 +
      parseFloat(parts[2])
    );
  }
  if (parts.length === 2) {
    return parseInt(parts[0], 10) * 60 + parseFloat(parts[1]);
  }
  const n = parseFloat(s);
  return isNaN(n) ? 0 : n;
}

/**
 * Parse Archive.org track number which may be "1", "01", "1/12", or missing.
 */
function parseTrackNumber(raw: unknown): number {
  const s = flexString(raw);
  if (!s) return Infinity; // missing → sort to end
  const n = parseInt(s, 10);
  return isNaN(n) ? Infinity : n;
}

/**
 * Extract a human-readable title from an Archive.org filename when the
 * metadata title is missing. Strips gd/grateful_dead prefix, date,
 * disc/track identifiers, and file extension.
 */
function titleFromFilename(filename: string): string {
  let name = filename.replace(/\.[^.]+$/, ""); // strip extension
  // Strip common prefixes: gd77-05-08d1t01, gd1977-05-08.sbd.xxx.d1t01
  name = name.replace(/^(gd|grateful_dead)[\d-]*/i, "");
  // Strip source/taper info segments (dot-separated) before disc/track id
  // e.g. ".sbd.wiley.8729.sbefail" or ".nak300.frank.12345"
  name = name.replace(/^(\.[a-z]+)+(\.\d+)*/i, "");
  // Strip disc/track markers like .d1t01, d1t01, d01t01
  name = name.replace(/\.?d\d+t\d+/i, "");
  // Replace separators with spaces
  name = name.replace(/[._-]+/g, " ").trim();
  if (!name) return filename;
  // Title case
  return name.replace(/\b\w/g, (c) => c.toUpperCase());
}

interface ArchiveFile {
  name?: unknown;
  title?: unknown;
  track?: unknown;
  length?: unknown;
  format?: unknown;
}

/**
 * Fetch track listing for an Archive.org recording identifier.
 * Returns playable tracks sorted by track number.
 */
export async function fetchArchiveTracks(
  identifier: string
): Promise<ArchiveTrack[]> {
  const cached = readCache(identifier);
  let data: { files?: ArchiveFile[] } | null = null;

  try {
    data = await fetchWithRetry(
      `https://archive.org/metadata/${identifier}`
    );
    writeCache(identifier, data);
  } catch {
    if (cached) return cached;
    throw new Error("Failed to load recording metadata from Archive.org");
  }

  const files: ArchiveFile[] = data?.files ?? [];
  const tracks = extractTracks(identifier, files);

  if (tracks.length === 0 && cached) return cached;
  return tracks;
}

function extractTracks(
  identifier: string,
  files: ArchiveFile[]
): ArchiveTrack[] {
  // Find first available format from priority list
  const lowerFormats = files.map((f) => flexString(f.format).toLowerCase());

  let matchingFormat: string | null = null;
  for (const fmt of FORMAT_PRIORITY) {
    const lower = fmt.toLowerCase();
    if (lowerFormats.some((f) => f === lower)) {
      matchingFormat = lower;
      break;
    }
  }

  if (!matchingFormat) return [];

  const audioFiles = files.filter(
    (f) => flexString(f.format).toLowerCase() === matchingFormat
  );

  const tracks: ArchiveTrack[] = audioFiles.map((f) => {
    const filename = flexString(f.name);
    const rawTitle = flexString(f.title);
    return {
      filename,
      title: rawTitle || titleFromFilename(filename),
      track: parseTrackNumber(f.track),
      duration: parseDuration(f.length),
      url: `https://archive.org/download/${identifier}/${encodeURIComponent(filename)}`,
    };
  });

  // Sort by track number, then filename
  tracks.sort((a, b) => {
    if (a.track !== b.track) return a.track - b.track;
    return a.filename.localeCompare(b.filename);
  });

  // Reassign sequential track numbers for display
  tracks.forEach((t, i) => {
    t.track = i + 1;
  });

  return tracks;
}

async function fetchWithRetry(
  url: string,
  attempt = 0
): Promise<{ files?: ArchiveFile[] }> {
  try {
    const res = await fetch(url);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    return await res.json();
  } catch (err) {
    if (attempt < MAX_RETRIES - 1) {
      const delay = attempt * 1000; // 0ms, 1000ms, 2000ms
      if (delay > 0) await new Promise((r) => setTimeout(r, delay));
      return fetchWithRetry(url, attempt + 1);
    }
    throw err;
  }
}

function readCache(identifier: string): ArchiveTrack[] | null {
  try {
    const raw = sessionStorage.getItem(CACHE_PREFIX + identifier);
    if (raw) return JSON.parse(raw);
  } catch {
    // sessionStorage unavailable or corrupt
  }
  return null;
}

function writeCache(
  identifier: string,
  data: { files?: ArchiveFile[] } | null
): void {
  if (!data) return;
  try {
    const tracks = extractTracks(identifier, data.files ?? []);
    if (tracks.length > 0) {
      sessionStorage.setItem(CACHE_PREFIX + identifier, JSON.stringify(tracks));
    }
  } catch {
    // quota exceeded or unavailable
  }
}
