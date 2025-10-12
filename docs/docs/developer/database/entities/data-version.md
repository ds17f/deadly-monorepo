# DataVersionEntity (data_version_v2)

Singleton entity that tracks the currently imported metadata package version. Stores version information, import metadata, and statistics about the imported dataset.

---

## Purpose

`DataVersionEntity` provides version tracking:

- **Version identifier** - Which metadata package is installed
- **Import metadata** - When imported, git commit, build info
- **Statistics** - Total shows, venues, files imported
- **Update detection** - Determine if newer version available
- **Audit trail** - Track data provenance

**Why Singleton**: Only one version can be active at a time. Fixed `id = 1` ensures single row.

---

## Schema

### Table Definition

```sql
CREATE TABLE data_version_v2 (
    id INTEGER PRIMARY KEY DEFAULT 1,

    -- Version info
    data_version TEXT NOT NULL,
    package_name TEXT NOT NULL,
    version_type TEXT NOT NULL,
    description TEXT,

    -- Import info
    imported_at INTEGER NOT NULL,
    git_commit TEXT,
    git_tag TEXT,
    build_timestamp TEXT,

    -- Statistics
    total_shows INTEGER NOT NULL DEFAULT 0,
    total_venues INTEGER NOT NULL DEFAULT 0,
    total_files INTEGER NOT NULL DEFAULT 0,
    total_size_bytes INTEGER NOT NULL DEFAULT 0,

    CHECK (id = 1)  -- Enforce singleton
);
```

**Singleton Constraint**: `id = 1` and `PRIMARY KEY` ensure only one row exists

**No Indexes**: Single-row table doesn't need indexes

---

## Fields

### Primary Key

#### `id` (INTEGER, PRIMARY KEY, DEFAULT 1, CHECK = 1)

Singleton identifier - always 1.

**Value**: `1` (only value allowed)

**Constraint**: `CHECK (id = 1)` prevents other values

**Usage**: Enforces single-row table pattern

---

### Version Information

#### `data_version` (TEXT, NOT NULL)

Semantic version of metadata package.

**Format**: Semantic versioning (MAJOR.MINOR.PATCH)

**Example**: `"2.0.0"`, `"2.1.3"`

**Source**: `manifest.json` from dead-metadata package

**Usage**:
- Display "Data version: 2.0.0"
- Compare with remote version for updates
- Breaking change detection (MAJOR version changes)

---

#### `package_name` (TEXT, NOT NULL)

Name of metadata package.

**Value**: `"Deadly Metadata"` (constant)

**Source**: `manifest.json`

**Usage**: Display, logging

---

#### `version_type` (TEXT, NOT NULL)

Release type identifier.

**Values**: `"release"`, `"beta"`, `"dev"`, `"snapshot"`

**Example**: `"release"`

**Source**: `manifest.json`

**Usage**:
- Display badge ("Beta" warning)
- Update channel selection (beta users get beta updates)
- Analytics

---

#### `description` (TEXT, NULLABLE)

Human-readable description of this version.

**Example**: `"September 2024 update: Added 50 new shows from Archive.org, updated ratings, fixed venue data for Fillmore East."`

**Source**: `manifest.json` (optional field)

**Nullable**: Older packages may not have description

**Usage**: Display in "About" screen, changelog

---

### Import Metadata

#### `imported_at` (INTEGER, NOT NULL)

Timestamp when metadata was imported.

**Format**: Unix timestamp (milliseconds)

**Example**: `1678901234000`

**Source**: `System.currentTimeMillis()` during import

**Usage**:
- Display "Data imported March 15, 2023"
- Stale data detection ("Last updated 90 days ago")
- Analytics

---

#### `git_commit` (TEXT, NULLABLE)

Git commit SHA from dead-metadata repository.

**Format**: 40-character hexadecimal SHA

**Example**: `"a3f2d8b1c4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9"`

**Source**: `manifest.json` (optional field)

**Nullable**: May not be present in all builds

**Usage**:
- Exact provenance tracking
- Link to GitHub commit
- Debugging (reproduce exact dataset)

---

#### `git_tag` (TEXT, NULLABLE)

Git tag from dead-metadata repository.

**Format**: Tag name

**Example**: `"v2.0.0"`, `"v2.1.3-beta"`

**Source**: `manifest.json` (optional field)

**Nullable**: Dev builds may not have tag

**Usage**:
- Display "Release v2.0.0"
- Link to GitHub release notes
- Correspondence with `data_version`

---

#### `build_timestamp` (TEXT, NULLABLE)

ISO timestamp when metadata package was built.

**Format**: ISO 8601 timestamp

**Example**: `"2024-03-15T10:30:45Z"`

**Source**: `manifest.json` (optional field)

**Nullable**: May not be present

