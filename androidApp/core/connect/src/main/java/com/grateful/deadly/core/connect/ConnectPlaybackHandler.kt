package com.grateful.deadly.core.connect

import android.util.Log
import com.grateful.deadly.core.api.connect.ConnectPlaybackEvent
import com.grateful.deadly.core.api.connect.ConnectService
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectPlaybackHandler @Inject constructor(
    private val connectService: ConnectService,
    private val mediaControllerRepository: MediaControllerRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            connectService.playbackEvents.collect { event ->
                handleEvent(event)
            }
        }
    }

    private suspend fun handleEvent(event: ConnectPlaybackEvent) {
        when (event) {
            is ConnectPlaybackEvent.PlayOn -> {
                val state = event.state
                Log.d(TAG, "Playing: showId=${state.showId}, recording=${state.recordingId}, track=${state.trackIndex}")
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
            }
            is ConnectPlaybackEvent.Command -> {
                val cmd = event.command
                Log.d(TAG, "Command: ${cmd.action}")
                when (cmd.action) {
                    "play" -> mediaControllerRepository.play()
                    "pause" -> mediaControllerRepository.pause()
                    "next" -> mediaControllerRepository.seekToNext()
                    "prev" -> mediaControllerRepository.seekToPrevious()
                    "seek" -> cmd.seekMs?.let { mediaControllerRepository.seekToPosition(it) }
                }
            }
            is ConnectPlaybackEvent.Stop -> {
                Log.d(TAG, "Session taken, pausing")
                mediaControllerRepository.pause()
            }
        }
    }

    companion object {
        private const val TAG = "ConnectPlayback"
    }
}
