# Grateful Dead Database Pipeline

A comprehensive 4-stage system for collecting, processing, and integrating Grateful Dead concert data from authoritative sources into mobile-optimized formats.

## System Overview

The pipeline creates a complete database of Grateful Dead concerts by combining definitive show information from JerryGarcia.com with recording quality data from Archive.org, plus a comprehensive collections processing system. The system produces mobile-ready data with search optimization for the Dead Archive application.

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  JerryGarcia    │    │   Archive.org   │    │   Collections   │
│     .com        │    │   Recordings    │    │   Definitions   │
│                 │    │                 │    │                 │
│ • Show Details  │    │ • Ratings       │    │ • Date Ranges   │
│ • Setlists      │    │ • Reviews       │    │ • Selectors     │
│ • Lineups       │    │ • Source Types  │    │ • Metadata      │
│ • Venues        │    │ • Quality Data  │    │ • Tags/Aliases  │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          ▼                      ▼                      ▼
     COLLECTION              COLLECTION             PRE-DEFINED
          │                      │                      │
          ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    STAGE 1: Collection                         │
│                  stage01-collected-data/                       │
│                                                                 │
│  jerrygarcia/           archive/              stage00-created-  │
│  ├── 2,313+ shows       ├── 17,790+          data/             │
│  └── complete data      └── recordings       └── collections   │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
                 INTEGRATION
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                   STAGE 2: Generation                          │
│                stage02-generated-data/                         │
│                                                                 │
│  shows/                 collections.json       recordings.json   │
│  ├── 2,313+ integrated  ├── resolved           ├── ratings     │
│  ├── with recordings    └── collections        └── + tracks    │
│  └── + collection tags                             metadata    │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
               SEARCH OPTIMIZATION
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                   STAGE 3: Search Data                         │
│                 stage03-search-data/                           │
│                                                                 │
│  shows_index.json   collections.json   venues.json  songs.json │
│  └── mobile search  └── search aliases └── geo data └── stats  │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
                  DEPLOYMENT
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    STAGE 4: Packaging                          │
│                                                                 │
│                      data.zip                                  │
│                 └── mobile package                             │
└─────────────────────────────────────────────────────────────────┘
```

## Data Sources

### JerryGarcia.com
**Primary Authority for Show Information**

- **Coverage**: Complete Grateful Dead history (1965-1995)
- **Quality**: Single authoritative source, manually curated
- **Content**:
  - Complete setlists with song sequences
  - Segue notation (>, →) showing song connections  
  - Band lineups with member names and instruments
  - Venue information with geographic data
  - Supporting acts and special guests
  - Show timing (early/late show detection)

### Archive.org
**Recording Quality and Community Data**

- **Coverage**: 17,790+ concert recordings with metadata
- **Quality**: Community-driven ratings and reviews
- **Content**:
  - Recording quality ratings (0-5 stars)
  - Source type classification (SBD, AUD, MATRIX, FM)
  - Review counts and confidence metrics
  - Technical metadata (format, lineage, etc.)

### Collections Definitions
**Curated Show Groupings**

- **Coverage**: 25+ pre-defined collections in `stage00-created-data/dead_collections.json`
- **Types**: Era-based, venue-based, official releases, special events
- **Features**:
  - Date range selectors with exclusions
  - Specific date lists
  - Automatic resolution to show IDs
  - Search aliases and metadata

## Stage 1: Data Collection

### JerryGarcia.com Collection
**Script**: `scripts/01-collect-data/collect_jerrygarcia_com_shows.py`

**Features**:
- Comprehensive crawl of entire show database
- Built-in error recovery and retry logic
- Venue data recovery system for incomplete records
- Progress tracking with resume capability
- Rate limiting for respectful server usage

**Key Capabilities**:
```
Show Data Recovery:
├── Primary extraction from show pages
├── Filename parsing for missing venue data
├── Cross-reference matching with other shows
└── Automatic data completion and validation
```

**Output**: Individual JSON files for each show in `stage01-collected-data/jerrygarcia/`

### Archive.org Collection  
**Script**: `scripts/01-collect-data/collect_archive_metadata.py`

**Features**:
- Archive.org API integration with intelligent pagination
- Weighted rating calculation based on review quality
- Source type detection and classification
- Batch processing with progress tracking
- Resume capability for interrupted collections

**Output**: Individual recording metadata files in `stage01-collected-data/archive/`

## Stage 2: Data Generation

### Recording Data Generation
**Script**: `scripts/02-generate-data/generate_archive_recordings.py`

Processes the Archive.org cache into recordings.json with:
- Comprehensive rating data with breakdowns (raw_rating, high_ratings, low_ratings)
- Track-level metadata (track names, durations, formats)
- Recording quality metadata for integration into shows

### Show Integration
**Script**: `scripts/02-generate-data/integrate_jerry_garcia_shows.py`

The integration system matches Archive.org recordings to JerryGarcia shows using a multi-level approach:

#### Level 1: Date-Based Grouping
```
Date Parsing & Normalization:
├── Handle multiple date formats (M/D/YYYY, MM/DD/YYYY)
├── Clean contaminated fields (tabs, extra text)
├── Extract show timing information (early/late)
└── Group recordings by normalized date
```

#### Level 2: Time-Based Distribution
```
Show Time Matching:
├── Early Shows ← recordings with "early" markers + non-specific
├── Late Shows  ← recordings with "late" markers + non-specific  
├── Regular     ← all recordings for single-show dates
└── Smart Logic: ambiguous recordings appear in ALL shows
```

#### Level 3: Venue-Based Filtering
```
Venue Matching (when needed):
├── Activate: multiple shows, same date, different venues
├── Normalize venue names for comparison
├── Route recordings by venue similarity scores
└── Fallback: unmatched recordings go to all shows
```

### Collections Processing
**Script**: `scripts/02-generate-data/process_collections.py`

Advanced collections resolution system:

#### Collection Resolution Engine
```
Selector Processing:
├── Date ranges with exclusions
├── Specific date lists
├── Additional dates inclusion
├── Multiple exclusion rules
└── Smart date validation
```

#### Show Membership Assignment
- Resolves collection selectors to actual show IDs
- Adds collection membership to individual show files
- Generates failure reports for unmatched collections
- Creates search-optimized collection metadata

#### Output Products
- `collections.json` - Complete collections with resolved show lists
- `collection_failures.json` - Detailed failure analysis (when failures occur)
- Updated show files with collection membership tags

## Stage 3: Search Data Generation

### Search Table Generation
**Script**: `scripts/03-search-data/generate_search_tables.py`

Creates denormalized search indexes optimized for mobile applications:

#### Show Search Index
```json
{
  "show_id": {
    "date": "1977-05-08",
    "venue": "Barton Hall, Cornell University",
    "city": "Ithaca",
    "state": "NY", 
    "avg_rating": 4.8,
    "recording_count": 12,
    "collections": ["dicks-picks-vol-8", "cornell-77"],
    "search_text": "cornell university barton hall ithaca 1977 may"
  }
}
```

#### Collections Search Data
- Normalized collection names as keys
- Search aliases (e.g., "dp", "dick picks" for Dick's Picks)
- Preview show lists (first 10 shows)
- Collection metadata for display

#### Additional Search Tables
- Venues with geographical data and aliases
- Songs with performance statistics
- Band members with instrument data

## Stage 4: Deployment

### Data Packaging
**Script**: `scripts/package_datazip.py`

Advanced packaging system that bundles all processed data into distribution formats:

#### Features
- **Data Analysis**: Comprehensive structure analysis and statistics calculation
- **Smart Packaging**: Combines Stage 2 generated data and Stage 3 search indexes
- **Manifest Generation**: Creates detailed package metadata with usage instructions
- **Validation**: Integrity checking and content verification
- **Compression**: Achieves ~85% size reduction (37MB → 6MB typical)

#### Package Contents
- `manifest.json` - Package metadata and integration guidance
- `collections.json` - Processed collections with resolved show memberships
- `recordings.json` - Archive.org ratings data with comprehensive track-level metadata
- `shows/` - 2,313+ individual show files with complete metadata
- `search/` - 5 optimized search indexes for mobile consumption

#### Usage Modes
```bash
# Create distribution package
python scripts/package_datazip.py --output data.zip --verbose

