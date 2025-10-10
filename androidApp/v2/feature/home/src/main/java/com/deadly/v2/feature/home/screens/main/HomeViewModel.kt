package com.deadly.v2.feature.home.screens.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadly.v2.core.api.home.HomeService
import com.deadly.v2.core.api.home.HomeContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * HomeViewModel - State management for rich Home screen
 * 
 * Following V2 architecture patterns:
 * - Single HomeService dependency for data orchestration
 * - StateFlow for reactive UI updates
 * - Clean separation between UI state and service state
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeService: HomeService
) : ViewModel() {
    
    companion object {
        private const val TAG = "HomeViewModel"
    }
    
    private val _uiState = MutableStateFlow(HomeUiState.initial())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        Log.d(TAG, "HomeViewModel initialized")
        observeHomeService()
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