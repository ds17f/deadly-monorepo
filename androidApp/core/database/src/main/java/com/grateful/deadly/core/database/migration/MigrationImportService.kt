package com.grateful.deadly.core.database.migration

import android.util.Log
import com.grateful.deadly.core.database.dao.FavoritesDao
import com.grateful.deadly.core.database.dao.RecentShowDao
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.TrackReviewDao
import com.grateful.deadly.core.database.dao.ShowPlayerTagDao
import com.grateful.deadly.core.database.entities.FavoriteShowEntity
import com.grateful.deadly.core.database.entities.RecentShowEntity
import com.grateful.deadly.core.database.entities.ShowEntity
import com.grateful.deadly.core.database.entities.TrackReviewEntity
import com.grateful.deadly.core.database.entities.ShowPlayerTagEntity
import com.grateful.deadly.core.model.AppDatabase
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationImportService @Inject constructor(
    @AppDatabase private val showDao: ShowDao,
    @AppDatabase private val favoritesDao: FavoritesDao,
    @AppDatabase private val recentShowDao: RecentShowDao,
    @AppDatabase private val trackReviewDao: TrackReviewDao,
    @AppDatabase private val showPlayerTagDao: ShowPlayerTagDao
) {

    companion object {
        private const val TAG = "MigrationImportService"
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromJson(jsonString: String): MigrationResult {
        val data = try {
            json.decodeFromString<MigrationData>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse migration JSON", e)
            return MigrationResult(0, 0, 0, listOf("Failed to parse file: ${e.message}"))
        }

        if (data.format != "deadly-migration") {
            return MigrationResult(0, 0, 0, listOf("Invalid file format: ${data.format}"))
        }

        Log.d(TAG, "Importing migration v${data.version} from ${data.appVersion}: ${data.library.size} favorites, ${data.recentPlays.size} recent")

        var favoritesImported = 0
        var recentImported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        // Import favorite shows
        // Track date→showId mapping for review import
        val dateToShowId = mutableMapOf<String, String>()
        for (item in data.library) {
            try {
                val show = findShow(item.date, item.venue)
                if (show != null) {
                    favoritesDao.addToFavorites(
                        FavoriteShowEntity(
                            showId = show.showId,
                            addedToFavoritesAt = item.addedAt,
                            customRating = item.customRating,
                            recordingQuality = item.recordingQuality,
                            playingQuality = item.playingQuality,
                            notes = item.notes
                        )
                    )
                    dateToShowId[item.date] = show.showId
                    favoritesImported++
                    Log.d(TAG, "Imported favorite show: ${item.date} → ${show.showId}")
                } else {
                    skipped++
                    Log.w(TAG, "No match for favorite show: ${item.date} ${item.venue}")
                }
            } catch (e: Exception) {
                errors.add("Favorite ${item.date}: ${e.message}")
                Log.e(TAG, "Error importing favorite show ${item.date}", e)
            }
        }

        // Import track reviews
        data.trackReviews?.forEach { tr ->
            try {
                val showId = dateToShowId[tr.showDate]
                if (showId != null) {
                    val now = System.currentTimeMillis()
                    trackReviewDao.upsert(
                        TrackReviewEntity(
                            showId = showId,
                            trackTitle = tr.trackTitle,
                            trackNumber = tr.trackNumber,
                            recordingId = tr.recordingId,
                            thumbs = tr.thumbs,
                            starRating = tr.starRating,
                            notes = tr.notes,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            } catch (e: Exception) {
                errors.add("TrackReview ${tr.showDate}/${tr.trackTitle}: ${e.message}")
            }
        }

        // Import player tags
        data.playerTags?.forEach { pt ->
            try {
                val showId = dateToShowId[pt.showDate]
                if (showId != null) {
                    showPlayerTagDao.upsert(
                        ShowPlayerTagEntity(
                            showId = showId,
                            playerName = pt.playerName,
                            instruments = pt.instruments,
                            isStandout = pt.isStandout,
                            notes = pt.notes,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                errors.add("PlayerTag ${pt.showDate}/${pt.playerName}: ${e.message}")
            }
        }

        // Import recent plays
        for (item in data.recentPlays) {
            try {
                val show = findShow(item.date, item.venue)
                if (show != null) {
                    val existing = recentShowDao.getShowById(show.showId)
                    if (existing != null) {
                        // Merge: keep the earlier firstPlayed and later lastPlayed
                        recentShowDao.updateShow(
                            showId = show.showId,
                            timestamp = maxOf(existing.lastPlayedTimestamp, item.lastPlayedAt),
                            playCount = maxOf(existing.totalPlayCount, item.playCount)
                        )
                    } else {
                        recentShowDao.insert(
                            RecentShowEntity(
                                showId = show.showId,
                                lastPlayedTimestamp = item.lastPlayedAt,
                                firstPlayedTimestamp = item.firstPlayedAt,
                                totalPlayCount = item.playCount
                            )
                        )
                    }
                    recentImported++
                    Log.d(TAG, "Imported recent show: ${item.date} → ${show.showId}")
                } else {
                    skipped++
                    Log.w(TAG, "No match for recent show: ${item.date} ${item.venue}")
                }
            } catch (e: Exception) {
                errors.add("Recent ${item.date}: ${e.message}")
                Log.e(TAG, "Error importing recent show ${item.date}", e)
            }
        }

        Log.d(TAG, "Import complete: $favoritesImported favorites, $recentImported recent, $skipped skipped, ${errors.size} errors")
        return MigrationResult(favoritesImported, recentImported, skipped, errors)
    }

    /**
     * Find a show in the new app's database by date and venue similarity.
     * Primary key: date. Secondary disambiguation: venue name Levenshtein distance.
     */
    private suspend fun findShow(date: String, venue: String?): ShowEntity? {
        val shows = showDao.getShowsByDate(date)
        return when {
            shows.isEmpty() -> null
            shows.size == 1 -> shows.first()
            venue != null -> shows.minByOrNull {
                levenshteinDistance(it.venueName.lowercase(), venue.lowercase())
            }
            else -> shows.first()
        }
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
                }
            }
        }
        return dp[m][n]
    }
}
