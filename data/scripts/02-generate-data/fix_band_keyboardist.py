#!/usr/bin/env python3
"""
Fix the keyboardist slot in AI-review `band_performance` for post-Brent shows.

The review generator emits a fixed-key player breakdown
{Jerry, Phil, Bob, Brent, Mickey, Billy} on EVERY show, so shows after Brent
Mydland's death (1990-07-26) still carry a "Brent" keyboard slot even though the
keyboardist was Vince Welnick (from 1990-09-07) and/or Bruce Hornsby (regular
1990-09-15 .. 1992-03-24). See data/scripts/shared/hornsby.py.

This rewrites the keyboard slot in stage00 (the durable source of truth):
  - relabels the slot to "Keys" (Hornsby + Welnick co-keyboardist era) or
    "Vince" (Welnick alone), per the authoritative timeline;
  - collapses any stray keyboard keys (empty "Brent" beside a real "Vince",
    "Bruce"/"Vince Welnick" variants, a hallucinated "Keith") into that one slot;
  - scrubs lingering "Brent" name credits out of the slot's text.

Shows on/before 1990-07-26 are untouched. Dry-run by default; pass --apply to write.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "shared"))
from hornsby import BRENT_DEATH, keyboardist_label  # noqa: E402

# Keys in band_performance that represent the keyboard slot (normalized, lowercase).
KEYBOARD_KEYS = {
    "brent", "brent mydland", "vince", "vince welnick",
    "bruce", "bruce hornsby", "keys", "keith", "keith godchaux",
}

# Keyboard keys that are factually WRONG on a post-Brent show. We only touch a
# review when one of these is present; already-correct slots (Vince / Bruce /
# Keys) are left exactly as written.
WRONG_KEYBOARD_KEYS = {"brent", "brent mydland", "keith", "keith godchaux"}

SHOWS_DIR = "data/stage00-created-data/ai-reviews/shows"
DATE_RE = re.compile(r"(\d{4})-(\d{2})-(\d{2})-")


def fix_band_performance(bp: dict, label: str) -> dict | None:
    """Return a corrected band_performance dict, or None if no change needed.

    Only acts when a factually-wrong keyboard key (Brent/Keith) is present on a
    post-Brent show. The slot is relabeled to the era label and any sibling
    keyboard slots are merged into it; the AI's description text is preserved
    verbatim (scrubbing it mangled correct memorial references like
    'honoring Brent's legacy')."""
    if not isinstance(bp, dict):
        return None
    if not any(k.strip().lower() in WRONG_KEYBOARD_KEYS for k in bp):
        return None  # keyboard slot already correct — don't touch it
    kb_values, first_kb_pos, rebuilt = [], None, {}
    for k, v in bp.items():
        if k.strip().lower() in KEYBOARD_KEYS:
            if first_kb_pos is None:
                first_kb_pos = len(rebuilt)
            if isinstance(v, str) and v.strip():
                kb_values.append(v.strip())
        else:
            rebuilt[k] = v
    items = list(rebuilt.items())
    items.insert(first_kb_pos, (label, " ".join(kb_values)))
    new_bp = dict(items)
    return new_bp if new_bp != bp else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="write changes (default: dry-run)")
    ap.add_argument("--limit", type=int, default=0, help="max before/after samples to print")
    args = ap.parse_args()

    files = subprocess.check_output(["git", "ls-files", SHOWS_DIR]).decode().split()
    changed = relabeled = collapsed = scrubbed = 0
    samples = 0
    for p in files:
        m = DATE_RE.search(p)
        if not m:
            continue
        date = f"{m.group(1)}-{m.group(2)}-{m.group(3)}"
        if date <= BRENT_DEATH:
            continue
        label = keyboardist_label(date)
        if label is None:
            continue
        d = json.loads(Path(p).read_text())
        bp = d.get("band_performance")
        new_bp = fix_band_performance(bp, label)
        if new_bp is None:
            continue
        changed += 1
        old_keys = [k for k in bp if k.strip().lower() in KEYBOARD_KEYS]
        if len(old_keys) > 1:
            collapsed += 1
        relabeled += 1
        if "Brent" in new_bp.get(label, ""):
            scrubbed += 1  # residual: value still names Brent (memorial or stray)
            print(f"  RESIDUAL Brent in value [{date}]: {new_bp[label]!r}")
        if args.limit and samples < args.limit:
            samples += 1
            print(f"\n[{date}] {Path(p).name}  → label '{label}'")
            print(f"  BEFORE keys: {list(bp.keys())}")
            for k in old_keys:
                print(f"    {k!r}: {bp[k]!r}")
            print(f"  AFTER  {label!r}: {new_bp[label]!r}")
        if args.apply:
            d["band_performance"] = new_bp
            Path(p).write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n")

    print(f"\n{'APPLIED' if args.apply else 'DRY-RUN'}: {changed} reviews changed "
          f"({relabeled} relabeled, {collapsed} collapsed multi-key, {scrubbed} text-scrubbed)")


if __name__ == "__main__":
    main()
