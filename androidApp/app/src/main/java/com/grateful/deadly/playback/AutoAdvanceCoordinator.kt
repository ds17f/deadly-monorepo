package com.grateful.deadly.playback

import android.util.Log
import com.grateful.deadly.core.api.playlist.PlaylistService
import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ADR-0010 Chunk 2 — chronological auto-advance.
 *
 * An independent coordinator whose ONLY input is
 * [MediaControllerRepository.showCompleted] (the positive end-of-show signal
 * from Chunk 1). It reads NO transport/Connect state to decide *whether* or
 * *what* to advance — that interrogation was the v1 whack-a-mole. Gating is
 * intrinsic: only the audio-producing device fires `showCompleted`, so only it
 * advances; a remote-controlling device never reaches end-of-show locally.
 *
 * On end-of-show:
 *   1. **Park** — tell Connect we're done ([ConnectService.sendStop]) so the
 *      server stops believing we're playing and can't drag the device back
 *      during the countdown (the structural fix for the v1 "advance immediately,
 *      no countdown in Connect" workaround).
 *   2. **Cancelable countdown.**
 *   3. **Advance** — [PlaylistService.playShow] of the next chronological show,
 *      whose output is byte-identical to a user tap (local audio + sendLoad).
 *
 * Its only interaction with transport is that final play — input-decoupled,
 * output-normal.
 */
@Singleton
class AutoAdvanceCoordinator @Inject constructor(
    private val media: MediaControllerRepository,
    private val showRepository: ShowRepository,
    private val playlistService: PlaylistService,
    private val connectService: ConnectService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    data class Countdown(val secondsRemaining: Int, val nextShowLabel: String?)

    private val _countdown = MutableStateFlow<Countdown?>(null)
    /** Drives the cancelable "next show in Ns" overlay; null when idle. */
    val countdown: StateFlow<Countdown?> = _countdown.asStateFlow()

    private var advanceJob: Job? = null
    private var started = false

    /** Called once at app launch (MainActivity). */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            media.showCompleted.collect { completedShowId ->
                onShowCompleted(completedShowId)
            }
        }
    }

    /** User canceled the pending advance. */
    fun cancel() {
        Log.d(TAG, "auto-advance: canceled by user")
        advanceJob?.cancel()
        advanceJob = null
        _countdown.value = null
    }

    private fun onShowCompleted(completedShowId: String) {
        Log.d(TAG, "auto-advance: show completed = $completedShowId")
        advanceJob?.cancel()
        advanceJob = scope.launch {
            // 1. Park: tell Connect we're done so the server stops believing we're
            //    playing and can't drag us back during the countdown. Sent
            //    unconditionally — it's a no-op (dropped) when not in a session,
            //    and `connectState` is an unreliable "am I connected" signal
            //    (it can be null mid-session). Only the audio-producing (active)
            //    device reaches here, so a stop is always the right thing to send.
            Log.d(TAG, "auto-advance: parking Connect (sendStop)")
            connectService.sendStop()

            // 2. Resolve the next chronological show.
            val completed = showRepository.getShowById(completedShowId)
            val next = completed?.let { showRepository.getNextShowByDate(it.date) }
            if (next == null) {
                Log.d(TAG, "auto-advance: no next show after $completedShowId — stopping")
                _countdown.value = null
                return@launch
            }

            // 3. Cancelable countdown (server is parked, so no drag-back).
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                _countdown.value = Countdown(remaining, next.displayTitle)
                delay(1000)
            }
            _countdown.value = null

            // 4. Advance — identical to a user tap on the next show.
            Log.d(TAG, "auto-advance: advancing to ${next.displayTitle}")
            playlistService.playShow(next, autoPlay = true)
        }
    }

    companion object {
        private const val TAG = "AutoAdvanceCoordinator"
        private const val COUNTDOWN_SECONDS = 15
    }
}
