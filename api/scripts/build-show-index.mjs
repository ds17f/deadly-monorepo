#!/usr/bin/env node
// Build a compact show-metadata index the API loads at boot to enrich
// user-data responses (recents, favorites) with display fields. The full
// show catalog lives only in the static site's per-show JSON; the API has
// no show data of its own, so we distill the display-relevant fields into
// one small file. See PLANS/web-profile.md (show-metadata source).
//
// Usage (from repo root):
//   node api/scripts/build-show-index.mjs [showsDir] [outFile] [recordingsDir]
// Defaults: ui/data/shows  ->  api-data/show-index.json
//           recordingsDir defaults to <showsDir>/../recordings

import fs from "node:fs";
import path from "node:path";

const showsDir = process.argv[2] ?? "ui/data/shows";
const outFile = process.argv[3] ?? "api-data/show-index.json";
const recordingsDir =
  process.argv[4] ?? path.join(path.dirname(showsDir), "recordings");

// Non-fatal if the data isn't present: write an empty index so the image
// build (and boot) still succeed — the API just returns bare records and
// the catalog can be regenerated once data is available.
if (!fs.existsSync(showsDir)) {
  console.warn(`[build-show-index] shows dir not found: ${showsDir} — writing empty index.`);
  fs.mkdirSync(path.dirname(outFile), { recursive: true });
  fs.writeFileSync(outFile, "{}");
  process.exit(0);
}

const files = fs.readdirSync(showsDir).filter((f) => f.endsWith(".json"));
const index = {};

// Card "ticket" image: prefer a ticket stub (front, then unknown, then any),
// then a show photo thumbnail. Mirrors resolveCoverImageUrl on the web show
// page. Null when none — the client falls back to the Archive.org thumbnail
// for bestRecordingId, then the logo.
function resolveImage(s) {
  const tickets = s.ticket_images ?? [];
  const front = tickets.find((t) => t.side === "front");
  if (front?.url) return front.url;
  const unknown = tickets.find((t) => t.side === "unknown");
  if (unknown?.url) return unknown.url;
  if (tickets[0]?.url) return tickets[0].url;
  const photo = (s.photos ?? [])[0];
  if (photo) return photo.thumbnail_url ?? photo.url ?? null;
  return null;
}

// The compact per-recording shape the web recording menu needs (source badge,
// rating, review count, runtime). Resolved by reading each recording's own
// JSON; the browser has no catalog, so the API serves this by showId.
//
// ⚠️ TECH DEBT — every field added to this shape is baked into the index for
// EVERY recording of EVERY show, growing the blob the API loads wholesale into
// RAM (adding these five fields already ~5×'d it: ~0.6 → ~3.1 MB). Before
// widening this, read ADR-0012 (docs/adr/0012-show-index-in-memory-json.md):
// more recording fields is the named trigger to move the show index into a
// SQLite table instead of growing this file.
const haveRecordings = fs.existsSync(recordingsDir);
if (!haveRecordings) {
  console.warn(
    `[build-show-index] recordings dir not found: ${recordingsDir} — recordings list omitted.`,
  );
}
function loadRecording(identifier) {
  if (!haveRecordings) return null;
  try {
    const r = JSON.parse(
      fs.readFileSync(path.join(recordingsDir, `${identifier}.json`), "utf-8"),
    );
    return {
      identifier: r.identifier ?? identifier,
      source_type: r.source_type ?? "UNKNOWN",
      rating: r.rating ?? 0,
      review_count: r.review_count ?? 0,
      runtime: r.runtime ?? "",
    };
  } catch {
    return null;
  }
}

for (const file of files) {
  try {
    const s = JSON.parse(fs.readFileSync(path.join(showsDir, file), "utf-8"));
    if (!s.show_id) continue;
    const recordings = Array.isArray(s.recordings)
      ? s.recordings.map(loadRecording).filter(Boolean)
      : [];
    index[s.show_id] = {
      date: s.date ?? null,
      venue: s.venue ?? null,
      city: s.city ?? null,
      state: s.state ?? null,
      country: s.country ?? null,
      rating: s.ai_show_review?.ratings?.ai_rating ?? 0,
      recordingCount: s.recording_count ?? 0,
      image: resolveImage(s),
      bestRecordingId: s.best_recording ?? null,
      recordings,
    };
  } catch (err) {
    console.warn(`[build-show-index] skip ${file}: ${err.message}`);
  }
}

fs.mkdirSync(path.dirname(outFile), { recursive: true });
fs.writeFileSync(outFile, JSON.stringify(index));
const bytes = fs.statSync(outFile).size;
console.log(
  `[build-show-index] wrote ${Object.keys(index).length} shows to ${outFile} (${(bytes / 1024).toFixed(0)} KB)`
);
