package com.grateful.deadly.feature.player.screens.main.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.library.LibraryService
import com.grateful.deadly.core.api.player.PanelContentService
import com.grateful.deadly.core.api.player.PlayerService
import com.grateful.deadly.core.model.CurrentTrackInfo
import com.grateful.deadly.core.model.LineupMember
import com.grateful.deadly.core.model.PlaybackStatus
import com.grateful.deadly.core.model.QueueInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerService: PlayerService,
    private val panelContentService: PanelContentService,
    private val libraryService: LibraryService
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    // Reactive UI state from PlayerService flows - using unified CurrentTrackInfo, PlaybackStatus, and QueueInfo
    val uiState: StateFlow<PlayerUiState> = combine(
        playerService.currentTrackInfo,
        playerService.playbackStatus,
        playerService.queueInfo
    ) { trackInfo, playbackStatus, queueInfo ->
        // Early return for null case - no track playing
        if (trackInfo == null) return@combine PlayerUiState(
            trackDisplayInfo = TrackDisplayInfo(
                title = "No Track Playing",
                artist = "",
                album = "",
                showDate = "",
                venue = "",
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
            isLoading = false,
            hasNext = queueInfo.hasNext,
            hasPrevious = queueInfo.hasPrevious,
            error = null
        )

        // trackInfo is guaranteed non-null from here on
        PlayerUiState(
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
                recordingId = trackInfo.recordingId
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
            error = null // TODO: Add error state from service
        )
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
                libraryService.downloadShow(showId)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading show $showId", e)
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
    val error: String? = null
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
    val recordingId: String?
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
