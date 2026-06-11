package com.grateful.deadly.playback

import android.util.Log
import com.grateful.deadly.core.api.playlist.PlaylistService
import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.model.Show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.ceil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ADR-0010 Chunk 2 + §7 — chronological auto-advance, cross-device.
 *
 * Driven by [MediaControllerRepository.showCompleted] (the Chunk 1 end-of-show
 * signal). It reads no transport state to *decide* whether to advance.
 *
 * In a Connect session it `announce`s the next show + a server-time deadline; the
 * shared `pendingAdvance` note then drives the countdown UI **and** the advance
 * on every device (this coordinator's note-collector). Offline it runs a purely
 * local countdown. The active device advances when the note is present and the
 * deadline passes; its [PlaylistService.playShow] load clears the note for all.
 * Cancel / Play now work from any device (commands when remote; act directly
 * when active/offline).
 */
@Singleton
class AutoAdvanceCoordinator @Inject constructor(
    private val media: MediaControllerRepository,
    private val showRepository: ShowRepository,
    private val playlistService: PlaylistService,
    private val connectService: ConnectService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    data class Countdown(val secondsRemaining: Int, val nextShow: Show)

    private val _countdown = MutableStateFlow<Countdown?>(null)
    /** Drives the "Next up in Ns" UI; null when idle. */
    val countdown: StateFlow<Countdown?> = _countdown.asStateFlow()

    private var localJob: Job? = null
    private var started = false

    /** Called once at app launch (MainActivity). */
    fun start() {
        if (started) return
        started = true

        // End-of-show → announce (in a session) or run a local countdown.
        scope.launch {
            media.showCompleted.collect { completedShowId -> onShowCompleted(completedShowId) }
        }

        // The shared note drives the countdown + advance when connected. Keyed on
        // the note alone (distinctUntilChanged), so position broadcasts don't
        // restart the ticker; collectLatest restarts it when the note changes
        // (e.g. advance_now moves the deadline).
        scope.launch {
            connectService.connectState
                .map { it?.pendingAdvance }
                .distinctUntilChanged()
                .collectLatest { note ->
                    if (!connectService.isConnected.value) return@collectLatest // offline: local job owns it
                    if (note == null) {
                        _countdown.value = null
                        return@collectLatest
                    }
                    val show = showRepository.getShowById(note.showId) ?: return@collectLatest
                    while (true) {
                        val serverNow = System.currentTimeMillis() + connectService.serverTimeOffsetMs.value
                        val remaining = ceil((note.deadline - serverNow) / 1000.0).toInt()
                        if (remaining <= 0) {
                            _countdown.value = null
                            if (connectService.isActiveDevice.value) {
                                Log.d(TAG, "auto-advance: advancing to ${show.displayTitle}")
                                playlistService.playShow(show, autoPlay = true) // load clears the note
                            }
                            break
                        }
                        _countdown.value = Countdown(remaining, show)
                        delay(1000)
                    }
                }
        }
    }

    /** Cancel the pending advance. */
    fun cancel() {
        Log.d(TAG, "auto-advance: cancel")
        localJob?.cancel()
        localJob = null
        _countdown.value = null
        if (connectService.isConnected.value) connectService.sendCancelAdvance()
    }

    /** Skip the countdown ("Play now"). */
    fun playNow() {
        val show = _countdown.value?.nextShow ?: return
        if (connectService.isConnected.value && !connectService.isActiveDevice.value) {
            connectService.sendAdvanceNow()
            return
        }
        localJob?.cancel()
        localJob = null
        _countdown.value = null
        scope.launch { playlistService.playShow(show, autoPlay = true) }
    }

    private fun onShowCompleted(completedShowId: String) {
        Log.d(TAG, "auto-advance: show completed = $completedShowId")
        localJob?.cancel()
        localJob = scope.launch {
            val completed = showRepository.getShowById(completedShowId)
            val next = completed?.let { showRepository.getNextShowByDate(it.date) }
            if (next == null) {
                Log.d(TAG, "auto-advance: no next show after $completedShowId")
                _countdown.value = null
                return@launch
            }

            if (connectService.isConnected.value) {
                // In a session: announce; the note-collector above drives the rest.
                val deadline = (System.currentTimeMillis() +
                    connectService.serverTimeOffsetMs.value +
                    COUNTDOWN_SECONDS * 1000L).toDouble()
                connectService.sendAnnounceNext(next.id, deadline)
                return@launch
            }

            // Offline: local-only countdown, then advance.
            for (remaining in COUNTDOWN_SECONDS downTo 1) {
                _countdown.value = Countdown(remaining, next)
                delay(1000)
            }
            _countdown.value = null
            playlistService.playShow(next, autoPlay = true)
        }
    }

    companion object {
        private const val TAG = "AutoAdvanceCoordinator"
        private const val COUNTDOWN_SECONDS = 15
    }
}
