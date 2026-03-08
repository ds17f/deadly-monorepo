package com.grateful.deadly.core.database.entities

/**
 * Room projection class for show queries that don't need large JSON blob columns.
 *
 * Excludes: setlistRaw, lineupRaw, recordingsRaw, songList, memberList,
 * setlistStatus, lineupStatus, url, month, yearMonth, showSequence, createdAt, updatedAt.
 *
 * Room maps these fields by column name from any query that returns matching columns.
 */
data class ShowSummary(
    val showId: String,
    val date: String,
    val year: Int,
    val band: String,
    val venueName: String,
    val city: String?,
    val state: String?,
    val country: String,
    val locationRaw: String?,
    val recordingCount: Int,
    val bestRecordingId: String?,
    val coverImageUrl: String?,
    val averageRating: Float?,
    val totalReviews: Int,
    val isFavorite: Boolean,
    val favoritedAt: Long?
)
