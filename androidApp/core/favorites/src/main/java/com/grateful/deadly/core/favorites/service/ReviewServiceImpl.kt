package com.grateful.deadly.core.favorites.service

import com.grateful.deadly.core.api.favorites.ReviewService
import com.grateful.deadly.core.database.dao.FavoriteSongDao
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.ShowPlayerTagDao
import com.grateful.deadly.core.database.dao.ShowReviewDao
import com.grateful.deadly.core.database.entities.FavoriteSongEntity
import com.grateful.deadly.core.database.entities.ShowPlayerTagEntity
import com.grateful.deadly.core.database.entities.ShowReviewEntity
import com.grateful.deadly.core.model.AppDatabase
import com.grateful.deadly.core.model.FavoriteTrack
import com.grateful.deadly.core.model.PlayerTag
import com.grateful.deadly.core.model.ShowReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewServiceImpl @Inject constructor(
    @AppDatabase private val showReviewDao: ShowReviewDao,
    @AppDatabase private val favoriteSongDao: FavoriteSongDao,
    @AppDatabase private val showPlayerTagDao: ShowPlayerTagDao,
    @AppDatabase private val showDao: ShowDao
) : ReviewService {

    override suspend fun getShowReview(showId: String): ShowReview? {
        val reviewEntity = showReviewDao.getByShowId(showId)
        val playerTags = showPlayerTagDao.getTagsForShow(showId).map { it.toDomain() }

        if (reviewEntity == null && playerTags.isEmpty()) return null

        return ShowReview(
            showId = showId,
            notes = reviewEntity?.notes,
            overallRating = reviewEntity?.customRating,
            recordingQuality = reviewEntity?.recordingQuality,
            playingQuality = reviewEntity?.playingQuality,
            reviewedRecordingId = reviewEntity?.reviewedRecordingId,
            playerTags = playerTags
        )
    }

    override fun getShowReviewFlow(showId: String): Flow<ShowReview> {
        return combine(
            showReviewDao.getByShowIdFlow(showId),
            showPlayerTagDao.getTagsForShowFlow(showId)
        ) { reviewEntity, tagEntities ->
            ShowReview(
                showId = showId,
                notes = reviewEntity?.notes,
                overallRating = reviewEntity?.customRating,
                recordingQuality = reviewEntity?.recordingQuality,
                playingQuality = reviewEntity?.playingQuality,
                reviewedRecordingId = reviewEntity?.reviewedRecordingId,
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

    override suspend fun toggleFavoriteSong(
        showId: String,
        trackTitle: String,
        trackNumber: Int?,
        recordingId: String?
    ) {
        val isFav = favoriteSongDao.isFavorite(showId, trackTitle, recordingId)
        if (isFav) {
            favoriteSongDao.delete(showId, trackTitle, recordingId)
        } else {
            favoriteSongDao.insert(
                FavoriteSongEntity(
                    showId = showId,
                    trackTitle = trackTitle,
                    trackNumber = trackNumber,
                    recordingId = recordingId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun isSongFavoriteFlow(showId: String, trackTitle: String, recordingId: String?): Flow<Boolean> {
        return favoriteSongDao.isFavoriteFlow(showId, trackTitle, recordingId)
    }

    override fun getFavoriteSongTitlesFlow(showId: String): Flow<Set<String>> {
        return favoriteSongDao.getFavoriteTitlesForShowFlow(showId).map { it.toSet() }
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
        val favoriteEntities = favoriteSongDao.getAllFavorites()
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
                addedAt = entity.createdAt
            )
        }
    }

    override suspend fun deleteShowReview(showId: String) {
        showPlayerTagDao.removeTagsForShow(showId)
        favoriteSongDao.deleteForShow(showId)
        showReviewDao.deleteByShowId(showId)
    }

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

    private fun ShowPlayerTagEntity.toDomain() = PlayerTag(
        playerName = playerName,
        instruments = instruments,
        isStandout = isStandout,
        notes = notes
    )
}
