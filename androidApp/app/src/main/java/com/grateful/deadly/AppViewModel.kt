package com.grateful.deadly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.favorites.FavoritesService
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.miniplayer.LastPlayedTrackService
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.core.network.monitor.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val favoritesService: FavoritesService
) : ViewModel() {

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
}
