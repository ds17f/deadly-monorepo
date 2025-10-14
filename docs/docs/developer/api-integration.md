# API Integration

This document describes how the application integrates with the Internet Archive API to fetch Grateful Dead concert data. The integration is designed to be platform-agnostic, with concepts applicable to both Android and iOS implementations.

## Overview

The application uses the **Internet Archive API** as its primary data source for Grateful Dead concert metadata, audio files, and user reviews.

### API Base URL

- **Base URL**: `https://archive.org/`
- **API Documentation**: https://archive.org/help/aboutapi.php

### Why Internet Archive?

- **Comprehensive catalog**: ~13,000 Grateful Dead recordings
- **High-quality audio**: FLAC, MP3, OGG formats available
- **Rich metadata**: Venue, date, taper, lineage information
- **User reviews**: Community ratings and notes
- **Free access**: No API keys or rate limits (reasonable usage)
- **Stable URLs**: Predictable URL structure for streaming

## Architecture

The API integration follows a three-layer architecture that separates concerns and enables testability:

```
Service Layer (Business Interface)
    ↓
Mapper Layer (Transform API ↔ Domain)
    ↓
API Client Layer (HTTP Communication)
```

### Layer Responsibilities

#### 1. Service Layer

**Purpose**: Provide business-level API for features to consume.

**Responsibilities**:
- Expose clean domain models (not API response structures)
- Handle caching strategy
- Provide Result-based error handling
- Return reactive streams of data

**Interface Example**:
```kotlin
interface ArchiveService {
    suspend fun getRecordingMetadata(recordingId: String): Result<RecordingMetadata>
    suspend fun getRecordingTracks(recordingId: String): Result<List<Track>>
    suspend fun getRecordingReviews(recordingId: String): Result<List<Review>>
}
```

**Why**: Features shouldn't need to know about HTTP, JSON parsing, or caching—just business operations.

#### 2. Mapper Layer

**Purpose**: Transform between API formats and domain models.

**Responsibilities**:
- Convert API responses → domain models
- Handle inconsistent API data formats
- Extract meaningful data from complex structures
- Provide sensible defaults for missing fields

**Why**: API responses often don't match internal data models. Separation allows changing either without affecting the other.

#### 3. API Client Layer

**Purpose**: Define HTTP endpoints and handle network communication.

**Responsibilities**:
- Define API endpoints
- Serialize/deserialize JSON
- Make HTTP requests
- Return raw API responses

**Why**: Isolates network concerns from business logic. Easily mockable for testing.

## API Endpoints

### Get Recording Metadata

The primary endpoint fetches complete information about a specific recording.

**Endpoint**: `GET /metadata/{identifier}`

**Parameters**:
- `identifier` - Archive.org item identifier (e.g., `gd1977-05-08.sbd.miller.97065`)

**Response**: Complete recording metadata including files, show information, and reviews

**Example URL**:
```
https://archive.org/metadata/gd1977-05-08.sbd.miller.97065
```

**Usage**:
```kotlin
val result = archiveService.getRecordingMetadata("gd1977-05-08.sbd.miller.97065")
```

## Data Models

### API Response Structure

The Archive.org metadata API returns a complex nested structure:

```json
{
  "files": [...],           // All files in the recording
  "metadata": {...},        // Show metadata
  "reviews": [...],         // User reviews
  "server": "...",          // CDN server
  "dir": "...",             // File directory path
  "workable_servers": [...]  // Available servers
}
```

#### Files Array

Each file represents an audio track, text file, or metadata file:

```json
{
  "name": "gd77-05-08d1t01.flac",
  "format": "FLAC",
  "size": "34567890",
  "length": "423.45",        // Duration in seconds
  "title": "Promised Land",
  "track": "1",
  "bitrate": "1000",
  "md5": "..."
}
```

#### Metadata Object

Show-level information:

```json
{
  "identifier": "gd1977-05-08.sbd.miller.97065",
  "title": "Grateful Dead Live at Cornell...",
  "date": "1977-05-08",
  "venue": "Barton Hall, Cornell University",
  "coverage": "Ithaca, NY",
  "creator": "Grateful Dead",
  "description": "...",
  "setlist": "...",
  "source": "Soundboard",
  "taper": "Betty Cantor-Jackson",
  "collection": ["GratefulDead", "etree"]
}
```

**Important**: Many fields can be either `string` or `string[]` (array). The mapper handles this inconsistency.

#### Reviews Array

