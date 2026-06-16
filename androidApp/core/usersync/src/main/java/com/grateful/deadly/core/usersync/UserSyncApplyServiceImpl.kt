package com.grateful.deadly.core.usersync

import android.util.Log
import com.grateful.deadly.core.api.auth.AuthService
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.usersync.ApplyResult
import com.grateful.deadly.core.api.usersync.SyncBacklogItemV3
import com.grateful.deadly.core.api.usersync.SyncFavoriteShowV3
import com.grateful.deadly.core.api.usersync.SyncFavoriteTrackV3
import com.grateful.deadly.core.api.usersync.SyncRecordingPrefV3
import com.grateful.deadly.core.api.usersync.SyncReviewV3
import com.grateful.deadly.core.api.usersync.UserSyncApplyService
import com.grateful.deadly.core.api.usersync.UserSyncService
import com.grateful.deadly.core.database.dao.BacklogDao
import com.grateful.deadly.core.database.dao.FavoriteSongDao
import com.grateful.deadly.core.database.dao.FavoritesDao
import com.grateful.deadly.core.database.dao.RecordingPreferenceDao
import com.grateful.deadly.core.database.dao.ShowDao
import com.grateful.deadly.core.database.dao.ShowPlayerTagDao
import com.grateful.deadly.core.database.dao.ShowReviewDao
import com.grateful.deadly.core.database.entities.BacklogEntity
import com.grateful.deadly.core.database.entities.FavoriteShowEntity
import com.grateful.deadly.core.database.entities.FavoriteSongEntity
import com.grateful.deadly.core.database.entities.RecordingPreferenceEntity
import com.grateful.deadly.core.database.entities.ShowPlayerTagEntity
import com.grateful.deadly.core.database.entities.ShowReviewEntity
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
    @AppDatabase private val showReviewDao: ShowReviewDao,
    @AppDatabase private val showPlayerTagDao: ShowPlayerTagDao,
    @AppDatabase private val recordingPreferenceDao: RecordingPreferenceDao,
    @AppDatabase private val backlogDao: BacklogDao,
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
                val reviews = applyReviews(backup.reviews)
                applyRecordingPreferences(backup.recordingPreferences)
                val backlogPushIds = applyBacklog(backup.backlog ?: emptyList())
                shows.copy(
                    backlogPushIds = backlogPushIds,
                    favoriteSongsScanned = songs.favoriteSongsScanned,
                    favoriteSongsApplied = songs.favoriteSongsApplied,
                    favoriteSongsSkippedLocalNewer = songs.favoriteSongsSkippedLocalNewer,
                    favoriteSongsSkippedMissingShow = songs.favoriteSongsSkippedMissingShow,
                    reviewsScanned = reviews.reviewsScanned,
                    reviewsApplied = reviews.reviewsApplied,
                    reviewsSkippedLocalNewer = reviews.reviewsSkippedLocalNewer,
                    reviewsSkippedMissingShow = reviews.reviewsSkippedMissingShow,
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

    private suspend fun applyReviews(remote: List<SyncReviewV3>): ApplyResult {
        var applied = 0
        var skippedLocalNewer = 0
        var skippedMissingShow = 0

        for (dto in remote) {
            // Reviews FK to shows — skip ones we don't have in the catalog.
            if (showDao.getShowById(dto.showId) == null) {
                skippedMissingShow++
                continue
            }

            val local = try {
                showReviewDao.getByShowIdIncludingTombstones(dto.showId)
            } catch (e: Exception) {
                Log.w(TAG, "review read failed for ${dto.showId}: ${e.message}")
                continue
            }

            val remoteUpdatedMs = dto.updatedAt * 1000
            if (local != null && local.updatedAt >= remoteUpdatedMs) {
                skippedLocalNewer++
                continue
            }

            try {
                showReviewDao.upsert(
                    ShowReviewEntity(
                        showId = dto.showId,
                        notes = dto.notes,
                        customRating = dto.overallRating?.toFloat(),
                        recordingQuality = dto.recordingQuality,
                        playingQuality = dto.playingQuality,
                        reviewedRecordingId = dto.reviewedRecordingId,
                        createdAt = local?.createdAt ?: remoteUpdatedMs,
                        updatedAt = remoteUpdatedMs,
                        deletedAt = dto.deletedAt?.let { it * 1000 },
                    )
                )

                // Player tags travel with the review — replace the local set
                // (skip when the review is tombstoned; there are no tags then).
                showPlayerTagDao.removeTagsForShow(dto.showId)
                if (dto.deletedAt == null) {
                    dto.playerTags?.forEach { tag ->
                        showPlayerTagDao.upsert(
                            ShowPlayerTagEntity(
                                showId = dto.showId,
                                playerName = tag.playerName,
                                instruments = tag.instruments,
                                isStandout = tag.isStandout,
                                notes = tag.notes,
                                createdAt = remoteUpdatedMs,
                            )
                        )
                    }
                }
                applied++
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                Log.w(TAG, "skip review ${dto.showId}: constraint")
                skippedMissingShow++
            } catch (e: Exception) {
                Log.w(TAG, "apply review ${dto.showId} failed: ${e.message}")
            }
        }

        Log.d(
            TAG,
            "reviews: scanned=${remote.size} applied=$applied " +
                "skippedLocalNewer=$skippedLocalNewer skippedMissingShow=$skippedMissingShow"
        )
        return ApplyResult(
            favoriteShowsScanned = 0,
            favoriteShowsApplied = 0,
            favoriteShowsSkippedLocalNewer = 0,
            favoriteShowsSkippedMissingShow = 0,
            reviewsScanned = remote.size,
            reviewsApplied = applied,
            reviewsSkippedLocalNewer = skippedLocalNewer,
            reviewsSkippedMissingShow = skippedMissingShow,
        )
    }

    // Recording prefs are a singleton-per-show row keyed by showId. FK to shows,
    // so skip prefs for shows not in the local catalog. Last-write-wins on
    // updatedAt; a remote tombstone soft-deletes the local row.
    // Backlog (Show Queue). One row per show keyed by showId; LWW on updatedAt,
    // a remote tombstone tombstones locally. No FK to shows, so unknown shows
    // are stored and simply ignored by the UI until the catalog knows them.
    // Returns showIds the coordinator should re-push: local rows the server is
    // missing or has an older copy of (incl. tombstones). This is the reverse
    // half of the merge — the pull already carries server→local; here we detect
    // local→server divergence so a dropped add/remove event heals on next pull.
    private suspend fun applyBacklog(remote: List<SyncBacklogItemV3>): List<String> {
        var applied = 0
        var skippedLocalNewer = 0
        for (dto in remote) {
            val local = try {
                backlogDao.getById(dto.showId)
            } catch (e: Exception) {
                Log.w(TAG, "backlog read failed for ${dto.showId}: ${e.message}")
                continue
            }
            val remoteUpdatedMs = dto.updatedAt * 1000
            if (local != null && local.updatedAt >= remoteUpdatedMs) {
                skippedLocalNewer++
                continue
            }
            try {
                backlogDao.upsert(
                    BacklogEntity(
                        showId = dto.showId,
                        position = dto.position.toLong(),
                        addedAt = dto.addedAt * 1000,
                        updatedAt = remoteUpdatedMs,
                        deletedAt = dto.deletedAt?.let { it * 1000 },
                    )
                )
                applied++
            } catch (e: Exception) {
                Log.w(TAG, "apply backlog ${dto.showId} failed: ${e.message}")
            }
        }

        // Reverse delta: compare local rows (incl. tombstones) against the server
        // list. Compare at SECOND granularity — the wire format truncates ms, so a
        // strict ms comparison would flag every row as "local newer" forever and
        // re-push on every pull (an infinite loop). A row needs pushing when the
        // server lacks it or its second-precision updatedAt is older than local's.
        val serverSecByShow = remote.associate { it.showId to it.updatedAt }
        val pushIds = try {
            backlogDao.getAllIncludingTombstones().filter { localRow ->
                val serverSec = serverSecByShow[localRow.showId]
                if (serverSec == null) {
                    // Server has no row. Only a LIVE local row is a real add it
                    // missed — a local tombstone the server never had is already
                    // converged (server-absent == not in queue), so don't re-push
                    // a DELETE that would just 404 on every foreground.
                    localRow.deletedAt == null
                } else {
                    // Server has the row: push when local is strictly newer
                    // (covers live edits and a remove the server hasn't seen).
                    (localRow.updatedAt / 1000) > serverSec
                }
            }.map { it.showId }
        } catch (e: Exception) {
            Log.w(TAG, "backlog reconcile scan failed: ${e.message}"); emptyList()
        }

        Log.d(TAG, "backlog: scanned=${remote.size} applied=$applied skippedLocalNewer=$skippedLocalNewer toPush=${pushIds.size}")
        return pushIds
    }

    private suspend fun applyRecordingPreferences(remote: List<SyncRecordingPrefV3>) {
        var applied = 0
        var skippedLocalNewer = 0
        var skippedMissingShow = 0

        for (dto in remote) {
            if (showDao.getShowById(dto.showId) == null) {
                skippedMissingShow++
                continue
            }

            val local = try {
                recordingPreferenceDao.get(dto.showId)
            } catch (e: Exception) {
                Log.w(TAG, "recording pref read failed for ${dto.showId}: ${e.message}")
                continue
            }

            val remoteUpdatedMs = dto.updatedAt * 1000
            if (local != null && local.updatedAt >= remoteUpdatedMs) {
                skippedLocalNewer++
                continue
            }

            try {
                recordingPreferenceDao.upsert(
                    RecordingPreferenceEntity(
                        showId = dto.showId,
                        recordingId = dto.recordingId,
                        updatedAt = remoteUpdatedMs,
                        deletedAt = dto.deletedAt?.let { it * 1000 },
                    )
                )
                applied++
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                skippedMissingShow++
            } catch (e: Exception) {
                Log.w(TAG, "apply recording pref ${dto.showId} failed: ${e.message}")
            }
        }

        Log.d(
            TAG,
            "recording_prefs: scanned=${remote.size} applied=$applied " +
                "skippedLocalNewer=$skippedLocalNewer skippedMissingShow=$skippedMissingShow"
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
