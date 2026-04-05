package com.grateful.deadly.core.connect

import android.util.Log
import com.grateful.deadly.core.api.connect.ConnectConnectionState
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

    // When true, the announce observer skips one cycle.
    // Set before the diff loop applies a server-originated change
    // so the resulting local state change doesn't echo back.
    private var suppressAnnounce = false

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
        // Periodic session_update every 5s while playing (keeps web progress bar in sync).
        // Only sends when THIS device is the active player and initial state received.
        scope.launch {
            mediaControllerRepository.isPlaying.collect { playing ->
                periodicReportJob?.cancel()
                if (playing) {
                    periodicReportJob = scope.launch {
                        while (true) {
                            delay(connectService.config.value.positionUpdateIntervalMs)
                            if (!connectService.receivedInitialState.value) continue
                            if (connectService.userState.value?.activeDeviceId == connectService.deviceId) {
                                sendUpdate("playing")
                            }
                        }
                    }
                }
            }
        }
        // Immediate announce: send session_update when local play/pause changes.
        // Only announces when this device is active or no device is active (claiming).
        scope.launch {
            var lastIsPlaying: Boolean? = null
            mediaControllerRepository.isPlaying.collect { playing ->
                val prev = lastIsPlaying
                lastIsPlaying = playing
                if (prev == null || prev == playing) return@collect
                if (!connectService.receivedInitialState.value) return@collect
                if (mediaControllerRepository.currentShowId.value == null) return@collect
                if (connectService.connectionState.value != ConnectConnectionState.CONNECTED) return@collect
                if (suppressAnnounce) {
                    suppressAnnounce = false
                    Log.d(TAG, "Announce suppressed (server-originated play/pause)")
                    return@collect
                }
                // Only announce if this device is active or no device is active
                val activeId = connectService.userState.value?.activeDeviceId
                if (activeId != null && activeId != connectService.deviceId) {
                    Log.d(TAG, "Announce skipped (another device is active)")
                    return@collect
                }
                val status = if (playing) "playing" else "paused"
                sendUpdate(status)
                suppressDiffReaction = true
                Log.d(TAG, "Announced: status=$status")
            }
        }
        // Immediate announce: send session_update when track index changes.
        scope.launch {
            var lastTrackIndex: Int? = null
            mediaControllerRepository.currentTrackIndex.collect { trackIndex ->
                val prev = lastTrackIndex
                lastTrackIndex = trackIndex
                if (prev == null || prev == trackIndex) return@collect
                if (!connectService.receivedInitialState.value) return@collect
                if (mediaControllerRepository.currentShowId.value == null) return@collect
                if (connectService.connectionState.value != ConnectConnectionState.CONNECTED) return@collect
                if (suppressAnnounce) {
                    suppressAnnounce = false
                    Log.d(TAG, "Announce suppressed (server-originated track change)")
                    return@collect
                }
                val activeId = connectService.userState.value?.activeDeviceId
                if (activeId != null && activeId != connectService.deviceId) {
                    Log.d(TAG, "Announce skipped (another device is active)")
                    return@collect
                }
                val status = if (mediaControllerRepository.isPlaying.value) "playing" else "paused"
                sendUpdate(status)
                suppressDiffReaction = true
                Log.d(TAG, "Announced: track changed $prev → $trackIndex, status=$status")
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
                    suppressAnnounce = true
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
                    suppressAnnounce = true
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
                        suppressAnnounce = true
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

    private suspend fun sendUpdate(
        status: String,
        tracks: List<SessionTrack>? = null,
        positionOverrideMs: Long? = null,
    ) {
        val showId = mediaControllerRepository.currentShowId.value
        val recordingId = mediaControllerRepository.currentRecordingId.value
        Log.d(TAG, "sendUpdate ENTER: status=$status, showId=${showId?.take(20)}, recId=${recordingId?.take(10)}, override=$positionOverrideMs")
        if (showId == null || recordingId == null) return
        val pos = positionOverrideMs ?: mediaControllerRepository.currentPosition.value
        val dur = mediaControllerRepository.duration.value
        val trackIdx = mediaControllerRepository.currentTrackIndex.value

        // Pull track title and show metadata from current MediaMetadata extras
        // so local playback announces include full context (not just PlayOn transfers)
        val currentMeta = mediaControllerRepository.currentTrack.value
        val trackTitle = currentMeta?.title?.toString()
        val date = lastShowDate ?: currentMeta?.extras?.getString("showDate")
        val venue = lastVenue ?: currentMeta?.extras?.getString("venue")
        val location = lastLocation ?: currentMeta?.extras?.getString("location")

        // Build tracks list from current queue if not provided
        val effectiveTracks = tracks ?: run {
            val mediaItems = mediaControllerRepository.getCurrentMediaItems()
            if (mediaItems.isNotEmpty()) {
                mediaItems.map { item ->
                    SessionTrack(
                        title = item.mediaMetadata.title?.toString() ?: "",
                        duration = 0.0,
                    )
                }
            } else null
        }

        Log.d(TAG, "sendUpdate SEND: status=$status, track=$trackIdx, pos=${pos}ms, dur=${dur}ms, override=${positionOverrideMs != null}")
        connectService.sendSessionUpdate(OutgoingPlaybackState(
            showId = showId,
            recordingId = recordingId,
            trackIndex = trackIdx,
            positionMs = pos,
            durationMs = dur,
            trackTitle = trackTitle,
            status = status,
            date = date,
            venue = venue,
            location = location,
            tracks = effectiveTracks,
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
                sendUpdate(reportedStatus, tracks = sessionTracks.ifEmpty { null }, positionOverrideMs = adjustedPositionMs)
            }
            is ConnectPlaybackEvent.Stop -> {
                Log.d(TAG, "Session taken, pausing")
                mediaControllerRepository.pause()
                sendUpdate("stopped")
            }
            is ConnectPlaybackEvent.SyncState -> {
                // Initial sync: always load the server's show/track so the UI
                // reflects canonical state. Only start audio when this device
                // is the active player.
                val state = event.state
                val showId = state.showId
                if (showId.isNullOrEmpty()) {
                    Log.d(TAG, "Initial sync: no show in server state")
                    return
                }
                val isThisDevice = state.activeDeviceId == connectService.deviceId
                val shouldPlay = isThisDevice && state.isPlaying
                Log.d(TAG, "Initial sync: show=$showId, track=${state.trackIndex}, playing=${state.isPlaying}, activeDevice=${state.activeDeviceName}, isThisDevice=$isThisDevice")
                lastShowDate = state.date
                lastVenue = state.venue
                lastLocation = state.location
                suppressAnnounce = true
                mediaControllerRepository.playTrack(
                    trackIndex = state.trackIndex,
                    recordingId = state.recordingId ?: return,
                    format = "VBR MP3",
                    showId = showId,
                    showDate = state.date ?: "",
                    venue = state.venue,
                    location = state.location,
                    position = state.positionMs,
                )
                if (!shouldPlay) {
                    mediaControllerRepository.pause()
                    Log.d(TAG, "Initial sync: loaded track=${state.trackIndex}, pos=${state.positionMs}ms (paused)")
                } else {
                    Log.d(TAG, "Initial sync: resumed as active device")
                }
                suppressDiffReaction = true
            }
        }
    }

    companion object {
        private const val TAG = "ConnectPlayback"
    }
}
