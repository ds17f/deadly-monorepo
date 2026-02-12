package com.deadly.v2.feature.playlist.screens.main.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadly.v2.core.api.playlist.PlaylistService
import com.deadly.v2.core.api.library.LibraryService
import com.deadly.v2.core.api.recent.RecentShowsService
import com.deadly.v2.core.model.*
import com.deadly.v2.core.media.repository.MediaControllerRepository
import com.deadly.v2.core.media.exception.FormatNotAvailableException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.SharingStarted
import javax.inject.Inject
import javax.inject.Named

/**
 * PlaylistViewModel - Clean ViewModel for Playlist UI
 * 
 * Coordinates between UI components and the PlaylistService.
 * Maintains UI state for all playlist components.
 */
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistService: PlaylistService,
    private val mediaControllerRepository: MediaControllerRepository,
    private val libraryService: LibraryService,
    private val recentShowsService: RecentShowsService
) : ViewModel() {
    
    companion object {
        private const val TAG = "PlaylistViewModel"
    }
    
    
    // Internal state for show and tracks data
    private val _baseUiState = MutableStateFlow(PlaylistUiState())
    private val _rawTrackData = MutableStateFlow<List<PlaylistTrackViewModel>>(emptyList())
    
    init {
        // Reactive collections loading - watch for showId changes and update collections
        viewModelScope.launch {
            _baseUiState
                .map { it.showData?.showId }
                .distinctUntilChanged()
                .collect { showId ->
                    if (showId != null) {
                        try {
                            val collections = playlistService.getShowCollections(showId)
                            _baseUiState.value = _baseUiState.value.copy(
                                showCollections = collections,
                                collectionsLoading = false
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading collections for show $showId", e)
                            _baseUiState.value = _baseUiState.value.copy(
                                showCollections = emptyList(),
                                collectionsLoading = false
                            )
                        }
                    } else {
                        _baseUiState.value = _baseUiState.value.copy(
                            showCollections = emptyList(),
                            collectionsLoading = false
                        )
                    }
                }
        }
    }
    
    // Reactive UI state that combines base state with MediaController state and library status
    val uiState: StateFlow<PlaylistUiState> = combine(
        _baseUiState,
        _rawTrackData,
        playlistService.isPlaying,
        playlistService.currentTrackInfo,
        _baseUiState
            .map { baseState -> baseState.showData?.showId }
            .distinctUntilChanged()
            .flatMapLatest { showId ->
                if (showId != null) {
                    libraryService.isShowInLibrary(showId)
                } else {
                    flowOf(false)
                }
            }
    ) { baseState, rawTracks, isPlaying, currentTrackInfo, isInLibrary ->
        
        // Update track data with current playing state
        val updatedTracks = rawTracks.map { track ->
            val isCurrentTrack = isTrackCurrentlyPlaying(track, currentTrackInfo)
            track.copy(
                isCurrentTrack = isCurrentTrack,
                isPlaying = isCurrentTrack && isPlaying
            )
        }
        
        // Determine if we're viewing the currently playing show/recording
        val playlistShowId = baseState.showData?.showId
        val playlistRecordingId = baseState.showData?.currentRecordingId
        val mediaShowId = currentTrackInfo?.showId
        val mediaRecordingId = currentTrackInfo?.recordingId
        
        val isCurrentShowAndRecording = playlistShowId != null && 
                                       playlistRecordingId != null &&
                                       (playlistShowId == mediaShowId || playlistShowId == mediaShowId?.replace("-", "")) &&
                                       playlistRecordingId == mediaRecordingId
        
        Log.v(TAG, "Play button logic: playlistShow='$playlistShowId' vs mediaShow='$mediaShowId'")
        Log.v(TAG, "Play button logic: playlistRecording='$playlistRecordingId' vs mediaRecording='$mediaRecordingId'")  
        Log.v(TAG, "Play button logic: isCurrentShowAndRecording=$isCurrentShowAndRecording, isPlaying=$isPlaying")
        Log.v(TAG, "Library status: showId='$playlistShowId', isInLibrary=$isInLibrary")
        
        // Update showData with current library status
        val updatedShowData = baseState.showData?.copy(isInLibrary = isInLibrary)
        
        baseState.copy(
            trackData = updatedTracks,
            isPlaying = isPlaying,
            isCurrentShowAndRecording = isCurrentShowAndRecording,
            mediaLoading = currentTrackInfo?.playbackState?.isLoading ?: false,
            showData = updatedShowData
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlaylistUiState()
    )
    
    // Track current track loading job for cancellation
    private var trackLoadingJob: Job? = null
    
    /**
     * Determine if a playlist track matches the currently playing track from MediaController
     */
    private fun isTrackCurrentlyPlaying(track: PlaylistTrackViewModel, currentTrackInfo: CurrentTrackInfo?): Boolean {
        if (currentTrackInfo == null) {
            Log.v(TAG, "Track matching: No currentTrackInfo available")
            return false
        }
        
        // Match based on recording ID first
        val currentRecordingId = _baseUiState.value.showData?.currentRecordingId
        
        Log.v(TAG, "Track matching: playlistRecordingId='$currentRecordingId' vs mediaRecordingId='${currentTrackInfo.recordingId}'")
        
        if (currentRecordingId != currentTrackInfo.recordingId) {
            Log.v(TAG, "Track matching: Recording IDs don't match - no highlight")
            return false
        }
        
        // Primary: Match by track title (most reliable for MediaController)
        val titleMatches = track.title.equals(currentTrackInfo.songTitle, ignoreCase = true) ||
                          currentTrackInfo.songTitle.contains(track.title, ignoreCase = true) ||
                          track.title.contains(currentTrackInfo.songTitle, ignoreCase = true)
        
        // Secondary: Match by trackUrl construction (recordingId_title pattern)
        val expectedTrackUrl = "${currentRecordingId}_${track.title}"
        val trackUrlMatches = currentTrackInfo.trackUrl == expectedTrackUrl
        
        // Tertiary: Match by filename if MediaController has it
        val filenameMatches = track.filename == currentTrackInfo.filename ||
                             currentTrackInfo.trackUrl.contains(track.filename)
        
        val matches = filenameMatches || trackUrlMatches
        
        Log.v(TAG, "Track matching: track.title='${track.title}' vs currentSong='${currentTrackInfo.songTitle}'")
        Log.v(TAG, "Track matching: expectedTrackUrl='$expectedTrackUrl' vs actualTrackUrl='${currentTrackInfo.trackUrl}'") 
        Log.v(TAG, "Track matching: titleMatches=$titleMatches, trackUrlMatches=$trackUrlMatches, filenameMatches=$filenameMatches, overall=$matches")
        
        return matches
    }
    
    /**
     * Load show data from the service
     * 
     * @param showId The show ID to load
     * @param recordingId Optional specific recording ID from navigation (e.g., Player→Playlist)
     */
    fun loadShow(showId: String?, recordingId: String? = null) {
        Log.d(TAG, "Loading show: $showId, recordingId: $recordingId")
        viewModelScope.launch {
            try {
                _baseUiState.value = _baseUiState.value.copy(isLoading = true, error = null)
                
                // Load show in service (DB data) with optional recordingId
                playlistService.loadShow(showId, recordingId)
                
                // Show DB data immediately
                val showData = playlistService.getCurrentShowInfo()
                _baseUiState.value = _baseUiState.value.copy(
                    isLoading = false,
                    showData = showData,
                    currentTrackIndex = -1
                )
                
                Log.d(TAG, "Show loaded successfully: ${showData?.displayDate} - showing DB data immediately")
                
                // Start track loading in background
                loadTrackListAsync()
                
                // Collections are loaded reactively via combine() flow
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading show", e)
                _baseUiState.value = _baseUiState.value.copy(
                    isLoading = false,
                    error = "Failed to load show: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Play a specific track
     */
    fun playTrack(trackIndex: Int) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🕒🎵 [V2-UI] PlaylistViewModel track selection: User clicked track at index $trackIndex at ${System.currentTimeMillis()}")
                playlistService.playTrack(trackIndex)
                
                _baseUiState.value = _baseUiState.value.copy(
                    currentTrackIndex = trackIndex
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error playing track", e)
            }
        }
    }
    
    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        val currentState = _baseUiState.value
        if (currentState.currentTrackIndex >= 0) {
            // Playback state is now managed by MediaController - no need to update locally
            Log.d(TAG, "Toggle play/pause - MediaController will handle state")
        } else {
            // Start playing first track
            playTrack(0)
        }
    }
    
    /**
     * Navigate to previous show
     */
    fun navigateToPreviousShow() {
        viewModelScope.launch {
            try {
                // TODO: Cancel distant prefetches in future iteration
                
                // Navigate in service (updates show instantly)
                playlistService.navigateToPreviousShow()
                
                // Show DB data immediately - no loading state blocks navigation
                val showData = playlistService.getCurrentShowInfo()
                _baseUiState.value = _baseUiState.value.copy(
                    showData = showData,
                    currentTrackIndex = -1,
                    isTrackListLoading = false // Reset track loading state
                )
                _rawTrackData.value = emptyList()
                
                Log.d(TAG, "Navigated to previous show: ${showData?.displayDate} - showing DB data immediately")
                
                // Start track loading with smart prefetch promotion
                loadTrackListAsync()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to previous show", e)
            }
        }
    }
    
    /**
     * Navigate to next show
     */
    fun navigateToNextShow() {
        viewModelScope.launch {
            try {
                // TODO: Cancel distant prefetches in future iteration
                
                // Navigate in service (updates show instantly)
                playlistService.navigateToNextShow()
                
                // Show DB data immediately - no loading state blocks navigation
                val showData = playlistService.getCurrentShowInfo()
                _baseUiState.value = _baseUiState.value.copy(
                    showData = showData,
                    currentTrackIndex = -1,
                    isTrackListLoading = false // Reset track loading state
                )
                _rawTrackData.value = emptyList()
                
                Log.d(TAG, "Navigated to next show: ${showData?.displayDate} - showing DB data immediately")
                
                // Start track loading with smart prefetch promotion
                loadTrackListAsync()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to next show", e)
            }
        }
    }
    
    /**
     * Add to library
     */
    fun addToLibrary() {
        viewModelScope.launch {
            try {
                val currentShow = _baseUiState.value.showData
                if (currentShow?.showId != null) {
                    libraryService.addToLibrary(currentShow.showId)
                        .onSuccess {
                            Log.d(TAG, "Successfully added show ${currentShow.showId} to library")
                            // UI will update automatically via reactive StateFlow
                        }
                        .onFailure { error ->
                            Log.e(TAG, "Failed to add show ${currentShow.showId} to library", error)
                        }
                } else {
                    Log.w(TAG, "Cannot add to library - no show ID available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to library", e)
            }
        }
    }
    
    /**
     * Download show
     */
    fun downloadShow() {
        viewModelScope.launch {
            try {
                playlistService.downloadShow()
                // In real implementation, this would trigger download state updates
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading show", e)
            }
        }
    }
    
    /**
     * Handle library actions from LibraryButton
     */
    fun handleLibraryAction(action: LibraryAction) {
        when (action) {
            LibraryAction.ADD_TO_LIBRARY -> addToLibrary()
            LibraryAction.REMOVE_FROM_LIBRARY -> removeFromLibrary()
            LibraryAction.REMOVE_WITH_DOWNLOADS -> removeFromLibraryWithDownloads()
        }
    }
    
    /**
     * Remove from library
     */
    private fun removeFromLibrary() {
        viewModelScope.launch {
            try {
                val currentShow = _baseUiState.value.showData
                if (currentShow?.showId != null) {
                    libraryService.removeFromLibrary(currentShow.showId)
                        .onSuccess {
                            Log.d(TAG, "Successfully removed show ${currentShow.showId} from library")
                            // UI will update automatically via reactive StateFlow
                        }
                        .onFailure { error ->
                            Log.e(TAG, "Failed to remove show ${currentShow.showId} from library", error)
                        }
                } else {
                    Log.w(TAG, "Cannot remove from library - no show ID available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing from library", e)
            }
        }
    }
    
    /**
     * Remove from library with downloads
     */
    private fun removeFromLibraryWithDownloads() {
        viewModelScope.launch {
            try {
                val currentShow = _baseUiState.value.showData
                if (currentShow?.showId != null) {
                    // First cancel any downloads for this show
                    libraryService.cancelShowDownloads(currentShow.showId)
                        .onSuccess {
                            // Then remove from library
                            libraryService.removeFromLibrary(currentShow.showId)
                                .onSuccess {
                                    Log.d(TAG, "Successfully removed show ${currentShow.showId} from library with downloads")
                                    // UI will update automatically via reactive StateFlow
                                }
                                .onFailure { error ->
                                    Log.e(TAG, "Failed to remove show ${currentShow.showId} from library after canceling downloads", error)
                                }
                        }
                        .onFailure { error ->
                            Log.e(TAG, "Failed to cancel downloads for show ${currentShow.showId}", error)
                        }
                } else {
                    Log.w(TAG, "Cannot remove from library with downloads - no show ID available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing from library with downloads", e)
            }
        }
    }
    
    /**
     * Share show
     */
    fun shareShow() {
        viewModelScope.launch {
            try {
                playlistService.shareShow()
                Log.d(TAG, "Shared show")
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing show", e)
            }
        }
    }
    
    /**
     * Show reviews (opens review details modal)
     */
    fun showReviews() {
        Log.d(TAG, "Show reviews requested")
        _baseUiState.value = _baseUiState.value.copy(showReviewDetails = true)
        loadReviews()
    }
    
    /**
     * Hide reviews (closes review details modal)
     */
    fun hideReviewDetails() {
        Log.d(TAG, "Hide reviews requested")
        _baseUiState.value = _baseUiState.value.copy(
            showReviewDetails = false,
            reviews = emptyList(),
            ratingDistribution = emptyMap(),
            reviewsError = null
        )
    }
    
    /**
     * Load reviews from service
     */
    private fun loadReviews() {
        viewModelScope.launch {
            try {
                _baseUiState.value = _baseUiState.value.copy(
                    reviewsLoading = true,
                    reviewsError = null
                )
                
                // Get reviews from service
                val reviews = playlistService.getCurrentReviews()
                val ratingDistribution = playlistService.getRatingDistribution()
                
                _baseUiState.value = _baseUiState.value.copy(
                    reviewsLoading = false,
                    reviews = reviews,
                    ratingDistribution = ratingDistribution
                )
                
                Log.d(TAG, "Reviews loaded: ${reviews.size} reviews")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading reviews", e)
                _baseUiState.value = _baseUiState.value.copy(
                    reviewsLoading = false,
                    reviewsError = "Failed to load reviews: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Show setlist
     */
    fun showSetlist() {
        Log.d(TAG, "Show setlist requested")
        showSetlistModal()
    }
    
    /**
     * Show setlist modal with real data
     */
    fun showSetlistModal() {
        Log.d(TAG, "Show setlist modal requested")
        _baseUiState.value = _baseUiState.value.copy(
            showSetlistModal = true,
            setlistLoading = true,
            setlistError = null
        )
        
        // Load real setlist data
        viewModelScope.launch {
            try {
                val setlistData = playlistService.getCurrentSetlist()
                _baseUiState.value = _baseUiState.value.copy(
                    setlistLoading = false,
                    setlistError = if (setlistData == null) "No setlist available for this show" else null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading setlist data", e)
                _baseUiState.value = _baseUiState.value.copy(
                    setlistLoading = false,
                    setlistError = "Failed to load setlist"
                )
            }
        }
    }
    
    /**
     * Get current setlist data (real data, not dummy)
     */
    fun getCurrentSetlistData(): com.deadly.v2.core.model.SetlistViewModel? {
        return try {
            // This is a synchronous call for UI - the data should already be loaded
            // via the async showSetlistModal call
            runBlocking {
                playlistService.getCurrentSetlist()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting setlist data", e)
            null
        }
    }
    
    /**
     * Get dummy 5/8/77 Barton Hall setlist data
     */
    fun getDummySetlistData(): com.deadly.v2.core.model.SetlistViewModel {
        return com.deadly.v2.core.model.SetlistViewModel(
            showDate = "May 8, 1977",
            venue = "Barton Hall, Cornell University",
            location = "Ithaca, NY",
            sets = listOf(
                com.deadly.v2.core.model.SetlistSetViewModel(
                    name = "Set One",
                    songs = listOf(
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Minglewood Blues"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Loser"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "El Paso"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "They Love Each Other"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Jack Straw"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Deal"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Looks Like Rain"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Row Jimmy"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Playing in the Band")
                    )
                ),
                com.deadly.v2.core.model.SetlistSetViewModel(
                    name = "Set Two",
                    songs = listOf(
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Samson and Delilah"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Help on the Way", hasSegue = true, segueSymbol = ">"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Slipknot!", hasSegue = true, segueSymbol = ">"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Franklin's Tower"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Estimated Prophet"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Eyes of the World", hasSegue = true, segueSymbol = ">"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Drums", hasSegue = true, segueSymbol = ">"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Not Fade Away", hasSegue = true, segueSymbol = ">"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Drums", hasSegue = true, segueSymbol = ">"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "The Other One", hasSegue = true, segueSymbol = ">"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Wharf Rat", hasSegue = true, segueSymbol = ">"),
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Around and Around")
                    )
                ),
                com.deadly.v2.core.model.SetlistSetViewModel(
                    name = "Encore",
                    songs = listOf(
                        com.deadly.v2.core.model.SetlistSongViewModel(null, "Johnny B. Goode")
                    )
                )
            )
        )
    }
    
    /**
     * Hide setlist modal
     */
    fun hideSetlistModal() {
        Log.d(TAG, "Hide setlist modal requested")
        _baseUiState.value = _baseUiState.value.copy(
            showSetlistModal = false,
            setlistLoading = false,
            setlistError = null
        )
    }
    
    /**
     * Show menu (for dropdown actions)
     */
    fun showMenu() {
        Log.d(TAG, "Show menu requested")
        _baseUiState.value = _baseUiState.value.copy(showMenu = true)
    }
    
    /**
     * Hide menu
     */
    fun hideMenu() {
        Log.d(TAG, "Hide menu requested")
        _baseUiState.value = _baseUiState.value.copy(showMenu = false)
    }
    
    /**
     * Choose recording (opens recording selection modal)
     */
    fun chooseRecording() {
        Log.d(TAG, "Choose recording requested")
        hideMenu()
        showRecordingSelection()
    }
    
    /**
     * Show recording selection modal
     */
    fun showRecordingSelection() {
        Log.d(TAG, "Show recording selection requested")
        viewModelScope.launch {
            try {
                // Set loading state
                _baseUiState.value = _baseUiState.value.copy(
                    recordingSelection = _baseUiState.value.recordingSelection.copy(
                        isVisible = true,
                        isLoading = true,
                        errorMessage = null
                    )
                )
                
                // Load recording options from service
                val showTitle = _baseUiState.value.showData?.displayDate ?: "Unknown Show"
                val recordingOptions = playlistService.getRecordingOptions()
                
                _baseUiState.value = _baseUiState.value.copy(
                    recordingSelection = _baseUiState.value.recordingSelection.copy(
                        showTitle = showTitle,
                        currentRecording = recordingOptions.currentRecording,
                        alternativeRecordings = recordingOptions.alternativeRecordings,
                        hasRecommended = recordingOptions.hasRecommended,
                        isLoading = false
                    )
                )
                
                Log.d(TAG, "Recording selection loaded: ${recordingOptions.alternativeRecordings.size} alternatives")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recording options", e)
                _baseUiState.value = _baseUiState.value.copy(
                    recordingSelection = _baseUiState.value.recordingSelection.copy(
                        isLoading = false,
                        errorMessage = "Failed to load recordings: ${e.message}"
                    )
                )
            }
        }
    }
    
    /**
     * Hide recording selection modal
     */
    fun hideRecordingSelection() {
        Log.d(TAG, "Hide recording selection requested")
        _baseUiState.value = _baseUiState.value.copy(
            recordingSelection = RecordingSelectionState()
        )
    }
    
    /**
     * Select a recording
     */
    fun selectRecording(recordingId: String) {
        Log.d(TAG, "Recording selected: $recordingId")
        viewModelScope.launch {
            try {
                playlistService.selectRecording(recordingId)
                
                // Update selection state in modal
                val currentSelection = _baseUiState.value.recordingSelection
                val updatedCurrent = currentSelection.currentRecording?.copy(isSelected = false)
                val updatedAlternatives = currentSelection.alternativeRecordings.map { option ->
                    option.copy(isSelected = option.identifier == recordingId)
                }
                
                _baseUiState.value = _baseUiState.value.copy(
                    recordingSelection = currentSelection.copy(
                        currentRecording = updatedCurrent,
                        alternativeRecordings = updatedAlternatives
                    )
                )
                
                // IMPORTANT: Update main show data with new currentRecordingId
                val updatedShowData = _baseUiState.value.showData?.copy(
                    currentRecordingId = recordingId
                )
                
                _baseUiState.value = _baseUiState.value.copy(
                    showData = updatedShowData,
                    isTrackListLoading = false // Reset track loading state 
                )
                _rawTrackData.value = emptyList() // Clear tracks to trigger reload
                
                Log.d(TAG, "Recording selection updated - refreshing tracks for: $recordingId")
                
                // Trigger track list reload with new recording
                loadTrackListAsync()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting recording", e)
            }
        }
    }
    
    /**
     * Set recording as default
     */
    fun setRecordingAsDefault(recordingId: String) {
        Log.d(TAG, "Set recording as default: $recordingId")
        viewModelScope.launch {
            try {
                playlistService.setRecordingAsDefault(recordingId)
                
                // IMPORTANT: Update main show data with new currentRecordingId
                val updatedShowData = _baseUiState.value.showData?.copy(
                    currentRecordingId = recordingId
                )
                
                _baseUiState.value = _baseUiState.value.copy(
                    showData = updatedShowData,
                    isTrackListLoading = false // Reset track loading state 
                )
                _rawTrackData.value = emptyList() // Clear tracks to trigger reload
                
                Log.d(TAG, "Recording set as default - refreshing tracks for: $recordingId")
                
                // Trigger track list reload with new recording
                loadTrackListAsync()
                
                Log.d(TAG, "Recording set as default successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting recording as default", e)
            }
        }
    }
    
    /**
     * Reset to recommended recording
     */
    fun resetToRecommended() {
        Log.d(TAG, "Reset to recommended recording requested")
        viewModelScope.launch {
            try {
                playlistService.resetToRecommended()
                
                // Get the recommended recording ID from the show data
                val recommendedRecordingId = _baseUiState.value.showData?.let { showData ->
                    // We need to get the current show info from service to get the bestRecordingId
                    playlistService.getCurrentShowInfo()?.currentRecordingId
                }
                
                if (recommendedRecordingId != null) {
                    // IMPORTANT: Update main show data with recommended currentRecordingId
                    val updatedShowData = _baseUiState.value.showData?.copy(
                        currentRecordingId = recommendedRecordingId
                    )
                    
                    _baseUiState.value = _baseUiState.value.copy(
                        showData = updatedShowData,
                        isTrackListLoading = false // Reset track loading state 
                    )
                    _rawTrackData.value = emptyList() // Clear tracks to trigger reload
                    
                    Log.d(TAG, "Reset to recommended - refreshing tracks for: $recommendedRecordingId")
                    
                    // Trigger track list reload with recommended recording
                    loadTrackListAsync()
                }
                
                Log.d(TAG, "Reset to recommended successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting to recommended", e)
            }
        }
    }
    
    /**
     * Show collections sheet
     */
    fun showCollectionsSheet() {
        Log.d(TAG, "Show collections sheet requested")
        _baseUiState.value = _baseUiState.value.copy(showCollectionsSheet = true)
    }
    
    /**
     * Hide collections sheet
     */
    fun hideCollectionsSheet() {
        Log.d(TAG, "Hide collections sheet requested")
        _baseUiState.value = _baseUiState.value.copy(showCollectionsSheet = false)
    }
    
    /**
     * Toggle playback (for main play/pause button) - V2 Media System
     * 
     * Behavior depends on context:
     * - If viewing currently playing show/recording: toggle play/pause
     * - If viewing different show/recording: start playing the new content
     */
    fun togglePlayback() {
        val currentState = uiState.value // Use reactive state with isCurrentShowAndRecording
        Log.d(TAG, "V2 Toggle playback - playing: ${currentState.isPlaying}, isCurrentShowAndRecording: ${currentState.isCurrentShowAndRecording}")

        viewModelScope.launch {
            try {
                if (currentState.isCurrentShowAndRecording && currentState.isPlaying) {
                    // Currently playing this show/recording → pause
                    Log.d(TAG, "V2 Media: Pausing current playback")
                    playlistService.pause()
                } else {
                    // Either not playing, or different show/recording → start playback
                    Log.d(TAG, "V2 Media: Starting playback (new or resume)")
                    
                    // Get show data to determine recording ID
                    val showData = currentState.showData
                    if (showData == null) {
                        Log.w(TAG, "No show data - cannot start playback")
                        return@launch
                    }

                    // also need the current recording
                    val currentRecording = showData.currentRecordingId
                    if (currentRecording == null) {
                        Log.w(TAG, "No currentRecording - cannot start playback")
                        return@launch
                    }

                    // Get the format that was selected during playlist building
                    val selectedFormat = playlistService.getCurrentSelectedFormat()
                    
                    if (selectedFormat == null) {
                        Log.w(TAG, "No format selected - cannot start playback")
                        return@launch
                    }
                    
                    val recordingId = currentRecording
                    
                    if (currentState.isCurrentShowAndRecording) {
                        Log.d(TAG, "V2 Media: Resuming current recording $recordingId")
                        playlistService.resume()
                    } else {
                        Log.d(TAG, "V2 Media: Play All for new recording $recordingId ($selectedFormat)")
                        
                        // Get show context from UI state
                        val showContext = currentState.showData
                        if (showContext == null) {
                            Log.e(TAG, "Cannot start playback: No show data available in UI state")
                            return@launch
                        }
                        
                        val showId = showContext.showId
                        if (showId.isBlank()) {
                            Log.e(TAG, "Cannot start playback: showId is blank from service")
                            return@launch
                        }
                        
                        val showDate = showContext.date
                        val venue = showContext?.venue
                        val location = showContext?.location
                        
                        // Record show play immediately when user intentionally starts playback
                        try {
                            recentShowsService.recordShowPlay(showId)
                            Log.d(TAG, "Recorded show play in RecentShowsService: $showId (Play All)")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to record show play in RecentShowsService: $showId", e)
                            // Don't fail playback if recent shows tracking fails
                        }
                        
                        // Use MediaControllerRepository for Play All logic (new show/recording)
                        mediaControllerRepository.playAll(
                            recordingId = recordingId, 
                            format = selectedFormat,
                            showId = showId,
                            showDate = showDate,
                            venue = venue,
                            location = location
                        )
                    }
                }
                
                // UI state will be updated via MediaController state observation
                
            } catch (e: FormatNotAvailableException) {
                Log.e(TAG, "Format playback failed: ${e.message}")
                Log.e(TAG, "Available formats: ${e.availableFormats}")
                
                // Show user error
                _baseUiState.value = _baseUiState.value.copy(
                    error = "Playback format '${e.requestedFormat}' not available"
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in V2 togglePlayback", e)
            }
        }
    }
    
    /**
     * Play track - V2 Media System
     */
    fun playTrack(track: PlaylistTrackViewModel) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🕒🎵 [V2-UI] PlaylistViewModel track selection: User clicked track ${track.title} at ${System.currentTimeMillis()}")
                
                // Get show data to determine recording ID
                val currentShowData = _baseUiState.value.showData
                if (currentShowData == null) {
                    Log.w(TAG, "No show data - cannot play track")
                    return@launch
                }

                // also need the current recording
                val currentRecording = currentShowData.currentRecordingId
                if (currentRecording == null) {
                    Log.w(TAG, "No currentRecording - cannot start playback")
                    return@launch
                }

                // Get the format that was selected during playlist building
                val selectedFormat = playlistService.getCurrentSelectedFormat()
                
                if (selectedFormat == null) {
                    Log.w(TAG, "No format selected - cannot play track")
                    return@launch
                }
                
                val recordingId = currentRecording
                val trackIndex = track.number - 1 // Convert to 0-based
                
                Log.d(TAG, "V2 Media: Play track $trackIndex of $recordingId ($selectedFormat)")
                
                // Get show context from UI state
                val showContext = _baseUiState.value.showData
                if (showContext == null) {
                    Log.e(TAG, "Cannot start playback: No show data available in UI state")
                    // TODO: Show user-friendly error message instead of silent failure
                    return@launch
                }
                
                val showId = showContext.showId
                if (showId.isBlank()) {
                    Log.e(TAG, "Cannot start playback: showId is blank from service")
                    // TODO: Show user-friendly error message instead of silent failure  
                    return@launch
                }
                
                val showDate = showContext.date
                val venue = showContext?.venue
                val location = showContext?.location
                
                // Record show play immediately when user intentionally starts playback
                try {
                    recentShowsService.recordShowPlay(showId)
                    Log.d(TAG, "Recorded show play in RecentShowsService: $showId (Play Track)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record show play in RecentShowsService: $showId", e)
                    // Don't fail playback if recent shows tracking fails
                }
                
                // Use MediaControllerRepository for track playback
                mediaControllerRepository.playTrack(
                    trackIndex = trackIndex, 
                    recordingId = recordingId, 
                    format = selectedFormat,
                    showId = showId,
                    showDate = showDate,
                    venue = venue,
                    location = location
                )
                
                // UI state will be updated via MediaController state observation
                
            } catch (e: FormatNotAvailableException) {
                Log.e(TAG, "Format playback failed for track: ${e.message}")
                Log.e(TAG, "Available formats: ${e.availableFormats}")
                
                // Show user error
                _baseUiState.value = _baseUiState.value.copy(
                    error = "Track format '${e.requestedFormat}' not available"
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in V2 playTrack", e)
            }
        }
    }
    
    /**
     * Download individual track
     */
    fun downloadTrack(track: PlaylistTrackViewModel) {
        Log.d(TAG, "Download track requested: ${track.title}")
        // In real implementation, would start track download
        // For now, just update the track to show downloading state
        val updatedTracks = _rawTrackData.value.map { existingTrack ->
            if (existingTrack.number == track.number) {
                existingTrack.copy(downloadProgress = 0.1f) // Start downloading
            } else {
                existingTrack
            }
        }
        _rawTrackData.value = updatedTracks
    }
    
    /**
     * Load track list asynchronously with smart prefetch promotion
     * Shows loading spinner over track section only
     */
    private fun loadTrackListAsync() {
        // Cancel any previous track loading
        trackLoadingJob?.cancel()
        
        // Load tracks directly - internal prefetch is transparent
        loadTracksFromService()
    }
    
    /**
     * Load tracks from service (either fresh or from cache)
     */
    private fun loadTracksFromService() {
        // Start track loading with spinner
        _baseUiState.value = _baseUiState.value.copy(
            isTrackListLoading = true
        )
        _rawTrackData.value = emptyList() // Clear previous tracks while loading
        
        trackLoadingJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Loading track list asynchronously...")
                val trackData = playlistService.getTrackList()
                
                _baseUiState.value = _baseUiState.value.copy(
                    isTrackListLoading = false
                )
                _rawTrackData.value = trackData
                
                Log.d(TAG, "Track list loaded: ${trackData.size} tracks")
                
                // Start background prefetching after current tracks load
                startAdjacentPrefetch()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading track list", e)
                _baseUiState.value = _baseUiState.value.copy(
                    isTrackListLoading = false
                )
                _rawTrackData.value = emptyList()
            }
        }
    }
    
    /**
     * Cancel prefetches that are no longer adjacent after navigation
     * (Internal prefetching handles this automatically)
     */
    private suspend fun cancelDistantPrefetches(direction: String) {
        // Prefetching is now handled internally by the service
        // No explicit cancel calls needed in ViewModel
        Log.d(TAG, "Prefetch cleanup handled internally after $direction navigation")
    }
    
    /**
     * Start prefetching adjacent shows in background
     * (Internal prefetching handles this automatically)
     */
    private fun startAdjacentPrefetch() {
        // Prefetching is now handled internally by the service
        // No explicit prefetch calls needed in ViewModel
        Log.d(TAG, "Prefetch started internally for adjacent shows")
    }
}