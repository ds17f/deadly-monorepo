#!/usr/bin/env python3
"""
Compare two stage01 Archive.org caches and summarize what changed.

Use it after `make collect-archive-data` to see which recordings are new
before you upload the cache and cut a data release.

Defaults:
  --old archive/                        (the published cache, from `make download-stage01`)
  --new stage01-collected-data/archive  (the fresh collection)

Reports:
  - New recordings, grouped by date. A date with no recording in the old
    cache is marked NEW DATE.
  - Recordings that are in the old cache but not in the new one. Usually
    these are fetch failures (timeouts) in the new run. Re-fetch them before
    you upload, or the upload drops them.

Usage:
    python scripts/diff_stage01.py
    python scripts/diff_stage01.py --format markdown > new-recordings.md
    python scripts/diff_stage01.py --old /path/to/old --new /path/to/new
"""

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.append(str(Path(__file__).parent))
from shared.date_corrections import apply_date_correction


def recording_ids(cache_dir: Path) -> set[str]:
    return {p.stem for p in cache_dir.glob("*.json") if p.name != "progress.json"}


def text(value) -> str:
    """Archive.org fields can be a string or a list of strings."""
    if isinstance(value, list):
        return "; ".join(str(v) for v in value)
    return str(value or "")


def load_summary(cache_dir: Path, identifier: str) -> dict:
    with open(cache_dir / f"{identifier}.json") as f:
        data = apply_date_correction(json.load(f))
    meta = data.get("raw_metadata", {})
    return {
        "identifier": identifier,
        "date": data.get("normalized_date") or meta.get("date", "unknown"),
        "venue": text(meta.get("venue", "")),
        "location": text(meta.get("coverage", "")),
        "source": text(meta.get("subject", "")),
        "transferer": text(meta.get("transferer", "")),
        "added": text(meta.get("addeddate", "")),
    }


def date_of(cache_dir: Path, identifier: str) -> str:
    with open(cache_dir / f"{identifier}.json") as f:
        return apply_date_correction(json.load(f)).get("normalized_date", "unknown")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--old", default="archive", help="Old (published) cache directory")
    parser.add_argument("--new", default="stage01-collected-data/archive", help="New cache directory")
    parser.add_argument("--format", choices=["text", "markdown"], default="text")
    args = parser.parse_args()

    old_dir, new_dir = Path(args.old), Path(args.new)
    for d in (old_dir, new_dir):
        if not d.is_dir():
            print(f"Directory not found: {d}", file=sys.stderr)
            return 1

    old_ids, new_ids = recording_ids(old_dir), recording_ids(new_dir)
    added = sorted(new_ids - old_ids)
    missing = sorted(old_ids - new_ids)
    old_dates = {date_of(old_dir, i) for i in old_ids}

    by_date: dict[str, list[dict]] = defaultdict(list)
    for identifier in added:
        rec = load_summary(new_dir, identifier)
        by_date[rec["date"]].append(rec)
    new_dates = sorted(d for d in by_date if d not in old_dates)

    md = args.format == "markdown"
    h = (lambda s: f"## {s}") if md else (lambda s: f"{s}\n{'=' * len(s)}")

    print("# Stage01 recording diff\n" if md else "")
    print(f"- Old cache: `{old_dir}` ({len(old_ids)} recordings)" if md else f"Old cache: {old_dir} ({len(old_ids)} recordings)")
    print(f"- New cache: `{new_dir}` ({len(new_ids)} recordings)" if md else f"New cache: {new_dir} ({len(new_ids)} recordings)")
    print(f"- Added: {len(added)} recordings on {len(by_date)} dates ({len(new_dates)} new dates)" if md
          else f"Added:     {len(added)} recordings on {len(by_date)} dates ({len(new_dates)} new dates)")
    print(f"- Missing from new: {len(missing)}" if md else f"Missing:   {len(missing)}")
    print()

    if new_dates:
        print(h("New dates (no recording in old cache)"))
        print()
        for d in new_dates:
            print(f"- {d}" if md else f"  {d}")
        print()

    if added:
        print(h("Added recordings"))
        print()
        for d in sorted(by_date):
            recs = by_date[d]
            place = ", ".join(x for x in (recs[0]["venue"], recs[0]["location"]) if x)
            flag = " — NEW DATE" if d in new_dates else ""
            print(f"### {d} {place}{flag}" if md else f"{d}  {place}{flag}")
            for r in recs:
                extra = "; ".join(x for x in (r["source"], r["transferer"], f"added {r['added']}" if r["added"] else "") if x)
                print(f"- `{r['identifier']}` ({extra})" if md else f"    {r['identifier']}  ({extra})")
            if md:
                print()
        print()

    if missing:
        print(h("Missing from new cache (likely fetch failures — re-fetch before upload)"))
        print()
        for i in missing:
            print(f"- `{i}`" if md else f"  {i}")
        print()

    return 0


if __name__ == "__main__":
    sys.exit(main())