# Analyze data structure only
python scripts/package_datazip.py --analyze

# Validate existing package
python scripts/package_datazip.py --validate
```

## Complete Pipeline Operation

### Full Pipeline Execution
```bash
# Complete 4-stage pipeline (5-7 hours first time)
make all

# Individual stages
make stage01-collect-data         # Data collection (5-7 hours)
make stage02-generate-data        # Data generation (fast)
make stage03-generate-search-data # Search generation (fast)
```

### Development Workflows

#### Testing Integration
```bash
# Test with limited dataset
python scripts/02-generate-data/integrate_jerry_garcia_shows.py --max-shows 50

# Process specific collections
python scripts/02-generate-data/process_collections.py --verbose

# Generate search data with analysis
python scripts/03-search-data/generate_search_tables.py --analyze
```

#### Incremental Updates
```bash
# Update only recent shows
python scripts/01-collect-data/collect_jerrygarcia_com_shows.py --start-page 1 --end-page 10

# Retry failed collections
python scripts/01-collect-data/collect_jerrygarcia_com_shows.py --retry-failed

# Process specific collections file
python scripts/02-generate-data/process_collections.py --collections-file custom.json
```

## Output Products

### Stage 2 Outputs (`stage02-generated-data/`)

#### Integrated Show Files
**Location**: `stage02-generated-data/shows/`

Each show contains complete information from all sources:

```json
{
  "show_id": "1977-05-08-cornell-university-ithaca-ny-usa",
  "url": "https://jerrygarcia.com/show/1977-05-08-cornell-university-ithaca-ny-usa/",
  "band": "Grateful Dead",
  "venue": "Barton Hall, Cornell University", 
  "city": "Ithaca",
  "state": "NY",
  "country": "USA",
  "date": "1977-05-08",
  
  "setlist": [...],
  "lineup": [...],
  "recordings": [...],
  "best_recording": "gd1977-05-08.sbd.miller.110987.sbeok.flac16",
  "avg_rating": 4.8,
  "recording_count": 12,
  "collections": ["dicks-picks-vol-8", "cornell-77", "1977-tour"],
  
  "matching_method": "date_only",
  "collection_timestamp": "2025-08-09T12:00:00.000000"
}
```

#### Collections Data
**Location**: `stage02-generated-data/collections.json`

Complete collections with resolved show memberships and metadata.

#### Comprehensive Recording Data
**Location**: `stage02-generated-data/recordings.json`

Complete recording metadata including quality ratings and track-level data optimized for mobile applications. Each recording includes:
- Quality ratings and review statistics
- Source type and confidence metrics
- Complete track listings with durations and formats
- Multiple file format support (FLAC, MP3, OGG)

### Stage 3 Outputs (`stage03-search-data/`)

Optimized search indexes for fast mobile queries:
- `shows_index.json` - Show search with collection tags
- `collections.json` - Collection search with aliases
- `venues.json` - Venue search with geographical data
- `songs.json` - Song search with performance statistics

## System Capabilities

### Data Quality Features
- **100% Show Coverage**: Complete Grateful Dead performing history
- **Comprehensive Integration**: All Archive recordings matched to shows
- **Collections Resolution**: Automatic processing of 25+ curated collections
- **Search Optimization**: Mobile-optimized denormalized indexes
- **Data Validation**: Built-in integrity checking and error correction
- **Failure Analysis**: Detailed reporting for troubleshooting

### Scalability Features
- **Stage-Based Architecture**: Clear separation of concerns
- **Resume Capability**: Interrupted processes can resume from checkpoints
- **Progress Tracking**: Real-time status monitoring during collection
- **Error Isolation**: Individual failures don't stop overall processing
- **Resource Management**: Memory-efficient processing of large datasets

### Complex Scenario Handling
- **Multiple Shows Per Date**: Smart routing based on timing and venue
- **Collection Selectors**: Complex date ranges with exclusions
- **Search Aliases**: Multiple search terms for common collections
- **Data Inconsistencies**: Automatic correction of common source data issues

## Performance Metrics

- **Full Pipeline**: 5-7 hours (includes complete data collection)
- **Stage 1 Collection**: 5-7 hours (Archive.org + Jerry Garcia data)
- **Stage 2 Generation**: ~10 seconds (processing cached data)
- **Stage 3 Search**: ~5 seconds (search index generation)
- **Storage**: ~5GB working space, ~500MB cache, 2-5MB final output
- **Collections**: 25+ collections processed with 100% resolution tracking

## Technical Specifications

### Dependencies
- **Python 3.8+** with requests, lxml, beautifulsoup4, python-dateutil
- **Network Access** for API calls to JerryGarcia.com and Archive.org
- **Storage Space** ~5GB working space, 2-5MB final output

### Data Models
- **Shared Models** (`scripts/shared/models.py`) ensure consistency
- **JSON Schema** standardized format across all outputs
- **Mobile Optimization** efficient structures for app consumption
- **Search Optimization** denormalized indexes for fast queries

### API Integration
- **Rate Limiting** respectful server usage with configurable delays
- **Error Recovery** robust retry logic with exponential backoff
- **Caching** intelligent data caching to minimize redundant requests
- **Resume Points** checkpointing for long-running operations

---

**System Version**: 3.0  
**Last Updated**: January 2025  
**Pipeline Status**: Production Ready with Collections & Search Optimization