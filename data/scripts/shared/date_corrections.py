"""
Recording date corrections for misdated Archive.org items.

Archive.org sometimes has the wrong `date` on a recording, so it does not
attach to the correct jerrygarcia.com show. The corrections are kept in
stage00-created-data/recording-date-corrections.json (committed, source of
truth). Stage02 scripts call apply_date_correction() on each raw cache record
before they use `normalized_date`.
"""

import json
from functools import lru_cache
from pathlib import Path

CORRECTIONS_FILE = Path(__file__).resolve().parents[2] / "stage00-created-data" / "recording-date-corrections.json"


@lru_cache(maxsize=1)
def load_date_corrections() -> dict[str, str]:
    """Return {identifier: corrected YYYY-MM-DD date}."""
    if not CORRECTIONS_FILE.exists():
        return {}
    with open(CORRECTIONS_FILE) as f:
        data = json.load(f)
    return {identifier: entry["date"] for identifier, entry in data.get("corrections", {}).items()}


def apply_date_correction(raw_recording: dict) -> dict:
    """Set `normalized_date` from the corrections file, if the recording has a correction."""
    corrected = load_date_corrections().get(raw_recording.get("identifier"))
    if corrected:
        raw_recording["normalized_date"] = corrected
    return raw_recording
