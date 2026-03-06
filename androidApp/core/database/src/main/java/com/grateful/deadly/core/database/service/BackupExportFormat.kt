package com.grateful.deadly.core.database.service

import kotlinx.serialization.Serializable

@Serializable
data class BackupExportV3(
    val version: Int = 3,
    val exportedAt: Long,
    val app: String,
    val favorites: FavoritesExport,
    val reviews: List<ReviewExportEntry>,
    val recordingPreferences: List<RecordingPreferenceExportEntry>
)

@Serializable
data class FavoritesExport(
    val shows: List<FavoriteShowEntry>,
    val tracks: List<FavoriteTrackEntry>
)

@Serializable
data class FavoriteShowEntry(
    val showId: String,
    val addedAt: Long,
    val isPinned: Boolean,
    val lastAccessedAt: Long? = null,
    val tags: List<String>? = null
)

@Serializable
data class FavoriteTrackEntry(
    val showId: String,
    val trackTitle: String,
    val trackNumber: Int? = null,
    val recordingId: String? = null
)

@Serializable
data class ReviewExportEntry(
    val showId: String,
    val notes: String? = null,
    val overallRating: Float? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null,
    val reviewedRecordingId: String? = null,
    val playerTags: List<PlayerTagExportEntry>? = null
)

@Serializable
data class PlayerTagExportEntry(
    val playerName: String,
    val instruments: String? = null,
    val isStandout: Boolean = true,
    val notes: String? = null
)

@Serializable
data class RecordingPreferenceExportEntry(
    val showId: String,
    val recordingId: String
)

data class BackupImportResult(
    val favoritesImported: Int,
    val favoritesSkipped: Int,
    val reviewsImported: Int,
    val tracksImported: Int,
    val preferencesImported: Int,
    val notFound: Int
)

/**
 * Minimal structure for peeking at the version field of any JSON backup.
 */
@Serializable
internal data class VersionPeek(
    val version: Int = 0,
    val format: String? = null
)
