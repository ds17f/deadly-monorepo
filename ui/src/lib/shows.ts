import fs from "fs";
import path from "path";
import type { Show } from "@/types/show";
import type { Recording } from "@/types/recording";

const DATA_DIR = path.join(process.cwd(), "data");

let _sortedIds: string[] | null = null;

function getSortedShowIds(): string[] {
  if (!_sortedIds) {
    const showsDir = path.join(DATA_DIR, "shows");
    _sortedIds = fs
      .readdirSync(showsDir)
      .filter((f) => f.endsWith(".json"))
      .map((f) => f.replace(".json", ""))
      .sort();
  }
  return _sortedIds;
}

export function getAllShowIds(): string[] {
  return getSortedShowIds();
}

/**
 * Returns a small contiguous slice of show IDs for fast dev builds.
 * Set DEV_PAGES=n to limit, or omit / set to 0 for all pages.
 */
export function getBuildShowIds(): string[] {
  const all = getSortedShowIds();
  const limit = parseInt(process.env.DEV_PAGES ?? "0", 10);
  if (!limit || limit <= 0) return all;
  // Pick a slice from the middle so prev/next navigation works
  const start = Math.max(0, Math.floor(all.length / 2) - Math.floor(limit / 2));
  return all.slice(start, start + limit);
}

export function getShowById(id: string): Show {
  const filePath = path.join(DATA_DIR, "shows", `${id}.json`);
  const raw = fs.readFileSync(filePath, "utf-8");
  return JSON.parse(raw) as Show;
}

export function getAdjacentShows(
  id: string
): { prev: string | null; next: string | null } {
  const ids = getSortedShowIds();
  const idx = ids.indexOf(id);
  return {
    prev: idx > 0 ? ids[idx - 1] : null,
    next: idx < ids.length - 1 ? ids[idx + 1] : null,
  };
}

export function getRecordingById(id: string): Recording | null {
  const filePath = path.join(DATA_DIR, "recordings", `${id}.json`);
  if (!fs.existsSync(filePath)) return null;
  const raw = fs.readFileSync(filePath, "utf-8");
  return JSON.parse(raw) as Recording;
}
