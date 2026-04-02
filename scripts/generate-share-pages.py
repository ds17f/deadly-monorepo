#!/usr/bin/env python3
"""Generate per-show static HTML pages for the share site.

Each page includes show-specific OpenGraph meta tags so that social media
crawlers render a rich card, plus the same deep-link landing page UI served
by web/index.html.

Usage:
    python scripts/generate-share-pages.py

Reads:  ui/data/shows/*.json
Writes: web/shows/{id}/index.html
"""

import json
import os
import glob
import html
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SHOWS_DIR = REPO_ROOT / "ui" / "data" / "shows"
TEMPLATE_PATH = REPO_ROOT / "web" / "index.html"
OUTPUT_DIR = REPO_ROOT / "web" / "shows"

# Base URL for absolute OG references (logo fallback, canonical URLs).
# Set SITE_URL env var to match the target environment; defaults to production.
SITE_URL = os.environ.get("SITE_URL", "https://thedeadly.app").rstrip("/")


def format_date(date_str: str) -> str:
    """Format YYYY-MM-DD as a human-readable date."""
    from datetime import date

    y, m, d = date_str.split("-")[:3]
    dt = date(int(y), int(m), int(d))
    return dt.strftime("%B %-d, %Y")


def resolve_cover_image(show: dict) -> str:
    """Pick the best image for the OG card, matching the Next.js logic."""
    for ticket in show.get("ticket_images", []):
        if ticket.get("side") == "front":
            return ticket["url"]
    for ticket in show.get("ticket_images", []):
        if ticket.get("side") == "unknown":
            return ticket["url"]
    photos = show.get("photos", [])
    if photos:
        return photos[0]["url"]
    return f"{SITE_URL}/logo.png"


def build_description(show: dict) -> str:
    """Build an OG description matching the Next.js generateMetadata logic."""
    parts = [f"Grateful Dead at {show['venue']}, {show['location_raw']}"]
    rc = show.get("recording_count", 0)
    if rc > 0:
        parts.append(f"{rc} recordings")
    avg = show.get("avg_rating", 0)
    if avg > 0:
        parts.append(f"{avg:.1f}\u2605")
    review = show.get("ai_show_review") or {}
    summary = review.get("summary")
    if summary:
        parts.append(summary)
    return " \u2014 ".join(parts)


def generate_share_page(show: dict, template: str) -> str:
    """Generate a share page with show-specific OG tags."""
    show_id = show["show_id"]
    date_str = format_date(show["date"])
    title = f"Grateful Dead {date_str} \u2014 {show['venue']} | The Deadly"
    description = build_description(show)
    image_url = resolve_cover_image(show)
    t = html.escape(title, quote=True)
    d = html.escape(description, quote=True)
    img = html.escape(image_url, quote=True)

    og_tags = (
        f'  <meta property="og:title" content="{t}">\n'
        f'  <meta property="og:description" content="{d}">\n'
        f'  <meta property="og:type" content="article">\n'
        f'  <meta property="og:site_name" content="The Deadly">\n'
        f'  <meta property="og:image" content="{img}">\n'
        f'  <meta name="twitter:card" content="summary_large_image">\n'
        f'  <meta name="twitter:title" content="{t}">\n'
        f'  <meta name="twitter:description" content="{d}">\n'
        f'  <meta name="twitter:image" content="{img}">'
    )

    # Replace the generic OG tags in the template with show-specific ones
    # Remove existing generic OG/twitter meta tags and inject show-specific ones
    lines = template.split("\n")
    out_lines = []
    og_injected = False
    for line in lines:
        stripped = line.strip()
        # Skip generic OG and twitter meta tags
        if any(
            stripped.startswith(f'<meta property="og:{p}"')
            for p in ("title", "description", "type", "url")
        ):
            if not og_injected:
                out_lines.append(og_tags)
                og_injected = True
            continue
        # Replace generic title
        if stripped.startswith("<title>"):
            out_lines.append(f"  <title>{t}</title>")
            continue
        # Replace generic description
        if (
            stripped.startswith('<meta name="description"')
            and "og:" not in stripped
        ):
            out_lines.append(f'  <meta name="description" content="{d}">')
            continue
        out_lines.append(line)

    if not og_injected:
        # Fallback: inject before </head>
        result = "\n".join(out_lines)
        result = result.replace("</head>", og_tags + "\n</head>")
        return result

    return "\n".join(out_lines)


def main():
    template = TEMPLATE_PATH.read_text()
    show_files = sorted(glob.glob(str(SHOWS_DIR / "*.json")))

    print(f"Generating share pages for {len(show_files)} shows...")

    for path in show_files:
        with open(path) as f:
            show = json.load(f)

        show_id = show["show_id"]
        out_dir = OUTPUT_DIR / show_id
        out_dir.mkdir(parents=True, exist_ok=True)

        page = generate_share_page(show, template)
        (out_dir / "index.html").write_text(page)

    print(f"Done. Generated {len(show_files)} share pages in {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
