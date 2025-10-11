# TODO: Internet Archive Integration Documentation

**Priority**: Critical
**Status**: Not Started
**Estimated Effort**: 6-8 hours

## Problem

The app's core functionality revolves around streaming Grateful Dead concerts from the Internet Archive, but there is no documentation explaining:
- Which Internet Archive collections are used
- How the data is discovered and fetched
- The data normalization process mentioned in the app description
- Metadata structure and how it's parsed
- Audio streaming implementation
- How the app handles Internet Archive's specific quirks

This makes it difficult to:
- Understand what data is available
- Debug issues with specific recordings
- Add support for additional collections
- Handle changes in Internet Archive's data structure
- Explain to users why certain shows appear or don't appear

## What Needs Documentation

### 1. Internet Archive Collections

Document which specific collections are used:
- **GratefulDead** collection?
- **etree** (Live Music Archive)?
- Other related collections?

For each collection, document:
- Collection identifier
- Number of recordings
- Audio quality standards
- Metadata quality
- Update frequency
- Access restrictions (if any)

Example:
```
Collection: GratefulDead
Identifier: GratefulDead
URL: https://archive.org/details/GratefulDead
Contains: ~13,000 recordings
Formats: FLAC, MP3, OGG
SoundBoard: Yes/No
Audience: Yes/No
```

### 2. Metadata Structure

The Internet Archive has complex metadata. Document:

#### Core Metadata Fields
- **identifier**: Unique show ID
- **title**: Show title
- **date**: Performance date (format?)
- **venue**: Venue name
- **coverage**: Location (city, state)
- **creator**: Band members
- **year**: Year of performance
- **collection**: Parent collection
- **subject**: Tags and keywords
- **description**: Show notes

#### Audio File Metadata
- **format**: File formats available
- **track**: Track number and title
- **length**: Duration
- **size**: File size
- **bitrate**: Audio quality

#### How to Access
Document the API calls to retrieve metadata:
```
GET https://archive.org/metadata/{identifier}
Response: Full metadata JSON
```

Show examples of actual responses and how they're parsed.

### 3. Data Normalization Process

The app description claims "normalized data for seamless searching". Document:

#### What Gets Normalized
- **Dates**: Different formats → Standard ISO date
- **Venues**: Various spellings → Canonical names
- **Band Members**: Name variations → Standard names
- **Locations**: City/state variations → Consistent format
- **Song Titles**: Different spellings → Standard names
- **Audio Quality**: Different descriptors → Standard ratings

#### Where Normalization Happens
- Is there a preprocessing step?
- Does it happen in the mapper layer?
- Is normalized data cached in the local database?
- Is there a manual curation component?

#### Example Transformations
```
Raw: "Grateful Dead Live at Fillmore West on 1970-02-28"
Normalized: {
  band: "Grateful Dead",
  venue: "Fillmore West",
  city: "San Francisco",
  state: "CA",
  date: "1970-02-28",
  venue_id: "fillmore-west-sf"
}
```

### 4. Search Implementation

Document how search works across Internet Archive:

#### Search Methods
- **Advanced Search API**: Using `advancedsearch.php`
- **Full-Text Search**: Searching show descriptions
- **Field-Specific Search**: By venue, date, song, etc.

#### Search Query Construction
Show how user searches translate to API queries:
- Search by song name → `?q=subject:"Dark Star"`
- Search by venue → `?q=coverage:*Fillmore*`
- Search by date range → `?q=date:[1970 TO 1980]`
- Search by band member → `?q=creator:"Jerry Garcia"`

#### Search Results Processing
- How results are ranked
- Pagination handling
- Result deduplication
- Filtering applied

### 5. Audio Streaming

Document how audio is streamed from Internet Archive:

#### File Discovery
- How to find audio files for a show
- Preferred format selection (FLAC > MP3 > OGG?)
- Multiple versions handling (soundboard vs audience)

#### Streaming URLs
```
https://archive.org/download/{identifier}/{filename}
Example:
https://archive.org/download/gd70-02-28.sbd.miller.91178.flac16/gd70-02-28d1t01.flac
```

#### Media Player Integration
- How Media3/ExoPlayer is configured
- Buffering strategy
- Format switching (quality levels)
- Playlist generation from setlists

#### Offline Capabilities
- Can shows be downloaded?
- How is offline playback implemented?
- Storage management

### 6. Setlist and Track Information

Document how setlists are extracted:

#### Setlist Sources
- Internet Archive metadata
- External sources (setlist.fm?)
- Manual curation?

#### Track Parsing
- How individual songs are identified
- Track timing extraction
- Set breaks identification
- Encore handling

#### Song Database
- Is there a local song database?
- How are songs matched across shows?
- Handling of song variations ("Dark Star" vs "Dark Star >")

### 7. Caching Strategy

Document how Internet Archive data is cached:

