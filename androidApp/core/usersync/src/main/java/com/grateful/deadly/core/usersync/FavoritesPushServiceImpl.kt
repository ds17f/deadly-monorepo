package com.grateful.deadly.core.usersync

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.FavoritesPushService
import com.grateful.deadly.core.api.usersync.PushResult
import com.grateful.deadly.core.api.usersync.SyncBacklogItemV3
import com.grateful.deadly.core.api.usersync.SyncFavoriteShowV3
import com.grateful.deadly.core.api.usersync.SyncFavoriteTrackV3
import com.grateful.deadly.core.api.usersync.SyncPlayerTagV3
import com.grateful.deadly.core.api.usersync.SyncReviewV3
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.dao.FavoriteSongDao
import com.grateful.deadly.core.database.dao.FavoritesDao
import com.grateful.deadly.core.database.dao.RecentShowDao
import com.grateful.deadly.core.database.dao.RecordingPreferenceDao
import com.grateful.deadly.core.database.dao.ShowPlayerTagDao
import com.grateful.deadly.core.database.dao.ShowReviewDao
import com.grateful.deadly.core.database.dao.BacklogDao
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
    @AppDatabase private val recentShowDao: RecentShowDao,
    @AppDatabase private val showReviewDao: ShowReviewDao,
    @AppDatabase private val showPlayerTagDao: ShowPlayerTagDao,
    @AppDatabase private val recordingPreferenceDao: RecordingPreferenceDao,
    @AppDatabase private val backlogDao: BacklogDao,
    private val userSyncService: UserSyncService,
    private val authService: AuthService,
    private val syncCoordinator: UserSyncCoordinator,
) : FavoritesPushService {

    companion object {
        private const val TAG = "FavoritesPushService"
        private const val BACKLOG_REORDER_REF = "_order"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLock = Mutex()

    override fun enqueueAndPush(showId: String) {
        enqueueAndFlush(SyncOutboxEntity.KIND_FAVORITE_SHOW, showId)
    }

    override fun enqueueAndPushFavoriteSong(localId: Long) {
        enqueueAndFlush(SyncOutboxEntity.KIND_FAVORITE_SONG, localId.toString())
    }

    override fun enqueueAndPushRecent(showId: String) {
        enqueueAndFlush(SyncOutboxEntity.KIND_RECENT, showId)
    }

    override fun enqueueAndPushReview(showId: String) {
        enqueueAndFlush(SyncOutboxEntity.KIND_REVIEW, showId)
    }

    override fun enqueueAndPushRecordingPref(showId: String) {
        enqueueAndFlush(SyncOutboxEntity.KIND_RECORDING_PREF, showId)
    }

    override fun enqueueAndPushBacklog(showId: String) {
        enqueueAndFlush(SyncOutboxEntity.KIND_BACKLOG_ITEM, showId)
    }

    override fun enqueueAndPushBacklogReorder() {
        enqueueAndFlush(SyncOutboxEntity.KIND_BACKLOG_REORDER, BACKLOG_REORDER_REF)
    }

    override suspend fun reconcileBacklog(showIds: List<String>): List<PushResult> {
        if (showIds.isEmpty()) return emptyList()
        Log.d(TAG, "reconcileBacklog: re-pushing ${showIds.size} diverged backlog rows")
        for (showId in showIds) enqueueRow(SyncOutboxEntity.KIND_BACKLOG_ITEM, showId)
        // Positions can have diverged too; coalesce a single reorder push so the
        // server order matches local after the rows land.
        enqueueRow(SyncOutboxEntity.KIND_BACKLOG_REORDER, BACKLOG_REORDER_REF)
        return flushPending()
    }

    override suspend fun enqueueAllLocalAndFlush(): List<PushResult> {
        val shows = try { favoritesDao.getAllFavoriteShows() } catch (e: Exception) {
            Log.w(TAG, "getAllFavoriteShows failed", e); emptyList()
        }
        for (show in shows) enqueueRow(SyncOutboxEntity.KIND_FAVORITE_SHOW, show.showId)

        val songs = try { favoriteSongDao.getAllFavorites() } catch (e: Exception) {
            Log.w(TAG, "getAllFavorites failed", e); emptyList()
        }
        for (song in songs) enqueueRow(SyncOutboxEntity.KIND_FAVORITE_SONG, song.id.toString())

        val recents = try { recentShowDao.getRecentShows(4) } catch (e: Exception) {
            Log.w(TAG, "getRecentShows failed", e); emptyList()
        }
        for (recent in recents) enqueueRow(SyncOutboxEntity.KIND_RECENT, recent.showId)

        val reviews = try { showReviewDao.getAll() } catch (e: Exception) {
            Log.w(TAG, "getAll reviews failed", e); emptyList()
        }
        for (review in reviews) enqueueRow(SyncOutboxEntity.KIND_REVIEW, review.showId)

        val recordingPrefs = try { recordingPreferenceDao.getAll() } catch (e: Exception) {
            Log.w(TAG, "getAll recording prefs failed", e); emptyList()
        }
        for (pref in recordingPrefs) enqueueRow(SyncOutboxEntity.KIND_RECORDING_PREF, pref.showId)

        val backlog = try { backlogDao.getBacklog() } catch (e: Exception) {
            Log.w(TAG, "getBacklog failed", e); emptyList()
        }
        for (item in backlog) enqueueRow(SyncOutboxEntity.KIND_BACKLOG_ITEM, item.showId)
        if (backlog.isNotEmpty()) enqueueRow(SyncOutboxEntity.KIND_BACKLOG_REORDER, BACKLOG_REORDER_REF)

        return flushPending()
    }

    private suspend fun enqueueRow(kind: String, refId: String) {
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
        }
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
            results += flushKind(SyncOutboxEntity.KIND_RECENT)
            results += flushKind(SyncOutboxEntity.KIND_REVIEW)
            results += flushKind(SyncOutboxEntity.KIND_RECORDING_PREF)
            results += flushKind(SyncOutboxEntity.KIND_BACKLOG_ITEM)
            results += flushKind(SyncOutboxEntity.KIND_BACKLOG_REORDER)
            if (results.any { it.success && it.operation != "NOOP" }) {
                syncCoordinator.triggerPull("after_push_flush")
            }
            results
        }
    }

    override suspend fun pendingCount(): Int =
        try {
            outbox.pendingCount(SyncOutboxEntity.KIND_FAVORITE_SHOW) +
                outbox.pendingCount(SyncOutboxEntity.KIND_FAVORITE_SONG) +
                outbox.pendingCount(SyncOutboxEntity.KIND_RECENT) +
                outbox.pendingCount(SyncOutboxEntity.KIND_REVIEW) +
                outbox.pendingCount(SyncOutboxEntity.KIND_RECORDING_PREF) +
                outbox.pendingCount(SyncOutboxEntity.KIND_BACKLOG_ITEM) +
                outbox.pendingCount(SyncOutboxEntity.KIND_BACKLOG_REORDER)
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
            SyncOutboxEntity.KIND_RECENT -> pushRecent(entry)
            SyncOutboxEntity.KIND_REVIEW -> pushReview(entry)
            SyncOutboxEntity.KIND_RECORDING_PREF -> pushRecordingPref(entry)
            SyncOutboxEntity.KIND_BACKLOG_ITEM -> pushBacklogItem(entry)
            SyncOutboxEntity.KIND_BACKLOG_REORDER -> pushBacklogReorder(entry)
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

    // Recents are announce-on-play: refId is the showId and the server stamps
    // the time, so there's no local row to read or tombstone to honor (v0).
    private suspend fun pushRecent(entry: SyncOutboxEntity): PushResult {
        return userSyncService.putRecent(entry.refId)
            .fold({ success(entry, "PUT") }, { failure(entry, "PUT", it.message ?: it::class.simpleName ?: "error") })
    }

    // Reviews push by showId. Player tags travel with the review (the server
    // replaces all tags for the show on PUT), so we gather them at push time.
    // A tombstoned review row becomes a DELETE.
    private suspend fun pushReview(entry: SyncOutboxEntity): PushResult {
        val showId = entry.refId
        val row = try {
            showReviewDao.getByShowIdIncludingTombstones(showId)
        } catch (e: Exception) {
            return failure(entry, "?", "local read failed: ${e.message}")
        }
        if (row == null) {
            outbox.delete(entry.id)
            return PushResult(entry.kind, entry.refId, "NOOP", success = true, error = null)
        }

        return if (row.deletedAt != null) {
            userSyncService.deleteReview(showId)
                .fold({ success(entry, "DELETE") }, { failure(entry, "DELETE", it.message ?: it::class.simpleName ?: "error") })
        } else {
            val tags = try {
                showPlayerTagDao.getTagsForShow(showId).map {
                    SyncPlayerTagV3(
                        playerName = it.playerName,
                        instruments = it.instruments,
                        isStandout = it.isStandout,
                        notes = it.notes,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "getTagsForShow($showId) failed", e)
                emptyList()
            }
            val dto = SyncReviewV3(
                showId = row.showId,
                notes = row.notes,
                overallRating = row.customRating?.toDouble(),
                recordingQuality = row.recordingQuality,
                playingQuality = row.playingQuality,
                reviewedRecordingId = row.reviewedRecordingId,
                playerTags = tags.ifEmpty { null },
                updatedAt = row.updatedAt / 1000,
                deletedAt = null,
            )
            userSyncService.putReview(dto)
                .fold({ success(entry, "PUT") }, { failure(entry, "PUT", it.message ?: it::class.simpleName ?: "error") })
        }
    }

    // Recording prefs push by showId. The flusher reads the current row at
    // push time: a live row is a PUT, an absent or tombstoned row is a DELETE.
    private suspend fun pushRecordingPref(entry: SyncOutboxEntity): PushResult {
        val showId = entry.refId
        val row = try {
            recordingPreferenceDao.get(showId)
        } catch (e: Exception) {
            return failure(entry, "?", "local read failed: ${e.message}")
        }

        return if (row == null || row.deletedAt != null) {
            userSyncService.deleteRecordingPref(showId)
                .fold({ success(entry, "DELETE") }, { failure(entry, "DELETE", it.message ?: it::class.simpleName ?: "error") })
        } else {
            userSyncService.putRecordingPref(showId, row.recordingId)
                .fold({ success(entry, "PUT") }, { failure(entry, "PUT", it.message ?: it::class.simpleName ?: "error") })
        }
    }

    // Backlog item push by showId. Read the row (incl. tombstones) at push
    // time: a live row is a PUT, a tombstoned or absent row is a DELETE.
    private suspend fun pushBacklogItem(entry: SyncOutboxEntity): PushResult {
        val showId = entry.refId
        val row = try {
            backlogDao.getById(showId)
        } catch (e: Exception) {
            return failure(entry, "?", "local read failed: ${e.message}")
        }

        return if (row == null || row.deletedAt != null) {
            userSyncService.deleteBacklogItem(showId)
                .fold({ success(entry, "DELETE") }, { failure(entry, "DELETE", it.message ?: it::class.simpleName ?: "error") })
        } else {
            val dto = SyncBacklogItemV3(
                showId = row.showId,
                position = row.position.toInt(),
                addedAt = row.addedAt / 1000,
                updatedAt = row.updatedAt / 1000,
                deletedAt = null,
            )
            userSyncService.putBacklogItem(dto)
                .fold({ success(entry, "PUT") }, { failure(entry, "PUT", it.message ?: it::class.simpleName ?: "error") })
        }
    }

    // Reorder push: read the current live order and PUT the whole list.
    private suspend fun pushBacklogReorder(entry: SyncOutboxEntity): PushResult {
        val showIds = try {
            backlogDao.getBacklog().map { it.showId }
        } catch (e: Exception) {
            return failure(entry, "?", "local read failed: ${e.message}")
        }
        return userSyncService.reorderBacklog(showIds)
            .fold({ success(entry, "PUT") }, { failure(entry, "PUT", it.message ?: it::class.simpleName ?: "error") })
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
