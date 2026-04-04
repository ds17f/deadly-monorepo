package com.grateful.deadly.core.connect

import android.util.Log
import com.grateful.deadly.core.api.connect.ConnectPlaybackEvent
import com.grateful.deadly.core.api.connect.ConnectService
import com.grateful.deadly.core.api.connect.OutgoingPlaybackState
import com.grateful.deadly.core.api.connect.SessionTrack
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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

    // When true, the diff-detection loop skips one reaction cycle.
    // Set before sending a session_update after session_play_on so the
    // server's echo (which may carry stale position) doesn't re-trigger actions.
    private var suppressDiffReaction = false

    // Previous user_state for diff detection
    private var prevIsPlaying: Boolean? = null
    private var prevTrackIndex: Int? = null
    private var prevPositionMs: Long? = null
    private var prevUpdatedAt: Long = 0

    init {
        scope.launch {
            connectService.playbackEvents.collect { event ->
                handleEvent(event)
            }
        }
        // Periodic session_update every 5s while playing (keeps web progress bar in sync)
        scope.launch {
            mediaControllerRepository.isPlaying.collect { playing ->
                periodicReportJob?.cancel()
                if (playing) {
                    periodicReportJob = scope.launch {
                        while (true) {
                            delay(connectService.config.value.positionUpdateIntervalMs)
                            sendUpdate("playing")
                        }
                    }
                }
            }
        }
        // React to server state diffs when this device is the active player
        scope.launch {
            connectService.userState.collect { state ->
                if (state == null) {
                    prevIsPlaying = null
                    prevTrackIndex = null
                    prevPositionMs = null
                    prevUpdatedAt = 0
                    return@collect
                }

                val isActive = state.activeDeviceId == connectService.deviceId

                if (!isActive) {
                    prevIsPlaying = state.isPlaying
                    prevTrackIndex = state.trackIndex
                    prevPositionMs = state.positionMs
                    prevUpdatedAt = state.updatedAt
                    return@collect
                }

                val pPlaying = prevIsPlaying
                val pTrackIndex = prevTrackIndex
                val pPositionMs = prevPositionMs

                if (pPlaying == null || pTrackIndex == null || pPositionMs == null) {
                    prevIsPlaying = state.isPlaying
                    prevTrackIndex = state.trackIndex
                    prevPositionMs = state.positionMs
                    prevUpdatedAt = state.updatedAt
                    return@collect
                }

                // Only react if updatedAt actually changed (new server broadcast)
                if (state.updatedAt == prevUpdatedAt) {
                    return@collect
                }

                // Skip this cycle if a local action (e.g. session_play_on) just
                // sent an update — the echo would carry stale position data
                if (suppressDiffReaction) {
                    suppressDiffReaction = false
                    prevIsPlaying = state.isPlaying
                    prevTrackIndex = state.trackIndex
                    prevPositionMs = state.positionMs
                    prevUpdatedAt = state.updatedAt
                    return@collect
                }

                // Play/pause diff
                if (state.isPlaying != pPlaying) {
                    if (state.isPlaying) {
                        mediaControllerRepository.play()
                        Log.d(TAG, "State diff: play")
                    } else {
                        mediaControllerRepository.pause()
                        Log.d(TAG, "State diff: pause")
                    }
                }

                // Track index diff (next/prev)
                if (state.trackIndex != pTrackIndex) {
                    if (state.trackIndex > pTrackIndex) {
                        mediaControllerRepository.seekToNext()
                    } else {
                        mediaControllerRepository.seekToPrevious()
                    }
                    Log.d(TAG, "State diff: track $pTrackIndex → ${state.trackIndex}")
                }

                // Seek diff (divergence > threshold, same track & play state)
                val seekThreshold = connectService.config.value.seekDivergenceThresholdMs
                if (state.trackIndex == pTrackIndex &&
                    state.isPlaying == pPlaying &&
                    abs(state.positionMs - pPositionMs) > seekThreshold
                ) {
                    val currentMs = mediaControllerRepository.currentPosition.value
                    if (abs(state.positionMs - currentMs) > seekThreshold) {
                        mediaControllerRepository.seekToPosition(state.positionMs)
                        Log.d(TAG, "State diff: seek to ${state.positionMs}ms")
                    }
                }

                prevIsPlaying = state.isPlaying
                prevTrackIndex = state.trackIndex
                prevPositionMs = state.positionMs
                prevUpdatedAt = state.updatedAt
            }
        }
    }

    private fun sendUpdate(status: String, tracks: List<SessionTrack>? = null) {
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
            tracks = tracks,
        ))
    }

    private suspend fun handleEvent(event: ConnectPlaybackEvent) {
        when (event) {
            is ConnectPlaybackEvent.PlayOn -> {
                val state = event.state
                val shouldPlay = state.status != "paused"
                // Compensate position for server relay + network transit time
                val relayedAt = event.relayedAt
                val adjustedPositionMs = if (relayedAt != null && shouldPlay) {
                    state.positionMs + (System.currentTimeMillis() - relayedAt)
                } else {
                    state.positionMs
                }
                Log.d(TAG, "PlayOn: showId=${state.showId}, recording=${state.recordingId}, track=${state.trackIndex}, shouldPlay=$shouldPlay, positionMs=${state.positionMs}, adjustedMs=$adjustedPositionMs")
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
                    position = adjustedPositionMs,
                )
                if (!shouldPlay) {
                    mediaControllerRepository.pause()
                    Log.d(TAG, "Paused after loading (source was paused)")
                }
                // Build tracks list from the current queue for server-side next/prev
                val mediaItems = mediaControllerRepository.getCurrentMediaItems()
                val sessionTracks = mediaItems.map { item ->
                    SessionTrack(
                        title = item.mediaMetadata.title?.toString() ?: "",
                        duration = 0.0,  // duration not known until playback
                    )
                }
                // Suppress the diff loop from reacting to the server's
                // echo of this update (which may carry a stale position)
                suppressDiffReaction = true
                val reportedStatus = if (shouldPlay) "playing" else "paused"
                sendUpdate(reportedStatus, tracks = sessionTracks.ifEmpty { null })
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
