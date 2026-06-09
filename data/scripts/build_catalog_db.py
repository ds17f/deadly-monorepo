#!/usr/bin/env python3
"""
Build the prebuilt catalog seed (catalog.db) from stage02.

The seed is a catalog-only SQLite database that both apps copy into their own
(already-migrated) DB via ATTACH + INSERT...SELECT, instead of importing ~20k
JSON files on-device. See docs/adr/0007-prebuilt-catalog-db.md and
PLANS/prebuilt-catalog-db.md.

  - Schema is created from data/catalog_schema.json (single source of truth; the
    apps' drift tests assert their catalog columns are covered by it).
  - Parse logic mirrors the iOS importers (ShowImporter / RecordingImporter /
    CollectionsImporter / ImportModels) so the seed matches what GRDB (iOS) and
    Room (Android) expect. shows/recordings column names are identical across
    platforms, so one seed copies into either with no per-platform mapping.
  - Catalog data only: device-local state (favorites, library, recents, reviews,
    sync outbox) is created empty by each app and is not in the seed.
  - FTS is NOT shipped; each app rebuilds its own (iOS show_search FTS4 custom
    tokenizer / Android shows_fts) from the copied shows.

Recording <-> show linkage (recordings carry no show_id; it's inverted from each
show's recordings[] list):
  - orphan recordings (referenced by no show) are dropped, matching the iOS
    importer; they're unreachable in-app anyway (lookup is WHERE show_id = ?).
  - recordings shared by >1 show get one deterministic owner (first by sorted
    show_id), because recordings.identifier is the sole PK. This also makes iOS
    and Android consistent (they disagree today: iOS keeps first, Android last).

Stdlib only. Usage:
  build_catalog_db.py --stage stage02-generated-data --out catalog.db --version 2.3.0
"""
import argparse
import glob
import gzip
import json
import os
import sqlite3
import sys
import time

SRC_RANK = ["SBD", "FM", "MATRIX", "REMASTER", "AUD"]


# ---------- field derivations (mirror iOS ShowImporter) ----------

def song_list(setlist):
    if not isinstance(setlist, list):
        return None
    names = [s.get("name") for st in setlist for s in (st.get("songs") or []) if s.get("name")]
    return ",".join(names) if names else None


def member_list(lineup):
    if not isinstance(lineup, list):
        return None
    names = [m.get("name") for m in lineup if m.get("name")]
    return ",".join(names) if names else None


def cover_url(show):
    tickets = show.get("ticket_images") or []
    for side in ("front", "unknown"):
        for ti in tickets:
            if ti.get("side") == side:
                return ti.get("url")
    photos = show.get("photos") or []
    return photos[0].get("url") if photos else None


def date_parts(date):
    parts = (date or "").split("-")
    year = int(parts[0]) if len(parts) > 0 and parts[0].isdigit() else 0
    month = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 0
    ym = f"{parts[0]}-{parts[1]}" if len(parts) >= 2 else date
    return year, month, ym


def _like_escape(s):
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


# ---------- collections show_selector resolution (mirror iOS/Android) ----------

def resolve_show_ids(selector, cur):
    """Resolve a show_selector against the already-populated shows table.
    Mirrors CollectionsImporter.resolveShowIds on both platforms."""
    if not selector:
        return []
    resolved = set()
    resolved.update(selector.get("show_ids") or [])

    for date in (selector.get("dates") or []):
        cur.execute("SELECT showId FROM shows WHERE date = ?", (date,))
        resolved.update(r[0] for r in cur.fetchall())

    for rng in (selector.get("ranges") or []):
        cur.execute("SELECT showId FROM shows WHERE date >= ? AND date <= ?",
                    (rng.get("start"), rng.get("end")))
        resolved.update(r[0] for r in cur.fetchall())

    rng = selector.get("range")
    if rng:
        cur.execute("SELECT showId FROM shows WHERE date >= ? AND date <= ?",
                    (rng.get("start"), rng.get("end")))
        ids = {r[0] for r in cur.fetchall()}
        for ex in (selector.get("exclusion_ranges") or []):
            cur.execute("SELECT showId FROM shows WHERE date >= ? AND date <= ?",
                        (ex.get("from"), ex.get("to")))
            ids.difference_update(r[0] for r in cur.fetchall())
        for exd in (selector.get("exclusion_dates") or []):
            cur.execute("SELECT showId FROM shows WHERE date = ?", (exd,))
            ids.difference_update(r[0] for r in cur.fetchall())
        resolved.update(ids)

    for venue in (selector.get("venues") or []):
        cur.execute("SELECT showId FROM shows WHERE venueName LIKE ? ESCAPE '\\'",
                    (f"%{_like_escape(venue)}%",))
        resolved.update(r[0] for r in cur.fetchall())

    for year in (selector.get("years") or []):
        cur.execute("SELECT showId FROM shows WHERE year = ?", (year,))
        resolved.update(r[0] for r in cur.fetchall())

    return sorted(resolved)


