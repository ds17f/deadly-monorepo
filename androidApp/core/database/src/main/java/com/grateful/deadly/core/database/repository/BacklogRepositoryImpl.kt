package com.grateful.deadly.core.database.repository

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
 */
@Singleton
class BacklogRepositoryImpl @Inject constructor(
    @AppDatabase private val dao: BacklogDao,
) : BacklogRepository {

    override fun observeShowIds(): Flow<List<String>> =
        dao.observeBacklog().map { rows -> rows.map { it.showId } }

    override suspend fun getShowIds(): List<String> =
        dao.getBacklog().map { it.showId }

    override suspend fun peekHeadId(): String? = dao.peekHead()?.showId

    override suspend fun popHead(): String? {
        val head = dao.peekHead() ?: return null
        dao.tombstone(head.showId, System.currentTimeMillis())
        return head.showId
    }

    override suspend fun addToBottom(showId: String) {
        val nextPosition = (dao.maxPosition() ?: 0L) + 1
        dao.upsert(
            BacklogEntity(
                showId = showId,
                position = nextPosition,
                addedAt = System.currentTimeMillis(),
                deletedAt = null,
            )
        )
    }

    override suspend fun contains(showId: String): Boolean = dao.contains(showId)

    override suspend fun remove(showId: String) {
        dao.tombstone(showId, System.currentTimeMillis())
    }

    override suspend fun reorder(orderedShowIds: List<String>) {
        orderedShowIds.forEachIndexed { index, showId ->
            dao.setPosition(showId, index.toLong())
        }
    }

    override suspend fun clear() {
        dao.tombstoneAll(System.currentTimeMillis())
    }

    override suspend fun count(): Int = dao.count()
}
