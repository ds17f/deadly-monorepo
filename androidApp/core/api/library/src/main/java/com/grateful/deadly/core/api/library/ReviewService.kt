package com.grateful.deadly.core.api.library

import com.grateful.deadly.core.model.FavoriteTrack
import com.grateful.deadly.core.model.PlayerTag
import com.grateful.deadly.core.model.ShowReview
import com.grateful.deadly.core.model.TrackReview
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for show and track review operations.
 */
interface ReviewService {

    suspend fun getShowReview(showId: String): ShowReview?

    fun getShowReviewFlow(showId: String): Flow<ShowReview>

    suspend fun updateShowNotes(showId: String, notes: String?)

    suspend fun updateShowRating(showId: String, rating: Float?)

    suspend fun updateRecordingQuality(showId: String, quality: Int?, recordingId: String? = null)

    suspend fun updatePlayingQuality(showId: String, quality: Int?)

    suspend fun getTrackReviews(showId: String): List<TrackReview>

    fun getTrackReviewsFlow(showId: String): Flow<List<TrackReview>>

    suspend fun getTrackReview(showId: String, trackTitle: String, recordingId: String?): TrackReview?

    fun getTrackReviewFlow(showId: String, trackTitle: String, recordingId: String?): Flow<TrackReview?>

    suspend fun upsertTrackReview(
        showId: String,
        trackTitle: String,
        trackNumber: Int? = null,
        recordingId: String? = null,
        thumbs: Int? = null,
        starRating: Int? = null,
        notes: String? = null
    )

    suspend fun getPlayerTags(showId: String): List<PlayerTag>

    suspend fun upsertPlayerTag(
        showId: String,
        playerName: String,
        instruments: String? = null,
        isStandout: Boolean = true,
        notes: String? = null
    )

    suspend fun removePlayerTag(showId: String, playerName: String)

    suspend fun getThumbsUpTracks(): List<FavoriteTrack>

    suspend fun deleteShowReview(showId: String)
}
