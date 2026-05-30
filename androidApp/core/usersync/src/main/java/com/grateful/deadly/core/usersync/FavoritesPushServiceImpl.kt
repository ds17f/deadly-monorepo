package com.grateful.deadly.core.usersync

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.FavoritesPushService
import com.grateful.deadly.core.api.usersync.PushResult
import com.grateful.deadly.core.api.usersync.SyncFavoriteShowV3
import com.grateful.deadly.core.api.usersync.SyncFavoriteTrackV3
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.dao.FavoriteSongDao
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
    @AppDatabase private val favoriteSongDao: FavoriteSongDao,
    private val userSyncService: UserSyncService,
    private val authService: AuthService,
    private val syncCoordinator: UserSyncCoordinator,
) : FavoritesPushService {

    companion object {
        private const val TAG = "FavoritesPushService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLock = Mutex()

    override fun enqueueAndPush(showId: String) {
        enqueueAndFlush(SyncOutboxEntity.KIND_FAVORITE_SHOW, showId)
    }

    override fun enqueueAndPushFavoriteSong(localId: Long) {
        enqueueAndFlush(SyncOutboxEntity.KIND_FAVORITE_SONG, localId.toString())
    }

    private fun enqueueAndFlush(kind: String, refId: String) {
        scope.launch {
            try {
                outbox.enqueue(
                    SyncOutboxEntity(
                        kind = kind,
                        refId = refId,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "enqueue($kind, $refId) failed", e)
                return@launch
            }
            flushPending()
        }
    }

    override suspend fun flushPending(): List<PushResult> {
        if (authService.authState.value !is AuthState.SignedIn) return emptyList()

        return flushLock.withLock {
            val results = mutableListOf<PushResult>()
            results += flushKind(SyncOutboxEntity.KIND_FAVORITE_SHOW)
            results += flushKind(SyncOutboxEntity.KIND_FAVORITE_SONG)
            if (results.any { it.success && it.operation != "NOOP" }) {
                syncCoordinator.triggerPull("after_push_flush")
            }
            results
        }
    }

    override suspend fun pendingCount(): Int =
        try {
            outbox.pendingCount(SyncOutboxEntity.KIND_FAVORITE_SHOW) +
                outbox.pendingCount(SyncOutboxEntity.KIND_FAVORITE_SONG)
        } catch (_: Exception) { 0 }

    private suspend fun flushKind(kind: String): List<PushResult> {
        val pending = try {
            outbox.fetchPending(kind)
        } catch (e: Exception) {
            Log.w(TAG, "fetchPending($kind) failed", e)
            return emptyList()
        }
        val results = mutableListOf<PushResult>()
        for (entry in pending) {
            results += pushOne(entry)
        }
        return results
    }

    private suspend fun pushOne(entry: SyncOutboxEntity): PushResult =
        when (entry.kind) {
            SyncOutboxEntity.KIND_FAVORITE_SHOW -> pushFavoriteShow(entry)
            SyncOutboxEntity.KIND_FAVORITE_SONG -> pushFavoriteSong(entry)
            else -> {
                // Unknown kind — drop it so the queue doesn't get stuck.
                outbox.delete(entry.id)
                PushResult(entry.kind, entry.refId, "NOOP", success = true, error = null)
            }
        }

    private suspend fun pushFavoriteShow(entry: SyncOutboxEntity): PushResult {
        val row = try {
            favoritesDao.getFavoriteShowByIdIncludingTombstones(entry.refId)
        } catch (e: Exception) {
            return failure(entry, "?", "local read failed: ${e.message}")
        }
        if (row == null) {
            outbox.delete(entry.id)
            return PushResult(entry.kind, entry.refId, "NOOP", success = true, error = null)
        }

        return if (row.deletedAt != null) {
            userSyncService.deleteFavoriteShow(row.showId)
                .fold({ success(entry, "DELETE") }, { failure(entry, "DELETE", it.message ?: it::class.simpleName ?: "error") })
        } else {
            val dto = SyncFavoriteShowV3(
                showId = row.showId,
                addedAt = row.addedToFavoritesAt / 1000,
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
    }

    private suspend fun pushFavoriteSong(entry: SyncOutboxEntity): PushResult {
        val localId = entry.refId.toLongOrNull()
            ?: return failure(entry, "?", "invalid song refId: ${entry.refId}")
        val row = try {
            favoriteSongDao.findByLocalIdIncludingTombstones(localId)
        } catch (e: Exception) {
            return failure(entry, "?", "local read failed: ${e.message}")
        }
        if (row == null) {
            outbox.delete(entry.id)
            return PushResult(entry.kind, entry.refId, "NOOP", success = true, error = null)
        }

        return if (row.deletedAt != null) {
            userSyncService.deleteFavoriteSong(row.showId, row.trackTitle)
                .fold({ success(entry, "DELETE") }, { failure(entry, "DELETE", it.message ?: it::class.simpleName ?: "error") })
        } else {
            val dto = SyncFavoriteTrackV3(
                id = null, // server keys by natural (showId, trackTitle); local id wouldn't match
                showId = row.showId,
                trackTitle = row.trackTitle,
                trackNumber = row.trackNumber,
                recordingId = row.recordingId,
                updatedAt = row.updatedAt / 1000,
                deletedAt = null,
            )
            userSyncService.putFavoriteSong(dto)
                .fold({ success(entry, "PUT") }, { failure(entry, "PUT", it.message ?: it::class.simpleName ?: "error") })
        }
    }

    private suspend fun success(entry: SyncOutboxEntity, op: String): PushResult {
        try { outbox.delete(entry.id) } catch (e: Exception) { Log.w(TAG, "outbox.delete failed", e) }
        return PushResult(entry.kind, entry.refId, op, success = true, error = null)
    }

    private suspend fun failure(entry: SyncOutboxEntity, op: String, error: String): PushResult {
        try { outbox.recordFailure(entry.id, System.currentTimeMillis(), error) } catch (_: Exception) {}
        return PushResult(entry.kind, entry.refId, op, success = false, error = error)
    }
}
