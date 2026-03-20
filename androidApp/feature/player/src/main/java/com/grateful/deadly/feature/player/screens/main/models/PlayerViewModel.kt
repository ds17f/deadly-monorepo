package com.grateful.deadly.feature.player.screens.main.models

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.favorites.FavoritesService
import com.grateful.deadly.core.api.favorites.ReviewService
import com.grateful.deadly.core.api.connect.ConnectService
import com.grateful.deadly.core.api.player.PanelContentService
import com.grateful.deadly.core.api.player.PlayerService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.media.equalizer.EqualizerRepository
import com.grateful.deadly.core.media.equalizer.EqualizerState
import com.grateful.deadly.core.api.connect.PlaybackCommand
import com.grateful.deadly.core.model.CurrentTrackInfo
import com.grateful.deadly.core.model.LineupMember
import com.grateful.deadly.core.model.PlaybackStatus
import com.grateful.deadly.core.model.QueueInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerService: PlayerService,
    private val panelContentService: PanelContentService,
    private val favoritesService: FavoritesService,
    private val reviewService: ReviewService,
    private val equalizerRepository: EqualizerRepository,
    val connectService: ConnectService,
    val appPreferences: AppPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    // Whether a remote device is currently active (not this device)
    val isRemoteActive: StateFlow<Boolean> = connectService.userState.map { state ->
        state != null && state.activeDeviceId != null && state.activeDeviceId != connectService.deviceId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Ticker that emits current time every 500ms when remote is active (for interpolated progress)
    private val progressTicker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(500)
        }
    }

    // Reactive UI state — switches between local PlayerService state and remote userState
    val uiState: StateFlow<PlayerUiState> = combine(
        combine(
            playerService.currentTrackInfo,
            playerService.playbackStatus,
            playerService.queueInfo,
        ) { trackInfo, playbackStatus, queueInfo ->
            Triple(trackInfo, playbackStatus, queueInfo)
        },
        combine(
            connectService.userState,
            progressTicker,
        ) { remoteState, now -> Pair(remoteState, now) },
    ) { (trackInfo, playbackStatus, queueInfo), (remoteState, now) ->
        val isRemote = remoteState != null &&
            remoteState.activeDeviceId != null &&
            remoteState.activeDeviceId != connectService.deviceId

        val localState = buildLocalUiState(trackInfo, playbackStatus, queueInfo)

        if (isRemote && remoteState != null) {
            val interpolatedMs = if (remoteState.isPlaying) {
                val elapsed = now - remoteState.updatedAt
                (remoteState.positionMs + elapsed).coerceAtMost(remoteState.durationMs)
            } else {
                remoteState.positionMs
            }
            val progress = if (remoteState.durationMs > 0) {
                (interpolatedMs.toFloat() / remoteState.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            localState.copy(
                progressDisplayInfo = ProgressDisplayInfo(
                    currentPosition = formatMs(interpolatedMs),
                    totalDuration = formatMs(remoteState.durationMs),
                    progressPercentage = progress,
                ),
                isPlaying = remoteState.isPlaying,
                remoteDeviceName = remoteState.activeDeviceName,
            )
        } else {
            localState
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState(
            trackDisplayInfo = TrackDisplayInfo(
                title = "Loading...",
                artist = "Grateful Dead",
                album = "Loading...",
                showDate = "Loading...",
                venue = "Loading...",
                duration = "0:00",
                recordingId = null
            ),
            navigationInfo = NavigationInfo(
                showId = null,
                recordingId = null
            ),
            progressDisplayInfo = ProgressDisplayInfo(
                currentPosition = "0:00",
                totalDuration = "0:00",
                progressPercentage = 0.0f
            ),
            isPlaying = false,
            isLoading = true,
            hasNext = false,
            hasPrevious = false,
            error = null
        )
    )

    // Panel content state
    private val _panelState = MutableStateFlow(PanelUiState())
    val panelState: StateFlow<PanelUiState> = _panelState.asStateFlow()

    // Track what we've already loaded to avoid redundant fetches
    private var lastLoadedShowId: String? = null
    private var lastLoadedSongTitle: String? = null

    init {
        // Observe track changes and load panel content
        viewModelScope.launch {
            playerService.currentTrackInfo
                .filterNotNull()
                .collect { trackInfo ->
                    val showChanged = trackInfo.showId != lastLoadedShowId
                    val songChanged = trackInfo.songTitle != lastLoadedSongTitle

                    if (showChanged || songChanged) {
                        lastLoadedShowId = trackInfo.showId
                        lastLoadedSongTitle = trackInfo.songTitle
                        loadPanelContent(trackInfo, showChanged, songChanged)
                        observeFavoriteState(trackInfo)
                    }
                }
        }
    }

    private fun loadPanelContent(
        trackInfo: CurrentTrackInfo,
        showChanged: Boolean,
        songChanged: Boolean
    ) {
        viewModelScope.launch {
            _panelState.value = _panelState.value.copy(isLoading = true)

            val creditsDeferred = if (showChanged) {
                async { panelContentService.getCredits(trackInfo.showId) }
            } else null

            val venueDeferred = if (showChanged) {
                async { panelContentService.getVenueInfo(trackInfo.showId) }
            } else null

            val lyricsDeferred = if (songChanged) {
                async { panelContentService.getLyrics(trackInfo.songTitle) }
            } else null

            val credits = creditsDeferred?.await() ?: _panelState.value.credits
            val venueInfo = venueDeferred?.await() ?: _panelState.value.venueInfo
            val lyrics = lyricsDeferred?.await() ?: _panelState.value.lyrics

            _panelState.value = PanelUiState(
                credits = credits,
                venueInfo = venueInfo,
                lyrics = lyrics,
                isLoading = false
            )
        }
    }

    private var favoriteStateJob: kotlinx.coroutines.Job? = null

    private fun observeFavoriteState(trackInfo: CurrentTrackInfo) {
        val showId = trackInfo.showId ?: return
        favoriteStateJob?.cancel()
        favoriteStateJob = viewModelScope.launch {
            try {
                reviewService.isSongFavoriteFlow(showId, trackInfo.songTitle, trackInfo.recordingId)
                    .collect { isFav ->
                        _isCurrentTrackFavorite.value = isFav
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing favorite state", e)
                _isCurrentTrackFavorite.value = false
            }
        }
    }

    private fun buildLocalUiState(
        trackInfo: CurrentTrackInfo?,
        playbackStatus: PlaybackStatus,
        queueInfo: QueueInfo,
    ): PlayerUiState {
        if (trackInfo == null) return PlayerUiState(
            trackDisplayInfo = TrackDisplayInfo(
                title = "No Track Playing",
                artist = "",
                album = "",
                showDate = "",
                venue = "",
                duration = "0:00",
                recordingId = null
            ),
            navigationInfo = NavigationInfo(showId = null, recordingId = null),
            progressDisplayInfo = ProgressDisplayInfo(
                currentPosition = "0:00",
                totalDuration = "0:00",
                progressPercentage = 0.0f
            ),
            isPlaying = false,
            isLoading = false,
            hasNext = queueInfo.hasNext,
            hasPrevious = queueInfo.hasPrevious,
            error = null
        )

        return PlayerUiState(
            trackDisplayInfo = TrackDisplayInfo(
                title = trackInfo.songTitle,
                artist = trackInfo.artist,
                album = trackInfo.album,
                showDate = trackInfo.showDate,
                venue = trackInfo.venue ?: "",
                duration = playerService.formatDuration(playbackStatus.duration),
                recordingId = trackInfo.recordingId,
                coverImageUrl = trackInfo.coverImageUrl
            ),
            navigationInfo = NavigationInfo(
                showId = trackInfo.showId,
                recordingId = trackInfo.recordingId,
                trackNumber = trackInfo.trackNumber
            ),
            progressDisplayInfo = ProgressDisplayInfo(
                currentPosition = playerService.formatPosition(playbackStatus.currentPosition),
                totalDuration = playerService.formatDuration(playbackStatus.duration),
                progressPercentage = playbackStatus.progress
            ),
            isPlaying = trackInfo.playbackState.isPlaying,
            isLoading = trackInfo.playbackState.isLoading,
            hasNext = queueInfo.hasNext,
            hasPrevious = queueInfo.hasPrevious,
            error = null
        )
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }

    private fun sendRemoteCommand(action: String, seekMs: Long? = null) {
        val targetId = connectService.userState.value?.activeDeviceId ?: return
        connectService.sendCommand(targetId, PlaybackCommand(action = action, seekMs = seekMs))
    }

    /**
     * Load recording - No-op since state comes from MediaController
     */
    fun loadRecording(recordingId: String) {
        Log.d(TAG, "Load recording: $recordingId - state comes from MediaController")
        // MediaController handles track loading, we just observe state
    }

    /**
     * Toggle play/pause
     */
    fun onPlayPauseClicked() {
        Log.d(TAG, "🕒🎵 [UI] PlayerViewModel play/pause clicked at ${System.currentTimeMillis()}")
        val remote = connectService.userState.value
        if (remote != null && remote.activeDeviceId != null && remote.activeDeviceId != connectService.deviceId) {
            sendRemoteCommand(if (remote.isPlaying) "pause" else "play")
            return
        }
        viewModelScope.launch {
            try {
                playerService.togglePlayPause()
            } catch (e: Exception) {
                Log.e(TAG, "🕒🎵 [ERROR] PlayerViewModel play/pause failed at ${System.currentTimeMillis()}", e)
            }
        }
    }

    /**
     * Seek to next track
     */
    fun onNextClicked() {
        Log.d(TAG, "🕒🎵 [UI] PlayerViewModel next clicked at ${System.currentTimeMillis()}")
        if (isRemoteActive.value) {
            sendRemoteCommand("next")
            return
        }
        viewModelScope.launch {
            try {
                playerService.seekToNext()
            } catch (e: Exception) {
                Log.e(TAG, "🕒🎵 [ERROR] PlayerViewModel next failed at ${System.currentTimeMillis()}", e)
            }
        }
    }

    /**
     * Seek to previous track
     */
    fun onPreviousClicked() {
        Log.d(TAG, "🕒🎵 [UI] PlayerViewModel previous clicked at ${System.currentTimeMillis()}")
        if (isRemoteActive.value) {
            sendRemoteCommand("prev")
            return
        }
        viewModelScope.launch {
            try {
                playerService.seekToPrevious()
            } catch (e: Exception) {
                Log.e(TAG, "🕒🎵 [ERROR] PlayerViewModel previous failed at ${System.currentTimeMillis()}", e)
            }
        }
    }

    /**
     * Seek to position
     */
    fun onSeek(position: Float) {
        Log.d(TAG, "🕒🎵 [UI] PlayerViewModel seek to $position at ${System.currentTimeMillis()}")
        val remote = connectService.userState.value
        if (remote != null && remote.activeDeviceId != null && remote.activeDeviceId != connectService.deviceId) {
            val seekMs = (remote.durationMs * position).toLong()
            sendRemoteCommand("seek", seekMs)
            return
        }
        viewModelScope.launch {
            try {
                // Get current duration and convert percentage to milliseconds
                val durationMs = playerService.playbackStatus.value.duration
                val positionMs = (durationMs * position).toLong()
                playerService.seekToPosition(positionMs)
            } catch (e: Exception) {
                Log.e(TAG, "🕒🎵 [ERROR] PlayerViewModel seek failed at ${System.currentTimeMillis()}", e)
            }
        }
    }

    /**
     * Download the currently playing show
     */
    fun downloadCurrentShow() {
        val showId = uiState.value.navigationInfo.showId ?: return
        viewModelScope.launch {
            try {
                favoritesService.downloadShow(showId)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading show $showId", e)
            }
        }
    }

    // Favorite song state

    private val _isCurrentTrackFavorite = MutableStateFlow(false)
    val isCurrentTrackFavorite: StateFlow<Boolean> = _isCurrentTrackFavorite.asStateFlow()

    /**
     * Toggle favorite state of the currently playing track.
     */
    fun toggleCurrentTrackFavorite() {
        val trackInfo = playerService.currentTrackInfo.value ?: return
        val showId = trackInfo.showId ?: return
        viewModelScope.launch {
            try {
                reviewService.toggleFavoriteSong(
                    showId = showId,
                    trackTitle = trackInfo.songTitle,
                    trackNumber = trackInfo.trackNumber,
                    recordingId = trackInfo.recordingId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite", e)
            }
        }
    }

    /**
     * Share current track
     */
    fun onShareClicked() {
        Log.d(TAG, "Share clicked")
        viewModelScope.launch {
            try {
                playerService.shareCurrentTrack()
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing track", e)
            }
        }
    }

    /**
     * Share current track as a text message (just the URL)
     */
    fun shareAsMessage() {
        val trackInfo = playerService.currentTrackInfo.value ?: return
        val showId = trackInfo.showId
        val recordingId = trackInfo.recordingId

        val url = buildString {
            append("${appPreferences.shareBaseUrl}/shows/$showId/recording/$recordingId")
            if (trackInfo.trackNumber != null) append("/track/${trackInfo.trackNumber}")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(Intent.createChooser(intent, "Share via").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    // ── Equalizer ────────────────────────────────────────────────────────

    val equalizerState: StateFlow<EqualizerState> = equalizerRepository.state

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerRepository.setEnabled(enabled)
    }

    fun setEqualizerBandLevel(index: Int, gainDb: Float) {
        equalizerRepository.setBandLevel(index, gainDb)
    }

    fun selectEqualizerPreset(preset: String) {
        equalizerRepository.selectPreset(preset)
    }

    fun resetEqualizer() {
        equalizerRepository.resetToFlat()
    }
}

/**
 * UI State for Player screen
 */
data class PlayerUiState(
    val trackDisplayInfo: TrackDisplayInfo,
    val navigationInfo: NavigationInfo,
    val progressDisplayInfo: ProgressDisplayInfo,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val error: String? = null,
    val remoteDeviceName: String? = null
)

/**
 * Display model for track information
 */
data class TrackDisplayInfo(
    val title: String,
    val artist: String,
    val album: String,
    val showDate: String,
    val venue: String,
    val duration: String,
    val recordingId: String? = null,
    val coverImageUrl: String? = null
)

/**
 * Navigation information for playlist routing
 */
data class NavigationInfo(
    val showId: String?,
    val recordingId: String?,
    val trackNumber: Int? = null
)

/**
 * Display model for progress information
 */
data class ProgressDisplayInfo(
    val currentPosition: String,
    val totalDuration: String,
    val progressPercentage: Float // 0.0 to 1.0
)

/**
 * UI state for info panels below the player controls
 */
data class PanelUiState(
    val credits: List<LineupMember>? = null,
    val venueInfo: String? = null,
    val lyrics: String? = null,
    val isLoading: Boolean = false
)
