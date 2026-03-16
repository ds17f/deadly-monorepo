# SQL Database Implementation Notes

This document provides guidance for implementing a SQL database schema based on the Grateful Dead metadata pipeline data structures. It complements the main pipeline documentation with database-specific implementation details.

## Overview

The pipeline generates structured JSON data that maps well to a relational database schema. The data follows a clear hierarchy: Shows contain Setlists and Lineups, while being enriched with Archive.org recording metadata.

## Core Schema Design

### Primary Tables

#### Shows Table
The central entity representing individual Grateful Dead concerts.

```sql
CREATE TABLE shows (
    show_id VARCHAR(255) PRIMARY KEY,  -- e.g., "1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"
    url TEXT,                          -- JerryGarcia.com source URL
    band VARCHAR(100) DEFAULT 'Grateful Dead',
    venue TEXT NOT NULL,
    location_raw TEXT,                 -- Original location string from source
    city VARCHAR(100),
    state VARCHAR(10),
    country VARCHAR(10) DEFAULT 'USA',
    date DATE NOT NULL,
    show_time VARCHAR(20),             -- "early", "late", or null for single shows
    
    -- Data completeness flags
    setlist_status VARCHAR(20),        -- "found", "missing"
    lineup_status VARCHAR(20),         -- "found", "missing"  
    supporting_acts_status VARCHAR(20), -- "found", "missing"
    
    -- Archive.org recording metrics
    avg_rating DECIMAL(10,8),          -- Weighted rating (0-5 scale)
    raw_rating DECIMAL(10,8),          -- Simple average rating (0-5 scale)
    recording_count INTEGER DEFAULT 0,
    confidence DECIMAL(3,2),           -- Rating confidence (0.0-1.0)
    best_recording VARCHAR(255),       -- Archive.org identifier of highest-rated recording
    total_high_ratings INTEGER DEFAULT 0,  -- Count of 4-5★ reviews
    total_low_ratings INTEGER DEFAULT 0,   -- Count of 1-2★ reviews
    
    -- Processing metadata
    matching_method VARCHAR(50),       -- "date_only", "date_venue", etc.
    filtering_applied JSON,            -- Array of filters applied during processing
    collection_timestamp TIMESTAMP,
    
    -- Indexes for common queries
    INDEX idx_date (date),
    INDEX idx_venue (venue),
    INDEX idx_city (city),
    INDEX idx_rating (avg_rating),
    INDEX idx_year_month (YEAR(date), MONTH(date))
);
```

#### Setlists Tables
Represents the structured song performance data.

```sql
CREATE TABLE setlists (
    id SERIAL PRIMARY KEY,
    show_id VARCHAR(255) NOT NULL,
    set_name VARCHAR(50) NOT NULL,     -- "Set 1", "Set 2", "Encore"
    set_order INTEGER NOT NULL,       -- Order of sets within show
    
    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE,
    INDEX idx_show_id (show_id)
);

CREATE TABLE setlist_songs (
    id SERIAL PRIMARY KEY,
    setlist_id INTEGER NOT NULL,
    song_name VARCHAR(255) NOT NULL,
    song_url TEXT,                     -- JerryGarcia.com song URL (may be null)
    position INTEGER NOT NULL,         -- Song order within set (1-based)
    segue_into_next BOOLEAN DEFAULT false, -- True if song segues into next
    
    FOREIGN KEY (setlist_id) REFERENCES setlists(id) ON DELETE CASCADE,
    INDEX idx_setlist_id (setlist_id),
    INDEX idx_song_name (song_name),
    INDEX idx_show_song (setlist_id, song_name) -- For song-in-show queries
);
```

#### Band Lineups
Represents band member participation in shows.

```sql
CREATE TABLE show_lineups (
    id SERIAL PRIMARY KEY,
    show_id VARCHAR(255) NOT NULL,
    member_name VARCHAR(100) NOT NULL,
    instruments TEXT,                  -- e.g., "guitar, vocals"
    image_url TEXT,                    -- JerryGarcia.com profile image
    
    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE,
    INDEX idx_show_id (show_id),
    INDEX idx_member (member_name),
    UNIQUE KEY unique_show_member (show_id, member_name)
);
```

#### Archive.org Recordings
Individual recording metadata linked to shows.

