package com.deadly.v2.core.database.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shows",
    indices = [
        Index(value = ["date"]),
        Index(value = ["year"]),
        Index(value = ["yearMonth"]),
        Index(value = ["venueName"]),
        Index(value = ["city"]),
        Index(value = ["state"])
    ]
)
data class ShowEntity(
    @PrimaryKey
    val showId: String,           // "1977-05-08-barton-hall-cornell-u-ithaca-ny-usa"
    
    // Date components for flexible searching
    val date: String,             // "1977-05-08" (full date)
    val year: Int,                // 1977 (indexed)
    val month: Int,               // 5 (indexed)
    val yearMonth: String,        // "1977-05" (indexed)
    
    // Show metadata
    val band: String,             // "Grateful Dead"
    val url: String?,             // Jerry Garcia URL
    
    // Venue data (denormalized for fast search - no FK needed)
    val venueName: String,        // "Barton Hall, Cornell University"
    val city: String?,            // "Ithaca"
    val state: String?,           // "NY"
    val country: String = "USA",
    val locationRaw: String?,     // "Ithaca, NY" (original)
    
    // Setlist data
    val setlistStatus: String?,   // "found", "not_found", etc.
    val setlistRaw: String?,      // JSON string of full setlist for UI display
    val songList: String?,        // "Scarlet Begonias,Fire on the Mountain" (comma-separated for FTS)
    
    // Band lineup data
    val lineupStatus: String?,    // "found", "missing", etc.
    val lineupRaw: String?,       // JSON string of full lineup for UI display
    val memberList: String?,      // "Jerry Garcia,Bob Weir,Phil Lesh" (comma-separated for FTS)
    
    // Multiple shows same date/venue (rare but happens)
    val showSequence: Int = 1,    // 1, 2, 3... for multiple shows
    
    // Recording data
    val recordingsRaw: String?,       // JSON array string of recording IDs ["rec1", "rec2"]
    val recordingCount: Int = 0,
    val bestRecordingId: String?,
    val averageRating: Float?,
    val totalReviews: Int = 0,
    
    // Library status (will be used by V2 features later)
    val isInLibrary: Boolean = false,
    val libraryAddedAt: Long?,
    
    // Timestamps
    val createdAt: Long,
    val updatedAt: Long
)

@Fts4
@Entity(tableName = "shows_fts")
data class ShowFtsEntity(
    val searchText: String  // Combined searchable text: date + venue + location + songList
)