package com.grateful.deadly.feature.library.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grateful.deadly.core.api.library.ReviewService
import com.grateful.deadly.core.model.FavoriteTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val reviewService: ReviewService
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<FavoriteTrack>>(emptyList())
    val favorites: StateFlow<List<FavoriteTrack>> = _favorites.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _favorites.value = reviewService.getThumbsUpTracks()
            _isLoading.value = false
        }
    }
}
