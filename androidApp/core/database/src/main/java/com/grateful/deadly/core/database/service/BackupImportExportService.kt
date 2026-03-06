package com.grateful.deadly.core.database.service

import android.util.Log
import com.grateful.deadly.core.database.dao.FavoritesDao
import com.grateful.deadly.core.database.dao.RecordingPreferenceDao
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.ShowPlayerTagDao
import com.grateful.deadly.core.database.dao.ShowReviewDao
import com.grateful.deadly.core.database.dao.TrackReviewDao
import com.grateful.deadly.core.database.entities.FavoriteShowEntity
import com.grateful.deadly.core.database.entities.RecordingPreferenceEntity
import com.grateful.deadly.core.database.entities.ShowPlayerTagEntity
import com.grateful.deadly.core.database.entities.ShowReviewEntity
import com.grateful.deadly.core.database.entities.TrackReviewEntity
import com.grateful.deadly.core.database.migration.MigrationData
import com.grateful.deadly.core.database.migration.MigrationImportService
import com.grateful.deadly.core.model.AppDatabase
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImportExportService @Inject constructor(
    @AppDatabase private val favoritesDao: FavoritesDao,
    @AppDatabase private val showDao: ShowDao,
    @AppDatabase private val showReviewDao: ShowReviewDao,
    @AppDatabase private val trackReviewDao: TrackReviewDao,
    @AppDatabase private val showPlayerTagDao: ShowPlayerTagDao,
    @AppDatabase private val recordingPreferenceDao: RecordingPreferenceDao,
    private val migrationImportService: MigrationImportService
) {
    companion object {
        private const val TAG = "BackupImportExportSvc"
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // MARK: - Export (v3)

    suspend fun export(): String {
        val favoriteShowEntities = favoritesDao.getAllFavoriteShows()
        val favoriteShows = favoriteShowEntities.map { entity ->
            FavoriteShowEntry(
                showId = entity.showId,
                addedAt = entity.addedToFavoritesAt,
                isPinned = entity.isPinned,
                lastAccessedAt = entity.lastAccessedAt,
                tags = entity.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            )
        }

        // Favorite tracks
        val favoriteTrackEntities = trackReviewDao.getFavoriteTracks()
        val favoriteTracks = favoriteTrackEntities.map { track ->
            FavoriteTrackEntry(
                showId = track.showId,
                trackTitle = track.trackTitle,
                trackNumber = track.trackNumber,
                recordingId = track.recordingId
            )
        }

        // All reviews (independent of library)
        val allReviews = showReviewDao.getAll()
        val allPlayerTags = showPlayerTagDao.getAll()
        val tagsByShow = allPlayerTags.groupBy { it.showId }

        val reviewEntries = allReviews.map { review ->
            val tags = tagsByShow[review.showId]
            ReviewExportEntry(
                showId = review.showId,
                notes = review.notes,
                overallRating = review.customRating,
                recordingQuality = review.recordingQuality,
                playingQuality = review.playingQuality,
                reviewedRecordingId = review.reviewedRecordingId,
                playerTags = tags?.takeIf { it.isNotEmpty() }?.map { tag ->
                    PlayerTagExportEntry(
                        playerName = tag.playerName,
                        instruments = tag.instruments,
                        isStandout = tag.isStandout,
                        notes = tag.notes
                    )
                }
            )
        }

        // Recording preferences (independent of library)
        val allPrefs = recordingPreferenceDao.getAll()
        val prefEntries = allPrefs.map { pref ->
            RecordingPreferenceExportEntry(showId = pref.showId, recordingId = pref.recordingId)
        }

        val export = BackupExportV3(
            exportedAt = System.currentTimeMillis(),
            app = "deadly-android",
            favorites = FavoritesExport(shows = favoriteShows, tracks = favoriteTracks),
            reviews = reviewEntries,
            recordingPreferences = prefEntries
        )

        return json.encodeToString(BackupExportV3.serializer(), export)
    }

    // MARK: - Import (version-detecting)

    suspend fun importBackup(jsonString: String): BackupImportResult {
        val peek = try {
            json.decodeFromString<VersionPeek>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to peek version from JSON", e)
            return BackupImportResult(0, 0, 0, 0, 0, 0)
        }

        return when {
            peek.version >= 3 -> importV3(jsonString)
            peek.format == "deadly-migration" -> {
                // Legacy migration format — delegate to existing service
                val result = migrationImportService.importFromJson(jsonString)
                BackupImportResult(
                    favoritesImported = result.favoritesImported,
                    favoritesSkipped = result.skipped,
                    reviewsImported = 0,
                    tracksImported = 0,
                    preferencesImported = 0,
                    notFound = 0
                )
            }
            else -> {
                Log.w(TAG, "Unknown backup format: version=${peek.version}, format=${peek.format}")
                BackupImportResult(0, 0, 0, 0, 0, 0)
            }
        }
    }

    // MARK: - v3 Import

    private suspend fun importV3(jsonString: String): BackupImportResult {
        val export = json.decodeFromString<BackupExportV3>(jsonString)
        val now = System.currentTimeMillis()

        var favoritesImported = 0
        var favoritesSkipped = 0
        var reviewsImported = 0
        var tracksImported = 0
        var preferencesImported = 0
        var notFound = 0

        // Import favorite shows
        for (fav in export.favorites.shows) {
            val showExists = showDao.getShowById(fav.showId) != null
            if (!showExists) {
                notFound++
                continue
            }
            if (favoritesDao.isShowFavorite(fav.showId)) {
                favoritesSkipped++
                continue
            }
            try {
                favoritesDao.addToFavorites(
                    FavoriteShowEntity(
                        showId = fav.showId,
                        addedToFavoritesAt = fav.addedAt,
                        isPinned = fav.isPinned,
                        lastAccessedAt = fav.lastAccessedAt,
                        tags = fav.tags?.joinToString(",")
                    )
                )
                favoritesImported++
            } catch (e: Exception) {
                Log.e(TAG, "Error importing favorite show ${fav.showId}", e)
            }
        }

        // Import favorite tracks (ensure thumbs=1 review exists)
        for (track in export.favorites.tracks) {
            val showExists = showDao.getShowById(track.showId) != null
            if (!showExists) continue
            try {
                val existing = trackReviewDao.getReview(track.showId, track.trackTitle, track.recordingId)
                if (existing == null || existing.thumbs != 1) {
                    trackReviewDao.upsert(
                        TrackReviewEntity(
                            id = existing?.id ?: 0,
                            showId = track.showId,
                            trackTitle = track.trackTitle,
                            trackNumber = track.trackNumber,
                            recordingId = track.recordingId,
                            thumbs = 1,
                            starRating = existing?.starRating,
                            notes = existing?.notes,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now
                        )
                    )
                    tracksImported++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error importing favorite track ${track.trackTitle}", e)
            }
        }

        // Import reviews (upsert)
        for (review in export.reviews) {
            val showExists = showDao.getShowById(review.showId) != null
            if (!showExists) continue
            try {
                showReviewDao.upsert(
                    ShowReviewEntity(
                        showId = review.showId,
                        notes = review.notes,
                        customRating = review.overallRating,
                        recordingQuality = review.recordingQuality,
                        playingQuality = review.playingQuality,
                        reviewedRecordingId = review.reviewedRecordingId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                reviewsImported++

                review.playerTags?.forEach { pt ->
                    showPlayerTagDao.upsert(
                        ShowPlayerTagEntity(
                            showId = review.showId,
                            playerName = pt.playerName,
                            instruments = pt.instruments,
                            isStandout = pt.isStandout,
                            notes = pt.notes,
                            createdAt = now
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error importing review for ${review.showId}", e)
            }
        }

        // Import recording preferences (upsert)
        for (pref in export.recordingPreferences) {
            val showExists = showDao.getShowById(pref.showId) != null
            if (!showExists) continue
            try {
                recordingPreferenceDao.upsert(
                    RecordingPreferenceEntity(
                        showId = pref.showId,
                        recordingId = pref.recordingId,
                        updatedAt = now
                    )
                )
                preferencesImported++
            } catch (e: Exception) {
                Log.e(TAG, "Error importing recording preference for ${pref.showId}", e)
            }
        }

        Log.d(TAG, "v3 import complete: $favoritesImported fav, $reviewsImported reviews, $tracksImported tracks, $preferencesImported prefs, $notFound not found")
        return BackupImportResult(
            favoritesImported = favoritesImported,
            favoritesSkipped = favoritesSkipped,
            reviewsImported = reviewsImported,
            tracksImported = tracksImported,
            preferencesImported = preferencesImported,
            notFound = notFound
        )
    }
}