```sql
CREATE TABLE recordings (
    identifier VARCHAR(255) PRIMARY KEY, -- Archive.org unique identifier
    show_id VARCHAR(255) NOT NULL,
    title TEXT,
    source_type VARCHAR(20),           -- "SBD", "AUD", "FM", "MATRIX", "REMASTER"
    lineage TEXT,                      -- Recording chain information
    taper VARCHAR(255),                -- Person who recorded/transferred
    description TEXT,                  -- Archive.org description
    date DATE,                         -- Recording date
    venue TEXT,                        -- Venue name
    location TEXT,                     -- City, State, Country
    
    -- Quality metrics
    rating DECIMAL(10,8),              -- Weighted rating for internal ranking
    raw_rating DECIMAL(10,8),          -- Simple average for display
    review_count INTEGER DEFAULT 0,
    confidence DECIMAL(3,2),           -- Rating confidence (0.0-1.0)
    
    collection_timestamp TIMESTAMP,
    
    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE,
    INDEX idx_show_id (show_id),
    INDEX idx_source_type (source_type),
    INDEX idx_rating (rating),
    INDEX idx_date (date)
);

-- Track-level data from Archive.org recordings
CREATE TABLE tracks (
    id SERIAL PRIMARY KEY,
    recording_id VARCHAR(255) NOT NULL,
    track_number VARCHAR(10) NOT NULL,  -- "01", "02", etc.
    title TEXT NOT NULL,                -- Song/track title
    duration DECIMAL(8,2),              -- Duration in seconds
    
    FOREIGN KEY (recording_id) REFERENCES recordings(identifier) ON DELETE CASCADE,
    INDEX idx_recording_id (recording_id),
    INDEX idx_track_number (recording_id, track_number),
    INDEX idx_title (title)
);

-- Multiple format support for each track
CREATE TABLE track_formats (
    id SERIAL PRIMARY KEY,
    track_id INTEGER NOT NULL,
    format VARCHAR(50) NOT NULL,        -- "Flac", "VBR MP3", "Ogg Vorbis", etc.
    filename TEXT NOT NULL,             -- Archive.org filename
    bitrate VARCHAR(20),                -- For compressed formats (e.g., "192")
    
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    INDEX idx_track_id (track_id),
    INDEX idx_format (format),
    UNIQUE KEY unique_track_format (track_id, format)
);

-- Source type distribution per show (from JSON source_types field)
CREATE TABLE recording_source_counts (
    show_id VARCHAR(255) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    count INTEGER NOT NULL,
    
    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE,
    PRIMARY KEY (show_id, source_type)
);
```

### Supporting Acts (Optional)
If supporting acts data is present:

```sql
CREATE TABLE supporting_acts (
    id SERIAL PRIMARY KEY,
    show_id VARCHAR(255) NOT NULL,
    act_name VARCHAR(255) NOT NULL,
    act_order INTEGER,                 -- Opening act order
    
    FOREIGN KEY (show_id) REFERENCES shows(show_id) ON DELETE CASCADE,
    INDEX idx_show_id (show_id)
);
```

## Search Optimization Tables

Based on the Stage 3 search tables, create materialized views or denormalized tables:

### Song Search Table
```sql
CREATE TABLE song_search (
    song_key VARCHAR(255) NOT NULL,    -- Normalized song name (e.g., "dark-star")
    song_name VARCHAR(255) NOT NULL,   -- Display name (e.g., "Dark Star")
    show_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    venue TEXT NOT NULL,
    location VARCHAR(255) NOT NULL,    -- "City, State, Country"
    set_name VARCHAR(50),
    position INTEGER,
    segue_into_next BOOLEAN,
    rating DECIMAL(10,8),
    raw_rating DECIMAL(10,8),
    
    INDEX idx_song_key (song_key),
    INDEX idx_song_name (song_name),
    INDEX idx_date (date),
    FULLTEXT idx_song_search (song_name, song_key)
);
```

