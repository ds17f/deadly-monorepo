# API Samples

This directory contains real Archive.org API response samples collected for testing and documentation purposes. These samples demonstrate edge cases and data variations that parsers must handle correctly.

## Purpose

These JSON files serve multiple purposes:

1. **Parser Testing** - Use these samples to verify your Archive.org API parser handles edge cases
2. **Implementation Reference** - See real-world data structures returned by the Archive.org metadata API
3. **Edge Case Documentation** - Understand the inconsistencies and variations in Archive.org responses

## Collection Method

- **Source**: Archive.org metadata API (`https://archive.org/metadata/{identifier}`)
- **Date Collected**: 2025-10-13
- **Truncation**: Files array limited to first 15-20 entries, reviews limited to first 5 entries
- **Selection Criteria**: Recording identifiers sampled from production data that demonstrated parser challenges

## Samples

### baseline-complete.json

**Recording**: `gd77-05-08.sbd.hicks.4982.sbeok.shnf` (Cornell '77)
**Lines**: 367
**Description**: Well-formed metadata with complete fields

**Demonstrates**:
- Complete metadata fields (date, venue, description, setlist, source, taper, transferer, lineage)
- `collection` field as **array**: `["GratefulDead", "etree", "stream_only"]`
- Standard audio file structure (FLAC format)
- Multiple reviews with ratings
- Typical track structure with metadata

**Use this sample to**:
- Test array handling for `collection` field
- Verify baseline parsing of complete data
- Validate track extraction and ordering

### edge-null-fields.json

**Recording**: `gd1965-11-01.sbd.bershaw.5417.sbeok.shnf` (1965 show)
**Lines**: 385
**Description**: Early era recording with sparse metadata

**Demonstrates**:
- `taper` field as **null** (not missing, explicitly null)
- `source` field as **null** (not missing, explicitly null)
- `collection` field as **array**: `["GratefulDead", "etree"]`
- Minimal description and venue information
- No setlist or lineage data

**Use this sample to**:
- Test null field handling in mapper
- Verify parser doesn't crash on missing taper/source
- Validate that optional fields work correctly with null values

## Edge Case Summary

| Edge Case | Field | Type | Sample(s) | Notes |
|-----------|-------|------|-----------|-------|
| Array field | `collection` | String OR Array | baseline-complete.json, edge-null-fields.json | FlexibleStringSerializer should take first element |
| Null field | `taper` | String OR null | edge-null-fields.json | Must handle gracefully, default to null |
| Null field | `source` | String OR null | edge-null-fields.json | Must handle gracefully, default to null |
| Missing field | `setlist` | String OR undefined | edge-null-fields.json | Common in early recordings |
| Missing field | `lineage` | String OR undefined | edge-null-fields.json | Not always documented |

## Known Variations Not Yet Captured

Based on implementation notes, these edge cases may exist but are not yet represented in samples:

- `venue` as array instead of string
- `description` as array instead of string
- Extremely sparse metadata (only identifier and title)
- Recordings with zero reviews
- Recordings with zero audio files

If you encounter these variations in the wild, consider adding them to this collection.

## Testing Recommendations

When implementing an Archive.org parser, test against these samples:

1. **Parse each sample successfully** - No crashes or exceptions
2. **Verify field mappings** - Check that domain models match expected values
3. **Test array handling** - Ensure `collection` array is converted to first element string
4. **Test null handling** - Ensure null `taper` and `source` don't break parser
5. **Test track extraction** - Verify audio files are correctly filtered and ordered
6. **Test review parsing** - Ensure reviews are extracted properly

## Usage Example

```bash
# Fetch fresh sample (requires curl)
curl -s "https://archive.org/metadata/gd77-05-08.sbd.hicks.4982.sbeok.shnf" | jq . > test-sample.json

# Validate against your parser
your-parser-tool test-sample.json
```

## References

- **Domain Models**: See `domain-models.md` for field definitions and types
- **API Integration**: See `api-integration.md` for mapper algorithms and caching
- **Archive.org API**: `https://archive.org/metadata/{identifier}`

## Updates

This collection should be updated when:

1. New edge cases are discovered in production
2. Archive.org changes their API response format
3. Additional variations are needed for testing

To add a new sample:

1. Fetch the raw API response: `curl -s "https://archive.org/metadata/{identifier}"`
2. Truncate large arrays (keep first 15-20 files, first 5 reviews)
3. Save as `edge-{description}.json` or `baseline-{description}.json`
4. Update this index with description and edge cases demonstrated
5. Add to Edge Case Summary table if introducing new variations
