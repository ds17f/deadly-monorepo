package com.deadly.v2.feature.collections.screens.main.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadly.v2.core.api.collections.DeadCollectionsService
import com.deadly.v2.core.design.component.FilterPath
import com.deadly.v2.core.model.DeadCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CollectionsViewModel - ViewModel for Collections screen
 * 
 * Manages UI state for the collections browsing experience.
 * Integrates with DeadCollectionsService for real collection data.
 */
@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionsService: DeadCollectionsService
) : ViewModel() {
    
    companion object {
        private const val TAG = "CollectionsViewModel"
    }
    
    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()
    
    // Filter state for HierarchicalFilter
    private val _filterPath = MutableStateFlow(FilterPath())
    val filterPath: StateFlow<FilterPath> = _filterPath.asStateFlow()
    
    // All collections from service
    private val _allCollections = MutableStateFlow<List<DeadCollection>>(emptyList())
    val allCollections: StateFlow<List<DeadCollection>> = _allCollections.asStateFlow()
    
    // Currently selected collection ID (primary source of truth)
    private val _selectedCollectionId = MutableStateFlow<String?>(null)
    val selectedCollectionId: StateFlow<String?> = _selectedCollectionId.asStateFlow()
    
    // Currently selected collection for showing shows (derived from ID + allCollections)
    val selectedCollection: StateFlow<DeadCollection?> = combine(
        selectedCollectionId,
        allCollections
    ) { collectionId, collections ->
        if (collectionId != null && collections.isNotEmpty()) {
            val collection = collections.find { it.id == collectionId }
            if (collection != null) {
                Log.d(TAG, "Selected collection found: ${collection.name}")
                collection
            } else {
                Log.d(TAG, "Selected collection ID '$collectionId' not found in all collections")
                null
            }
        } else {
            null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    // Observe featured collections from service
    val featuredCollections: StateFlow<List<DeadCollection>> = 
        collectionsService.featuredCollections.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Filtered collections based on selected filter path
    val filteredCollections: StateFlow<List<DeadCollection>> = combine(
        allCollections,
        filterPath
    ) { collections, path ->
        Log.d(TAG, "Filtering collections - Total: ${collections.size}, Filter path: ${path.getCombinedId()}")
        if (path.isEmpty) {
            Log.d(TAG, "No filter selected, showing all ${collections.size} collections")
            collections
        } else {
            val selectedTags = path.nodes.map { it.id }
            Log.d(TAG, "Selected filter tags: $selectedTags")
            
            val filtered = collections.filter { collection ->
                // Handle filtering logic based on filter structure:
                // - If "official" is selected (root), show all official collections
                // - If "official" + subcategory (e.g., "dicks-picks"), filter further
                // - If "guest" or "era" is selected (single-level), show all matching collections
                
                val hasMatch = when {
                    // Official root filter logic
                    selectedTags.contains("official") && selectedTags.size == 1 -> {
                        // Just "Official" selected - show all official collections
                        collection.tags.contains("official")
                    }
                    selectedTags.contains("official") && selectedTags.size == 2 -> {
                        // "Official" + subcategory selected - filter further
                        val subcategory = selectedTags.find { it != "official" }
                        collection.tags.contains("official") && collection.tags.contains(subcategory)
                    }
                    // Single-level filters (guest, era)
                    selectedTags.contains("guest") -> {
                        collection.tags.contains("guest")
                    }
                    selectedTags.contains("era") -> {
                        collection.tags.contains("era")
                    }
                    else -> {
                        // Fallback to any tag matching
                        selectedTags.any { tag -> collection.tags.contains(tag) }
                    }
                }
                
                Log.d(TAG, "Collection '${collection.name}' tags: ${collection.tags}, matches: $hasMatch")
                hasMatch
            }
            
            Log.d(TAG, "Filtered result: ${filtered.size} collections")
            filtered
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Selected collection index in current filtered list (derived from ID + filtered collections)
    val selectedCollectionIndex: StateFlow<Int> = combine(
        selectedCollectionId,
        filteredCollections
    ) { collectionId, filtered ->
        if (collectionId != null && filtered.isNotEmpty()) {
            val index = filtered.indexOfFirst { it.id == collectionId }
            if (index >= 0) {
                Log.d(TAG, "Collection '$collectionId' found at index $index in filtered list")
                index
            } else {
                Log.d(TAG, "Collection '$collectionId' not found in filtered list, defaulting to 0")
                0
            }
        } else {
            Log.d(TAG, "No collection selected or filtered list empty, index = 0")
            0
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    
    init {
        Log.d(TAG, "CollectionsViewModel initialized")
        loadAllCollections()
        setupFilterHandling()
    }
    
    /**
     * Setup robust filter handling to preserve selected collection when possible
     */
    private fun setupFilterHandling() {
        viewModelScope.launch {
            // Monitor when filteredCollections changes
            combine(
                selectedCollectionId,
                filteredCollections
            ) { currentSelectedId, filtered ->
                if (currentSelectedId != null && filtered.isNotEmpty()) {
                    // Check if currently selected collection exists in filtered list
                    val exists = filtered.any { it.id == currentSelectedId }
                    if (!exists) {
                        // Selected collection doesn't exist in filtered list, select first available
                        val firstCollection = filtered.firstOrNull()
                        if (firstCollection != null) {
                            Log.d(TAG, "Selected collection '$currentSelectedId' not in filtered list, selecting first: ${firstCollection.name}")
                            _selectedCollectionId.value = firstCollection.id
                        }
                    }
                } else if (currentSelectedId == null && filtered.isNotEmpty()) {
                    // No selection but we have filtered items, select the first one
                    val firstCollection = filtered.first()
                    Log.d(TAG, "No collection selected, auto-selecting first: ${firstCollection.name}")
                    _selectedCollectionId.value = firstCollection.id
                }
            }.collect { /* State is managed above */ }
        }
    }
    
    /**
     * Initialize with a specific collection ID (for routing from collection details)
     */
    fun initializeWithCollectionId(collectionId: String?) {
        collectionId?.let { id ->
            Log.d(TAG, "Initializing with collection ID: $id")
            _selectedCollectionId.value = id
        }
    }
    
    /**
     * Load all collections from service
     */
    private fun loadAllCollections() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                Log.d(TAG, "Calling collectionsService.getAllCollections()")
                val result = collectionsService.getAllCollections()
                result.fold(
                    onSuccess = { collections ->
                        Log.d(TAG, "Successfully loaded ${collections.size} collections from service")
                        collections.forEach { collection ->
                            Log.d(TAG, "Collection: '${collection.name}' with tags: ${collection.tags}")
                        }
                        _allCollections.value = collections
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Failed to load collections from service", exception)
                        _uiState.value = _uiState.value.copy(isLoading = false, error = exception.message)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading collections", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
    
    /**
     * Handle filter path change from HierarchicalFilter
     */
    fun onFilterPathChanged(newPath: FilterPath) {
        Log.d(TAG, "Filter path changed: ${newPath.getCombinedId()}")
        _filterPath.value = newPath
    }
    
    /**
     * Handle collection selection from carousel (by collection object)
     */
    fun onCollectionSelected(collection: DeadCollection) {
        Log.d(TAG, "Collection selected: ${collection.name} with ${collection.shows.size} shows")
        _selectedCollectionId.value = collection.id
    }
    
    /**
     * Handle collection selection by ID (primary method for navigation and carousel)
     */
    fun onCollectionSelectedById(collectionId: String) {
        Log.d(TAG, "Collection selected by ID: $collectionId")
        _selectedCollectionId.value = collectionId
    }
    
    /**
     * Handle collection search
     */
    fun onSearchCollections() {
        Log.d(TAG, "Search collections requested")
        // TODO: Implement collections search
    }
}

/**
 * UI state for Collections screen
 */
data class CollectionsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)