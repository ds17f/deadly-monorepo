#!/usr/bin/env python3
"""
SPIKE — build a prebuilt catalog.db seed from stage02.

STATUS: spike / not yet wired into CI. See ADR-0007
(docs/adr/0007-prebuilt-catalog-db.md) and PLANS/prebuilt-catalog-db.md.
This is the basis for the eventual CI builder; the schema here is the proposed
canonical seed contract (to be promoted to data/catalog_schema.json).

WHAT IT DOES
  Reads stage02 JSON and emits a catalog-only SQLite seed that both apps copy
  into their own (already-migrated) DB via ATTACH + INSERT…SELECT. Catalog data
  only — device-local state (isFavorite/favoritedAt, library_shows, recents,
  reviews, sync outbox) is created empty by each app. FTS is NOT shipped; each
  app rebuilds its own (iOS show_search FTS4 custom tokenizer / Android shows_fts).

  Parse logic mirrors the iOS importers (ShowImporter / RecordingImporter /
  ImportModels) so seed columns match what both GRDB and Room expect. shows and
  recordings column names are identical across platforms → one seed, no mapping.

RECORDING ↔ SHOW LINKAGE (see PLANS doc "Builder gotchas")
  Recording JSON has no show_id; linkage is inverted from each show's
  recordings[] list. Of 17,854 recording files:
    - 17,588 referenced by exactly one show (normal)
    -     57 referenced by TWO shows (early/late splits, same-date multi-venue) —
            the identifier-only PK can't hold both, so we pick one deterministically.
            (Live apps disagree here today: iOS onConflict=.ignore keeps the FIRST
            show, Android onConflict=REPLACE keeps the LAST. The seed makes this
            deterministic + identical on both platforms.)
    -    209 ORPHANS (file on disk, no show references it): studio sessions,
            rehearsals, interviews, "various"/unknown venues, partial dates
            (XX-XX / 00). The iOS importer skips these; so do we (DROP_ORPHANS).
            They aren't reachable in-app anyway (recordings are queried
            WHERE show_id = ?), so dropping them is faithful and saves space.

Usage: build_catalog_db.py <stage02_dir> <out.db> [data_version]
"""
import json, os, sqlite3, glob, gzip, shutil, sys, time

stage = sys.argv[1] if len(sys.argv) > 1 else "stage02-generated-data"
out   = sys.argv[2] if len(sys.argv) > 2 else "/tmp/catalog.db"
ver   = sys.argv[3] if len(sys.argv) > 3 else "0.0.0"
now   = int(time.time() * 1000)

DROP_ORPHANS = True   # match iOS importer: skip recordings no show references
SRC_RANK = ["SBD", "FM", "MATRIX", "REMASTER", "AUD"]

if os.path.exists(out):
    os.remove(out)
con = sqlite3.connect(out); c = con.cursor()

# ---- canonical seed schema (catalog only) ----
c.execute("""CREATE TABLE shows(
  showId TEXT PRIMARY KEY, date TEXT, year INTEGER, month INTEGER, yearMonth TEXT,
  band TEXT, url TEXT, venueName TEXT, city TEXT, state TEXT, country TEXT,
  locationRaw TEXT, setlistStatus TEXT, setlistRaw TEXT, songList TEXT,
  lineupStatus TEXT, lineupRaw TEXT, memberList TEXT, showSequence INTEGER,
  recordingsRaw TEXT, recordingCount INTEGER, bestRecordingId TEXT, bestSourceType TEXT,
  averageRating REAL, totalReviews INTEGER, coverImageUrl TEXT,
  createdAt INTEGER, updatedAt INTEGER)""")
c.execute("""CREATE TABLE recordings(
  identifier TEXT PRIMARY KEY, show_id TEXT, source_type TEXT, rating REAL,
  raw_rating REAL, review_count INTEGER, confidence REAL, high_ratings INTEGER,
  low_ratings INTEGER, taper TEXT, source TEXT, lineage TEXT,
  source_type_string TEXT, collection_timestamp INTEGER)""")
c.execute("""CREATE TABLE dead_collections(
  id TEXT PRIMARY KEY, name TEXT, description TEXT, tagsJson TEXT, showIdsJson TEXT,
  totalShows INTEGER, primaryTag TEXT, createdAt INTEGER, updatedAt INTEGER)""")
c.execute("""CREATE TABLE data_version(
  id INTEGER PRIMARY KEY, dataVersion TEXT, packageName TEXT, versionType TEXT,
  description TEXT, importedAt INTEGER, gitCommit TEXT, gitTag TEXT,
  buildTimestamp TEXT, totalShows INTEGER, totalVenues INTEGER,
  totalFiles INTEGER, totalSizeBytes INTEGER)""")


def song_list(setlist):
    if not isinstance(setlist, list):
        return None
    out = [song.get("name") for s in setlist for song in (s.get("songs") or []) if song.get("name")]
    return ",".join(out) if out else None


def member_list(lineup):
    if not isinstance(lineup, list):
        return None
    names = [m.get("name") for m in lineup if m.get("name")]
    return ",".join(names) if names else None


