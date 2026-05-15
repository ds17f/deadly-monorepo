#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.11"
# ///
"""Build a slim synthetic data.zip for hermetic tests.

Reads the full catalog from a source `data.zip` (default: the sibling
`dead-metadata/data.zip` next to this monorepo) and emits a minimal
schema-compatible zip containing a hand-picked set of canonical shows
plus the recordings they reference.

The output is committed at `hermetic/fixtures/synthetic/data.zip`.
Tests that want a fast-loading, deterministic catalog point the app
at this file (via the hermetic server's `data.zip` mapping) instead
of the multi-MB real catalog.

Usage:
    uv run hermetic/scripts/build_synthetic_datazip.py \
        [--source ../dead-metadata/data.zip] \
        [--out hermetic/fixtures/synthetic/data.zip]
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

# Hand-picked canonical shows. Each is recognizable to Dead listeners,
# covers a different era, and exercises different fixture shapes
# (recordings present vs. empty, single vs. multi-recording, etc.).
CANONICAL_SHOWS = [
    # 1965 — early Acid Test era, no archive recordings (edge case: empty `recordings`)
    "1965-12-04-big-nigs-house-san-jose-ca-usa",
    # 1969 — early Pigpen era at the Fillmore West, recordings present
    "1969-02-27-fillmore-west-san-francisco-ca-usa",
    # 1972 — Europe '72, London Lyceum
    "1972-05-26-the-strand-lyceum-london-england",
    # 1977 — the legendary Cornell show, multi-recording
    "1977-05-08-barton-hall-cornell-u-ithaca-ny-usa",
    # 1990 — late-Brent / Bruce era, big arena show
    "1990-03-29-nassau-coliseum-uniondale-ny-usa",
    # 1993 — Vince era, a representative late show
    "1993-05-16-sam-boyd-silver-bowl-u-n-l-v-las-vegas-nv-usa",
]


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument(
        "--source",
        type=Path,
        default=Path(__file__).resolve().parents[2].parent / "dead-metadata" / "data.zip",
        help="Source data.zip with the full catalog",
    )
    p.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "fixtures" / "synthetic" / "data.zip",
        help="Output synthetic data.zip",
    )
    args = p.parse_args()

    if not args.source.exists():
        print(f"error: source data.zip not found at {args.source}", file=sys.stderr)
        print("Provide --source pointing at a full data.zip (e.g. download from GitHub releases).", file=sys.stderr)
        return 2

    args.out.parent.mkdir(parents=True, exist_ok=True)

    show_blobs: dict[str, bytes] = {}
    recording_blobs: dict[str, bytes] = {}
    referenced_recordings: set[str] = set()

    with zipfile.ZipFile(args.source, "r") as src:
        # 1. Read each canonical show and collect its referenced recording IDs.
        for show_id in CANONICAL_SHOWS:
            name = f"shows/{show_id}.json"
            try:
                raw = src.read(name)
            except KeyError:
                print(f"warning: show not in source zip: {name}", file=sys.stderr)
                continue
            show = json.loads(raw)
            show_blobs[name] = raw
            for rec_id in show.get("recordings", []) or []:
                referenced_recordings.add(rec_id)

        # 2. Read the recording JSONs that were referenced.
        for rec_id in referenced_recordings:
            name = f"recordings/{rec_id}.json"
            try:
                recording_blobs[name] = src.read(name)
            except KeyError:
                print(f"warning: referenced recording not in source zip: {name}", file=sys.stderr)

    # 3. Build a synthetic manifest (preserve `package.version` shape; mark as synthetic).
    synthetic_manifest = {
        "package": {
            "name": "Dead Archive Metadata (synthetic test fixture)",
            "version": "synthetic-1.0.0",
            "version_type": "synthetic",
            "description": (
                "Hand-picked subset of the real catalog, for hermetic test fixtures. "
                "Loads in milliseconds; deterministic show set; do not ship to users."
            ),
            "created": datetime.now(timezone.utc).isoformat(),
            "generator": "hermetic/scripts/build_synthetic_datazip.py",
        },
        "contents": {
            "stage2_data": {
                "description": "Synthetic test subset",
                "files": {
                    "collections.json": "One synthetic collection containing all included shows",
                    "recordings/": "Recording JSONs referenced by the included shows",
                    "shows/": "Hand-picked canonical Grateful Dead shows",
                },
            },
        },
        "statistics": {
            "total_shows": len(show_blobs),
            "total_recordings": len(recording_blobs),
        },
        "data_sources": ["Subset of dead-metadata data.zip"],
        "usage": {
            "hermetic_tests": "Served by WireMock as the data.zip response for fast deterministic test runs",
        },
    }

    # 4. Build a minimal collections.json: one synthetic collection that includes
    #    every show we packaged, so tests can exercise "open a collection" flows.
    synthetic_collections = {
        "collections": [
            {
                "id": "synthetic-canonical-shows",
                "name": "Canonical shows (synthetic test set)",
                "description": (
                    "The hand-picked shows in this hermetic test fixture, "
                    "spanning 1965 through 1993."
                ),
                "tags": ["synthetic", "test-fixture"],
                "show_selector": {"dates": [s.split("-")[0:3] and "-".join(s.split("-")[:3]) for s in CANONICAL_SHOWS]},
                "show_ids": list(show_blobs.keys() and [name[len("shows/"):-len(".json")] for name in show_blobs]),
                "total_shows": len(show_blobs),
            }
        ]
    }

    # 5. Write the zip.
    with zipfile.ZipFile(args.out, "w", compression=zipfile.ZIP_DEFLATED) as out:
        out.writestr("manifest.json", json.dumps(synthetic_manifest, indent=2))
        out.writestr("collections.json", json.dumps(synthetic_collections, indent=2))
        for name, raw in sorted(show_blobs.items()):
            out.writestr(name, raw)
        for name, raw in sorted(recording_blobs.items()):
            out.writestr(name, raw)

    size = args.out.stat().st_size
    print(f"Wrote {args.out}", file=sys.stderr)
    print(
        f"  shows: {len(show_blobs)}  recordings: {len(recording_blobs)}  "
        f"size: {size:,} bytes ({size / 1024:.1f} KB)",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
