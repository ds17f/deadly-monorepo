package com.grateful.deadly.core.playqueue.service

import com.grateful.deadly.core.api.playqueue.PlayQueueService
import com.grateful.deadly.core.database.dao.PlayQueueDao
import com.grateful.deadly.core.database.entities.PlayQueueEntity
import com.grateful.deadly.core.model.AppDatabase
import com.grateful.deadly.core.model.QueuedShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Persistent show-queue implementation (ADR-0010), backed by [PlayQueueDao].
 *
 * Ordering and head/tail mechanics live in the DAO; this service maps entities
 * to [QueuedShow] domain models and exposes a reactive [queue].
 */
@Singleton
class PlayQueueServiceImpl @Inject constructor(
    @AppDatabase private val dao: PlayQueueDao,
    @Named("PlayQueueApplicationScope") private val scope: CoroutineScope
) : PlayQueueService {

    override val queue: StateFlow<List<QueuedShow>> =
        dao.observeQueue()
            .map { rows -> rows.map { it.toQueuedShow() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun enqueue(showId: String, recordingId: String?) {
        dao.append(PlayQueueEntity(showId = showId, recordingId = recordingId, position = 0))
    }

    override suspend fun enqueueNext(
        showId: String,
        recordingId: String?,
        resumeTrackIndex: Int?,
        resumePositionMs: Long?
    ) {
        dao.insertHead(
            PlayQueueEntity(
                showId = showId,
                recordingId = recordingId,
                position = 0,
                resumeTrackIndex = resumeTrackIndex,
                resumePositionMs = resumePositionMs
            )
        )
    }

    override suspend fun remove(id: Long) = dao.deleteById(id)

    override suspend fun removeByShowId(showId: String) = dao.deleteByShowId(showId)

    override suspend fun move(fromIndex: Int, toIndex: Int) {
        val ids = dao.getQueue().map { it.id }.toMutableList()
        if (fromIndex !in ids.indices) return
        val moved = ids.removeAt(fromIndex)
        ids.add(toIndex.coerceIn(0, ids.size), moved)
        dao.reorder(ids)
    }

    override suspend fun clear() = dao.clear()

    override suspend fun peekNext(): QueuedShow? = dao.peekHead()?.toQueuedShow()

    override suspend fun popNext(): QueuedShow? {
        val head = dao.peekHead() ?: return null
        dao.deleteById(head.id)
        return head.toQueuedShow()
    }

    override suspend fun contains(showId: String): Boolean = dao.containsShow(showId)
}

private fun PlayQueueEntity.toQueuedShow(): QueuedShow = QueuedShow(
    id = id,
    showId = showId,
    recordingId = recordingId,
    resumeTrackIndex = resumeTrackIndex,
    resumePositionMs = resumePositionMs
)