**Usage**:
- Display "Built March 15, 2024"
- Provenance tracking
- Debugging

---

### Statistics

#### `total_shows` (INTEGER, NOT NULL, DEFAULT 0)

Total number of shows imported.

**Example**: `2400`

**Source**: Counted during import from shows.json

**Usage**:
- Display "2,400 shows in catalog"
- Sanity check (detect incomplete imports)
- Analytics

---

#### `total_venues` (INTEGER, NOT NULL, DEFAULT 0)

Total number of unique venues in dataset.

**Example**: `900`

**Source**: Counted during import (distinct venue IDs)

**Usage**:
- Display "900 venues"
- Statistics page
- Analytics

---

#### `total_files` (INTEGER, NOT NULL, DEFAULT 0)

Total number of files in metadata ZIP.

**Example**: `12`

**Source**: Counted during ZIP extraction

**Usage**:
- Import verification
- Debugging (detect corrupted ZIPs)

---

#### `total_size_bytes` (INTEGER, NOT NULL, DEFAULT 0)

Total uncompressed size of metadata files in bytes.

**Example**: `52428800` (50 MB)

**Source**: Sum of file sizes from ZIP

**Usage**:
- Display "50 MB of data"
- Storage statistics
- Download progress calculation

---

## Singleton Pattern

### Why Singleton?

**Requirement**: Only one metadata version active at a time

**Alternatives**:
- Multiple rows with "active" flag → complexity, potential bugs
- No table, store in SharedPreferences → inconsistent with database schema
- Separate table per version → schema migration nightmare

**Chosen Approach**: Single row with `id = 1` constraint

### Enforcement

**PRIMARY KEY**: Prevents duplicate `id = 1` rows

**CHECK Constraint**: `CHECK (id = 1)` prevents other IDs

**Insert Logic**:
```sql
INSERT OR REPLACE INTO data_version_v2 (id, ...) VALUES (1, ...);
```

**Always uses `id = 1`**, so `REPLACE` strategy updates existing row

---

## Common Queries

### Get Current Version

```sql
SELECT * FROM data_version_v2 WHERE id = 1;
```

**Performance**: O(1) via PRIMARY KEY

**Returns**: Single row or NULL if no data imported

---

### Get Version String

```sql
SELECT data_version FROM data_version_v2 WHERE id = 1;
```

**Performance**: O(1)

**Returns**: String like `"2.0.0"` or NULL

---

### Check if Data Exists

```sql
SELECT COUNT(*) > 0 FROM data_version_v2;
```

**Performance**: Fast

**Returns**: `true` if any data imported, `false` otherwise

**Usage**: First-run detection

---

### Update Version (Singleton Insert)

```sql
INSERT OR REPLACE INTO data_version_v2 (
    id, data_version, package_name, version_type, description,
    imported_at, git_commit, git_tag, build_timestamp,
    total_shows, total_venues, total_files, total_size_bytes
) VALUES (
    1, '2.0.0', 'Deadly Metadata', 'release', 'September 2024 update',
    1678901234000, 'a3f2d8b...', 'v2.0.0', '2024-03-15T10:30:45Z',
    2400, 900, 12, 52428800
);
```

**Effect**: Updates existing row (due to `REPLACE` strategy)

**Usage**: Import new metadata version

---

### Delete Version (for reimport)

```sql
DELETE FROM data_version_v2;
```

**Effect**: Removes version record

**Usage**: Clear before reimporting, factory reset

---

## Common Operations

### Record Import

```kotlin
suspend fun recordImport(manifest: ManifestJson, stats: ImportStats) {
    val entity = DataVersionEntity(
        id = 1,
        dataVersion = manifest.version,
        packageName = manifest.packageName,
        versionType = manifest.versionType,
        description = manifest.description,
        importedAt = System.currentTimeMillis(),
        gitCommit = manifest.gitCommit,
        gitTag = manifest.gitTag,
        buildTimestamp = manifest.buildTimestamp,
        totalShows = stats.showCount,
        totalVenues = stats.venueCount,
        totalFiles = stats.fileCount,
        totalSizeBytes = stats.totalBytes
    )
    dataVersionDao.insertOrUpdate(entity)
}
```

---

### Check for Updates

```kotlin
suspend fun checkForUpdate(remoteVersion: String): Boolean {
    val currentVersion = dataVersionDao.getCurrentVersion() ?: return true

    return compareVersions(currentVersion, remoteVersion) < 0
}

fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split(".").map { it.toInt() }
    val parts2 = v2.split(".").map { it.toInt() }

    for (i in 0 until maxOf(parts1.size, parts2.size)) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1.compareTo(p2)
    }
    return 0
}
```

---

### Display Version Info

