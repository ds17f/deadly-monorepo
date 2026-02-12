package com.deadly.v2.feature.miniplayer.screens.main.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadly.v2.core.api.miniplayer.MiniPlayerService
import com.deadly.v2.core.model.MiniPlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * V2 MiniPlayer ViewModel
 * 
 * Service integration layer following V2 architecture patterns.
 * Coordinates MiniPlayerService state with UI state management.
 * 
 * Follows exact patterns from V2 playlist/search ViewModels.
 */
@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val miniPlayerService: MiniPlayerService
) : ViewModel() {
    
    companion object {
        private const val TAG = "MiniPlayerViewModel"
    }
    
    private val _uiState = MutableStateFlow(MiniPlayerUiState())
    val uiState: StateFlow<MiniPlayerUiState> = _uiState.asStateFlow()
    
    init {
        Log.d(TAG, "MiniPlayerViewModel initialized")
        initializeService()
        observeServiceState()
    }
    
    /**
     * Initialize service resources
     */
    private fun initializeService() {
        // No initialization needed - PlaybackStateService handles its own lifecycle
        Log.d(TAG, "MiniPlayer service ready - no initialization required")
    }
    
    /**
     * Observe service state and transform into UI state
     * Combines multiple service flows into single UI state flow
     */
    private fun observeServiceState() {
        viewModelScope.launch {
            combine(
                miniPlayerService.currentTrackInfo,
                miniPlayerService.playbackStatus
            ) { trackInfo, playbackStatus ->
                
                _uiState.value = MiniPlayerUiState(
                    isPlaying = trackInfo?.playbackState?.isPlaying ?: false,
                    currentTrack = trackInfo,
                    progress = playbackStatus.progress,
                    showId = trackInfo?.showId, // Extract from trackInfo
                    recordingId = trackInfo?.recordingId, // Extract from trackInfo
                    shouldShow = trackInfo != null, // Show MiniPlayer when track is loaded
                    isLoading = trackInfo?.playbackState?.isLoading ?: false,
                    error = null
                )
                
            }.collect { /* State is updated above */ }
        }
    }
    
    /**
     * Handle user play/pause button clicks
     */
    fun togglePlayPause() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "User requested play/pause toggle")
                miniPlayerService.togglePlayPause()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle play/pause", e)
                _uiState.value = _uiState.value.copy(error = "Playback error")
            }
        }
    }
    
    /**
     * Handle user tap to expand MiniPlayer
     * Callback will be handled by Screen component for navigation
     */
    fun onTapToExpand() {
        val currentShowId = _uiState.value.showId
        Log.d(TAG, "User tapped to expand - showId: $currentShowId")
        // Navigation handled by Screen component callback
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Cleanup resources on ViewModel destruction
     */
    override fun onCleared() {
        super.onCleared()
        // No cleanup needed - PlaybackStateService handles its own lifecycle
        Log.d(TAG, "MiniPlayer ViewModel cleared - no cleanup required")
    }
}