package com.grateful.deadly.core.database.repository

import com.grateful.deadly.core.api.usersync.FavoritesPushService
import com.grateful.deadly.core.database.dao.BacklogDao
import com.grateful.deadly.core.database.entities.BacklogEntity
import com.grateful.deadly.core.domain.repository.BacklogRepository
import com.grateful.deadly.core.model.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [BacklogRepository]. Ordering is by `position` ascending; remove
 * tombstones (sync-friendly), add appends below the current max position so a
 * re-added show goes to the bottom and its tombstone clears.
 *
 * Every mutation bumps `updatedAt` (the LWW comparator) and enqueues a
 * per-action push (slice 4) via [FavoritesPushService] — the flusher reads the
 * current row at push time and decides PUT vs DELETE.
 */
@Singleton
class BacklogRepositoryImpl @Inject constructor(
    @AppDatabase private val dao: BacklogDao,
    private val pushService: FavoritesPushService,
) : BacklogRepository {

    override fun observeShowIds(): Flow<List<String>> =
        dao.observeBacklog().map { rows -> rows.map { it.showId } }

    override suspend fun getShowIds(): List<String> =
        dao.getBacklog().map { it.showId }

    override suspend fun peekHeadId(): String? = dao.peekHead()?.showId

    override suspend fun popHead(): String? {
        val head = dao.peekHead() ?: return null
        dao.tombstone(head.showId, System.currentTimeMillis())
        pushService.enqueueAndPushBacklog(head.showId)
        return head.showId
    }

    override suspend fun addToBottom(showId: String) {
        val nextPosition = (dao.maxPosition() ?: 0L) + 1
        val now = System.currentTimeMillis()
        dao.upsert(
            BacklogEntity(
                showId = showId,
                position = nextPosition,
                addedAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        )
        pushService.enqueueAndPushBacklog(showId)
    }

    override suspend fun contains(showId: String): Boolean = dao.contains(showId)

    override suspend fun remove(showId: String) {
        dao.tombstone(showId, System.currentTimeMillis())
        pushService.enqueueAndPushBacklog(showId)
    }

    override suspend fun reorder(orderedShowIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedShowIds.forEachIndexed { index, showId ->
            dao.setPosition(showId, index.toLong(), now)
        }
        pushService.enqueueAndPushBacklogReorder()
    }

    override suspend fun clear() {
        val cleared = dao.getBacklog().map { it.showId }
        dao.tombstoneAll(System.currentTimeMillis())
        cleared.forEach { pushService.enqueueAndPushBacklog(it) }
    }

    override suspend fun count(): Int = dao.count()
}