User reviews from Archive.org:

```json
{
  "reviewtitle": "Best show ever!",
  "reviewbody": "Amazing performance...",
  "reviewer": "username",
  "reviewdate": "2020-01-15",
  "stars": 5
}
```

### Domain Models

The mapper transforms API responses into clean domain models:

#### RecordingMetadata

```kotlin
data class RecordingMetadata(
    val identifier: String,
    val title: String,
    val date: String?,
    val venue: String?,
    val description: String?,
    val setlist: String?,
    val source: String?,
    val taper: String?,
    val transferer: String?,
    val lineage: String?,
    val totalTracks: Int,
    val totalReviews: Int
)
```

#### Track

```kotlin
data class Track(
    val name: String,
    val title: String?,
    val trackNumber: Int?,
    val duration: String?,
    val format: String,
    val size: String?,
    val bitrate: String?,
    val sampleRate: String?,
    val isAudio: Boolean
)
```

#### Review

```kotlin
data class Review(
    val reviewer: String?,
    val title: String?,
    val body: String?,
    val rating: Int?,
    val reviewDate: String?
)
```

## Data Mapping Strategy

### Mapper Responsibilities

The mapper transforms complex, inconsistent API data into clean domain models.

#### 1. Audio File Filtering

**Challenge**: The files array contains audio files, text files, metadata files, images, etc.

**Solution**: Filter by file extension to identify audio files:
- Supported: mp3, flac, ogg, m4a, wav, aac, wma
- Ignore: txt, xml, jpg, png, pdf

#### 2. Track Title Extraction

**Challenge**: Not all audio files have a `title` field in metadata.

**Solution**: Extract title from filename:
- Remove common prefixes (gd, grateful_dead)
- Strip date prefixes (YYYY-MM-DD)
- Remove disc/track markers (d1t01)
- Replace underscores with spaces

**Example**:
```
Filename: gd1977-05-08d1t02.flac
Extracted: "02" (if no title metadata)
```

#### 3. Flexible Field Parsing

**Challenge**: Archive.org returns some fields as `string` OR `string[]`.

**Solution**: Custom deserializers that handle both:
- **Single value fields** (venue, taper): Take first array element if array
- **Multi-value fields** (description, setlist): Join array with newlines

**Example**:
```json
// Sometimes:
"venue": "Barton Hall"

// Sometimes:
"venue": ["Barton Hall", "Cornell University"]

// Mapper always returns: "Barton Hall"
```

#### 4. Track Number Inference

**Challenge**: Some tracks don't have explicit track numbers.

**Solution**: Use array index + 1 as fallback, then sort by track number.

#### 5. Review Aggregation

**Challenge**: Reviews are optional and may not exist.

**Solution**: Return empty list if no reviews, count total for metadata.

## Caching Strategy

The service layer implements caching to reduce API calls and improve performance.

### Why Cache?

- **Performance**: Avoid repeated network calls for same data
- **Offline support**: Allow viewing previously fetched recordings
- **Bandwidth**: Reduce data usage for users
- **Reliability**: Work when Archive.org is slow or unavailable

### Cache Design

**Storage**: Filesystem cache (platform cache directory)

**Cache files**:
- `{recordingId}.metadata.json` - Recording metadata
- `{recordingId}.tracks.json` - Track list
- `{recordingId}.reviews.json` - Reviews

**Expiry**: 24 hours (recordings rarely change)

**Format**: JSON (human-readable, debuggable)

### Cache Flow

```
1. Check if cache file exists
   ↓
2. Check if cache expired (> 24 hours old)
   ↓
3. If valid → Deserialize and return cached data
   ↓
4. If expired/missing → Fetch from API
   ↓
5. Map to domain model
   ↓
6. Serialize and cache
   ↓
7. Return data
```

### Cache Invalidation

**Automatic**: Files older than 24 hours are considered stale

**Manual**: Service provides methods to clear cache:
- Clear specific recording
- Clear all cached data

### Platform Notes

- **Android**: Use `context.cacheDir` (auto-cleaned by system)
- **iOS**: Use `FileManager.default.temporaryDirectory` or `.cachesDirectory`

## Error Handling

### Result-Based Errors

All service methods return `Result<T>` for explicit error handling:

```kotlin
val result = archiveService.getRecordingMetadata(recordingId)

result.onSuccess { metadata ->
    // Use metadata
}

result.onFailure { error ->
    // Handle error
}
```

