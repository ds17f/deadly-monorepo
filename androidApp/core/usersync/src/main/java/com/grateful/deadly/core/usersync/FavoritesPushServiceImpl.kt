package com.grateful.deadly.core.usersync

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.FavoritesPushService
import com.grateful.deadly.core.api.usersync.PushResult
import com.grateful.deadly.core.api.usersync.SyncFavoriteShowV3
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.dao.FavoritesDao
import com.grateful.deadly.core.database.dao.SyncOutboxDao
import com.grateful.deadly.core.database.entities.SyncOutboxEntity
import com.grateful.deadly.core.model.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesPushServiceImpl @Inject constructor(
    @AppDatabase private val outbox: SyncOutboxDao,
    @AppDatabase private val favoritesDao: FavoritesDao,
    private val userSyncService: UserSyncService,
    private val authService: AuthService,
) : FavoritesPushService {

    companion object {
        private const val TAG = "FavoritesPushService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Serialize concurrent flushes so we never push the same row twice in parallel. */
    private val flushLock = Mutex()

    override fun enqueueAndPush(showId: String) {
        scope.launch {
            try {
                outbox.enqueue(
                    SyncOutboxEntity(
                        kind = SyncOutboxEntity.KIND_FAVORITE_SHOW,
                        refId = showId,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "enqueue($showId) failed", e)
                return@launch
            }
            flushPending()
        }
    }

    override suspend fun flushPending(): List<PushResult> {
        if (authService.authState.value !is AuthState.SignedIn) return emptyList()

        return flushLock.withLock {
            val pending = try {
                outbox.fetchPending(SyncOutboxEntity.KIND_FAVORITE_SHOW)
            } catch (e: Exception) {
                Log.w(TAG, "fetchPending failed", e)
                return@withLock emptyList()
            }

            val results = mutableListOf<PushResult>()
            for (entry in pending) {
                results += pushOne(entry)
            }
            results
        }
    }

    override suspend fun pendingCount(): Int =
        try { outbox.pendingCount(SyncOutboxEntity.KIND_FAVORITE_SHOW) } catch (_: Exception) { 0 }

    private suspend fun pushOne(entry: SyncOutboxEntity): PushResult {
        val row = try {
            favoritesDao.getFavoriteShowByIdIncludingTombstones(entry.refId)
        } catch (e: Exception) {
            return failure(entry, "?", "local read failed: ${e.message}")
        }

        if (row == null) {
            // Row was hard-deleted somewhere. Drop the outbox entry.
            outbox.delete(entry.id)
            return PushResult(entry.refId, "NOOP", success = true, error = null)
        }

        val result = if (row.deletedAt != null) {
            userSyncService.deleteFavoriteShow(row.showId)
                .fold({ success(entry, "DELETE") }, { failure(entry, "DELETE", it.message ?: it::class.simpleName ?: "error") })
        } else {
            val dto = SyncFavoriteShowV3(
                showId = row.showId,
                addedAt = row.addedToFavoritesAt / 1000, // server uses seconds
                isPinned = row.isPinned,
                lastAccessedAt = row.lastAccessedAt?.div(1000),
                tags = row.tags?.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() },
                notes = row.notes,
                preferredRecordingId = row.preferredRecordingId,
                downloadedRecordingId = row.downloadedRecordingId,
                downloadedFormat = row.downloadedFormat,
                recordingQuality = row.recordingQuality,
                playingQuality = row.playingQuality,
                customRating = row.customRating?.toDouble(),
                updatedAt = row.updatedAt / 1000,
                deletedAt = null,
            )
            userSyncService.putFavoriteShow(dto)
                .fold({ success(entry, "PUT") }, { failure(entry, "PUT", it.message ?: it::class.simpleName ?: "error") })
        }
        return result
    }

    private suspend fun success(entry: SyncOutboxEntity, op: String): PushResult {
        try { outbox.delete(entry.id) } catch (e: Exception) { Log.w(TAG, "outbox.delete failed", e) }
        return PushResult(entry.refId, op, success = true, error = null)
    }

    private suspend fun failure(entry: SyncOutboxEntity, op: String, error: String): PushResult {
        try { outbox.recordFailure(entry.id, System.currentTimeMillis(), error) } catch (_: Exception) {}
        return PushResult(entry.refId, op, success = false, error = error)
    }
}
