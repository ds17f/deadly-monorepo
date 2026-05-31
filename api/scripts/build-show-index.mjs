#!/usr/bin/env node
// Build a compact show-metadata index the API loads at boot to enrich
// user-data responses (recents, favorites) with display fields. The full
// show catalog lives only in the static site's per-show JSON; the API has
// no show data of its own, so we distill the display-relevant fields into
// one small file. See PLANS/web-profile.md (show-metadata source).
//
// Usage (from repo root):
//   node api/scripts/build-show-index.mjs [showsDir] [outFile]
// Defaults: ui/data/shows  ->  api-data/show-index.json

import fs from "node:fs";
import path from "node:path";

const showsDir = process.argv[2] ?? "ui/data/shows";
const outFile = process.argv[3] ?? "api-data/show-index.json";

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

for (const file of files) {
  try {
    const s = JSON.parse(fs.readFileSync(path.join(showsDir, file), "utf-8"));
    if (!s.show_id) continue;
    index[s.show_id] = {
      date: s.date ?? null,
      venue: s.venue ?? null,
      city: s.city ?? null,
      state: s.state ?? null,
      country: s.country ?? null,
      rating: s.ai_show_review?.ratings?.ai_rating ?? 0,
      recordingCount: s.recording_count ?? 0,
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
