# CLAUDE.md — Data Pipeline

This directory contains the Grateful Dead concert metadata pipeline, part of the `deadly-monorepo`.

## Overview

**Coverage**: 2,200+ shows (1965-1995), 484+ venues, 550+ songs with comprehensive ratings data
**Pipeline**: 4-stage architecture with 13 Python scripts producing `data.zip` consumed by iOS, Android, and web

## Directory Structure

- `stage00-created-data/` — AI reviews + collections (committed, source of truth)
- `stage01-collected-data/` — API cache from Archive.org + jerrygarcia.com (gitignored, stored as GitHub Release `data-stage01-v1`)
- `stage02-generated-data/` — Generated from stage00 + stage01 (gitignored, built by CI)
- `scripts/` — Python pipeline scripts
- `version` — Single source of truth for the current data version, read by all platforms and CI

## Common Workflows

All commands should be run from the **monorepo root** using `make data-*` targets:

```bash
# Download released data for local dev
make data-download VERSION=2.3.0

# Pipeline development: regenerate stage02 from stage00+stage01
make data-download-stage01
make data-generate

# Package data.zip from stage02
make data-package

# Release new data version (tags, CI builds and publishes)
make data-release VERSION=2.4.0

# Re-collect from APIs (rare, takes hours)
make data-collect
```

Or run directly from `data/`:
```bash
cd data && make help
```

## Data Release Process

1. Make changes to `stage00-created-data/` or scripts
2. Test locally: `make data-generate && make data-package`
3. Release: `make data-release VERSION=X.Y.Z`
4. CI (`.github/workflows/data-release.yml`) builds and publishes `data.zip` to the `data-vX.Y.Z` release
5. Update `data/version` — deploy-pages.yml and mobile apps read this to fetch the pinned release

## Architecture

### Stage 1: Data Collection → `stage01-collected-data/`
- `scripts/01-collect-data/collect_archive_metadata.py` — Archive.org API (2-3 hours)
- `scripts/01-collect-data/collect_jerrygarcia_com_shows.py` — jerrygarcia.com (3-4 hours)

### Stage 2: Data Generation → `stage02-generated-data/`
- `scripts/02-generate-data/process_recordings_minimal.py` — Recording ratings from cache
- `scripts/02-generate-data/integrate_jerry_garcia_shows.py` — Show integration
- `scripts/02-generate-data/process_collections.py` — Collections processing

### Stage 4: Packaging
- `scripts/package_datazip.py` — Bundles stage02 into `data.zip`

## Key Details

- AI review data in `stage00-created-data/ai-reviews/` is the durable source of truth (9,888 recording reviews, 1,960 show reviews)
- Stage02 generation scripts merge reviews from stage00 automatically
- Python deps: `scripts/requirements.txt` (requests, lxml, beautifulsoup4, python-dateutil)
- All scripts support `--verbose` flag
- Data processing is idempotent
