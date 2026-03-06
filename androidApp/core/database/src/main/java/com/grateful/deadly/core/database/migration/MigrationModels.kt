package com.grateful.deadly.core.database.migration

import kotlinx.serialization.Serializable

/**
 * Cross-app migration data format.
 * Matches the format exported by the old app (com.deadly.app).
 * Shows are matched by date + venue since showIds differ between apps.
 */
@Serializable
data class MigrationData(
    val version: Int = 1,
    val format: String = "deadly-migration",
    val createdAt: Long,
    val appVersion: String,
    val library: List<MigrationFavoriteShow>,
    val recentPlays: List<MigrationRecentShow>,
    val lastPlayed: MigrationLastPlayed? = null,
    val reviews: List<MigrationShowReview>? = null,
    val trackReviews: List<MigrationTrackReview>? = null,
    val playerTags: List<MigrationPlayerTag>? = null
)

@Serializable
data class MigrationFavoriteShow(
    val date: String,
    val venue: String? = null,
    val location: String? = null,
    val addedAt: Long,
    val preferredRecordingId: String? = null,
    val notes: String? = null,
    val customRating: Float? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null
)

@Serializable
data class MigrationRecentShow(
    val date: String,
    val venue: String? = null,
    val location: String? = null,
    val lastPlayedAt: Long,
    val firstPlayedAt: Long,
    val playCount: Int
)

@Serializable
data class MigrationLastPlayed(
    val showDate: String,
    val showVenue: String? = null,
    val recordingId: String,
    val trackIndex: Int,
    val positionMs: Long,
    val trackTitle: String,
    val trackFilename: String
)

@Serializable
data class MigrationShowReview(
    val showDate: String,
    val notes: String? = null,
    val customRating: Float? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null
)

@Serializable
data class MigrationTrackReview(
    val showDate: String,
    val trackTitle: String,
    val trackNumber: Int? = null,
    val recordingId: String? = null,
    val thumbs: Int? = null,
    val starRating: Int? = null,
    val notes: String? = null
)

@Serializable
data class MigrationPlayerTag(
    val showDate: String,
    val playerName: String,
    val instruments: String? = null,
    val isStandout: Boolean = true,
    val notes: String? = null
)

data class MigrationResult(
    val favoritesImported: Int,
    val recentImported: Int,
    val skipped: Int,
    val errors: List<String>
)
