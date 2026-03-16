#!/usr/bin/env python3
"""
Extract AI Reviews to stage00-created-data

One-time migration tool that extracts ai_review objects from stage02 recording files
and ai_show_review objects from stage02 show files into stage00-created-data/ai-reviews/
as the durable source of truth.

These review files are expensive to generate (days of LLM processing) and should not
be destroyed by make clean or pipeline regeneration.

Usage:
    python scripts/extract_ai_reviews.py
    python scripts/extract_ai_reviews.py --verbose
    python scripts/extract_ai_reviews.py --dry-run
"""

import json
import argparse
import logging
from pathlib import Path

logger = logging.getLogger(__name__)


def extract_recording_reviews(recordings_dir: Path, output_dir: Path, dry_run: bool = False) -> dict:
    """Extract ai_review objects from stage02 recording files."""
    output_dir.mkdir(parents=True, exist_ok=True)

    stats = {"extracted": 0, "skipped": 0, "total": 0, "errors": 0}

    recording_files = sorted(recordings_dir.glob("*.json"))
    stats["total"] = len(recording_files)

    for recording_file in recording_files:
        try:
            with open(recording_file) as f:
                data = json.load(f)

            ai_review = data.get("ai_review")
            if not ai_review:
                stats["skipped"] += 1
                continue

            recording_id = recording_file.stem
            output_file = output_dir / f"{recording_id}.json"

            if not dry_run:
                with open(output_file, "w") as f:
                    json.dump(ai_review, f, indent=2, ensure_ascii=False)

            stats["extracted"] += 1
            logger.debug(f"Extracted recording review: {recording_id}")

        except Exception as e:
            stats["errors"] += 1
            logger.warning(f"Error processing {recording_file.name}: {e}")

    return stats


def extract_show_reviews(shows_dir: Path, output_dir: Path, dry_run: bool = False) -> dict:
    """Extract ai_show_review objects from stage02 show files."""
    output_dir.mkdir(parents=True, exist_ok=True)

    stats = {"extracted": 0, "skipped": 0, "total": 0, "errors": 0}

    show_files = sorted(shows_dir.glob("*.json"))
    stats["total"] = len(show_files)

    for show_file in show_files:
        try:
            with open(show_file) as f:
                data = json.load(f)

            ai_show_review = data.get("ai_show_review")
            if not ai_show_review:
                stats["skipped"] += 1
                continue

            show_id = show_file.stem
            output_file = output_dir / f"{show_id}.json"

            if not dry_run:
                with open(output_file, "w") as f:
                    json.dump(ai_show_review, f, indent=2, ensure_ascii=False)

            stats["extracted"] += 1
            logger.debug(f"Extracted show review: {show_id}")

        except Exception as e:
            stats["errors"] += 1
            logger.warning(f"Error processing {show_file.name}: {e}")

    return stats


def main():
    parser = argparse.ArgumentParser(
        description="Extract AI reviews from stage02 to stage00-created-data"
    )
    parser.add_argument(
        "--recordings-dir",
        default="stage02-generated-data/recordings",
        help="Source directory for recording files",
    )
    parser.add_argument(
        "--shows-dir",
        default="stage02-generated-data/shows",
        help="Source directory for show files",
    )
    parser.add_argument(
        "--output-dir",
        default="stage00-created-data/ai-reviews",
        help="Output directory for extracted reviews",
    )
    parser.add_argument("--verbose", action="store_true", help="Enable verbose logging")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be extracted without writing files",
    )

    args = parser.parse_args()

    # Setup logging
    level = logging.DEBUG if args.verbose else logging.INFO
    logging.basicConfig(level=level, format="%(asctime)s - %(levelname)s - %(message)s")

    recordings_dir = Path(args.recordings_dir)
    shows_dir = Path(args.shows_dir)
    output_base = Path(args.output_dir)

    if args.dry_run:
        logger.info("DRY RUN - no files will be written")

    # Extract recording reviews
    logger.info(f"Extracting recording reviews from {recordings_dir}...")
    rec_output = output_base / "recordings"
    rec_stats = extract_recording_reviews(recordings_dir, rec_output, dry_run=args.dry_run)
    logger.info(
        f"Recordings: {rec_stats['extracted']} extracted, "
        f"{rec_stats['skipped']} skipped (no review), "
        f"{rec_stats['errors']} errors, "
        f"{rec_stats['total']} total"
    )

    # Extract show reviews
    logger.info(f"Extracting show reviews from {shows_dir}...")
    show_output = output_base / "shows"
    show_stats = extract_show_reviews(shows_dir, show_output, dry_run=args.dry_run)
    logger.info(
        f"Shows: {show_stats['extracted']} extracted, "
        f"{show_stats['skipped']} skipped (no review), "
        f"{show_stats['errors']} errors, "
        f"{show_stats['total']} total"
    )

    # Summary
    total_extracted = rec_stats["extracted"] + show_stats["extracted"]
    logger.info(f"Total extracted: {total_extracted} review files to {output_base}")

    if not args.dry_run:
        logger.info(f"Recording reviews: {rec_output}")
        logger.info(f"Show reviews: {show_output}")


if __name__ == "__main__":
    main()