### Venue Search Table
```sql
CREATE TABLE venue_search (
    venue_key VARCHAR(255) NOT NULL,   -- Normalized venue name
    venue_name TEXT NOT NULL,          -- Display name
    location VARCHAR(255) NOT NULL,
    city VARCHAR(100),
    state VARCHAR(10),
    country VARCHAR(10),
    show_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    rating DECIMAL(10,8),
    raw_rating DECIMAL(10,8),
    recording_count INTEGER,
    
    INDEX idx_venue_key (venue_key),
    INDEX idx_city (city),
    INDEX idx_state (state),
    FULLTEXT idx_venue_search (venue_name, city)
);
```

### Member Search Table
```sql
CREATE TABLE member_search (
    member_key VARCHAR(255) NOT NULL,  -- Normalized member name
    member_name VARCHAR(100) NOT NULL, -- Display name
    show_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    venue TEXT NOT NULL,
    instruments TEXT,
    rating DECIMAL(10,8),
    
    INDEX idx_member_key (member_key),
    INDEX idx_member_name (member_name),
    INDEX idx_date (date),
    FULLTEXT idx_member_search (member_name, instruments)
);
```

## Data Import Strategy

### JSON to SQL Mapping

1. **Shows**: Direct mapping from `stage02-generated-data/shows/*.json`
2. **Recordings**: Import from `stage02-generated-data/recordings.json` with track data extraction
3. **Search Tables**: Import from `stage03-search-data/*.json` files
4. **Batch Processing**: Process shows in chronological order for better locality

#### Recording and Track Data Import

The new `recordings.json` structure includes comprehensive track metadata:

```json
{
  "recordings": {
    "gd1970-05-02.138227.sbd.miller.flac1648": {
      "rating": 2.24,
      "review_count": 52,
      "tracks": [
        {
          "track": "01",
          "title": "Tuning",
          "duration": 107.08,
          "formats": [
            {"format": "Flac", "filename": "01Tuning.flac"},
            {"format": "VBR MP3", "filename": "01Tuning.mp3", "bitrate": "188"}
          ]
        }
      ]
    }
  }
}
```

### Sample Import Logic

```sql
-- Example for importing a show file
INSERT INTO shows (
    show_id, url, band, venue, city, state, country, date,
    setlist_status, lineup_status, avg_rating, raw_rating,
    recording_count, best_recording, collection_timestamp
) VALUES (
    JSON_UNQUOTE(JSON_EXTRACT(show_json, '$.show_id')),
    JSON_UNQUOTE(JSON_EXTRACT(show_json, '$.url')),
    -- ... continue for all fields
);

-- Import setlist data
INSERT INTO setlists (show_id, set_name, set_order)
SELECT 
    show_id,
    JSON_UNQUOTE(JSON_EXTRACT(setlist_item, '$.set_name')),
    setlist_index
FROM shows s
JOIN JSON_TABLE(s.setlist_json, '$[*]' 
    COLUMNS (
        setlist_index FOR ORDINALITY,
        setlist_item JSON PATH '$'
    )
) jt;

-- Import recording data with track metadata
INSERT INTO recordings (
    identifier, show_id, title, source_type, date, venue, location,
    rating, raw_rating, review_count, confidence, collection_timestamp
) 
SELECT 
    recording_id,
    -- Map recording to show_id based on date/venue matching logic
    COALESCE(s.show_id, CONCAT(
        JSON_UNQUOTE(JSON_EXTRACT(recording_data, '$.date')), '-',
        LOWER(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(recording_data, '$.venue')), ' ', '-'))
    )),
    JSON_UNQUOTE(JSON_EXTRACT(recording_data, '$.title')),
    JSON_UNQUOTE(JSON_EXTRACT(recording_data, '$.source_type')),
    JSON_UNQUOTE(JSON_EXTRACT(recording_data, '$.date')),
    JSON_UNQUOTE(JSON_EXTRACT(recording_data, '$.venue')),
    JSON_UNQUOTE(JSON_EXTRACT(recording_data, '$.location')),
    JSON_EXTRACT(recording_data, '$.rating'),
    JSON_EXTRACT(recording_data, '$.raw_rating'),
    JSON_EXTRACT(recording_data, '$.review_count'),
    JSON_EXTRACT(recording_data, '$.confidence'),
    NOW()
FROM recordings_json r
LEFT JOIN shows s ON s.date = JSON_UNQUOTE(JSON_EXTRACT(r.recording_data, '$.date'))
                  AND s.venue = JSON_UNQUOTE(JSON_EXTRACT(r.recording_data, '$.venue'));

-- Import track data
INSERT INTO tracks (recording_id, track_number, title, duration)
SELECT 
    r.identifier,
    JSON_UNQUOTE(JSON_EXTRACT(track_item, '$.track')),
    JSON_UNQUOTE(JSON_EXTRACT(track_item, '$.title')),
    JSON_EXTRACT(track_item, '$.duration')
FROM recordings r
JOIN JSON_TABLE(r.tracks_json, '$[*]'
    COLUMNS (
        track_item JSON PATH '$'
    )
) tracks;

-- Import track format data
INSERT INTO track_formats (track_id, format, filename, bitrate)
SELECT 
    t.id,
    JSON_UNQUOTE(JSON_EXTRACT(format_item, '$.format')),
    JSON_UNQUOTE(JSON_EXTRACT(format_item, '$.filename')),
    JSON_UNQUOTE(JSON_EXTRACT(format_item, '$.bitrate'))
FROM tracks t
JOIN JSON_TABLE(t.formats_json, '$[*]'
    COLUMNS (
        format_item JSON PATH '$'
    )
) formats;
```

