package com.grateful.deadly.core.connect

import android.util.Log
import com.grateful.deadly.core.api.connect.ConnectPlaybackEvent
import com.grateful.deadly.core.api.connect.ConnectService
import com.grateful.deadly.core.api.connect.OutgoingPlaybackState
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectPlaybackHandler @Inject constructor(
    private val connectService: ConnectService,
    private val mediaControllerRepository: MediaControllerRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Cache metadata from the last PlayOn event for session updates
    private var lastShowDate: String? = null
    private var lastVenue: String? = null
    private var lastLocation: String? = null
    private var periodicReportJob: Job? = null

    init {
        scope.launch {
            connectService.playbackEvents.collect { event ->
                handleEvent(event)
            }
        }
        // Periodic session_update every 15s while playing (keeps web progress bar in sync)
        scope.launch {
            mediaControllerRepository.isPlaying.collect { playing ->
                periodicReportJob?.cancel()
                if (playing) {
                    periodicReportJob = scope.launch {
                        while (true) {
                            delay(15_000)
                            sendUpdate("playing")
                        }
                    }
                }
            }
        }
    }

    private fun sendUpdate(status: String) {
        val showId = mediaControllerRepository.currentShowId.value ?: return
        val recordingId = mediaControllerRepository.currentRecordingId.value ?: return
        connectService.sendSessionUpdate(OutgoingPlaybackState(
            showId = showId,
            recordingId = recordingId,
            trackIndex = mediaControllerRepository.currentTrackIndex.value,
            positionMs = mediaControllerRepository.currentPosition.value,
            durationMs = mediaControllerRepository.duration.value,
            trackTitle = null,
            status = status,
            date = lastShowDate,
            venue = lastVenue,
            location = lastLocation,
        ))
    }

    private suspend fun handleEvent(event: ConnectPlaybackEvent) {
        when (event) {
            is ConnectPlaybackEvent.PlayOn -> {
                val state = event.state
                Log.d(TAG, "Playing: showId=${state.showId}, recording=${state.recordingId}, track=${state.trackIndex}")
                lastShowDate = state.date
                lastVenue = state.venue
                lastLocation = state.location
                mediaControllerRepository.playTrack(
                    trackIndex = state.trackIndex,
                    recordingId = state.recordingId,
                    format = "VBR MP3",
                    showId = state.showId,
                    showDate = state.date ?: "",
                    venue = state.venue,
                    location = state.location,
                    position = state.positionMs,
                )
                sendUpdate("playing")
            }
            is ConnectPlaybackEvent.Command -> {
                val cmd = event.command
                Log.d(TAG, "Command: ${cmd.action}")
                when (cmd.action) {
                    "play" -> {
                        mediaControllerRepository.play()
                        sendUpdate("playing")
                    }
                    "pause" -> {
                        mediaControllerRepository.pause()
                        sendUpdate("paused")
                    }
                    "next" -> mediaControllerRepository.seekToNext()
                    "prev" -> mediaControllerRepository.seekToPrevious()
                    "seek" -> cmd.seekMs?.let { mediaControllerRepository.seekToPosition(it) }
                }
            }
            is ConnectPlaybackEvent.Stop -> {
                Log.d(TAG, "Session taken, pausing")
                mediaControllerRepository.pause()
                sendUpdate("stopped")
            }
        }
    }

    companion object {
        private const val TAG = "ConnectPlayback"
    }
}