# ---------- schema-driven insert ----------

def load_schema(path):
    with open(path) as f:
        return json.load(f)


def create_tables(con, schema):
    for name, spec in schema["tables"].items():
        cols = ", ".join(f'"{c["name"]}" {c["type"]}' for c in spec["columns"])
        pk = spec.get("primaryKey")
        pk_clause = f', PRIMARY KEY ({", ".join(pk)})' if pk else ""
        con.execute(f'CREATE TABLE "{name}" ({cols}{pk_clause})')


def make_inserter(con, schema, table):
    columns = [c["name"] for c in schema["tables"][table]["columns"]]
    placeholders = ",".join("?" * len(columns))
    sql = f'INSERT OR REPLACE INTO "{table}" VALUES ({placeholders})'

    def insert(row):
        con.execute(sql, tuple(row.get(c) for c in columns))
    return insert


# ---------- build ----------

def build(stage, out, version, schema_path, keep_orphans, verbose):
    now = int(time.time() * 1000)
    schema = load_schema(schema_path)

    if os.path.exists(out):
        os.remove(out)
    con = sqlite3.connect(out)
    cur = con.cursor()
    create_tables(con, schema)

    ins_show = make_inserter(con, schema, "shows")
    ins_rec = make_inserter(con, schema, "recordings")
    ins_coll = make_inserter(con, schema, "dead_collections")
    ins_ver = make_inserter(con, schema, "data_version")

    show_files = sorted(glob.glob(os.path.join(stage, "shows", "*.json")))
    if not show_files:
        sys.exit(f"error: no show files under {stage}/shows")

    # Pass 1: reverse index recordingId -> [show_id], sorted for a stable tie-break.
    rec_to_shows = {}
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
        year, month, ym = date_parts(d.get("date"))
        setlist = d.get("setlist")
        lineup = d.get("lineup")
        src = d.get("source_types") or {}
        avg = d.get("avg_rating") or 0.0
        ins_show({
            "showId": sid, "date": d.get("date"), "year": year, "month": month,
            "yearMonth": ym, "band": d.get("band", "Grateful Dead"), "url": d.get("url"),
            "venueName": d.get("venue", ""), "city": d.get("city"), "state": d.get("state"),
            "country": d.get("country") or "USA", "locationRaw": d.get("location_raw"),
            "setlistStatus": d.get("setlist_status"),
            "setlistRaw": json.dumps(setlist) if setlist is not None else None,
            "songList": song_list(setlist), "lineupStatus": d.get("lineup_status"),
            "lineupRaw": json.dumps(lineup) if lineup else None,
            "memberList": member_list(lineup), "showSequence": 1,
            "recordingsRaw": json.dumps(d.get("recordings")) if d.get("recordings") else None,
            "recordingCount": d.get("recording_count") or 0,
            "bestRecordingId": d.get("best_recording"),
            "bestSourceType": next((s for s in SRC_RANK if s in src), None),
            "averageRating": avg if avg > 0 else None,
            "totalReviews": (d.get("total_high_ratings") or 0) + (d.get("total_low_ratings") or 0),
            "coverImageUrl": cover_url(d), "createdAt": now, "updatedAt": now,
        })
        n_show += 1

    # Pass 3: recordings (one row per identifier; show_id = first referencing show)
    n_rec = n_orphan = 0
    for f in sorted(glob.glob(os.path.join(stage, "recordings", "*.json"))):
        rid = os.path.basename(f)[:-5]
        shows = rec_to_shows.get(rid)
        if not shows:
            n_orphan += 1
            if not keep_orphans:
                continue
        d = json.load(open(f))
        ins_rec({
            "identifier": rid, "show_id": shows[0] if shows else "",
            "source_type": d.get("source_type"), "rating": d.get("rating") or 0.0,
            "raw_rating": d.get("raw_rating") or 0.0, "review_count": d.get("review_count") or 0,
            "confidence": d.get("confidence") or 0.0, "high_ratings": d.get("high_ratings") or 0,
            "low_ratings": d.get("low_ratings") or 0, "taper": d.get("taper") or "",
            "source": d.get("source") or "", "lineage": d.get("lineage") or "",
            "source_type_string": d.get("source_type"), "collection_timestamp": now,
        })
        n_rec += 1

    # Pass 4: collections (resolve show_selector against the shows just inserted)
    n_coll = 0
    coll_path = os.path.join(stage, "collections.json")
    if os.path.exists(coll_path):
        cj = json.load(open(coll_path))
        items = cj.get("collections", []) if isinstance(cj, dict) else cj
        for col in (items or []):
            tags = col.get("tags") or []
            show_ids = resolve_show_ids(col.get("show_selector"), cur)
            ins_coll({
                "id": col.get("id"), "name": col.get("name", ""),
                "description": col.get("description", ""),
                "tagsJson": json.dumps(tags), "showIdsJson": json.dumps(show_ids),
                "totalShows": len(show_ids), "primaryTag": tags[0] if tags else None,
                "createdAt": now, "updatedAt": now,
            })
            n_coll += 1

    ins_ver({
        "id": 1, "dataVersion": version, "packageName": "deadly-monorepo-data",
        "versionType": "release", "description": f"Prebuilt catalog seed {version}",
        "importedAt": now, "gitCommit": None, "gitTag": f"data-v{version}",
        "buildTimestamp": None, "totalShows": n_show, "totalVenues": 0,
        "totalFiles": n_rec, "totalSizeBytes": 0,
    })

    con.commit()
    con.close()
    # VACUUM needs its own connection (cannot run inside a transaction).
    con = sqlite3.connect(out)
    con.execute("VACUUM")
    con.close()

    if n_show == 0 or n_rec == 0:
        sys.exit(f"error: refusing to publish empty seed ({n_show} shows, {n_rec} recordings)")

    raw = os.path.getsize(out)
    with open(out, "rb") as f:
        gz = len(gzip.compress(f.read(), 9))  # report-only; the .zip is produced by make

    print(f"catalog.db: {n_show} shows, {n_rec} recordings"
          + (f" ({n_orphan} orphans dropped)" if not keep_orphans else f" (incl. {n_orphan} orphans)")
          + f", {n_coll} collections")
    print(f"size: {raw/1024/1024:.1f} MB  |  ~{gz/1024/1024:.2f} MB compressed  -> {out}")
    if verbose:
        for col in con_open_count(out):
            print(f"  {col}")


def con_open_count(out):
    con = sqlite3.connect(out)
    for t in ("shows", "recordings", "dead_collections", "data_version"):
        n = con.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
        yield f"{t}: {n} rows"
    con.close()


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    default_schema = os.path.join(os.path.dirname(here), "catalog_schema.json")
    p = argparse.ArgumentParser(description="Build the prebuilt catalog.db seed from stage02.")
    p.add_argument("--stage", default="stage02-generated-data", help="stage02 directory")
    p.add_argument("--out", default="catalog.db", help="output .db path (also writes .gz)")
    p.add_argument("--version", required=True, help="data version (e.g. 2.3.0)")
    p.add_argument("--schema", default=default_schema, help="catalog_schema.json path")
    p.add_argument("--keep-orphans", action="store_true",
                   help="keep recordings no show references (default: drop, matching iOS)")
    p.add_argument("--verbose", action="store_true")
    a = p.parse_args()
    build(a.stage, a.out, a.version, a.schema, a.keep_orphans, a.verbose)


if __name__ == "__main__":
    main()
