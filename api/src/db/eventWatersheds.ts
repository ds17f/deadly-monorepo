import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Per-event/field cutover timestamps. Dashboard queries that depend on a
 * field or event added after analytics began should `WHERE ts >= getWatershed(key)`
 * so rows that predate the new shape don't appear as anomalies (sudden drops,
 * undefined props, etc).
 *
 * Backed by `api/config/event-watersheds.json`. The file is read once on first
 * lookup and cached. Restart the API to apply edits.
 */

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const CONFIG_PATH =
  process.env.EVENT_WATERSHEDS_PATH ??
  path.resolve(__dirname, "../../config/event-watersheds.json");

let cache: Record<string, number> | null = null;

/**
 * Returns the cutover timestamp (ms since epoch) for the given event or
 * event.field key, or null if no watershed is configured (i.e. the field
 * has always been present, or the caller forgot to register it).
 *
 * Callers should treat null as "no gate needed" and run the query unfiltered.
 */
export function getWatershed(key: string): number | null {
  if (!cache) load();
  const v = cache?.[key];
  return v ?? null;
}

/** Force a re-read of the config file. Useful for tests or a hot-reload endpoint. */
export function reloadWatersheds(): void {
  cache = null;
  load();
}

/**
 * Snapshot of the configured watersheds as ISO strings, for the admin UI to
 * render visual markers on charts. Returns `{ key: isoTimestamp }`.
 */
export function getAllWatersheds(): Record<string, string> {
  if (!cache) load();
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(cache ?? {})) {
    out[k] = new Date(v).toISOString();
  }
  return out;
}

function load(): void {
  const raw = JSON.parse(fs.readFileSync(CONFIG_PATH, "utf-8")) as Record<
    string,
    unknown
  >;
  const parsed: Record<string, number> = {};
  for (const [key, value] of Object.entries(raw)) {
    // Skip metadata keys (_format, _example, etc).
    if (key.startsWith("_")) continue;
    if (typeof value !== "string") continue;
    const ts = Date.parse(value);
    if (Number.isNaN(ts)) {
      // eslint-disable-next-line no-console
      console.warn(`[eventWatersheds] invalid timestamp for "${key}": ${value}`);
      continue;
    }
    parsed[key] = ts;
  }
  cache = parsed;
}
