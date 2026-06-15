package com.grateful.deadly.feature.home.screens.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.home.HomeService
import com.grateful.deadly.core.api.home.HomeContent
import com.grateful.deadly.core.api.home.TrendingService
import com.grateful.deadly.core.database.AnalyticsService
import com.grateful.deadly.core.database.AppPreferences
import com.grateful.deadly.core.database.ToastController
import com.grateful.deadly.core.domain.repository.BacklogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * HomeViewModel - State management for rich Home screen
 * 
 * Following architecture patterns:
 * - Single HomeService dependency for data orchestration
 * - StateFlow for reactive UI updates
 * - Clean separation between UI state and service state
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeService: HomeService,
    private val trendingService: TrendingService,
    private val appPreferences: AppPreferences,
    private val analyticsService: AnalyticsService,
    private val backlogRepository: BacklogRepository,
    private val toastController: ToastController,
) : ViewModel() {

    /** Add a show to the backlog ("Up Next") from the long-press sheet. */
    fun addToUpNext(showId: String) {
        viewModelScope.launch {
            if (backlogRepository.contains(showId)) {
                toastController.show("Already in Up Next")
            } else {
                backlogRepository.addToBottom(showId)
                toastController.show("Added to Up Next")
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState.initial())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "HomeViewModel initialized")
        observeHomeService()
        // Refetch trending when the user flips the anniversaries filter.
        // The TrendingService has its own observer too — this is belt-and-
        // suspenders to make sure the VM's lifecycle catches the change.
        viewModelScope.launch {
            appPreferences.homeTrendingIncludeAnniversaries
                .drop(1)
                .collect {
                    Log.d(TAG, "anniversaries pref changed → refresh trending")
                    trendingService.refresh()
                }
        }
    }
    
    /**
     * Observe HomeService content and update UI state
     */
    private fun observeHomeService() {
        viewModelScope.launch {
            homeService.homeContent.collect { homeContent ->
                Log.d(TAG, "HomeService content updated: ${homeContent.recentShows.size} recent, " +
                          "${homeContent.todayInHistory.size} history, ${homeContent.featuredCollections.size} collections")
                
                _uiState.value = _uiState.value.copy(
                    homeContent = homeContent,
                    isLoading = false,
                    error = null
                )
            }
        }
    }
    
    /**
     * Refresh all home content
     */
    fun refresh() {
        Log.d(TAG, "refresh() called")
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                val result = homeService.refreshAll()
                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to refresh content"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to refresh content"
                )
            }
        }
    }
    
    /** Cycle the trending window forward (Day → Week → Month → All → Day). */
    fun cycleTrendingWindow() {
        homeService.cycleTrendingWindow()
    }

    /** Telemetry hook for the Fan Favorites "Show more" re-roll. */
    fun trackPopularShowMore() {
        analyticsService.track("feature_use", mapOf(
            "feature" to "popular_show_more",
            "category" to "action",
            "value" to appPreferences.homePopularDecade.value,
        ))
    }

    /** Telemetry hook for the Featured Collections "Show more" re-roll. */
    fun trackCollectionsShowMore() {
        analyticsService.track("feature_use", mapOf(
            "feature" to "collections_show_more",
            "category" to "action",
        ))
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "HomeViewModel cleared")
    }
}

/**
 * UI State for HomeScreen
 * 
 * Wraps HomeService content with additional UI concerns
 */
data class HomeUiState(
    val homeContent: HomeContent,
    val isLoading: Boolean,
    val error: String?
) {
    companion object {
        fun initial() = HomeUiState(
            homeContent = HomeContent.initial(),
            isLoading = true,
            error = null
        )
    }
    
    val hasError: Boolean get() = error != null
}