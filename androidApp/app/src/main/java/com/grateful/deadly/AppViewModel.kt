package com.grateful.deadly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.miniplayer.LastPlayedTrackService
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
    private val lastPlayedTrackService: LastPlayedTrackService
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
}
