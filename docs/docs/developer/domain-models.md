# Domain Models

This document describes the core domain models returned by the Archive API service. These models represent the application's business entities and are platform-agnostic.

## Overview

The Archive service returns three primary domain models:

- **RecordingMetadata** - Information about a concert recording
- **Track** - Individual audio track from a recording
- **Review** - User review for a recording

These models are **mapped** from Archive.org API responses and represent the clean, normalized data used throughout the application.

**Source of Truth**: Android implementation at `androidApp/v2/core/model/src/main/java/com/deadly/v2/core/model/PlaylistModels.kt:204-217`

## RecordingMetadata

Represents metadata for a specific Grateful Dead concert recording from Archive.org.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| identifier | String | Yes | Archive.org unique identifier (e.g., "gd1977-05-08.sbd.miller.97065") |
| title | String | Yes | Recording title (e.g., "Grateful Dead Live at Barton Hall...") |
| date | String | No | Performance date in YYYY-MM-DD format (e.g., "1977-05-08") |
| venue | String | No | Venue name (e.g., "Barton Hall, Cornell University") |
| description | String | No | Show description and notes. May be multi-line text. |
| setlist | String | No | Setlist for the show. May be multi-line text with set breaks. |
| source | String | No | Recording source information (e.g., "Soundboard", "Audience") |
| taper | String | No | Name of person who recorded the show (e.g., "Betty Cantor-Jackson") |
| transferer | String | No | Name of person who digitized/transferred the recording |
| lineage | String | No | Equipment chain used for recording. May be multi-line text. |
| totalTracks | Int | Yes | Total number of audio tracks in this recording |
| totalReviews | Int | Yes | Total number of user reviews for this recording |

### Notes

- **Multi-line fields**: `description`, `setlist`, and `lineage` may contain newline characters (\n)
- **Nullable fields**: Most fields are optional except `identifier`, `title`, `totalTracks`, `totalReviews`
- **Default values**: `totalTracks` and `totalReviews` default to 0 if not provided

### Example

```json
{
  "identifier": "gd1977-05-08.sbd.miller.97065",
  "title": "Grateful Dead Live at Barton Hall, Cornell University",
  "date": "1977-05-08",
  "venue": "Barton Hall, Cornell University",
  "description": "One of the most legendary Grateful Dead shows...",
  "setlist": "Set 1: New Minglewood Blues, Loser, El Paso...\nSet 2: Scarlet Begonias > Fire...",
  "source": "Soundboard",
  "taper": "Betty Cantor-Jackson",
  "transferer": "Bill Suckalooskey",
  "lineage": "Master reel > DAT > CDR > FLAC",
  "totalTracks": 18,
  "totalReviews": 127
}
```

## Track

Represents an individual audio track from a recording.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| name | String | Yes | Filename of the audio file (e.g., "gd77-05-08d1t01.flac") |
| title | String | No | Track title/song name. If missing from API, extracted from filename. |
| trackNumber | Int | No | Track number in sequence. If missing from API, uses array position + 1. |
| duration | String | No | Track duration as string (e.g., "423.45" for seconds, or "7:03") |
| format | String | Yes | Audio format (e.g., "FLAC", "MP3", "VBR MP3", "Ogg Vorbis") |
| size | String | No | File size in bytes as string (e.g., "34567890") |
| bitrate | String | No | Audio bitrate (e.g., "1000" for 1000kbps) |
| sampleRate | String | No | Sample rate (e.g., "44100" for 44.1kHz) |
| isAudio | Boolean | Yes | Always true for tracks (used to distinguish from other file types) |

### Notes

- **Track ordering**: Tracks should be sorted by `trackNumber` after mapping
- **Title extraction**: If `title` is null in API response, extract from `name` using filename parsing algorithm
- **Track number fallback**: If `trackNumber` is null in API response, use array index + 1
- **String numeric fields**: `duration`, `size`, `bitrate`, `sampleRate` are strings (not numbers) because Archive.org returns them inconsistently

### Example

```json
{
  "name": "gd77-05-08d1t01.flac",
  "title": "New Minglewood Blues",
  "trackNumber": 1,
  "duration": "423.45",
  "format": "FLAC",
  "size": "34567890",
  "bitrate": "1000",
  "sampleRate": "44100",
  "isAudio": true
}
```

## Review

Represents a user review from Archive.org.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| reviewer | String | No | Archive.org username of reviewer |
| title | String | No | Review title/subject |
| body | String | No | Review text content |
| rating | Int | No | Star rating from 1-5 |
| reviewDate | String | No | Date review was posted (format varies by Archive.org) |

### Notes

- **All fields optional**: Archive.org reviews may have incomplete data
- **Rating range**: When present, `rating` is typically 1-5 (stars)
- **Empty reviews**: It's valid to have a review with only a rating and no text

### Example

```json
{
  "reviewer": "deadhead1977",
  "title": "Best show ever!",
  "body": "This is the definitive version of Scarlet > Fire. The energy is incredible and the sound quality is pristine.",
  "rating": 5,
  "reviewDate": "2020-03-15"
}
```

## Type Mappings by Platform

When implementing these models in different platforms, use these type equivalents:

| Domain Type | Kotlin | Swift | TypeScript |
|-------------|--------|-------|------------|
| String | String | String | string |
| Int | Int | Int | number |
| Boolean | Boolean | Bool | boolean |

### Nullable Fields

- **Kotlin**: Use nullable types (e.g., `String?`)
- **Swift**: Use optionals (e.g., `String?`)
- **TypeScript**: Use union with undefined (e.g., `string \| undefined`)

### Default Values

For required fields with defaults:

- `totalTracks`: Default to 0
- `totalReviews`: Default to 0
- `isAudio`: Default to true (for Track model)

## Usage

### Service Layer

These models are returned by the Archive service:

```
archiveService.getRecordingMetadata(recordingId) → Result<RecordingMetadata>
archiveService.getRecordingTracks(recordingId) → Result<List<Track>>
archiveService.getRecordingReviews(recordingId) → Result<List<Review>>
```

### Caching

These domain models (not raw API responses) are what gets cached to disk. See `api-integration.md` for cache file format.

### Data Flow

```
Archive.org API Response
    ↓
Mapper transforms to domain models
    ↓
Service caches domain models
    ↓
ViewModels consume domain models
    ↓
UI displays data
```

## Validation

When implementing these models, consider these validation rules:

### RecordingMetadata
- `identifier` should not be empty
- `title` should not be empty
- `totalTracks` should be >= 0
- `totalReviews` should be >= 0

### Track
- `name` should not be empty
- `format` should not be empty
- `trackNumber` should be > 0 (if present)
- `isAudio` should always be true

### Review
- `rating` should be 1-5 (if present)
- At least one field should be non-null (otherwise it's an empty review)

## References

- **Implementation**: See `androidApp/v2/core/model/src/main/java/com/deadly/v2/core/model/PlaylistModels.kt`
- **Mapping Logic**: See `androidApp/v2/core/network/archive/src/main/java/com/deadly/v2/core/network/archive/mapper/ArchiveMapper.kt`
- **API Integration**: See `api-integration.md` for how these models are created from API responses
- **Caching**: See "Cache File Format" section in `api-integration.md` for serialized examples
