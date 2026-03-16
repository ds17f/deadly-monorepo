# Dead Archive Metadata Repository

This repository contains the complete metadata collection and processing pipeline for the Dead Archive project. It transforms raw data from multiple sources into a comprehensive, normalized database suitable for mobile app consumption.

## Overview

**Purpose**: Separate metadata processing from the main Android app repository  
**Coverage**: 2,200+ shows (1965-1995), 484+ venues, 550+ songs with comprehensive ratings data  
**Status**: Production V1 system actively used by Android app, ready for V2 architecture integration  
**Pipeline**: 4-stage service-oriented architecture with 6,812+ lines of Python code across 13 specialized scripts

## Architecture

The pipeline follows a 4-stage service-oriented architecture with clear separation between raw collected data and processed derivatives:

### Stage 1: Data Collection → `stage01-collected-data/`
- **Archive.org**: Complete recording metadata collection (17,790+ recordings)
- **Jerry Garcia Shows**: Comprehensive show database from jerrygarcia.com (2,313+ shows)

### Stage 2: Data Generation → `stage02-generated-data/` + `stage02-processed-data/`
- **Archive.org Processing**: Processes cached data into ratings and show aggregations
- **Jerry Garcia Integration**: Integrates JG shows with recording ratings
- **Collections Processing**: Resolves collection selectors to show IDs and adds metadata
- **Search Data Generation**: Creates denormalized search indexes for mobile optimization

### Stage 3: Search Data Generation → `stage03-search-data/`
- **Search Tables**: Generates optimized search indexes for mobile app consumption
- **Collections Search**: Collection-based search functionality integration

### Stage 4: Deployment
- **Data Packaging**: Bundles processed data into compressed mobile-ready package

## Repository Structure

```
dead-metadata/
├── scripts/                           # Python processing scripts
│   ├── 01-collect-data/              # Stage 1: Data Collection
│   │   ├── collect_archive_metadata.py      # Archive.org API collection (661 lines)
│   │   └── collect_jerrygarcia_com_shows.py # Complete show database (1,100+ lines)
│   ├── 02-generate-data/             # Stage 2: Data Generation
│   │   ├── generate_archive_products.py     # Process Archive cache (362 lines)
│   │   ├── integrate_jerry_garcia_shows.py  # Integrate JG shows with ratings
│   │   └── process_collections.py           # Collections processing (570 lines)
│   ├── 03-search-data/               # Stage 3: Search Generation
│   │   └── generate_search_tables.py        # Search optimization
│   ├── shared/                       # Shared utilities and models
│   │   ├── models.py                 # Data models and structures
│   │   └── recording_utils.py        # Recording processing utilities
│   └── requirements.txt              # Python dependencies
├── stage00-created-data/             # Pre-defined data and configurations
│   └── dead_collections.json         # Collection definitions
├── stage01-collected-data/           # Raw collected data
│   ├── archive/                      # Archive.org metadata cache
│   └── jerrygarcia/                  # Jerry Garcia show database
├── stage02-generated-data/           # Generated data products
│   ├── shows/                        # Individual show files with ratings
│   ├── collections.json             # Processed collections data
│   └── recording_ratings.json       # Comprehensive ratings data
├── stage02-processed-data/           # Legacy setlist processing (if used)
│   ├── venues.json                   # Venue reference database
│   ├── songs.json                    # Song reference database
│   └── setlists.json                 # Processed setlist data
├── stage03-search-data/              # Search optimization
│   ├── shows_index.json             # Show search index
│   ├── collections.json             # Collections search data
│   ├── venues.json                  # Venue search data
│   └── songs.json                   # Song search data
├── docs/                            # Documentation
├── data.zip                         # Final package for Android app
├── Makefile                         # Build automation
└── README.md                        # This file
```

## Quick Start

### Complete Pipeline
```bash
make all                          # Run complete 4-stage pipeline (5-7 hours first time)
```