def cover_url(d):
    tis = d.get("ticket_images") or []
    for side in ("front", "unknown"):
        for ti in tis:
            if ti.get("side") == side:
                return ti.get("url")
    ph = d.get("photos") or []
    return ph[0].get("url") if ph else None


# Pass 1: reverse index recordingId -> [shows], with a deterministic order so the
# tie-break for shared recordings is stable (sorted show_id; first wins).
rec_to_shows = {}
show_files = sorted(glob.glob(os.path.join(stage, "shows", "*.json")))
for f in show_files:
    d = json.load(open(f))
    sid = d.get("show_id") or os.path.basename(f)[:-5]
    for rid in (d.get("recordings") or []):
        rec_to_shows.setdefault(rid, []).append(sid)
for rid in rec_to_shows:
    rec_to_shows[rid].sort()

# Pass 2: shows
n_show = 0
for f in show_files:
    d = json.load(open(f))
    sid = d.get("show_id") or os.path.basename(f)[:-5]
    parts = (d.get("date") or "").split("-")
    year = int(parts[0]) if len(parts) > 0 and parts[0].isdigit() else 0
    month = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 0
    ym = f"{parts[0]}-{parts[1]}" if len(parts) >= 2 else d.get("date")
    setlist = d.get("setlist")
    lineup = d.get("lineup")
    src = d.get("source_types") or {}
    best_src = next((s for s in SRC_RANK if s in src), None)
    total_reviews = (d.get("total_high_ratings") or 0) + (d.get("total_low_ratings") or 0)
    avg = d.get("avg_rating") or 0.0
    c.execute("INSERT OR REPLACE INTO shows VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
        sid, d.get("date"), year, month, ym, d.get("band", "Grateful Dead"), d.get("url"),
        d.get("venue", ""), d.get("city"), d.get("state"), d.get("country") or "USA",
        d.get("location_raw"), d.get("setlist_status"),
        json.dumps(setlist) if setlist is not None else None,
        song_list(setlist), d.get("lineup_status"),
        json.dumps(lineup) if lineup else None, member_list(lineup), 1,
        json.dumps(d.get("recordings")) if d.get("recordings") else None,
        d.get("recording_count") or 0, d.get("best_recording"), best_src,
        avg if avg > 0 else None, total_reviews, cover_url(d), now, now))
    n_show += 1

# Pass 3: recordings (one row per identifier; show_id = first referencing show)
n_rec = n_orphan = 0
for f in sorted(glob.glob(os.path.join(stage, "recordings", "*.json"))):
    rid = os.path.basename(f)[:-5]
    shows = rec_to_shows.get(rid)
    if not shows:
        n_orphan += 1
        if DROP_ORPHANS:
            continue
    d = json.load(open(f))
    c.execute("INSERT OR REPLACE INTO recordings VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
        rid, shows[0] if shows else "", d.get("source_type"), d.get("rating") or 0.0,
        d.get("raw_rating") or 0.0, d.get("review_count") or 0, d.get("confidence") or 0.0,
        d.get("high_ratings") or 0, d.get("low_ratings") or 0, d.get("taper") or "",
        d.get("source") or "", d.get("lineage") or "", d.get("source_type"), now))
    n_rec += 1

# Pass 4: collections (lightweight — full showSelector resolution is a TODO; the
# real builder must port CollectionsImporter selector logic to compute showIds).
coll_path = os.path.join(stage, "collections.json")
n_coll = 0
if os.path.exists(coll_path):
    cj = json.load(open(coll_path))
    items = cj.get("collections", cj) if isinstance(cj, dict) else cj
    for col in (items or []):
        tags = col.get("tags") or []
        show_ids = col.get("show_ids") or []
        c.execute("INSERT OR REPLACE INTO dead_collections VALUES (?,?,?,?,?,?,?,?,?)", (
            col.get("id"), col.get("name", ""), col.get("description", ""),
            json.dumps(tags), json.dumps(show_ids), len(show_ids),
            tags[0] if tags else None, now, now))
        n_coll += 1

c.execute("INSERT INTO data_version VALUES (1,?,?,?,?,?,?,?,?,?,?,?,?)", (
    ver, "deadly-monorepo-data", "release", f"Prebuilt catalog seed {ver}",
    now, None, f"data-v{ver}", None, n_show, 0, n_rec, 0))

con.commit()
con.close()
con = sqlite3.connect(out); con.execute("VACUUM"); con.close()  # VACUUM needs its own (non-txn) conn

raw = os.path.getsize(out)
with open(out, "rb") as fi, gzip.open(out + ".gz", "wb", 9) as fo:
    shutil.copyfileobj(fi, fo)
gz = os.path.getsize(out + ".gz")

print(f"=== catalog.db built: {out} ===")
print(f"rows: {n_show} shows, {n_rec} recordings"
      + (f" ({n_orphan} orphans dropped)" if DROP_ORPHANS else f" (incl. {n_orphan} orphans)")
      + f", {n_coll} collections")
print(f"size: {raw/1024/1024:.1f} MB uncompressed  |  {gz/1024/1024:.2f} MB gzip")
