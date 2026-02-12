package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * V2 Recording domain model
 * 
 * Represents a specific recording/taping of a Grateful Dead show.
 * Contains basic recording metadata from the database. Additional 
 * metadata (title, tracks, format) will be loaded from Archive.org API.
 */
@Serializable
data class Recording(
    val identifier: String,
    val showId: String,
    val sourceType: RecordingSourceType,
    val rating: Double,
    val reviewCount: Int,
    val taper: String? = null,
    val source: String? = null,
    val lineage: String? = null,
    val sourceTypeString: String? = null
    // Future Archive API integration will add:
    // val title: String - from Archive.org metadata
    // val tracks: List<Track> - from Archive.org files listing  
    // val format: AudioFormat - detected from track analysis
) {
    /**
     * Whether this recording has a meaningful rating
     */
    val hasRating: Boolean
        get() = rating > 0.0 && reviewCount > 0
    
    /**
     * Formatted rating display
     */
    val displayRating: String
        get() = if (hasRating) "%.1f/5.0 (%d reviews)".format(rating, reviewCount) else "Not Rated"
    
    /**
     * Simple display title using available data
     */
    val displayTitle: String
        get() = "${sourceType.displayName} • $displayRating"
}

/**
 * Recording source types with safe string parsing
 */
@Serializable
enum class RecordingSourceType(val displayName: String) {
    SOUNDBOARD("SBD"),
    AUDIENCE("AUD"), 
    FM("FM"),
    MATRIX("Matrix"),
    REMASTER("Remaster"),
    UNKNOWN("Unknown");
    
    companion object {
        /**
         * Parse source type from string with safe fallback
         */
        fun fromString(value: String?): RecordingSourceType {
            return when (value?.uppercase()) {
                "SBD", "SOUNDBOARD" -> SOUNDBOARD
                "AUD", "AUDIENCE" -> AUDIENCE
                "FM" -> FM
                "MATRIX", "MTX" -> MATRIX
                "REMASTER" -> REMASTER
                else -> UNKNOWN
            }
        }
    }
}