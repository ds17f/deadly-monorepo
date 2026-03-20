package com.grateful.deadly.core.connect

import com.grateful.deadly.core.api.connect.ConnectPlaybackState
import com.grateful.deadly.core.api.connect.ConnectService
import com.grateful.deadly.core.api.connect.PlaybackCommand
import com.grateful.deadly.core.api.player.PlayerService
import com.grateful.deadly.core.api.playlist.PlaylistService
import com.grateful.deadly.core.model.PlaybackState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges local playback ↔ Connect messages.
 *
 * - Observes [PlayerService] state and sends session_update / position_update.
 * - Receives remote commands and translates them to [PlayerService] calls.
 * - Receives session_play_on and loads the show via [PlaylistService].
 */
@Singleton
class ConnectPlaybackBridge @Inject constructor(
    private val connectService: ConnectService,
    private val playerService: PlayerService,
    private val playlistService: PlaylistService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionTimerJob: Job? = null

    fun start(impl: ConnectServiceImpl) {
        observeLocalPlayback()
        registerRemoteHandlers(impl)
        impl.onRemoteTakeover {
            scope.launch {
                if (playerService.isPlaying.value) {
                    playerService.togglePlayPause()
                }
                positionTimerJob?.cancel()
            }
        }
        impl.onUserStateSync { state ->
            scope.launch {
                val activeDeviceId = connectService.userState.value?.activeDeviceId
                val isActive = activeDeviceId == connectService.deviceId

                playlistService.loadShow(state.showId, state.recordingId)
                playlistService.playTrack(state.trackIndex)
                if (state.positionMs > 0) {
                    delay(500)
                    playerService.seekToPosition(state.positionMs)
                }
                // If not the active device, pause (just syncing)
                if (!isActive) {
                    delay(300)
                    if (playerService.isPlaying.value) {
                        playerService.togglePlayPause()
                    }
                }
            }
        }
    }

    // ── Local → Remote ──────────────────────────────────────────────────

    private fun observeLocalPlayback() {
        // Send session_update when track or playing state changes
        scope.launch {
            combine(
                playerService.currentTrackInfo.filterNotNull(),
                playerService.isPlaying,
            ) { track, playing -> Pair(track, playing) }
                .distinctUntilChanged()
                .collectLatest { (track, _) ->
                    val state = buildPlaybackState(track) ?: return@collectLatest
                    val activeDeviceId = connectService.userState.value?.activeDeviceId
                    val isActive = activeDeviceId == connectService.deviceId
                    val isParked = activeDeviceId == null
                    if (isActive || isParked) {
                        connectService.announcePlayback(state)
                        restartPositionTimer(track.playbackState)
                    } else {
                        positionTimerJob?.cancel()
                    }
                }
        }
    }

    private fun restartPositionTimer(playbackState: PlaybackState) {
        positionTimerJob?.cancel()
        if (playbackState != PlaybackState.PLAYING) return
        val activeDeviceId = connectService.userState.value?.activeDeviceId
        val isActive = activeDeviceId == connectService.deviceId
        val isParked = activeDeviceId == null
        if (!isActive && !isParked) return
        positionTimerJob = scope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL)
                val track = playerService.currentTrackInfo.value ?: break
                val state = buildPlaybackState(track) ?: break
                connectService.sendPositionUpdate(state)
            }
        }
    }

    private fun buildPlaybackState(
        track: com.grateful.deadly.core.model.CurrentTrackInfo,
    ): ConnectPlaybackState? {
        if (track.showId.isBlank()) return null
        return ConnectPlaybackState(
            showId = track.showId,
            recordingId = track.recordingId,
            trackIndex = track.trackNumber?.minus(1) ?: 0,
            positionMs = track.position,
            durationMs = track.duration,
            trackTitle = track.songTitle,
            status = mapPlaybackStatus(track.playbackState),
            date = track.showDate,
            venue = track.venue,
            location = track.location,
        )
    }

    private fun mapPlaybackStatus(state: PlaybackState): String = when (state) {
        PlaybackState.PLAYING -> "playing"
        PlaybackState.READY, PlaybackState.BUFFERING, PlaybackState.LOADING -> "paused"
        PlaybackState.IDLE, PlaybackState.ENDED -> "stopped"
    }

    // ── Remote → Local ──────────────────────────────────────────────────

    private fun registerRemoteHandlers(impl: ConnectServiceImpl) {
        impl.onCommandReceived { _, command -> handleCommand(command) }
        impl.onPlayOnReceived { state -> handlePlayOn(state) }
    }

    private fun handleCommand(command: PlaybackCommand) {
        scope.launch {
            when (command.action) {
                "play", "pause" -> playerService.togglePlayPause()
                "next" -> playerService.seekToNext()
                "prev" -> playerService.seekToPrevious()
                "seek" -> command.seekMs?.let { playerService.seekToPosition(it) }
                "stop" -> { /* no explicit stop on Android player */ }
            }
        }
    }

    private fun handlePlayOn(state: ConnectPlaybackState) {
        scope.launch {
            // Load the show + recording, then play from the given track/position
            playlistService.loadShow(state.showId, state.recordingId)
            playlistService.playTrack(state.trackIndex)
            if (state.positionMs > 0) {
                // Small delay to let the player initialize before seeking
                delay(500)
                playerService.seekToPosition(state.positionMs)
            }
        }
    }

    companion object {
        private const val POSITION_UPDATE_INTERVAL = 15_000L
    }
}
