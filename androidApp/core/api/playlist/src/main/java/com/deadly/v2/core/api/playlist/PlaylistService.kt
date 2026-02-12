package com.deadly.v2.core.api.playlist

import com.deadly.v2.core.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * PlaylistService - Clean service interface for playlist functionality
 * 
 * Following V2 architecture patterns and clean architecture principles,
 * this interface returns ViewModel types that represent UI concerns.
 * Real implementation will map domain models to ViewModels, stub provides dummy data.
 */
interface PlaylistService {
    
    /**
     * Load show data for the playlist
     * 
     * @param showId The show ID to load
     * @param recordingId Optional specific recording ID from navigation (e.g., Player→Playlist)
     */
    suspend fun loadShow(showId: String?, recordingId: String? = null)
    
    /**
     * Get current show information as ViewModel
     */
    suspend fun getCurrentShowInfo(): PlaylistShowViewModel?
    
    /**
     * Get track list for current show as ViewModels
     */
    suspend fun getTrackList(): List<PlaylistTrackViewModel>
    
    /**
     * Cancel any current track loading operation
     */
    fun cancelTrackLoading()
    
    
    /**
     * Play a specific track by index
     */
    suspend fun playTrack(trackIndex: Int)
    
    /**
     * Navigate to the next show chronologically
     */
    suspend fun navigateToNextShow()
    
    /**
     * Navigate to the previous show chronologically  
     */
    suspend fun navigateToPreviousShow()
    
    /**
     * Add current show to library
     */
    suspend fun addToLibrary()
    
    /**
     * Download current show
     */
    suspend fun downloadShow()
    
    /**
     * Share current show
     */
    suspend fun shareShow()
    
    /**
     * Load setlist for current show
     */
    suspend fun loadSetlist()
    
    /**
     * Get current show's setlist as UI model
     * Returns null if no setlist data is available
     */
    suspend fun getCurrentSetlist(): com.deadly.v2.core.model.SetlistViewModel?
    
    /**
     * Pause playback
     */
    suspend fun pause()
    
    /**
     * Resume playback
     */
    suspend fun resume()
    
    /**
     * Get current reviews for the loaded show/recording
     */
    suspend fun getCurrentReviews(): List<PlaylistReview>
    
    /**
     * Get rating distribution for the current show/recording
     */
    suspend fun getRatingDistribution(): Map<Int, Int>
    
    /**
     * Get recording options for the current show
     */
    suspend fun getRecordingOptions(): RecordingOptionsResult
    
    /**
     * Select a different recording for the current show
     */
    suspend fun selectRecording(recordingId: String)
    
    /**
     * Set a recording as the default for this show
     */
    suspend fun setRecordingAsDefault(recordingId: String)
    
    /**
     * Reset to the recommended recording for this show
     */
    suspend fun resetToRecommended()
    
    /**
     * Get the currently selected audio format for playback coordination
     * Returns the format that was selected during track list building
     */
    fun getCurrentSelectedFormat(): String?
    
    /**
     * Get collections containing the current show
     * @param showId The show ID to find collections for
     * @return List of collections containing this show
     */
    suspend fun getShowCollections(showId: String): List<DeadCollection>
    
    // === MediaController State Observation ===
    
    /**
     * Whether audio is currently playing - reactive stream from MediaController
     */
    val isPlaying: StateFlow<Boolean>
    
    /**
     * Unified playback position state with computed progress
     * Contains currentPosition, duration, and computed progress as cohesive unit
     */
    val playbackStatus: StateFlow<PlaybackStatus>
    
    /**
     * Current track information from MediaController for playlist highlighting
     * Returns null if no track is currently loaded
     */
    val currentTrackInfo: StateFlow<CurrentTrackInfo?>
    
    /**
     * Queue information for navigation decisions
     * Contains currentIndex, totalTracks, and computed navigation properties
     */
    val queueInfo: StateFlow<QueueInfo>
}