package com.deadly.v2.feature.player.screens.main.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadly.v2.core.api.player.PlayerService
import com.deadly.v2.core.model.CurrentTrackInfo
import com.deadly.v2.core.model.PlaybackStatus
import com.deadly.v2.core.model.QueueInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * V2 Player ViewModel - Real MediaController Integration
 * 
 * Uses PlayerService to provide reactive state from MediaControllerRepository.
 * Handles all player UI interactions and delegates to centralized media control.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerService: PlayerService
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
                artwork = null
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
                artwork = null // TODO: Add artwork support
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
                artwork = null
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
        Log.d(TAG, "🕒🎵 [V2-UI] PlayerViewModel play/pause clicked at ${System.currentTimeMillis()}")
        viewModelScope.launch {
            try {
                playerService.togglePlayPause()
            } catch (e: Exception) {
                Log.e(TAG, "🕒🎵 [V2-ERROR] PlayerViewModel play/pause failed at ${System.currentTimeMillis()}", e)
            }
        }
    }
    
    /**
     * Seek to next track
     */
    fun onNextClicked() {
        Log.d(TAG, "🕒🎵 [V2-UI] PlayerViewModel next clicked at ${System.currentTimeMillis()}")
        viewModelScope.launch {
            try {
                playerService.seekToNext()
            } catch (e: Exception) {
                Log.e(TAG, "🕒🎵 [V2-ERROR] PlayerViewModel next failed at ${System.currentTimeMillis()}", e)
            }
        }
    }
    
    /**
     * Seek to previous track  
     */
    fun onPreviousClicked() {
        Log.d(TAG, "🕒🎵 [V2-UI] PlayerViewModel previous clicked at ${System.currentTimeMillis()}")
        viewModelScope.launch {
            try {
                playerService.seekToPrevious()
            } catch (e: Exception) {
                Log.e(TAG, "🕒🎵 [V2-ERROR] PlayerViewModel previous failed at ${System.currentTimeMillis()}", e)
            }
        }
    }
    
    /**
     * Seek to position
     */
    fun onSeek(position: Float) {
        Log.d(TAG, "🕒🎵 [V2-UI] PlayerViewModel seek to $position at ${System.currentTimeMillis()}")
        viewModelScope.launch {
            try {
                // Get current duration and convert percentage to milliseconds
                val durationMs = playerService.playbackStatus.value.duration
                val positionMs = (durationMs * position).toLong()
                playerService.seekToPosition(positionMs)
            } catch (e: Exception) {
                Log.e(TAG, "🕒🎵 [V2-ERROR] PlayerViewModel seek failed at ${System.currentTimeMillis()}", e)
            }
        }
    }
    
    /**
     * Get debug metadata for inspection panel
     */
    suspend fun getDebugMetadata(): Map<String, String?> {
        return try {
            playerService.getDebugMetadata()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting debug metadata", e)
            mapOf("error" to "Failed to get debug metadata: ${e.message}")
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
    val artwork: String? = null
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