## Business Rules and Constraints

### Data Validation
```sql
-- Date range validation (Grateful Dead performing years)
ALTER TABLE shows ADD CONSTRAINT valid_date_range 
CHECK (date BETWEEN '1965-01-01' AND '1995-12-31');

-- Rating constraints
ALTER TABLE shows ADD CONSTRAINT valid_rating_range
CHECK (avg_rating IS NULL OR (avg_rating >= 0 AND avg_rating <= 5));

ALTER TABLE shows ADD CONSTRAINT valid_confidence
CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1));

-- Recording count consistency
ALTER TABLE shows ADD CONSTRAINT valid_recording_count
CHECK (recording_count >= 0);

-- Required fields
ALTER TABLE shows ALTER COLUMN venue SET NOT NULL;
ALTER TABLE shows ALTER COLUMN date SET NOT NULL;

-- Show ID format validation (basic)
ALTER TABLE shows ADD CONSTRAINT valid_show_id_format
CHECK (show_id REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}-.+');

-- Track validation
ALTER TABLE tracks ADD CONSTRAINT valid_duration
CHECK (duration IS NULL OR duration > 0);

ALTER TABLE tracks ADD CONSTRAINT valid_track_number
CHECK (track_number REGEXP '^[0-9]+[a-z]?$');

-- Track format validation
ALTER TABLE track_formats ADD CONSTRAINT valid_bitrate
CHECK (bitrate IS NULL OR bitrate REGEXP '^[0-9]+$');
```

### Referential Integrity
```sql
-- Ensure best_recording exists in recordings table
ALTER TABLE shows ADD CONSTRAINT fk_best_recording
FOREIGN KEY (best_recording) REFERENCES recordings(identifier);

-- Ensure setlist songs reference valid setlists
ALTER TABLE setlist_songs ADD CONSTRAINT fk_setlist
FOREIGN KEY (setlist_id) REFERENCES setlists(id) ON DELETE CASCADE;

-- Ensure track formats reference valid tracks
ALTER TABLE track_formats ADD CONSTRAINT fk_track
FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE;
```

## Performance Optimization

### Recommended Indexes
```sql
-- Core performance indexes
CREATE INDEX idx_shows_date_rating ON shows(date, avg_rating DESC);
CREATE INDEX idx_shows_venue_date ON shows(venue, date);
CREATE INDEX idx_songs_performance ON setlist_songs(song_name, setlist_id);

-- Recording and track indexes
CREATE INDEX idx_recordings_show_rating ON recordings(show_id, rating DESC);
CREATE INDEX idx_tracks_recording_track ON tracks(recording_id, track_number);
CREATE INDEX idx_track_formats_track_format ON track_formats(track_id, format);
CREATE FULLTEXT INDEX idx_tracks_title ON tracks(title);

-- Search indexes
CREATE FULLTEXT INDEX idx_shows_search ON shows(venue, city);
CREATE FULLTEXT INDEX idx_songs_fulltext ON setlist_songs(song_name);

-- Composite indexes for common queries
CREATE INDEX idx_show_year_state ON shows(YEAR(date), state);
CREATE INDEX idx_recording_source_rating ON recordings(source_type, rating DESC);
```

