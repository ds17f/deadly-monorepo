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
    val library: List<MigrationLibraryShow>,
    val recentPlays: List<MigrationRecentShow>,
    val lastPlayed: MigrationLastPlayed? = null
)

@Serializable
data class MigrationLibraryShow(
    val date: String,
    val venue: String? = null,
    val location: String? = null,
    val addedAt: Long,
    val preferredRecordingId: String? = null
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

data class MigrationResult(
    val libraryImported: Int,
    val recentImported: Int,
    val skipped: Int,
    val errors: List<String>
)
