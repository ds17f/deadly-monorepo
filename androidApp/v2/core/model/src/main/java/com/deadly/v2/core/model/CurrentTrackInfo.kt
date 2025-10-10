package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * Comprehensive track information model for V2 architecture
 * 
 * Central data model containing all metadata and playback state for the currently playing track.
 * Used throughout V2 services and UI components to ensure consistent track data representation.
 * 
 * FOUNDATION FIRST: Part of Phase 0 architecture improvements to centralize track metadata.
 */
@Serializable
data class CurrentTrackInfo(
    // Track identification
    val trackUrl: String,
    val recordingId: String,
    val showId: String,
    
    // Denormalized show data for immediate display
    val showDate: String,           // e.g., "1977-05-08"
    val venue: String?,             // e.g., "Barton Hall"
    val location: String?,          // e.g., "Cornell University, Ithaca, NY"
    
    // Track-specific data
    val songTitle: String,          // e.g., "Scarlet Begonias"
    val artist: String,             // e.g., "Grateful Dead"
    val album: String,              // e.g., "May 8, 1977 - Barton Hall"
    val trackNumber: Int?,          // e.g., 5
    val filename: String,           // Original filename
    val format: String,             // e.g., "mp3", "flac"
    
    // Playback state
    val playbackState: PlaybackState,
    val position: Long,             // Current position in milliseconds
    val duration: Long              // Track duration in milliseconds
) {
    /**
     * Formatted display title - parsed song title
     */
    val displayTitle: String
        get() = if (songTitle.isNotBlank()) {
            songTitle
        } else {
            "Unknown Track"
        }
    
    /**
     * Formatted show date for display
     * Format: "Jul 17, 1976"
     */
    val displayDate: String
        get() = formatShowDate(showDate)
    
    /**
     * Formatted subtitle with date and venue
     * Uses location fallback when venue is unknown
     */
    val displaySubtitle: String
        get() = buildString {
            if (showDate.isNotBlank()) {
                append(showDate)
            }
            if (!venue.isNullOrBlank() && venue != "Unknown Venue") {
                if (showDate.isNotBlank()) append(" - ")
                append(venue)
            } else if (!location.isNullOrBlank()) {
                if (showDate.isNotBlank()) append(" - ")
                append(location)
            }
        }
    
    private fun formatShowDate(dateString: String): String {
        return try {
            // Convert from YYYY-MM-DD to more readable format
            val parts = dateString.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = parts[1].toInt()
                val day = parts[2].toInt()
                
                val monthNames = arrayOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                )
                
                "${monthNames[month - 1]} $day, $year"
            } else {
                dateString
            }
        } catch (e: Exception) {
            dateString
        }
    }
}