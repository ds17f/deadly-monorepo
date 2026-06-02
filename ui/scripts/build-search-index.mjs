// Prebuild step: generate the client search index from the show catalog.
//
// Reads ui/data/shows/*.json and emits a compact, dictionary-encoded index to
// public/search-index.<dataVersion>.json. The web app lazy-loads this once on
// first search, caches it in IndexedDB keyed by version, and queries it
// in-browser (MiniSearch) — so search is song-aware (setlist titles), costs the
// server nothing beyond one static file, and matches the mobile FTS feel.
//
// Encoding: songs are de-duplicated into a single lookup table and referenced
// by integer per show. That ~halves the uncompressed size (helps IndexedDB
// footprint + in-browser parse), on top of Caddy's gzip/brotli over the wire.

import fs from "node:fs";
import path from "node:path";

const CWD = process.cwd(); // ui/ during `npm run build`
const SHOWS_DIR = path.join(CWD, "data", "shows");
const OUT_DIR = path.join(CWD, "public");

function readDataVersion() {
  // Source of truth is the repo-root data/version; fall back to "dev".
  for (const p of [
    path.join(CWD, "..", "data", "version"),
    path.join(CWD, "data", "version"),
  ]) {
    try {
      return fs.readFileSync(p, "utf-8").trim();
    } catch {
      /* keep looking */
    }
  }
  return "dev";
}

function songNames(show) {
  const out = [];
  for (const set of show.setlist ?? []) {
    for (const s of set.songs ?? []) {
      const n = (s.name ?? "").trim();
      if (n) out.push(n);
    }
  }
  // De-dupe within a show, preserve order.
  return [...new Set(out)];
}

function memberNames(show) {
  const out = [];
  for (const m of show.lineup ?? []) {
    const n = (m.name ?? "").trim();
    if (n) out.push(n);
  }
  return [...new Set(out)];
}

function main() {
  const version = readDataVersion();
  const files = fs.readdirSync(SHOWS_DIR).filter((f) => f.endsWith(".json"));

  // De-dupe songs and members into shared lookup tables (both repeat heavily
  // across 2.3k shows), referenced by int per show to keep the file compact.
  const interner = () => {
    const index = new Map();
    const list = [];
    return {
      list,
      id: (name) => {
        let id = index.get(name);
        if (id === undefined) {
          id = list.length;
          list.push(name);
          index.set(name, id);
        }
        return id;
      },
    };
  };
  const songDict = interner();
  const memberDict = interner();

  const docs = [];
  let withSetlist = 0;
  for (const file of files) {
    const show = JSON.parse(fs.readFileSync(path.join(SHOWS_DIR, file), "utf-8"));
    const songs = songNames(show);
    const members = memberNames(show);
    if (songs.length) withSetlist++;
    docs.push({
      i: show.show_id,
      d: show.date ?? "",
      v: show.venue ?? "",
      c: show.city ?? "",
      s: show.state ?? "",
      g: songs.map(songDict.id), // setlist song ids
      m: members.map(memberDict.id), // lineup member ids
      t: Object.keys(show.source_types ?? {}), // source-type tags (SBD/AUD/…)
    });
  }

  const songs = songDict.list;
  const members = memberDict.list;
  const payload = { version, songs, members, docs };
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const outFile = path.join(OUT_DIR, `search-index.${version}.json`);
  const json = JSON.stringify(payload);
  fs.writeFileSync(outFile, json);

  const kb = (n) => `${(n / 1024).toFixed(0)} KB`;
  console.log(
    `[search-index] v${version}: ${docs.length} shows ` +
      `(${withSetlist} w/ setlist), ${songs.length} songs, ` +
      `${members.length} members → ` +
      `${path.relative(CWD, outFile)} (${kb(Buffer.byteLength(json))})`,
  );
}

main();
