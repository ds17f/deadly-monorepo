package com.grateful.deadly.core.favorites.service

import com.grateful.deadly.core.api.favorites.ReviewService
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.ShowPlayerTagDao
import com.grateful.deadly.core.database.dao.ShowReviewDao
import com.grateful.deadly.core.database.dao.TrackReviewDao
import com.grateful.deadly.core.database.entities.ShowPlayerTagEntity
import com.grateful.deadly.core.database.entities.ShowReviewEntity
import com.grateful.deadly.core.database.entities.TrackReviewEntity
import com.grateful.deadly.core.model.AppDatabase
import com.grateful.deadly.core.model.FavoriteTrack
import com.grateful.deadly.core.model.PlayerTag
import com.grateful.deadly.core.model.ShowReview
import com.grateful.deadly.core.model.TrackReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewServiceImpl @Inject constructor(
    @AppDatabase private val showReviewDao: ShowReviewDao,
    @AppDatabase private val trackReviewDao: TrackReviewDao,
    @AppDatabase private val showPlayerTagDao: ShowPlayerTagDao,
    @AppDatabase private val showDao: ShowDao
) : ReviewService {

    override suspend fun getShowReview(showId: String): ShowReview? {
        val reviewEntity = showReviewDao.getByShowId(showId)
        val trackReviews = trackReviewDao.getReviewsForShow(showId).map { it.toDomain() }
        val playerTags = showPlayerTagDao.getTagsForShow(showId).map { it.toDomain() }

        // Return null only if there's no data at all
        if (reviewEntity == null && trackReviews.isEmpty() && playerTags.isEmpty()) return null

        return ShowReview(
            showId = showId,
            notes = reviewEntity?.notes,
            overallRating = reviewEntity?.customRating,
            recordingQuality = reviewEntity?.recordingQuality,
            playingQuality = reviewEntity?.playingQuality,
            reviewedRecordingId = reviewEntity?.reviewedRecordingId,
            trackReviews = trackReviews,
            playerTags = playerTags
        )
    }

    override fun getShowReviewFlow(showId: String): Flow<ShowReview> {
        return combine(
            showReviewDao.getByShowIdFlow(showId),
            trackReviewDao.getReviewsForShowFlow(showId),
            showPlayerTagDao.getTagsForShowFlow(showId)
        ) { reviewEntity, trackEntities, tagEntities ->
            ShowReview(
                showId = showId,
                notes = reviewEntity?.notes,
                overallRating = reviewEntity?.customRating,
                recordingQuality = reviewEntity?.recordingQuality,
                playingQuality = reviewEntity?.playingQuality,
                reviewedRecordingId = reviewEntity?.reviewedRecordingId,
                trackReviews = trackEntities.map { it.toDomain() },
                playerTags = tagEntities.map { it.toDomain() }
            )
        }
    }

    override suspend fun updateShowNotes(showId: String, notes: String?) {
        ensureShowReviewExists(showId)
        showReviewDao.updateNotes(showId, notes)
    }

    override suspend fun updateShowRating(showId: String, rating: Float?) {
        ensureShowReviewExists(showId)
        showReviewDao.updateCustomRating(showId, rating)
    }

    override suspend fun updateRecordingQuality(showId: String, quality: Int?, recordingId: String?) {
        ensureShowReviewExists(showId)
        showReviewDao.updateRecordingQuality(showId, quality, recordingId)
    }

    override suspend fun updatePlayingQuality(showId: String, quality: Int?) {
        ensureShowReviewExists(showId)
        showReviewDao.updatePlayingQuality(showId, quality)
    }

    override suspend fun getTrackReviews(showId: String): List<TrackReview> {
        return trackReviewDao.getReviewsForShow(showId).map { it.toDomain() }
    }

    override fun getTrackReviewsFlow(showId: String): Flow<List<TrackReview>> {
        return trackReviewDao.getReviewsForShowFlow(showId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTrackReview(showId: String, trackTitle: String, recordingId: String?): TrackReview? {
        return trackReviewDao.getReview(showId, trackTitle, recordingId)?.toDomain()
    }

    override fun getTrackReviewFlow(showId: String, trackTitle: String, recordingId: String?): Flow<TrackReview?> {
        return trackReviewDao.getReviewFlow(showId, trackTitle, recordingId).map { it?.toDomain() }
    }

    override suspend fun upsertTrackReview(
        showId: String,
        trackTitle: String,
        trackNumber: Int?,
        recordingId: String?,
        thumbs: Int?,
        starRating: Int?,
        notes: String?
    ) {
        val now = System.currentTimeMillis()
        val existing = trackReviewDao.getReview(showId, trackTitle, recordingId)
        val entity = TrackReviewEntity(
            id = existing?.id ?: 0,
            showId = showId,
            trackTitle = trackTitle,
            trackNumber = trackNumber,
            recordingId = recordingId,
            thumbs = thumbs,
            starRating = starRating,
            notes = notes,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        trackReviewDao.upsert(entity)
    }

    override suspend fun getPlayerTags(showId: String): List<PlayerTag> {
        return showPlayerTagDao.getTagsForShow(showId).map { it.toDomain() }
    }

    override suspend fun upsertPlayerTag(
        showId: String,
        playerName: String,
        instruments: String?,
        isStandout: Boolean,
        notes: String?
    ) {
        val existing = showPlayerTagDao.getTagsForShow(showId).find { it.playerName == playerName }
        val entity = ShowPlayerTagEntity(
            id = existing?.id ?: 0,
            showId = showId,
            playerName = playerName,
            instruments = instruments,
            isStandout = isStandout,
            notes = notes,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        showPlayerTagDao.upsert(entity)
    }

    override suspend fun removePlayerTag(showId: String, playerName: String) {
        showPlayerTagDao.removeTag(showId, playerName)
    }

    override suspend fun getFavoriteTracks(): List<FavoriteTrack> {
        val favoriteEntities = trackReviewDao.getFavoriteTracks()
        val showIds = favoriteEntities.map { it.showId }.distinct()
        val shows = showDao.getShowsByIds(showIds).associateBy { it.showId }
        return favoriteEntities.mapNotNull { entity ->
            val show = shows[entity.showId] ?: return@mapNotNull null
            FavoriteTrack(
                showId = entity.showId,
                showDate = show.date,
                venue = show.venueName,
                trackTitle = entity.trackTitle,
                trackNumber = entity.trackNumber,
                recordingId = entity.recordingId,
                addedAt = entity.updatedAt
            )
        }
    }

    override suspend fun deleteShowReview(showId: String) {
        showPlayerTagDao.removeTagsForShow(showId)
        trackReviewDao.deleteReviewsForShow(showId)
        showReviewDao.deleteByShowId(showId)
    }

    // Ensure a show_reviews row exists so UPDATE queries work
    private suspend fun ensureShowReviewExists(showId: String) {
        if (showReviewDao.getByShowId(showId) == null) {
            val now = System.currentTimeMillis()
            showReviewDao.upsert(ShowReviewEntity(
                showId = showId,
                createdAt = now,
                updatedAt = now
            ))
        }
    }

    // Entity → Domain mappers

    private fun TrackReviewEntity.toDomain() = TrackReview(
        trackTitle = trackTitle,
        trackNumber = trackNumber,
        recordingId = recordingId,
        thumbs = thumbs,
        starRating = starRating,
        notes = notes
    )

    private fun ShowPlayerTagEntity.toDomain() = PlayerTag(
        playerName = playerName,
        instruments = instruments,
        isStandout = isStandout,
        notes = notes
    )
}
