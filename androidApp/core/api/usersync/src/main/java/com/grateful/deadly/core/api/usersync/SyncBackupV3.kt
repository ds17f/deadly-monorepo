package com.grateful.deadly.core.api.usersync

import kotlinx.serialization.Serializable

// V3 sync DTOs. Mirror api/src/db/userdata.ts exactly. Kept separate
// from any file-export DTOs (which omit recentShows / playbackPosition
// and several optional favorite fields) because the wire format must
// be lossless.

@Serializable
data class SyncBackupV3(
    val version: Int,
    val exportedAt: Long,
    val app: String,
    val favorites: SyncFavorites,
    val reviews: List<SyncReviewV3>,
    val recordingPreferences: List<SyncRecordingPrefV3>,
    val settings: SyncSettingsV3? = null,
    val recentShows: List<SyncRecentShowV3>? = null,
    val playbackPosition: SyncPlaybackPositionV3? = null,
)

@Serializable
data class SyncFavorites(
    val shows: List<SyncFavoriteShowV3>,
    val tracks: List<SyncFavoriteTrackV3>,
)

@Serializable
data class SyncFavoriteShowV3(
    val showId: String,
    val addedAt: Long,
    val isPinned: Boolean,
    val lastAccessedAt: Long? = null,
    val tags: List<String>? = null,
    val notes: String? = null,
    val preferredRecordingId: String? = null,
    val downloadedRecordingId: String? = null,
    val downloadedFormat: String? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null,
    val customRating: Double? = null,
)

@Serializable
data class SyncFavoriteTrackV3(
    val showId: String,
    val trackTitle: String,
    val trackNumber: Int? = null,
    val recordingId: String? = null,
)

@Serializable
data class SyncReviewV3(
    val showId: String,
    val notes: String? = null,
    val overallRating: Double? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null,
    val reviewedRecordingId: String? = null,
    val playerTags: List<SyncPlayerTagV3>? = null,
)

@Serializable
data class SyncPlayerTagV3(
    val playerName: String,
    val instruments: String? = null,
    val isStandout: Boolean,
    val notes: String? = null,
)

@Serializable
data class SyncRecordingPrefV3(
    val showId: String,
    val recordingId: String,
)

@Serializable
data class SyncSettingsV3(
    val includeShowsWithoutRecordings: Boolean? = null,
    val favoritesDisplayMode: String? = null,
    val forceOnline: Boolean? = null,
    val sourceBadgeStyle: String? = null,
    val shareAttachImage: Boolean? = null,
    val eqEnabled: Boolean? = null,
    val eqPreset: String? = null,
    val eqBandLevels: String? = null,
)

@Serializable
data class SyncRecentShowV3(
    val showId: String,
    val lastPlayedAt: Long,
    val firstPlayedAt: Long,
    val totalPlayCount: Int,
)

@Serializable
data class SyncPlaybackPositionV3(
    val showId: String,
    val recordingId: String,
    val trackIndex: Int,
    val positionMs: Long,
    val date: String? = null,
    val venue: String? = null,
    val location: String? = null,
)
