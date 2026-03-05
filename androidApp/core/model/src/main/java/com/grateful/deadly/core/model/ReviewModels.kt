package com.grateful.deadly.core.model

import kotlinx.serialization.Serializable

/**
 * Aggregated review data for a show. Combines show-level ratings
 * with track reviews and player tags.
 */
@Serializable
data class ShowReview(
    val showId: String,
    val notes: String? = null,
    val overallRating: Float? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null,
    val reviewedRecordingId: String? = null,
    val trackReviews: List<TrackReview> = emptyList(),
    val playerTags: List<PlayerTag> = emptyList()
) {
    val hasContent: Boolean get() = notes != null || overallRating != null ||
        recordingQuality != null || playingQuality != null ||
        trackReviews.isNotEmpty() || playerTags.isNotEmpty()
}

/**
 * Per-track review with thumbs rating and optional detailed star rating.
 */
@Serializable
data class TrackReview(
    val trackTitle: String,
    val trackNumber: Int? = null,
    val recordingId: String? = null,
    val thumbs: Int? = null,       // 1=up, -1=down, null=unrated
    val starRating: Int? = null,   // 1-5
    val notes: String? = null
) {
    val isThumbsUp: Boolean get() = thumbs == 1
    val isThumbsDown: Boolean get() = thumbs == -1
    val hasRating: Boolean get() = thumbs != null || starRating != null
}

/**
 * A thumbs-up track with show context for the Favorites screen.
 */
@Serializable
data class FavoriteTrack(
    val showId: String,
    val showDate: String,
    val venue: String,
    val trackTitle: String,
    val trackNumber: Int?,
    val recordingId: String?
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