### Query Optimization Hints

```sql
-- Find all shows in 1977 with Dark Star
SELECT DISTINCT s.show_id, s.date, s.venue, s.avg_rating
FROM shows s
JOIN setlists sl ON s.show_id = sl.show_id
JOIN setlist_songs ss ON sl.id = ss.setlist_id
WHERE YEAR(s.date) = 1977 
  AND ss.song_name LIKE '%Dark Star%'
ORDER BY s.date;

-- Top-rated shows at the Fillmore
SELECT show_id, date, avg_rating, recording_count
FROM shows 
WHERE venue LIKE '%Fillmore%' 
  AND avg_rating IS NOT NULL
ORDER BY avg_rating DESC, recording_count DESC
LIMIT 20;

-- Get all tracks for a specific recording with formats
SELECT t.track_number, t.title, t.duration,
       GROUP_CONCAT(tf.format ORDER BY 
         CASE tf.format 
           WHEN 'Flac' THEN 1 
           WHEN 'VBR MP3' THEN 2 
           ELSE 3 
         END) as available_formats
FROM tracks t
LEFT JOIN track_formats tf ON t.id = tf.track_id
WHERE t.recording_id = 'gd1977-05-08.sbd.miller.97375.sbeok.flac16'
GROUP BY t.id, t.track_number, t.title, t.duration
ORDER BY CAST(t.track_number AS UNSIGNED);

-- Find recordings with specific track by title
SELECT r.identifier, r.date, r.venue, t.track_number, t.title, t.duration
FROM recordings r
JOIN tracks t ON r.identifier = t.recording_id
WHERE t.title LIKE '%Dark Star%'
ORDER BY r.date, t.track_number;

-- Get total duration and track count per recording
SELECT r.identifier, r.date, r.venue, r.source_type,
       COUNT(t.id) as track_count,
       SUM(t.duration) as total_duration_seconds,
       TIME_FORMAT(SEC_TO_TIME(SUM(t.duration)), '%H:%i:%s') as total_duration
FROM recordings r
LEFT JOIN tracks t ON r.identifier = t.recording_id
GROUP BY r.identifier
ORDER BY total_duration_seconds DESC
LIMIT 20;
```

## Data Statistics

Based on the pipeline output:

- **Shows**: ~2,313 total (1965-1995)
- **Venues**: ~484 unique venues
- **Songs**: ~550 unique songs with aliases
- **Recordings**: 17,790+ Archive.org recordings with complete track metadata
- **Tracks**: ~300,000+ individual track entries (estimated 15-20 tracks per recording average)
- **Track Formats**: ~900,000+ format entries (estimated 3 formats per track: FLAC, MP3, OGG)
- **Members**: ~20+ band members across all eras
- **Geographic Distribution**: Primarily USA with some Canada shows

### Storage Estimates
- **Shows table**: ~500KB (2,313 rows × ~200 bytes avg)
- **Setlist_songs table**: ~2MB (estimated 40,000+ song performances)
- **Recordings table**: ~20MB (17,790 rows × ~1KB avg with track metadata)
- **Tracks table**: ~40MB (300,000 rows × ~130 bytes avg)
- **Track_formats table**: ~60MB (900,000 rows × ~70 bytes avg)
- **Search tables**: ~5MB total (denormalized data)

**Total estimated database size**: 130-150MB for core data, plus indexes (~200MB total).

## Integration Notes

### Pipeline Integration
- Run database import after Stage 3 (search data generation)
- Use `collection_timestamp` fields to track data freshness
- Consider incremental updates for new shows/recordings

### API Considerations
- Search tables enable fast mobile app queries
- Core show data provides complete details for individual views
- Recording data supports quality-based filtering
- Track tables enable detailed playback features with format selection
- Track metadata supports duration calculations and progress tracking
- Format tables enable quality-based streaming (FLAC vs MP3 preferences)

### Backup Strategy
- Regular backups of core shows/setlists (relatively static)
- More frequent backups of recordings table (may grow with new Archive.org discoveries)
- Version control for schema changes as pipeline evolves

---

**Note**: This schema is based on analysis of the actual JSON output from the Grateful Dead metadata pipeline as of August 2025. Adjust field sizes and constraints based on your specific requirements and data distribution.