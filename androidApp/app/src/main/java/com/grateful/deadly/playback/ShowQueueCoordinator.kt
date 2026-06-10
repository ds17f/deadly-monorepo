package com.grateful.deadly.playback

import android.util.Log
import com.grateful.deadly.core.api.playqueue.PlayQueueService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped driver for the show queue's playback behavior (ADR-0010):
 *
 *  - **Pop on play (#5):** whenever a show becomes current, remove it from the
 *    upcoming queue — so playing a queued show from *anywhere* consumes it.
 *  - **Auto-advance (#4):** when a show ends, advance to the next queued show
 *    (or, in "queue then history" mode, the next show by date) per the user's
 *    end-of-show preference, immediately or after a cancelable 15s countdown.
 *  - **Interrupt snackbar:** when a play-now replaces a different playing show,
 *    surface a "Queue A" offer to re-queue it with a resume snapshot.
 *
 * Observes [MediaControllerRepository] flows; drives playback via
 * [MediaControllerRepository.playShow]. [start] is called once at app launch.
 */
@Singleton
class ShowQueueCoordinator @Inject constructor(
    private val media: MediaControllerRepository,
    private val playQueue: PlayQueueService,
    private val showRepository: ShowRepository,
    private val appPreferences: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    data class CountdownState(val remaining: Int, val nextLabel: String?)

    /** A show interrupted by a play-now, hydrated for the "Queue A" snackbar. */
    data class InterruptInfo(
        val showId: String,
        val label: String,
        val recordingId: String?,
        val resumeTrackIndex: Int?,
        val resumePositionMs: Long?,
    )

    private val _countdown = MutableStateFlow<CountdownState?>(null)
    val countdown: StateFlow<CountdownState?> = _countdown.asStateFlow()

    private val _interrupt = MutableStateFlow<InterruptInfo?>(null)
    val interrupt: StateFlow<InterruptInfo?> = _interrupt.asStateFlow()

    private var lastShowId: String? = null
    private var countdownJob: Job? = null
    private var interruptDismissJob: Job? = null

    @Volatile private var isAutoAdvancing = false
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true

        // When a show becomes current: pop it from the queue *only if it's the
        // head* (playing the next-up show consumes it; playing one from deeper in
        // the queue leaves it in place). Cancel any pending countdown on a user play.
        scope.launch {
            media.currentShowId.collect { showId ->
                if (showId != null && showId != lastShowId) {
                    lastShowId = showId
                    if (!isAutoAdvancing) {
                        if (playQueue.peekNext()?.showId == showId) {
                            playQueue.removeByShowId(showId)
                        }
                        cancelCountdown()
                    }
                }
            }
        }

        // Hydrate + surface the interrupt "Queue A" snackbar.
        scope.launch {
            media.interruptEvents.collect { snap ->
                val show = runCatching { showRepository.getShowById(snap.showId) }.getOrNull()
                _interrupt.value = InterruptInfo(
                    showId = snap.showId,
                    label = show?.date ?: "previous show",
                    recordingId = snap.recordingId,
                    resumeTrackIndex = snap.trackIndex.takeIf { it > 0 },
                    resumePositionMs = snap.positionMs.takeIf { it > 0 },
                )
                interruptDismissJob?.cancel()
                interruptDismissJob = scope.launch {
                    delay(INTERRUPT_TIMEOUT_MS)
                    _interrupt.value = null
                }
            }
        }

        // End-of-show detection. Only treat ENDED as end-of-show if we actually
        // played in this session — otherwise a cold-start / restored ENDED state
        // would spuriously auto-advance (and auto-play) at launch.
        scope.launch {
            var hasBeenPlaying = false
            media.playbackState.collect { state ->
                if (state == PlaybackState.PLAYING) hasBeenPlaying = true
                if (state == PlaybackState.ENDED && hasBeenPlaying) {
                    hasBeenPlaying = false
                    onShowEnded()
                }
            }
        }
    }

    /** Re-queue the interrupted show at the head with its resume snapshot. */
    fun requeueInterrupted() {
        val info = _interrupt.value ?: return
        scope.launch {
            playQueue.enqueueNext(
                showId = info.showId,
                recordingId = info.recordingId,
                resumeTrackIndex = info.resumeTrackIndex,
                resumePositionMs = info.resumePositionMs,
            )
        }
        dismissInterrupt()
    }

    fun dismissInterrupt() {
        interruptDismissJob?.cancel()
        _interrupt.value = null
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _countdown.value = null
    }

    private suspend fun onShowEnded() {
        val mode = appPreferences.endOfShowMode.value
        if (mode == AppPreferences.END_OF_SHOW_OFF) return

        val nextQueued = playQueue.peekNext()
        val nextHistory = if (nextQueued == null && mode == AppPreferences.END_OF_SHOW_QUEUE_HISTORY) {
            lastShowId?.let { showRepository.getShowById(it) }
                ?.let { showRepository.getNextShowByDate(it.date) }
        } else null

        if (nextQueued == null && nextHistory == null) return

        val label = nextQueued?.let { showRepository.getShowById(it.showId)?.date }
            ?: nextHistory?.date

        if (appPreferences.endOfShowImmediate.value) {
            advance()
        } else {
            cancelCountdown()
            countdownJob = scope.launch {
                for (remaining in AppPreferences.END_OF_SHOW_COUNTDOWN_SECONDS downTo 1) {
                    _countdown.value = CountdownState(remaining, label)
                    delay(1000)
                }
                _countdown.value = null
                advance()
            }
        }
    }

    /** Pop the queue head (or roll to the next show by date) and play it. */
    private suspend fun advance() {
        isAutoAdvancing = true
        try {
            val mode = appPreferences.endOfShowMode.value
            val endedShowId = lastShowId
            // Pop the head, skipping any entry for the show that just ended so we
            // never replay the current show (guards a queue that still holds it).
            var queued = playQueue.popNext()
            while (queued != null && queued.showId == endedShowId) {
                queued = playQueue.popNext()
            }
            val targetShowId: String
            val recordingOverride: String?
            val resumeTrackIndex: Int
            val resumePositionMs: Long
            if (queued != null) {
                targetShowId = queued.showId
                recordingOverride = queued.recordingId
                resumeTrackIndex = queued.resumeTrackIndex ?: 0
                resumePositionMs = queued.resumePositionMs ?: 0L
            } else if (mode == AppPreferences.END_OF_SHOW_QUEUE_HISTORY) {
                val current = lastShowId?.let { showRepository.getShowById(it) } ?: return
                val next = showRepository.getNextShowByDate(current.date) ?: return
                targetShowId = next.id
                recordingOverride = null
                resumeTrackIndex = 0
                resumePositionMs = 0L
            } else {
                return
            }

            val show = showRepository.getShowById(targetShowId) ?: return
            val recordingId = recordingOverride ?: show.bestRecordingId ?: return
            media.playShow(
                recordingId = recordingId,
                showId = show.id,
                showDate = show.date,
                venue = show.venue.name,
                location = show.location.displayText,
                coverImageUrl = show.coverImageUrl,
                startTrackIndex = resumeTrackIndex,
                startPosition = resumePositionMs,
                source = "auto_advance",
            )
        } catch (e: Exception) {
            Log.e(TAG, "advance() failed", e)
        } finally {
            // Let currentShowId settle before re-enabling interrupt detection.
            delay(1500)
            isAutoAdvancing = false
        }
    }

    companion object {
        private const val TAG = "ShowQueueCoordinator"
        private const val INTERRUPT_TIMEOUT_MS = 6000L
    }
}
