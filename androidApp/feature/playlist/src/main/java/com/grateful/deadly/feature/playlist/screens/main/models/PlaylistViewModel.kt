package com.grateful.deadly.feature.playlist.screens.main.models

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.playlist.PlaylistService
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.api.favorites.FavoritesService
import com.grateful.deadly.core.api.favorites.ReviewService
import com.grateful.deadly.core.api.recent.RecentShowsService
import com.grateful.deadly.core.model.*
import com.grateful.deadly.core.model.ShowReview
import com.grateful.deadly.core.connect.ConnectService
import com.grateful.deadly.core.media.equalizer.EqualizerRepository
import com.grateful.deadly.core.media.equalizer.EqualizerState
import com.grateful.deadly.core.media.repository.MediaControllerRepository
import com.grateful.deadly.core.media.exception.FormatNotAvailableException
import com.grateful.deadly.core.network.monitor.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
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
    private val favoritesService: FavoritesService,
    private val recentShowsService: RecentShowsService,
    private val reviewService: ReviewService,
    private val equalizerRepository: EqualizerRepository,
    private val analyticsService: AnalyticsService,
    private val connectService: ConnectService,
    networkMonitor: NetworkMonitor,
    val appPreferences: AppPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val isOffline: StateFlow<Boolean> = networkMonitor.isOnline
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    companion object {
        private const val TAG = "PlaylistViewModel"
    }
    
    
    // Internal state for show and tracks data
    private val _baseUiState = MutableStateFlow(PlaylistUiState())
    private val _rawTrackData = MutableStateFlow<List<PlaylistTrackViewModel>>(emptyList())
    
    // Thumbs-up track titles for the current show
    private val _favoriteTitles = MutableStateFlow<Set<String>>(emptySet())

    // Whether the user has written a review for the current show
    private val _hasUserReview = MutableStateFlow(false)

    // Show review state
    private val _userReview = MutableStateFlow(ShowReview(showId = ""))
    val userReview: StateFlow<ShowReview> = _userReview.asStateFlow()
    private val _reviewLineup = MutableStateFlow<List<String>>(emptyList())
    val reviewLineup: StateFlow<List<String>> = _reviewLineup.asStateFlow()
    private val _showWriteReview = MutableStateFlow(false)
    val showWriteReview: StateFlow<Boolean> = _showWriteReview.asStateFlow()

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

        // Reactive favorite track loading - watch favorite songs for the current show
        viewModelScope.launch {
            _baseUiState
                .map { it.showData?.showId }
                .distinctUntilChanged()
                .flatMapLatest { showId ->
                    if (showId != null) reviewService.getFavoriteSongTitlesFlow(showId)
                    else flowOf(emptySet())
                }
                .collect { titles ->
                    _favoriteTitles.value = titles
                }
        }

        // Reactive user review loading - watch for showId changes
        viewModelScope.launch {
            _baseUiState
                .map { it.showData?.showId }
                .distinctUntilChanged()
                .collect { showId ->
                    if (showId != null) {
                        val review = reviewService.getShowReview(showId) ?: ShowReview(showId = showId)
                        _userReview.value = review
                        _hasUserReview.value = review.hasContent
                    } else {
                        _userReview.value = ShowReview(showId = "")
                        _hasUserReview.value = false
                    }
                }
        }

        // Reactive track download status - refresh track icons on download state change (debounced)
        viewModelScope.launch {
            _baseUiState
                .map { it.showData?.showId }
                .distinctUntilChanged()
                .flatMapLatest { showId ->
                    if (showId != null) {
                        playlistService.observeShowDownloadProgress(showId)
                    } else {
                        flowOf(ShowDownloadProgress("", FavoritesDownloadStatus.NOT_DOWNLOADED, 0f, 0L, 0L, 0, 0))
                    }
                }
                .debounce(500)
                .collect { progress ->
                    if (_rawTrackData.value.isNotEmpty()) {
                        Log.d(TAG, "Download status changed (${progress.status}, ${progress.tracksCompleted}/${progress.tracksTotal}) — refreshing track download icons")
                        refreshTrackDownloadStatus()
                    }
                }
        }
    }
    
    // Combined library + download state to keep main combine at 5 flows
    private data class FavoriteAndDownloadState(
        val isFavorite: Boolean = false,
        val downloadProgress: Float? = null,
        val downloadStatus: FavoritesDownloadStatus? = null
    )

    // Inner combine result to work around Kotlin's 5-flow combine limit
    private data class CorePlaylistState(
        val baseState: PlaylistUiState,
        val rawTracks: List<PlaylistTrackViewModel>,
        val isPlaying: Boolean,
        val currentTrackInfo: CurrentTrackInfo?,
        val favoriteAndDownload: FavoriteAndDownloadState
    )

    // Reactive UI state that combines base state with MediaController state and library status
    val uiState: StateFlow<PlaylistUiState> = combine(
        combine(
            _baseUiState,
            _rawTrackData,
            playlistService.isPlaying,
            playlistService.currentTrackInfo,
            _baseUiState
                .map { baseState -> baseState.showData?.showId }
                .distinctUntilChanged()
                .flatMapLatest { showId ->
                    if (showId != null) {
                        combine(
                            favoritesService.isShowFavorite(showId),
                            playlistService.observeShowDownloadProgress(showId)
                        ) { inFavorites, progress ->
                            val mappedProgress = when (progress.status) {
                                FavoritesDownloadStatus.NOT_DOWNLOADED -> null
                                FavoritesDownloadStatus.QUEUED -> 0.0f
                                FavoritesDownloadStatus.DOWNLOADING -> progress.overallProgress
                                FavoritesDownloadStatus.COMPLETED -> 1.0f
                                FavoritesDownloadStatus.PAUSED -> progress.overallProgress
                                FavoritesDownloadStatus.FAILED -> null
                                FavoritesDownloadStatus.CANCELLED -> null
                            }
                            val mappedStatus = when (progress.status) {
                                FavoritesDownloadStatus.NOT_DOWNLOADED,
                                FavoritesDownloadStatus.FAILED,
                                FavoritesDownloadStatus.CANCELLED -> null
                                else -> progress.status
                            }
                            FavoriteAndDownloadState(
                                isFavorite = inFavorites,
                                downloadProgress = mappedProgress,
                                downloadStatus = mappedStatus
                            )
                        }
                    } else {
                        flowOf(FavoriteAndDownloadState())
                    }
                }
        ) { baseState, rawTracks, isPlaying, currentTrackInfo, favoriteAndDownload ->
            CorePlaylistState(baseState, rawTracks, isPlaying, currentTrackInfo, favoriteAndDownload)
        },
        _favoriteTitles,
        _hasUserReview
    ) { core, favorites, hasUserReview ->
        val (baseState, rawTracks, isPlaying, currentTrackInfo, favoriteAndDownload) = core

        // Update track data with current playing state
        val updatedTracks = rawTracks.map { track ->
            val isCurrentTrack = isTrackCurrentlyPlaying(track, currentTrackInfo)
            track.copy(
                isCurrentTrack = isCurrentTrack,
                isPlaying = isCurrentTrack && isPlaying,
                isFavorite = track.title in favorites
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
        Log.v(TAG, "Favorites status: showId='$playlistShowId', isFavorite=${favoriteAndDownload.isFavorite}")

        // Update showData with current library and download status
        val updatedShowData = baseState.showData?.copy(
            isFavorite = favoriteAndDownload.isFavorite,
            downloadProgress = favoriteAndDownload.downloadProgress,
            downloadStatus = favoriteAndDownload.downloadStatus
        )

        baseState.copy(
            trackData = updatedTracks,
            isPlaying = isPlaying,
            isCurrentShowAndRecording = isCurrentShowAndRecording,
            mediaLoading = currentTrackInfo?.playbackState?.isLoading ?: false,
            showData = updatedShowData,
            hasUserReview = hasUserReview
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
     * @param trackNumber Optional track number to auto-play after tracks load (e.g., from deep link)
     * @param autoPlay Whether to start playback automatically (e.g., from "Play Now" deep link action)
     */
    fun loadShow(showId: String?, recordingId: String? = null, trackNumber: Int? = null, autoPlay: Boolean = false) {
        Log.d(TAG, "Loading show: $showId, recordingId: $recordingId, trackNumber: $trackNumber, autoPlay: $autoPlay")
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

                // Auto-select or auto-play specific track once tracks are available
                if (trackNumber != null) {
                    viewModelScope.launch {
                        _rawTrackData.first { it.isNotEmpty() }
                        val track = _rawTrackData.value.firstOrNull { it.number == trackNumber }
                        if (track != null) {
                            Log.d(TAG, "Selecting track $trackNumber from deep link (autoPlay=$autoPlay): ${track.title}")
                            playTrack(track, autoPlay = autoPlay)
                        } else {
                            Log.w(TAG, "Track $trackNumber not found in loaded tracks")
                        }
                    }
                } else if (autoPlay) {
                    // No specific track requested — auto-play track 1
                    viewModelScope.launch {
                        _rawTrackData.first { it.isNotEmpty() }
                        val firstTrack = _rawTrackData.value.firstOrNull()
                        if (firstTrack != null) {
                            Log.d(TAG, "Auto-playing first track from deep link: ${firstTrack.title}")
                            playTrack(firstTrack, autoPlay = true)
                        }
                    }
                }

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
                Log.d(TAG, "🕒🎵 [UI] PlaylistViewModel track selection: User clicked track at index $trackIndex at ${System.currentTimeMillis()}")
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
    fun addToFavorites() {
        viewModelScope.launch {
            try {
                val currentShow = _baseUiState.value.showData
                if (currentShow?.showId != null) {
                    favoritesService.addToFavorites(currentShow.showId)
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
     * Download show — state machine: start / pause / resume / remove
     */
    fun downloadShow() {
        val showData = uiState.value.showData ?: return
        val status = showData.downloadStatus

        when (status) {
            FavoritesDownloadStatus.COMPLETED -> {
                // Already downloaded — show removal confirmation
                _baseUiState.value = _baseUiState.value.copy(showRemoveDownloadDialog = true)
            }
            FavoritesDownloadStatus.DOWNLOADING,
            FavoritesDownloadStatus.QUEUED -> {
                // Active download — pause
                playlistService.pauseShowDownload()
            }
            FavoritesDownloadStatus.PAUSED -> {
                // Paused — resume
                playlistService.resumeShowDownload()
            }
            else -> {
                // Not downloaded or failed — start download
                viewModelScope.launch {
                    try {
                        playlistService.downloadShow()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error downloading show", e)
                    }
                }
            }
        }
    }

    /**
     * Confirm removal of downloaded show files
     */
    fun confirmRemoveDownload() {
        _baseUiState.value = _baseUiState.value.copy(showRemoveDownloadDialog = false)
        viewModelScope.launch {
            try {
                val showId = _baseUiState.value.showData?.showId ?: return@launch
                favoritesService.cancelShowDownloads(showId)
                    .onSuccess {
                        Log.d(TAG, "Successfully removed downloads for show $showId")
                        delay(500) // Let ExoPlayer async cache removal complete
                        refreshTrackDownloadStatus()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to remove downloads for show $showId", error)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing downloads", e)
            }
        }
    }

    /**
     * Dismiss the remove download confirmation dialog
     */
    fun dismissRemoveDownloadDialog() {
        _baseUiState.value = _baseUiState.value.copy(showRemoveDownloadDialog = false)
    }

    /**
     * Handle favorites actions
     */
    fun handleFavoritesAction(action: FavoritesAction) {
        when (action) {
            FavoritesAction.ADD_TO_FAVORITES -> addToFavorites()
            FavoritesAction.REMOVE_FROM_FAVORITES -> removeFromFavorites()
            FavoritesAction.REMOVE_WITH_DOWNLOADS -> removeFromFavoritesWithDownloads()
        }
    }
    
    /**
     * Remove from library
     */
    private fun removeFromFavorites() {
        viewModelScope.launch {
            try {
                val currentShow = _baseUiState.value.showData
                if (currentShow?.showId != null) {
                    favoritesService.removeFromFavorites(currentShow.showId)
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
    private fun removeFromFavoritesWithDownloads() {
        viewModelScope.launch {
            try {
                val currentShow = _baseUiState.value.showData
                if (currentShow?.showId != null) {
                    // First cancel any downloads for this show
                    favoritesService.cancelShowDownloads(currentShow.showId)
                        .onSuccess {
                            // Then remove from library
                            favoritesService.removeFromFavorites(currentShow.showId)
                                .onSuccess {
                                    Log.d(TAG, "Successfully removed show ${currentShow.showId} from library with downloads")
                                    delay(500) // Let ExoPlayer async cache removal complete
                                    refreshTrackDownloadStatus()
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
     * Share current show as a text message (just the URL)
     */
    fun shareAsMessage() {
        val showData = _baseUiState.value.showData ?: return

        val url = if (showData.currentRecordingId != null) {
            "${appPreferences.shareBaseUrl}/shows/${showData.showId}/recording/${showData.currentRecordingId}"
        } else {
            "${appPreferences.shareBaseUrl}/shows/${showData.showId}"
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
    
    /**
     * Show reviews (opens review details modal)
     */
    fun showReviews() {
        Log.d(TAG, "Show reviews requested")
        analyticsService.track("feature_use", mapOf("feature" to "view_reviews"))
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
        analyticsService.track("feature_use", mapOf("feature" to "view_setlist"))
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
    fun getCurrentSetlistData(): com.grateful.deadly.core.model.SetlistViewModel? {
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
    fun getDummySetlistData(): com.grateful.deadly.core.model.SetlistViewModel {
        return com.grateful.deadly.core.model.SetlistViewModel(
            showDate = "May 8, 1977",
            venue = "Barton Hall, Cornell University",
            location = "Ithaca, NY",
            sets = listOf(
                com.grateful.deadly.core.model.SetlistSetViewModel(
                    name = "Set One",
                    songs = listOf(
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Minglewood Blues"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Loser"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "El Paso"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "They Love Each Other"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Jack Straw"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Deal"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Looks Like Rain"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Row Jimmy"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Playing in the Band")
                    )
                ),
                com.grateful.deadly.core.model.SetlistSetViewModel(
                    name = "Set Two",
                    songs = listOf(
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Samson and Delilah"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Help on the Way", hasSegue = true, segueSymbol = ">"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Slipknot!", hasSegue = true, segueSymbol = ">"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Franklin's Tower"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Estimated Prophet"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Eyes of the World", hasSegue = true, segueSymbol = ">"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Drums", hasSegue = true, segueSymbol = ">"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Not Fade Away", hasSegue = true, segueSymbol = ">"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Drums", hasSegue = true, segueSymbol = ">"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "The Other One", hasSegue = true, segueSymbol = ">"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Wharf Rat", hasSegue = true, segueSymbol = ">"),
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Around and Around")
                    )
                ),
                com.grateful.deadly.core.model.SetlistSetViewModel(
                    name = "Encore",
                    songs = listOf(
                        com.grateful.deadly.core.model.SetlistSongViewModel(null, "Johnny B. Goode")
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
        analyticsService.track("feature_use", mapOf("feature" to "open_menu"))
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
     * Set recording as default — always persists the preference (independent of library membership).
     * Shows a confirmation dialog first if there is a download conflict for library shows.
     */
    fun setRecordingAsDefault(recordingId: String) {
        Log.d(TAG, "Set recording as default: $recordingId")
        val showId = _baseUiState.value.showData?.showId ?: return
        viewModelScope.launch {
            try {
                // Update in-memory recording (for immediate track list refresh)
                playlistService.setRecordingAsDefault(recordingId)

                // IMPORTANT: Update main show data with new currentRecordingId
                val updatedShowData = _baseUiState.value.showData?.copy(
                    currentRecordingId = recordingId
                )

                _baseUiState.value = _baseUiState.value.copy(
                    showData = updatedShowData,
                    isTrackListLoading = false
                )
                _rawTrackData.value = emptyList()

                Log.d(TAG, "Recording set as default - refreshing tracks for: $recordingId")
                loadTrackListAsync()

                // Always persist recording preference (independent of library)
                favoritesService.setPreferredRecording(showId, recordingId)
                Log.d(TAG, "Persisted preferred recording for $showId: $recordingId")

                // Download conflict check only applies to library shows
                val isFavorite = uiState.value.showData?.isFavorite ?: false
                if (isFavorite) {
                    val hasConflict = playlistService.hasDownloadConflict(showId, recordingId)
                    if (hasConflict) {
                        Log.d(TAG, "Download conflict detected for $showId — showing confirmation dialog")
                        _baseUiState.value = _baseUiState.value.copy(
                            showDownloadConflictDialog = true,
                            pendingRecordingId = recordingId
                        )
                    }
                }

                Log.d(TAG, "Recording set as default successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting recording as default", e)
            }
        }
    }

    /**
     * Confirm switching to the pending recording despite a download conflict.
     * Persists the new preference and removes the old download.
     */
    fun confirmRecordingChange() {
        val showId = _baseUiState.value.showData?.showId ?: return
        val recordingId = _baseUiState.value.pendingRecordingId ?: return
        _baseUiState.value = _baseUiState.value.copy(
            showDownloadConflictDialog = false,
            pendingRecordingId = null
        )
        viewModelScope.launch {
            try {
                playlistService.confirmRecordingChange(showId, recordingId)
                Log.d(TAG, "Recording change confirmed for $showId: $recordingId, old download removed")
                delay(500) // Let ExoPlayer async cache removal complete
                refreshTrackDownloadStatus()
            } catch (e: Exception) {
                Log.e(TAG, "Error confirming recording change", e)
            }
        }
    }

    /**
     * Cancel the pending recording change — dismiss the conflict dialog without
     * deleting the existing download or persisting the new preference.
     */
    fun dismissDownloadConflictDialog() {
        _baseUiState.value = _baseUiState.value.copy(
            showDownloadConflictDialog = false,
            pendingRecordingId = null
        )
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
        analyticsService.track("feature_use", mapOf("feature" to "view_collections"))
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
     * Toggle playback (for main play/pause button)
     * 
     * Behavior depends on context:
     * - If viewing currently playing show/recording: toggle play/pause
     * - If viewing different show/recording: start playing the new content
     */
    fun togglePlayback() {
        val currentState = uiState.value // Use reactive state with isCurrentShowAndRecording
        Log.d(TAG, "Toggle playback - playing: ${currentState.isPlaying}, isCurrentShowAndRecording: ${currentState.isCurrentShowAndRecording}")

        viewModelScope.launch {
            try {
                if (currentState.isCurrentShowAndRecording && currentState.isPlaying) {
                    // Currently playing this show/recording → pause
                    Log.d(TAG, "Media: Pausing current playback")
                    playlistService.pause()
                    Log.d(TAG, "Connect: sending pause from togglePlayback")
                    connectService.sendPause()
                } else {
                    // Either not playing, or different show/recording → start playback
                    Log.d(TAG, "Media: Starting playback (new or resume)")
                    
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
                    val venue = showContext.venue
                    val location = showContext.location

                    if (currentState.isCurrentShowAndRecording) {
                        Log.d(TAG, "Media: Resuming current recording $recordingId")
                        playlistService.resume()
                    } else {
                        Log.d(TAG, "Media: Play All for new recording $recordingId ($selectedFormat)")

                        // Record show play immediately when user intentionally starts playback
                        try {
                            recentShowsService.recordShowPlay(showId)
                            Log.d(TAG, "Recorded show play in RecentShowsService: $showId (Play All)")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to record show play in RecentShowsService: $showId", e)
                        }

                        // Use MediaControllerRepository for Play All logic (new show/recording)
                        mediaControllerRepository.playAll(
                            recordingId = recordingId,
                            format = selectedFormat,
                            showId = showId,
                            showDate = showDate,
                            venue = venue,
                            location = location,
                            coverImageUrl = showContext.coverImageUrl
                        )
                    }

                    // Always send load to Connect so the server knows which show
                    // Android wants to play (resume and new-show both need this)
                    val sessionTracks = _rawTrackData.value.map { t ->
                        ConnectSessionTrack(
                            title = t.title,
                            durationMs = parseDurationToMs(t.duration)
                        )
                    }
                    val firstDurationMs = sessionTracks.firstOrNull()?.durationMs ?: 0
                    Log.d(TAG, "Connect: sendLoad from togglePlayback — show=$showId rec=$recordingId tracks=${sessionTracks.size}")
                    connectService.sendLoad(
                        showId = showId,
                        recordingId = recordingId,
                        tracks = sessionTracks,
                        trackIndex = 0,
                        positionMs = 0,
                        durationMs = firstDurationMs,
                        date = showDate,
                        venue = venue,
                        location = location,
                    )
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
                Log.e(TAG, "Error in togglePlayback", e)
            }
        }
    }
    
    /**
     * Play track
     */
    fun playTrack(track: PlaylistTrackViewModel, autoPlay: Boolean = true) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🕒🎵 [UI] PlaylistViewModel track selection: User clicked track ${track.title} at ${System.currentTimeMillis()}")
                
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
                
                Log.d(TAG, "Media: Play track $trackIndex of $recordingId ($selectedFormat)")
                
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
                    location = location,
                    coverImageUrl = showContext?.coverImageUrl,
                    autoPlay = autoPlay
                )

                // Notify Connect server
                val sessionTracks = _rawTrackData.value.map { t ->
                    ConnectSessionTrack(
                        title = t.title,
                        durationMs = parseDurationToMs(t.duration)
                    )
                }
                val trackDurationMs = sessionTracks.getOrNull(trackIndex)?.durationMs ?: 0
                Log.d(TAG, "Connect: sendLoad from playTrack — show=$showId rec=$recordingId track=$trackIndex tracks=${sessionTracks.size}")
                connectService.sendLoad(
                    showId = showId,
                    recordingId = recordingId,
                    tracks = sessionTracks,
                    trackIndex = trackIndex,
                    positionMs = 0,
                    durationMs = trackDurationMs,
                    date = showDate,
                    venue = venue,
                    location = location,
                    autoplay = autoPlay,
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
                Log.e(TAG, "Error in playTrack", e)
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
     * Lightweight refresh of track download status without network calls.
     * Updates isDownloaded / downloadProgress on existing tracks using local cache only.
     */
    private fun refreshTrackDownloadStatus() {
        val current = _rawTrackData.value
        if (current.isEmpty()) return
        _rawTrackData.value = playlistService.refreshTrackDownloadFlags(current)
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

    fun loadUserReview() {
        val showId = _baseUiState.value.showData?.showId ?: return
        viewModelScope.launch {
            val existing = reviewService.getShowReview(showId)
            val isEdit = existing?.hasContent == true
            analyticsService.track("feature_use", mapOf("feature" to if (isEdit) "edit_review" else "write_review"))
            _userReview.value = existing ?: ShowReview(showId = showId)
            _reviewLineup.value = playlistService.getCurrentShowLineup()
            _showWriteReview.value = true
        }
    }

    fun hideWriteReview() {
        _showWriteReview.value = false
    }

    fun deleteUserReview() {
        val showId = _baseUiState.value.showData?.showId ?: return
        analyticsService.track("feature_use", mapOf("feature" to "delete_review"))
        viewModelScope.launch {
            reviewService.deleteShowReview(showId)
            _hasUserReview.value = false
            _userReview.value = ShowReview(showId = showId)
            _showWriteReview.value = false
        }
    }

    fun saveUserReview(
        notes: String?,
        rating: Float?,
        recordingQuality: Int?,
        playingQuality: Int?,
        standoutPlayers: List<String>
    ) {
        val showId = _baseUiState.value.showData?.showId ?: return
        val recordingId = _baseUiState.value.showData?.currentRecordingId
        analyticsService.track("feature_use", mapOf("feature" to "save_review"))
        viewModelScope.launch {
            reviewService.updateShowNotes(showId, notes)
            reviewService.updateShowRating(showId, rating)
            reviewService.updateRecordingQuality(showId, recordingQuality, recordingId)
            reviewService.updatePlayingQuality(showId, playingQuality)

            val existingTags = reviewService.getPlayerTags(showId)
            val existingNames = existingTags.map { it.playerName }.toSet()
            for (name in existingNames - standoutPlayers.toSet()) {
                reviewService.removePlayerTag(showId, name)
            }
            for (name in standoutPlayers.toSet() - existingNames) {
                reviewService.upsertPlayerTag(showId, name, isStandout = true)
            }

            val saved = reviewService.getShowReview(showId)
            _hasUserReview.value = saved?.hasContent == true
            _userReview.value = saved ?: ShowReview(showId = showId)
            _showWriteReview.value = false
        }
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

    /** Parse "mm:ss" or "hh:mm:ss" to milliseconds for Connect session tracks. */
    private fun parseDurationToMs(duration: String): Int {
        val parts = duration.split(":")
        return try {
            when (parts.size) {
                2 -> (parts[0].toInt() * 60 + parts[1].toInt()) * 1000
                3 -> (parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()) * 1000
                else -> 0
            }
        } catch (_: NumberFormatException) { 0 }
    }
}