package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * V2 Show domain model
 * 
 * Represents a Grateful Dead concert as a pure domain entity.
 * Contains show-level metadata and references to recordings,
 * but not the full recording objects themselves.
 */
@Serializable
data class Show(
    val id: String,
    val date: String,
    val year: Int,
    val band: String,
    val venue: Venue,
    val location: Location,
    val setlist: Setlist?,
    val lineup: Lineup?,
    
    // Recording references
    val recordingIds: List<String>,
    val bestRecordingId: String?,
    
    // Show-level stats (precomputed from recordings)
    val recordingCount: Int,
    val averageRating: Float?,
    val totalReviews: Int,
    
    // User state  
    val isInLibrary: Boolean,
    val libraryAddedAt: Long?
) {
    /**
     * Display title for the show
     */
    val displayTitle: String
        get() = "${venue.name} - $date"
    
    /**
     * Whether this show has ratings
     */
    val hasRating: Boolean
        get() = averageRating != null && averageRating > 0f
    
    /**
     * Formatted rating display
     */
    val displayRating: String
        get() = averageRating?.let { "%.1f★".format(it) } ?: "Not Rated"
    
    /**
     * Whether this show has multiple recordings
     */
    val hasMultipleRecordings: Boolean
        get() = recordingCount > 1
}