#### What Gets Cached
- Metadata for recently viewed shows
- Search results
- Favorite shows
- Playlist data
- Album art/imagery

#### Cache Invalidation
- How often is data refreshed?
- Forced refresh mechanism
- Handling stale data

#### Local Database
- What's stored in Room database?
- Sync strategy
- Offline-first approach?

### 8. Performance Optimization

Document optimizations for Internet Archive integration:

#### Request Optimization
- Batch metadata requests?
- Parallel fetching strategy
- Request prioritization

#### Data Compression
- Are responses compressed?
- Parsing optimization

#### Image Loading
- Show imagery/posters
- Thumbnail generation
- Image caching

### 9. Error Handling

Document specific Internet Archive errors:

#### Common Issues
- Show no longer available
- Audio files missing or corrupted
- Metadata incomplete
- Rate limiting (if any)
- Archive.org downtime

#### User-Facing Messages
- How errors are communicated
- Retry mechanisms
- Fallback behaviors

### 10. Internet Archive Compliance

Document compliance with Internet Archive terms:

#### Attribution
- Is attribution displayed?
- Where and how?

#### Terms of Service
- Any restrictions on usage
- Rate limiting compliance
- Caching limitations
- Commercial use considerations

#### Licensing
- Content licensing
- Display of licensing info
- User expectations

## Structure

Create: `docs/docs/developer/internet-archive.md`

Suggested outline:
```markdown
# Internet Archive Integration

## Overview
[What is Internet Archive, why it's perfect for this app]

## Collections
[Which collections are accessed]

## Metadata
[Structure, fields, examples]

## Data Normalization
[Process, examples, where it happens]

## Search
[How search is implemented]

## Audio Streaming
[Streaming implementation]

## Setlists and Tracks
[Track information extraction]

## Caching
[What's cached and why]

## Performance
[Optimization strategies]

## Error Handling
[Common issues and solutions]

## Compliance
[Terms of service, attribution, licensing]

## Testing
[How to test with Internet Archive data]

## References
[Links to Internet Archive docs]
```

## Research Required

To write this documentation, investigate:

1. **API calls**: Search codebase for `archive.org` URLs
2. **ArchiveApiService**: What endpoints are actually used?
3. **ArchiveMetadataResponse**: What fields are parsed?
4. **ArchiveMapper**: What transformations occur?
5. **Database schema**: What Internet Archive data is stored locally?
6. **Search implementation**: How are queries constructed?
7. **Media player**: How are streaming URLs generated?
8. **Normalization code**: Where does data cleanup happen?
9. **Error handling**: How are IA-specific errors handled?
10. **Caching**: What's cached and for how long?

## Code References

Key files to examine:
- `androidApp/v2/core/network/archive/api/ArchiveApiService.kt`
- `androidApp/v2/core/network/archive/model/ArchiveMetadataResponse.kt`
- `androidApp/v2/core/network/archive/mapper/ArchiveMapper.kt`
- `androidApp/v2/core/database/` (database entities)
- `androidApp/v2/core/search/` (search implementation)
- `androidApp/v2/core/media/` or `androidApp/v2/core/player/` (streaming)
- Any normalization utilities

## External Resources

Research and link to:
- [Internet Archive API Documentation](https://archive.org/services/docs/api/)
- [Internet Archive Metadata](https://archive.org/services/docs/api/metadata-schema/)
- [Advanced Search](https://archive.org/advancedsearch.php)
- [Live Music Archive](https://archive.org/details/etree)
- [GratefulDead Collection](https://archive.org/details/GratefulDead)

## Checklist

- [ ] Identify all collections accessed
- [ ] Document metadata structure with examples
- [ ] Explain data normalization process
- [ ] Document search API usage
- [ ] Explain audio streaming implementation
- [ ] Document setlist extraction
- [ ] Document caching strategy
- [ ] Document performance optimizations
- [ ] Document error handling
- [ ] Document compliance with IA terms
- [ ] Add code examples for common tasks
- [ ] Include actual API response examples
- [ ] Create flow diagrams (search, playback)
- [ ] Link to Internet Archive documentation
- [ ] Document testing approaches
- [ ] Add troubleshooting section
- [ ] Document both Android and iOS implementations

## Success Criteria

A developer should be able to:
- Understand exactly which Internet Archive collections are used
- Explain how data flows from IA to the UI
- Add support for new collections
- Debug issues with specific recordings
- Understand the normalization process
- Modify search functionality
- Troubleshoot streaming problems
- Explain to users why certain features work the way they do

## Notes

This documentation is critical because the Internet Archive is not a typical API. It has:
- Unique metadata structure
- Multiple audio formats and quality levels
- Complex search capabilities
- Specific terms of service
- Occasional data quality issues

Understanding how the app works with Internet Archive is essential for maintaining and extending the app's core functionality.

This documentation should be written for both developers (technical implementation) and for product people (understanding capabilities and limitations).
