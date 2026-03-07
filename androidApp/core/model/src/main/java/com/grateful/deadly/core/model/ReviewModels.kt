package com.grateful.deadly.core.model

import kotlinx.serialization.Serializable

/**
 * Aggregated review data for a show. Combines show-level ratings
 * with player tags.
 */
@Serializable
data class ShowReview(
    val showId: String,
    val notes: String? = null,
    val overallRating: Float? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null,
    val reviewedRecordingId: String? = null,
    val playerTags: List<PlayerTag> = emptyList()
) {
    val hasContent: Boolean get() = !notes.isNullOrBlank() || overallRating != null ||
        recordingQuality != null || playingQuality != null ||
        playerTags.isNotEmpty()
}

/**
 * A favorited track with show context for the Favorites screen.
 */
@Serializable
data class FavoriteTrack(
    val showId: String,
    val showDate: String,
    val venue: String,
    val trackTitle: String,
    val trackNumber: Int?,
    val recordingId: String?,
    val addedAt: Long = 0
)

/**
 * A tagged musician for a particular show.
 */
@Serializable
data class PlayerTag(
    val playerName: String,
    val instruments: String? = null,
    val isStandout: Boolean = true,
    val notes: String? = null
)
