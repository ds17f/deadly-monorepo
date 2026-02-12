package com.deadly.v2.feature.collections.screens.details.models

import com.deadly.v2.core.model.DeadCollection

/**
 * UI state for Collection Details screen
 * 
 * Represents different states of collection details loading and display.
 */
sealed class CollectionDetailsUiState {
    
    /**
     * Loading state - fetching collection data
     */
    data object Loading : CollectionDetailsUiState()
    
    /**
     * Success state - collection data loaded successfully
     * 
     * @param collection The loaded collection with shows and metadata
     */
    data class Success(
        val collection: DeadCollection
    ) : CollectionDetailsUiState()
    
    /**
     * Error state - failed to load collection data
     * 
     * @param message Error message describing what went wrong
     */
    data class Error(
        val message: String
    ) : CollectionDetailsUiState()
}