### Individual Stages
```bash
make stage01-collect-data         # Stage 1: Data Collection (5-7 hours)
make stage02-generate-data        # Stage 2: Data Generation (fast)
make stage03-generate-search-data # Stage 3: Search Generation (fast)
```

### Individual Steps
```bash
# Stage 1: Data Collection
make collect-archive-data         # Collect Archive.org metadata (2-3 hours)
make collect-jerrygarcia-shows    # Collect show database (3-4 hours)

# Stage 2: Data Generation
make generate-recording-ratings   # Generate ratings from cache
make integrate-shows              # Integrate JG shows with ratings
make process-collections          # Process collections and add to shows

# Stage 3: Search Generation
make generate-search-data         # Generate search tables for mobile app

# Utilities
make clean                        # Clean generated data
```

## Output Files

The pipeline produces these final data products:

### Stage 2 Outputs (`stage02-generated-data/`)
1. **`recording_ratings.json`** - Archive.org ratings with comprehensive review statistics
2. **`collections.json`** - Processed collections with resolved show memberships
3. **`shows/`** - Individual show files with ratings, collections, and metadata

### Stage 3 Outputs (`stage03-search-data/`)
1. **`shows_index.json`** - Optimized show search index for mobile apps
2. **`collections.json`** - Collection search data with aliases and previews
3. **`venues.json`** - Venue search index with geographical data
4. **`songs.json`** - Song search index with aliases and statistics

### Final Package
1. **`data.zip`** - Compressed package containing all processed data for Android app deployment

## Integration with Main App

The main Android app repository imports the processed metadata:

```bash
# In main dead repository:
make import-metadata    # Copy data.zip from ../dead-metadata/
make build             # Build app with bundled metadata
```

## Key Features

✅ **Stage-Based Architecture**: Clear separation of concerns with 4-stage pipeline  
✅ **Collections Processing**: Automated resolution of collection selectors to show IDs  
✅ **Search Optimization**: Denormalized search indexes for fast mobile queries  
✅ **Resume Capability**: Collection stages can be interrupted and resumed  
✅ **Quality Assurance**: 99.995% song match rate, 100% venue identification  
✅ **Production Ready**: Battle-tested pipeline used by V1 Android app  
✅ **V2 Integration**: Data structure ready for V2 database architecture

## Documentation

- **[CLAUDE.md](CLAUDE.md)** - Comprehensive development guide and implementation details
- **[docs/](docs/)** - Additional technical documentation
- **Makefile targets** - Run `make help` for all available commands

## Data Sources

- **Archive.org**: 17,790+ recording metadata files, ratings, and reviews
- **jerrygarcia.com**: Definitive show database with complete setlists, lineups, venues (1965-1995)
- **CS.CMU.EDU**: Historical setlist database (1972-1995) - Legacy pipeline only
- **GDSets.com**: Setlist data and concert images - Legacy pipeline only

## Performance

- **Full Pipeline**: 5-7 hours (includes complete data collection)
- **Stage 1 Collection**: 5-7 hours (Archive.org + Jerry Garcia data)
- **Stage 2 Generation**: ~10 seconds (processing cached data)
- **Stage 3 Search**: ~5 seconds (search index generation)
- **Final Output**: 2-5MB compressed for mobile deployment
- **Cache Storage**: ~500MB for Archive.org metadata cache

## Collections System

The pipeline includes a comprehensive collections processing system:

- **Pre-defined Collections**: 25+ curated collections in `stage00-created-data/dead_collections.json`
- **Automatic Resolution**: Date ranges and selectors automatically resolved to show IDs
- **Search Integration**: Collections optimized for mobile search with aliases and previews
- **Failure Analysis**: Detailed reporting when collections fail to match shows

## Development

### Requirements
- Python 3.8+ with virtual environment support
- ~5GB working storage for full pipeline
- Internet connection for data collection stages

### Environment Setup
```bash
# Environment is automatically managed by Makefile
make collect-archive-data  # Creates .venv and installs dependencies
```

---

**Maintainer**: Dead Archive Development Team  
**Last Updated**: January 2025  
**Integration**: Feeds V1 Android app, ready for V2 architecture