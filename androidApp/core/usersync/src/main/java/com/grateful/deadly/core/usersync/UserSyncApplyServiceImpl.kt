package com.grateful.deadly.core.usersync

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.ApplyResult
import com.grateful.deadly.core.api.usersync.SyncFavoriteShowV3
import com.grateful.deadly.core.api.usersync.UserSyncApplyService
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.dao.FavoritesDao
import com.grateful.deadly.core.database.entities.FavoriteShowEntity
import com.grateful.deadly.core.model.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSyncApplyServiceImpl @Inject constructor(
    private val userSyncService: UserSyncService,
    @AppDatabase private val favoritesDao: FavoritesDao,
    private val authService: AuthService,
) : UserSyncApplyService {

    companion object {
        private const val TAG = "UserSyncApply"
    }

    /** Serialize concurrent applies so two triggers (e.g. foreground + post-push) don't race. */
    private val applyLock = Mutex()

    override suspend fun pullAndApply(): Result<ApplyResult> {
        if (authService.authState.value !is AuthState.SignedIn) {
            return Result.failure(IllegalStateException("Not signed in"))
        }
        return applyLock.withLock {
            userSyncService.pullFullBackup().mapCatching { backup ->
                applyFavoriteShows(backup.favorites.shows)
            }
        }
    }

    private suspend fun applyFavoriteShows(remote: List<SyncFavoriteShowV3>): ApplyResult {
        var applied = 0
        var skippedLocalNewer = 0
        var skippedMissingShow = 0

        for (dto in remote) {
            val local = try {
                favoritesDao.getFavoriteShowByIdIncludingTombstones(dto.showId)
            } catch (e: Exception) {
                Log.w(TAG, "read failed for ${dto.showId}: ${e.message}")
                continue
            }

            // LWW: keep local if its updatedAt is newer or equal. Server timestamps
            // are seconds; local are ms. Compare in ms.
            val remoteUpdatedMs = dto.updatedAt * 1000
            if (local != null && local.updatedAt >= remoteUpdatedMs) {
                skippedLocalNewer++
                continue
            }

            val entity = dto.toEntity(existing = local)
            try {
                favoritesDao.addToFavorites(entity)
                applied++
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // FK to shows: the show isn't in the local catalog (older data version).
                // Skip — next data refresh will let this row import.
                Log.w(TAG, "skip ${dto.showId}: show not in local catalog")
                skippedMissingShow++
            } catch (e: Exception) {
                Log.w(TAG, "apply ${dto.showId} failed: ${e.message}")
            }
        }

        Log.d(
            TAG,
            "favorite_shows: scanned=${remote.size} applied=$applied " +
                "skippedLocalNewer=$skippedLocalNewer skippedMissingShow=$skippedMissingShow"
        )
        return ApplyResult(
            favoriteShowsScanned = remote.size,
            favoriteShowsApplied = applied,
            favoriteShowsSkippedLocalNewer = skippedLocalNewer,
            favoriteShowsSkippedMissingShow = skippedMissingShow,
        )
    }

    /** Map a server DTO onto a Room entity, preserving any download fields we shouldn't trample. */
    private fun SyncFavoriteShowV3.toEntity(existing: FavoriteShowEntity?): FavoriteShowEntity {
        return FavoriteShowEntity(
            showId = showId,
            addedToFavoritesAt = addedAt * 1000,
            isPinned = isPinned,
            notes = notes,
            preferredRecordingId = preferredRecordingId,
            // Downloads are device-local concerns. Keep whatever we already have
            // locally rather than overwriting with server's (which may be null or
            // reflect a different device's download).
            downloadedRecordingId = existing?.downloadedRecordingId ?: downloadedRecordingId,
            downloadedFormat = existing?.downloadedFormat ?: downloadedFormat,
            recordingQuality = recordingQuality,
            playingQuality = playingQuality,
            customRating = customRating?.toFloat(),
            lastAccessedAt = lastAccessedAt?.let { it * 1000 },
            tags = tags?.joinToString(","),
            updatedAt = updatedAt * 1000,
            deletedAt = deletedAt?.let { it * 1000 },
        )
    }
}