```kotlin
suspend fun getVersionInfo(): String {
    val version = dataVersionDao.getCurrentDataVersion() ?: return "No data imported"

    return buildString {
        append("Version: ${version.dataVersion}")
        if (version.versionType != "release") {
            append(" (${version.versionType})")
        }
        append("\n")
        append("Imported: ${formatDate(version.importedAt)}")
        append("\n")
        append("Shows: ${version.totalShows.format()}")
        append("\n")
        append("Venues: ${version.totalVenues.format()}")
        version.description?.let {
            append("\n\n$it")
        }
    }
}
```

---

### Detect First Run

```kotlin
suspend fun isFirstRun(): Boolean {
    return !dataVersionDao.hasDataVersion()
}

// Usage
if (isFirstRun()) {
    // Show onboarding, trigger initial data import
    startDataImport()
}
```

---

## Manifest Format

The `manifest.json` file in dead-metadata package provides version information:

### Example manifest.json

```json
{
  "version": "2.0.0",
  "packageName": "Deadly Metadata",
  "versionType": "release",
  "description": "September 2024 update: Added 50 new shows, updated ratings.",
  "buildTimestamp": "2024-09-15T10:30:45Z",
  "gitCommit": "a3f2d8b1c4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9",
  "gitTag": "v2.0.0",
  "files": [
    {"name": "shows.json", "size": 5242880, "sha256": "abc123..."},
    {"name": "recordings.json", "size": 3145728, "sha256": "def456..."}
  ],
  "statistics": {
    "totalShows": 2400,
    "totalRecordings": 16800,
    "totalVenues": 900,
    "dateRange": {
      "earliest": "1965-11-03",
      "latest": "1995-07-09"
    }
  }
}
```

### Parsing Manifest

```kotlin
@Serializable
data class ManifestJson(
    val version: String,
    val packageName: String,
    val versionType: String,
    val description: String? = null,
    val buildTimestamp: String? = null,
    val gitCommit: String? = null,
    val gitTag: String? = null,
    val files: List<FileInfo>? = null,
    val statistics: Statistics? = null
)

@Serializable
data class FileInfo(
    val name: String,
    val size: Long,
    val sha256: String
)

@Serializable
data class Statistics(
    val totalShows: Int,
    val totalRecordings: Int,
    val totalVenues: Int
)
```

---

## Update Flow

### Checking for Updates

1. Fetch remote `manifest.json` from dead-metadata GitHub releases
2. Parse remote version
3. Compare with local version
4. If remote > local, prompt user to update

### Performing Update

1. Download new metadata ZIP
2. Verify ZIP integrity (SHA256)
3. **Transaction begin**
4. Delete old data (shows, recordings, etc.)
5. Import new data from ZIP
6. Update `data_version_v2` table with new version
7. **Transaction commit**
8. Notify user of completion

### Rollback on Failure

If import fails mid-transaction:
- Transaction rolls back
- Old data remains intact
- Old version record unchanged
- User can retry

---

## Code Locations

### Android

- **Entity**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/entities/DataVersionEntity.kt:7`
- **DAO**: `androidApp/v2/core/database/src/main/java/com/deadly/v2/core/database/dao/DataVersionDao.kt:10`

### iOS

TBD (to be implemented)

---

## Implementation Notes

### Why `data_version_v2` Table Name?

**Suffix `_v2`**: Distinguishes from old V1 database schema

**Migration Path**: Allows V1 and V2 tables to coexist during migration

**Future**: If V3 schema needed, can use `data_version_v3`

---

### Singleton vs Configuration

**Alternative**: Store version in app configuration/SharedPreferences

**Why Database**:
- Transactional consistency (version updated atomically with data)
- Schema documentation (version is part of data model)
- Queryable (can JOIN with other tables if needed)
- Backup/restore (version exported with data)

---

### Version Comparison

**Semantic Versioning**: `MAJOR.MINOR.PATCH`

**Breaking Changes**: MAJOR version increment signals schema changes

**Example**:
- `2.0.0` → `2.1.0`: Compatible update (new shows added)
- `2.1.0` → `3.0.0`: Breaking change (schema changed, requires migration)

**Implementation**:
```kotlin
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) {
    companion object {
        fun parse(version: String): SemanticVersion {
            val parts = version.split(".").map { it.toInt() }
            return SemanticVersion(
                major = parts.getOrElse(0) { 0 },
                minor = parts.getOrElse(1) { 0 },
                patch = parts.getOrElse(2) { 0 }
            )
        }
    }

    fun isBreakingChange(other: SemanticVersion): Boolean {
        return other.major > this.major
    }

    operator fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }
}
```

---

## See Also

- [Data Sources](../data-sources.md#metadata-package-structure) - Manifest format details
- [Design Philosophy](../design-philosophy.md) - Why database-first approach
- [ShowEntity](shows.md) - Main entity managed by version tracking
