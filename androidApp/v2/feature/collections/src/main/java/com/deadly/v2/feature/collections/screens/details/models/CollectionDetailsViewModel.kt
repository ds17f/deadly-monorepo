package com.deadly.v2.feature.collections.screens.details.models

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadly.v2.core.api.collections.DeadCollectionsService
// Simplified ViewModel without debug components for now
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Collection Details screen
 * 
 * Manages collection details state and coordinates with DeadCollectionsService
 * following V2 service-oriented architecture patterns.
 */
@HiltViewModel
class CollectionDetailsViewModel @Inject constructor(
    private val collectionsService: DeadCollectionsService,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    companion object {
        private const val TAG = "CollectionDetailsViewModel"
    }
    
    private val collectionId: String = savedStateHandle.get<String>("collectionId") ?: ""
    
    private val _uiState = MutableStateFlow<CollectionDetailsUiState>(CollectionDetailsUiState.Loading)
    val uiState: StateFlow<CollectionDetailsUiState> = _uiState.asStateFlow()
    
    init {
        Log.d(TAG, "CollectionDetailsViewModel initialized for collection: $collectionId")
        loadCollectionDetails()
    }
    
    /**
     * Load collection details and shows
     */
    private fun loadCollectionDetails() {
        Log.d(TAG, "Loading details for collection: $collectionId")
        
        viewModelScope.launch {
            try {
                _uiState.value = CollectionDetailsUiState.Loading
                
                val result = collectionsService.getCollectionDetails(collectionId)
                
                result.fold(
                    onSuccess = { details ->
                        Log.d(TAG, "Successfully loaded collection: ${details.collection.name} with ${details.collection.shows.size} shows")
                        _uiState.value = CollectionDetailsUiState.Success(details.collection)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load collection details", error)
                        _uiState.value = CollectionDetailsUiState.Error(
                            error.message ?: "Unknown error occurred"
                        )
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading collection details", e)
                _uiState.value = CollectionDetailsUiState.Error(
                    "Failed to load collection: ${e.message}"
                )
            }
        }
    }
    
}