**Why Result types**:
- Explicit error handling (can't ignore errors)
- Composable (can chain/transform)
- Type-safe (errors are typed)
- No exceptions for flow control

### Error Scenarios

| Scenario | Cause | Handling |
|----------|-------|----------|
| Network error | No connection | Return failure, use cached data if available |
| 404 Not Found | Invalid identifier | Return failure with descriptive message |
| 500 Server Error | Archive.org issues | Return failure, suggest retry |
| JSON parsing error | Malformed response | Return failure, log for debugging |
| Cache read error | File I/O problem | Fall back to API fetch |

### Error Types

Create a sealed class for specific errors:

```kotlin
sealed class ArchiveError {
    object NetworkError : ArchiveError()
    object NotFound : ArchiveError()
    object ServerError : ArchiveError()
    data class ParseError(val message: String) : ArchiveError()
}
```

Usage:
```kotlin
suspend fun getRecordingMetadata(recordingId: String): Result<RecordingMetadata, ArchiveError>
```

## Performance Optimizations

### 1. Filesystem Caching

Most significant optimization—eliminates redundant network calls.

### 2. Single API Call for Multiple Data

One `/metadata/{identifier}` call provides:
- Metadata
- Track list
- Reviews

Service layer splits this into separate methods with independent caching.

### 3. Lazy Processing

Only process what's needed:
- Don't filter audio files until `getTracks()` is called
- Don't parse reviews until `getReviews()` is called

### 4. Async Operations

All API calls are async (suspend functions in Kotlin, async/await in Swift):
- Non-blocking
- Can run in parallel
- Cancel when no longer needed

### 5. JSON Deserialization

Configure JSON parser to:
- Ignore unknown fields (don't parse what we don't need)
- Use efficient deserializers
- Handle missing fields gracefully

## Building Streaming URLs

Audio files can be streamed directly from Archive.org using predictable URLs:

**Pattern**:
```
https://archive.org/download/{identifier}/{filename}
```

**Example**:
```
https://archive.org/download/gd1977-05-08.sbd.miller.97065/gd77-05-08d1t01.flac
```

**How to construct**:
1. Get `identifier` from metadata
2. Get `filename` from track in files array
3. Combine: `https://archive.org/download/{identifier}/{filename}`

**Media player integration**:
- Use constructed URL as media source
- Platform media players (ExoPlayer, AVPlayer) handle streaming automatically
- No special Archive.org integration needed

## Testing Strategy

### Unit Tests: Mappers

Test data transformations in isolation:

```kotlin
@Test
fun `mapToTracks filters audio files only`() {
    val response = createMockResponse(
        files = listOf(
            file(name = "track.mp3", format = "MP3"),
            file(name = "info.txt", format = "Text")
        )
    )

    val tracks = mapper.mapToTracks(response)

    assertEquals(1, tracks.size)
    assertEquals("track.mp3", tracks[0].name)
}
```

### Unit Tests: Serializers

Test handling of inconsistent API data:

```kotlin
@Test
fun `flexible serializer handles string array`() {
    val json = """{"field": ["first", "second"]}"""
    val result = deserialize(json)
    assertEquals("first", result.field)
}
```

### Integration Tests: Service

Test with mocked API client:

```kotlin
@Test
fun `getRecordingMetadata uses cache on second call`() {
    // First call - hits API
    service.getRecordingMetadata("test-id")

    // Second call - uses cache
    service.getRecordingMetadata("test-id")

    // Verify API only called once
    verify(apiClient, times(1)).getMetadata("test-id")
}
```

### Smoke Tests: Real API

Test with real Archive.org (run separately from CI):

```kotlin
@Test
@Ignore("Integration test - requires network")
fun `fetch real Cornell 77 show`() {
    val result = service.getRecordingMetadata("gd1977-05-08.sbd.miller.97065")

    assertTrue(result.isSuccess)
    result.onSuccess { metadata ->
        assertEquals("1977-05-08", metadata.date)
        assertTrue(metadata.venue?.contains("Cornell") == true)
    }
}
```

## Common Implementation Tasks

### Adding a New Endpoint

To add support for Archive.org's search API:

1. **Define endpoint in API client**:
```kotlin
suspend fun searchRecordings(query: String): Response<SearchResponse>
```

2. **Create response models** for search results

3. **Add mapper method** to transform search results to domain

4. **Add service method**:
```kotlin
suspend fun searchRecordings(query: String): Result<List<Recording>>
```

5. **Implement caching** (if appropriate)

### Updating Response Models

When Archive.org adds new metadata fields:

1. Add field to API response model
2. Add custom deserializer if needed (for flexible types)
3. Update mapper to include field in domain model (if relevant)
4. Add test for new field

### Debugging Failed Requests

**Check the actual API response**:
```bash
curl https://archive.org/metadata/{identifier}
```

**Common issues**:
- Recording removed from Archive.org (404)
- Invalid identifier format
- Temporary Archive.org outage (503)
- Malformed JSON in specific recordings

**Platform-specific debugging**:
- Android: `adb logcat` for network logs
- iOS: Network debugging in Xcode console

## Platform Implementation Notes

### Kotlin (Android)

- **HTTP client**: Retrofit + OkHttp
- **JSON**: Kotlinx Serialization
- **DI**: Hilt
- **Async**: Coroutines with suspend functions
- **Cache**: Context.cacheDir

**Relevant files**:
- `v2/core/network/archive/api/` - API client
- `v2/core/network/archive/service/` - Service implementation
- `v2/core/network/archive/mapper/` - Data mapping
- `v2/core/network/archive/model/` - Response models

### Swift (iOS) - To Be Implemented

- **HTTP client**: URLSession or Alamofire
- **JSON**: Codable or SwiftJSON
- **DI**: Property injection or factory
- **Async**: async/await
- **Cache**: FileManager.default.cachesDirectory

**Suggested structure**:
- `iosApp/Network/Archive/` - Mirror Android structure
- Define protocols matching Kotlin interfaces
- Implement async/await versions of suspend functions

See `api-integration-swift.md` for Swift-specific details (to be written during porting).

## Internet Archive Compliance

### Attribution

Display attribution when showing Archive.org content:
- "Data from archive.org"
- Include license URL from metadata (if available)

### Terms of Service

- **No scraping**: Don't make excessive automated requests
- **Reasonable usage**: Cache aggressively to reduce load
- **Respect robots.txt**: (Not applicable for API)
- **Commercial use**: Check Archive.org terms for commercial usage

### Licensing

- Most Grateful Dead recordings are under Creative Commons licenses
- Display license info from metadata when available
- Link to Archive.org item page for full details

## Appendix: Mapper Algorithms

This section provides platform-agnostic pseudocode for the key mapping algorithms. For edge cases and implementation details, refer to the Android source: `androidApp/v2/core/network/archive/mapper/ArchiveMapper.kt`

### Algorithm 1: Audio File Filter

**Purpose**: Identify audio files from the complete files array.

**Input**: List of file objects with `name` and `format` properties

**Output**: Filtered list containing only audio files

**Pseudocode**:
```
AUDIO_EXTENSIONS = ["mp3", "flac", "ogg", "m4a", "wav", "aac", "wma"]

function isAudioFile(filename):
    extension = getFileExtension(filename).toLowerCase()
    return extension in AUDIO_EXTENSIONS

function filterAudioFiles(files):
    audioFiles = []
    for each file in files:
        if isAudioFile(file.name):
            audioFiles.append(file)
    return audioFiles
```

**Example**:
```
Input files:
  - "gd77-05-08d1t01.flac" → Audio ✓
  - "gd77-05-08_info.txt" → Not audio ✗
  - "gd77-05-08d1t02.mp3" → Audio ✓
  - "artwork.jpg" → Not audio ✗

Output: 2 audio files
```

### Algorithm 2: Track Title Extraction

**Purpose**: Extract a readable track title from filename when metadata doesn't provide one.

**Input**: Filename string (e.g., "gd1977-05-08d1t02.flac")

**Output**: Extracted title string or original filename if extraction fails

**Pseudocode**:
```
function extractTitleFromFilename(filename):
    // Remove file extension
    title = removeFileExtension(filename)

    // Remove common prefixes
    title = removePrefix(title, "gd")
    title = removePrefix(title, "grateful_dead")

    // Remove date pattern (YYYY-MM-DD) from start
    title = removePattern(title, "^[0-9]{4}-[0-9]{2}-[0-9]{2}")

    // Remove disc/track pattern (e.g., "d1t01.")
    title = removePattern(title, "^d[0-9]t[0-9]+\\.")

    // Replace underscores with spaces
    title = replaceAll(title, "_", " ")

    // Trim whitespace
    title = trim(title)

    // If result is empty, return original filename
    if isEmpty(title):
        return filename
    else:
        return title
```

**Examples**:
```
Input: "gd1977-05-08d1t02.flac"
Output: "02"

Input: "grateful_dead_scarlet_begonias.mp3"
Output: "scarlet begonias"

Input: "gd77-05-08d2t05_fire_on_the_mountain.flac"
Output: "fire on the mountain"

Input: "something_unexpected.flac"
Output: "something unexpected"
```

### Algorithm 3: Flexible String Parsing

**Purpose**: Handle Archive.org fields that can be either a single string OR an array of strings.

**Input**: JSON value (can be string, array, number, or null)

**Output**: Single string value or null

**Used for**: Fields like `venue`, `creator`, `taper`, `transferer`

**Pseudocode**:
```
function parseFlexibleString(jsonValue):
    if jsonValue is null:
        return null

    if jsonValue is string:
        return jsonValue

    if jsonValue is number:
        return toString(jsonValue)

    if jsonValue is array:
        // Return first non-empty string element
        for each element in jsonValue:
            if element is string or number:
                stringValue = toString(element)
                if isNotBlank(stringValue):
                    return stringValue
        return null

    // Unknown type
    return null
```

**Examples**:
```
Input: "Barton Hall"
Output: "Barton Hall"

Input: ["Barton Hall", "Cornell University"]
Output: "Barton Hall"

Input: []
Output: null

Input: ["", "Cornell University"]
Output: "Cornell University"

Input: 1977
Output: "1977"
```

### Algorithm 4: Flexible String List Parsing

**Purpose**: Handle Archive.org fields that can be a string OR array of strings, joining multiple values.

**Input**: JSON value (can be string, array, or null)

**Output**: Single string with array elements joined by newlines, or null

**Used for**: Fields like `description`, `setlist`, `source`, `lineage`, `notes`, `collection`, `subject`

**Pseudocode**:
```
function parseFlexibleStringList(jsonValue):
    if jsonValue is null:
        return null

    if jsonValue is string:
        return jsonValue

    if jsonValue is array:
        // Collect all non-empty string elements
        strings = []
        for each element in jsonValue:
            if element is string:
                if isNotBlank(element):
                    strings.append(element)

        // Join with newlines
        if isEmpty(strings):
            return null
        else:
            return joinWithNewlines(strings)

    // Unknown type
    return null
```

**Examples**:
```
Input: "Set 1: Dark Star, El Paso"
Output: "Set 1: Dark Star, El Paso"

Input: ["Set 1: Dark Star, El Paso", "Set 2: Scarlet > Fire"]
Output: "Set 1: Dark Star, El Paso\nSet 2: Scarlet > Fire"

Input: ["GratefulDead", "etree", "audio"]
Output: "GratefulDead\netree\naudio"

Input: []
Output: null

Input: [""]
Output: null
```

## Cache File Format

The service layer caches **mapped domain models** (not raw API responses) as JSON files. This section shows the exact structure of cached data.

### Cache File Naming

- **Metadata**: `{recordingId}.metadata.json`
- **Tracks**: `{recordingId}.tracks.json`
- **Reviews**: `{recordingId}.reviews.json`

**Example**: For recording ID `gd1977-05-08.sbd.miller.97065`:
- `gd1977-05-08.sbd.miller.97065.metadata.json`
- `gd1977-05-08.sbd.miller.97065.tracks.json`
- `gd1977-05-08.sbd.miller.97065.reviews.json`

### Cached Metadata Format

**File**: `{recordingId}.metadata.json`

**Content**: Single `RecordingMetadata` object

**Example**:
```json
{
  "identifier": "gd1977-05-08.sbd.miller.97065",
  "title": "Grateful Dead Live at Barton Hall, Cornell University on 1977-05-08",
  "date": "1977-05-08",
  "venue": "Barton Hall, Cornell University",
  "description": "One of the most legendary Grateful Dead shows of all time. Amazing soundboard recording from Betty Cantor-Jackson.",
  "setlist": "Set 1: New Minglewood Blues, Loser, El Paso, They Love Each Other, Jack Straw, Deal, Lazy Lightning > Supplication, Brown Eyed Women, Mama Tried, Row Jimmy\n\nSet 2: Scarlet Begonias > Fire On The Mountain, Estimated Prophet, St. Stephen > Not Fade Away > St. Stephen > Morning Dew\n\nEncore: One More Saturday Night",
  "source": "Soundboard",
  "taper": "Betty Cantor-Jackson",
  "transferer": "Bill Suckalooskey",
  "lineage": "Master Reel > Cassette Master > DAT > CDR > FLAC",
  "totalTracks": 18,
  "totalReviews": 127
}
```

**Notes**:
- Multi-line fields (`setlist`, `description`, `lineage`) contain actual newline characters (`\n`)
- Null fields are omitted or set to `null`
- Counts (`totalTracks`, `totalReviews`) are always present (default to 0)

### Cached Tracks Format

**File**: `{recordingId}.tracks.json`

**Content**: Array of `Track` objects

**Example**:
```json
[
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
  },
  {
    "name": "gd77-05-08d1t02.flac",
    "title": "Loser",
    "trackNumber": 2,
    "duration": "456.78",
    "format": "FLAC",
    "size": "37654321",
    "bitrate": "1000",
    "sampleRate": "44100",
    "isAudio": true
  },
  {
    "name": "gd77-05-08d1t03.flac",
    "title": null,
    "trackNumber": 3,
    "duration": null,
    "format": "FLAC",
    "size": null,
    "bitrate": null,
    "sampleRate": null,
    "isAudio": true
  }
]
```

**Notes**:
- Array is sorted by `trackNumber`
- Only audio files are included (filtered)
- Missing metadata fields are `null`
- `isAudio` is always `true`
- Numeric fields (`duration`, `size`, `bitrate`, `sampleRate`) are stored as strings

### Cached Reviews Format

**File**: `{recordingId}.reviews.json`

**Content**: Array of `Review` objects

**Example**:
```json
[
  {
    "reviewer": "deadhead1977",
    "title": "Best show ever!",
    "body": "This is the definitive version of Scarlet > Fire. The energy is incredible and the sound quality is pristine. Betty's SBD recordings are unmatched.",
    "rating": 5,
    "reviewDate": "2020-03-15"
  },
  {
    "reviewer": "gratefullistener",
    "title": "Essential listening",
    "body": "Cornell '77 lives up to the hype. Essential for any Dead fan.",
    "rating": 5,
    "reviewDate": "2019-11-22"
  },
  {
    "reviewer": "tourhead",
    "title": null,
    "body": null,
    "rating": 4,
    "reviewDate": "2021-05-08"
  }
]
```

**Notes**:
- Empty array `[]` if no reviews exist
- All fields are optional (can be `null`)
- Some reviews may only have a rating with no text
- Rating is typically 1-5 (integer)

### Cache Expiry

All cache files use **file modification time** to determine expiry:

```
if (currentTime - fileModificationTime) > 24 hours:
    cache is expired
else:
    cache is valid
```

**Platform implementations**:
- **Android**: `file.lastModified()` returns milliseconds since epoch
- **iOS**: `FileManager.attributesOfItem()` returns `NSFileModificationDate`

### Working with Cached Data

**Reading cache**:
1. Check if file exists
2. Check file modification time (must be < 24 hours old)
3. Read file contents as string
4. Deserialize JSON to domain model
5. Return data

**Writing cache**:
1. Serialize domain model to JSON string
2. Write string to cache file (overwrites if exists)
3. File modification time is automatically set to now

**Cache invalidation**:
- **Per recording**: Delete all three files for that recording ID
- **All cache**: Delete all `.json` files in cache directory

## References

### External Documentation

- [Internet Archive API Documentation](https://archive.org/services/docs/api/)
- [Internet Archive Metadata Schema](https://archive.org/services/docs/api/metadata-schema/)
- [Grateful Dead Collection](https://archive.org/details/GratefulDead)
- [Live Music Archive (etree)](https://archive.org/details/etree)

### Related Documentation

- `architecture.md` - Overall architecture patterns
- `domain-models.md` - Complete domain model specifications
- `api-samples/index.md` - Real Archive.org API response samples demonstrating edge cases
- `internet-archive-integration.md` - Detailed Archive.org specifics
- `caching-strategy.md` - App-wide caching patterns (to be written)
- `error-handling.md` - Error handling conventions (to be written)

## Future Enhancements

1. **Search API**: Implement Archive.org advanced search
2. **Collection browsing**: Browse Grateful Dead collections
3. **Related recordings**: Find similar shows by date/venue
4. **Offline mode**: Full offline support with downloads
5. **Image fetching**: Support for show artwork/posters
6. **Metadata updates**: Handle recording updates from Archive.org
7. **Rate limiting**: Add client-side rate limiting if needed
8. **Metrics**: Track API performance and cache hit rates
