package com.grateful.deadly.core.usersync

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.ApplyResult
import com.grateful.deadly.core.api.usersync.SyncFavoriteShowV3
import com.grateful.deadly.core.api.usersync.SyncFavoriteTrackV3
import com.grateful.deadly.core.api.usersync.UserSyncApplyService
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.dao.FavoriteSongDao
import com.grateful.deadly.core.database.dao.FavoritesDao
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.entities.FavoriteShowEntity
import com.grateful.deadly.core.database.entities.FavoriteSongEntity
import com.grateful.deadly.core.model.AppDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSyncApplyServiceImpl @Inject constructor(
    private val userSyncService: UserSyncService,
    @AppDatabase private val favoritesDao: FavoritesDao,
    @AppDatabase private val favoriteSongDao: FavoriteSongDao,
    @AppDatabase private val showDao: ShowDao,
    private val authService: AuthService,
) : UserSyncApplyService {

    companion object {
        private const val TAG = "UserSyncApply"
    }

    private val applyLock = Mutex()

    override suspend fun pullAndApply(): Result<ApplyResult> {
        if (authService.authState.value !is AuthState.SignedIn) {
            return Result.failure(IllegalStateException("Not signed in"))
        }
        return applyLock.withLock {
            userSyncService.pullFullBackup().mapCatching { backup ->
                val shows = applyFavoriteShows(backup.favorites.shows)
                val songs = applyFavoriteSongs(backup.favorites.tracks)
                shows.copy(
                    favoriteSongsScanned = songs.favoriteSongsScanned,
                    favoriteSongsApplied = songs.favoriteSongsApplied,
                    favoriteSongsSkippedLocalNewer = songs.favoriteSongsSkippedLocalNewer,
                    favoriteSongsSkippedMissingShow = songs.favoriteSongsSkippedMissingShow,
                )
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

    private suspend fun applyFavoriteSongs(remote: List<SyncFavoriteTrackV3>): ApplyResult {
        var applied = 0
        var skippedLocalNewer = 0
        var skippedMissingShow = 0

        for (dto in remote) {
            // Skip rows for shows we don't know about — FK would blow up anyway.
            if (showDao.getShowById(dto.showId) == null) {
                skippedMissingShow++
                continue
            }

            val local = try {
                favoriteSongDao.findByKeyIncludingTombstones(dto.showId, dto.trackTitle)
            } catch (e: Exception) {
                Log.w(TAG, "song read failed for ${dto.showId}/${dto.trackTitle}: ${e.message}")
                continue
            }

            val remoteUpdatedMs = dto.updatedAt * 1000
            if (local != null && local.updatedAt >= remoteUpdatedMs) {
                skippedLocalNewer++
                continue
            }

            try {
                if (local != null) {
                    favoriteSongDao.applyFromSyncUpdate(
                        id = local.id,
                        trackNumber = dto.trackNumber,
                        recordingId = dto.recordingId,
                        createdAt = local.createdAt,
                        updatedAt = remoteUpdatedMs,
                        deletedAt = dto.deletedAt?.let { it * 1000 },
                    )
                } else {
                    favoriteSongDao.insert(
                        FavoriteSongEntity(
                            showId = dto.showId,
                            trackTitle = dto.trackTitle,
                            trackNumber = dto.trackNumber,
                            recordingId = dto.recordingId,
                            createdAt = remoteUpdatedMs,
                            updatedAt = remoteUpdatedMs,
                            deletedAt = dto.deletedAt?.let { it * 1000 },
                        )
                    )
                }
                applied++
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                Log.w(TAG, "skip song ${dto.showId}/${dto.trackTitle}: constraint")
                skippedMissingShow++
            } catch (e: Exception) {
                Log.w(TAG, "apply song ${dto.showId}/${dto.trackTitle} failed: ${e.message}")
            }
        }

        Log.d(
            TAG,
            "favorite_songs: scanned=${remote.size} applied=$applied " +
                "skippedLocalNewer=$skippedLocalNewer skippedMissingShow=$skippedMissingShow"
        )
        return ApplyResult(
            favoriteShowsScanned = 0,
            favoriteShowsApplied = 0,
            favoriteShowsSkippedLocalNewer = 0,
            favoriteShowsSkippedMissingShow = 0,
            favoriteSongsScanned = remote.size,
            favoriteSongsApplied = applied,
            favoriteSongsSkippedLocalNewer = skippedLocalNewer,
            favoriteSongsSkippedMissingShow = skippedMissingShow,
        )
    }

    private fun SyncFavoriteShowV3.toEntity(existing: FavoriteShowEntity?): FavoriteShowEntity {
        return FavoriteShowEntity(
            showId = showId,
            addedToFavoritesAt = addedAt * 1000,
            isPinned = isPinned,
            notes = notes,
            preferredRecordingId = preferredRecordingId,
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
