package com.grateful.deadly.core.api.favorites

import com.grateful.deadly.core.model.FavoriteTrack
import com.grateful.deadly.core.model.PlayerTag
import com.grateful.deadly.core.model.ShowReview
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for show review and song favorite operations.
 */
interface ReviewService {

    suspend fun getShowReview(showId: String): ShowReview?

    fun getShowReviewFlow(showId: String): Flow<ShowReview>

    suspend fun updateShowNotes(showId: String, notes: String?)

    suspend fun updateShowRating(showId: String, rating: Float?)

    suspend fun updateRecordingQuality(showId: String, quality: Int?, recordingId: String? = null)

    suspend fun updatePlayingQuality(showId: String, quality: Int?)

    suspend fun toggleFavoriteSong(
        showId: String,
        trackTitle: String,
        trackNumber: Int? = null,
        recordingId: String? = null
    )

    fun isSongFavoriteFlow(showId: String, trackTitle: String, recordingId: String?): Flow<Boolean>

    fun getFavoriteSongTitlesFlow(showId: String): Flow<Set<String>>

    suspend fun getPlayerTags(showId: String): List<PlayerTag>

    suspend fun upsertPlayerTag(
        showId: String,
        playerName: String,
        instruments: String? = null,
        isStandout: Boolean = true,
        notes: String? = null
    )

    suspend fun removePlayerTag(showId: String, playerName: String)

    suspend fun getFavoriteTracks(): List<FavoriteTrack>

    suspend fun deleteShowReview(showId: String)
}
