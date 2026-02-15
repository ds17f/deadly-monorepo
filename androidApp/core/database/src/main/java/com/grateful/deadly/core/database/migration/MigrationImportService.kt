package com.grateful.deadly.core.database.migration

import android.util.Log
import com.grateful.deadly.core.database.dao.LibraryDao
import com.grateful.deadly.core.database.dao.RecentShowDao
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.entities.LibraryShowEntity
import com.grateful.deadly.core.database.entities.RecentShowEntity
import com.grateful.deadly.core.database.entities.ShowEntity
import com.grateful.deadly.core.model.AppDatabase
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationImportService @Inject constructor(
    @AppDatabase private val showDao: ShowDao,
    @AppDatabase private val libraryDao: LibraryDao,
    @AppDatabase private val recentShowDao: RecentShowDao
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

        Log.d(TAG, "Importing migration v${data.version} from ${data.appVersion}: ${data.library.size} library, ${data.recentPlays.size} recent")

        var libraryImported = 0
        var recentImported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        // Import library shows
        for (item in data.library) {
            try {
                val show = findShow(item.date, item.venue)
                if (show != null) {
                    libraryDao.addToLibrary(
                        LibraryShowEntity(
                            showId = show.showId,
                            addedToLibraryAt = item.addedAt
                        )
                    )
                    libraryImported++
                    Log.d(TAG, "Imported library show: ${item.date} → ${show.showId}")
                } else {
                    skipped++
                    Log.w(TAG, "No match for library show: ${item.date} ${item.venue}")
                }
            } catch (e: Exception) {
                errors.add("Library ${item.date}: ${e.message}")
                Log.e(TAG, "Error importing library show ${item.date}", e)
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

        Log.d(TAG, "Import complete: $libraryImported library, $recentImported recent, $skipped skipped, ${errors.size} errors")
        return MigrationResult(libraryImported, recentImported, skipped, errors)
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
