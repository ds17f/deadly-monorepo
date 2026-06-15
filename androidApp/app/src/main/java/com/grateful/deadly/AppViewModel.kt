package com.grateful.deadly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.favorites.FavoritesService
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.database.AppReviewManager
import com.grateful.deadly.core.database.ToastController
import com.grateful.deadly.core.database.ShowQueueTabRequest
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.miniplayer.LastPlayedTrackService
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.core.network.monitor.NetworkMonitor
import com.grateful.deadly.playback.AutoAdvanceCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
    appPreferences: AppPreferences,
    private val analyticsService: AnalyticsService,
    private val lastPlayedTrackService: LastPlayedTrackService,
    private val showRepository: ShowRepository,
    private val favoritesService: FavoritesService,
    private val appReviewManager: AppReviewManager,
    private val autoAdvanceCoordinator: AutoAdvanceCoordinator,
    private val showQueueTabRequest: ShowQueueTabRequest,
    toastController: ToastController,
) : ViewModel() {

    /** Ask Favorites to open on its Show Queue tab (player/playlist "View Show Queue"). */
    fun requestShowQueueTab() = showQueueTabRequest.request()

    /** App-wide transient toast messages, surfaced by the root SnackbarHost. */
    val toasts: SharedFlow<String> = toastController.messages

    // ADR-0010: end-of-show countdown UI (active device or remote).
    val autoAdvanceCountdown: StateFlow<AutoAdvanceCoordinator.Countdown?> =
        autoAdvanceCoordinator.countdown
    fun cancelAutoAdvance() = autoAdvanceCoordinator.cancel()
    fun playNextNow() = autoAdvanceCoordinator.playNow()

    val isOffline: StateFlow<Boolean> = combine(
        networkMonitor.isOnline,
        appPreferences.forceOnline
    ) { online, forced -> !online && !forced }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    init {
        lastPlayedTrackService.startMonitoring()
        viewModelScope.launch {
            lastPlayedTrackService.restoreLastPlayedTrack()
        }
    }

    suspend fun getShow(showId: String): Show? = showRepository.getShowById(showId)

    fun addToFavorites(showId: String) {
        viewModelScope.launch { favoritesService.addToFavorites(showId) }
    }

    fun trackFeature(feature: String) {
        analyticsService.track("feature_use", mapOf("feature" to feature))
    }

    // ── In-App Review ────────────────────────────────────────────────

    val showReviewDialog: StateFlow<Boolean> = appReviewManager.showPrePromptDialog
    val launchInAppReview: StateFlow<Boolean> = appReviewManager.launchInAppReview

    fun onReviewYes() = appReviewManager.onUserSaidYes()
    fun onReviewDismiss() = appReviewManager.onUserSaidNotReally()
    fun onInAppReviewLaunched() = appReviewManager.onInAppReviewLaunched()
}
