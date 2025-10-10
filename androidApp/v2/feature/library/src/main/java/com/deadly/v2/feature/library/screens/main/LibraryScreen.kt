package com.deadly.v2.feature.library.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.deadly.v2.core.design.resources.IconResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deadly.v2.core.design.component.HierarchicalFilter
import com.deadly.v2.core.design.component.FilterPath
import com.deadly.v2.core.design.component.FilterTrees
import com.deadly.v2.core.model.*
import com.deadly.v2.feature.library.screens.main.components.*
import com.deadly.v2.feature.library.screens.main.models.LibraryViewModel

/**
 * V2 Library Screen - Main library interface following V2 architecture
 * 
 * Features:
 * - Hierarchical decade/season filtering 
 * - Advanced sorting with pin priority
 * - List/grid display modes
 * - Library management actions
 * - Real-time download status integration
 * 
 * Note: Scaffold-free content designed for use within MainNavigation's AppScaffold.
 * TopBar configuration handled by LibraryBarConfiguration.
 */
@Composable
fun LibraryScreen(
    onNavigateToShow: (String) -> Unit = {},
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // UI State
    var filterPath by remember { mutableStateOf(FilterPath()) }
    var sortBy by remember { mutableStateOf(LibrarySortOption.DATE_ADDED) }
    var sortDirection by remember { mutableStateOf(LibrarySortDirection.DESCENDING) }
    var displayMode by remember { mutableStateOf(LibraryDisplayMode.LIST) }
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var selectedShowForActions by remember { mutableStateOf<LibraryShowViewModel?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
            // Hierarchical Filters
            HierarchicalFilter(
                filterTree = FilterTrees.buildDeadToursTree(),
                selectedPath = filterPath,
                onSelectionChanged = { filterPath = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Sort Controls and Display Toggle
            SortAndDisplayControls(
                sortBy = sortBy,
                sortDirection = sortDirection,
                displayMode = displayMode,
                onSortSelectorClick = { showSortBottomSheet = true },
                onDisplayModeChanged = { displayMode = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Main Content
            when {
                uiState.isLoading -> {
                    LoadingContent(modifier = Modifier.weight(1f))
                }
                
                uiState.error != null -> {
                    ErrorContent(
                        message = uiState.error!!,
                        onRetry = viewModel::retry,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                uiState.shows.isEmpty() -> {
                    EmptyLibraryContent(
                        onPopulateTestData = viewModel::populateTestData,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                else -> {
                    // Apply filtering and sorting
                    val filteredAndSortedShows = remember(uiState.shows, filterPath, sortBy, sortDirection) {
                        applyFiltersAndSorting(
                            shows = uiState.shows,
                            filterPath = filterPath,
                            sortBy = sortBy,
                            sortDirection = sortDirection
                        )
                    }
                    
                    LibraryContent(
                        shows = filteredAndSortedShows,
                        displayMode = displayMode,
                        onShowClick = onNavigateToShow,
                        onPlayClick = onNavigateToPlayer,
                        onShowLongPress = { show -> selectedShowForActions = show },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    
    // Bottom Sheets
    if (showAddBottomSheet) {
        AddToLibraryBottomSheet(
            onDismiss = { showAddBottomSheet = false }
        )
    }
    
    if (showSortBottomSheet) {
        SortOptionsBottomSheet(
            currentSortOption = sortBy,
            currentSortDirection = sortDirection,
            onSortOptionSelected = { option, direction ->
                sortBy = option
                sortDirection = direction
                showSortBottomSheet = false
            },
            onDismiss = { showSortBottomSheet = false }
        )
    }
    
    selectedShowForActions?.let { show ->
        ShowActionsBottomSheet(
            show = show,
            onDismiss = { selectedShowForActions = null },
            onShare = { 
                viewModel.shareShow(show.showId)
                selectedShowForActions = null
            },
            onRemoveFromLibrary = { 
                viewModel.removeFromLibrary(show.showId)
                selectedShowForActions = null
            },
            onDownload = {
                viewModel.downloadShow(show.showId)
                selectedShowForActions = null
            },
            onRemoveDownload = {
                viewModel.cancelDownload(show.showId)
                selectedShowForActions = null
            },
            onPin = {
                viewModel.pinShow(show.showId)
                selectedShowForActions = null
            },
            onUnpin = {
                viewModel.unpinShow(show.showId)
                selectedShowForActions = null
            }
        )
    }
}

/**
 * Main library content with list or grid display
 */
@Composable
private fun LibraryContent(
    shows: List<LibraryShowViewModel>,
    displayMode: LibraryDisplayMode,
    onShowClick: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onShowLongPress: (LibraryShowViewModel) -> Unit,
    modifier: Modifier = Modifier
) {
    when (displayMode) {
        LibraryDisplayMode.LIST -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shows) { show ->
                    LibraryShowListItem(
                        show = show,
                        onClick = { onShowClick(show.showId) },
                        onLongPress = { onShowLongPress(show) }
                    )
                }
            }
        }
        LibraryDisplayMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shows) { show ->
                    LibraryShowGridItem(
                        show = show,
                        onClick = { onShowClick(show.showId) },
                        onLongPress = { onShowLongPress(show) }
                    )
                }
            }
        }
    }
}

/**
 * Loading state content
 */
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading your library...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state content
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error Loading Library",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/**
 * Empty library content with test data option
 */
@Composable
private fun EmptyLibraryContent(
    onPopulateTestData: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your Library is Empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Add some shows to get started. In development mode, use \"Populate Test Data\" to load realistic test data.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onPopulateTestData,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Populate Test Data")
        }
    }
}

/**
 * Apply filtering and sorting to the shows list (with pin priority)
 */
private fun applyFiltersAndSorting(
    shows: List<LibraryShowViewModel>,
    filterPath: FilterPath,
    sortBy: LibrarySortOption,
    sortDirection: LibrarySortDirection
): List<LibraryShowViewModel> {
    // Apply filtering first
    val filteredShows = if (filterPath.isEmpty) {
        shows
    } else {
        applyHierarchicalFiltering(shows, filterPath)
    }
    
    // Then apply sorting (with pin priority)
    return when (sortBy) {
        LibrarySortOption.DATE_OF_SHOW -> {
            if (sortDirection == LibrarySortDirection.ASCENDING) {
                filteredShows.sortedWith(compareBy<LibraryShowViewModel> { !it.isPinned }.thenBy { it.date })
            } else {
                filteredShows.sortedWith(compareBy<LibraryShowViewModel> { !it.isPinned }.thenByDescending { it.date })
            }
        }
        LibrarySortOption.DATE_ADDED -> {
            if (sortDirection == LibrarySortDirection.ASCENDING) {
                filteredShows.sortedWith(compareBy<LibraryShowViewModel> { !it.isPinned }.thenBy { it.addedToLibraryAt })
            } else {
                filteredShows.sortedWith(compareBy<LibraryShowViewModel> { !it.isPinned }.thenByDescending { it.addedToLibraryAt })
            }
        }
        LibrarySortOption.VENUE -> {
            if (sortDirection == LibrarySortDirection.ASCENDING) {
                filteredShows.sortedWith(compareBy<LibraryShowViewModel> { !it.isPinned }.thenBy { it.venue })
            } else {
                filteredShows.sortedWith(compareBy<LibraryShowViewModel> { !it.isPinned }.thenByDescending { it.venue })
            }
        }
        LibrarySortOption.RATING -> {
            if (sortDirection == LibrarySortDirection.ASCENDING) {
                filteredShows.sortedWith(compareBy<LibraryShowViewModel> { !it.isPinned }.thenBy { it.rating ?: 0f })
            } else {
                filteredShows.sortedWith(compareBy<LibraryShowViewModel> { !it.isPinned }.thenByDescending { it.rating ?: 0f })
            }
        }
    }
}

/**
 * Apply hierarchical filtering based on decade and season
 */
private fun applyHierarchicalFiltering(
    shows: List<LibraryShowViewModel>,
    filterPath: FilterPath
): List<LibraryShowViewModel> {
    val selectedDecadeNode = filterPath.nodes.firstOrNull()
    val selectedSeasonNode = filterPath.nodes.getOrNull(1) // Second level is season
    
    return if (selectedDecadeNode != null) {
        shows.filter { show ->
            // Parse year from show data
            val year = show.date.substring(0, 4).toIntOrNull() ?: 0
            
            // First filter by decade
            val decadeMatches = when (selectedDecadeNode.id) {
                "60s" -> year in 1960..1969
                "70s" -> year in 1970..1979
                "80s" -> year in 1980..1989
                "90s" -> year in 1990..1999
                else -> true // Show all if unknown decade
            }
            
            // If decade matches and we have a season filter, also check season
            if (decadeMatches && selectedSeasonNode != null) {
                val month = extractMonthFromDate(show.date)
                if (month != null) {
                    when (selectedSeasonNode.id.substringAfter("_")) { // Extract season from ID like "70s_spring"
                        "spring" -> month in 3..5   // March, April, May
                        "summer" -> month in 6..8   // June, July, August  
                        "fall" -> month in 9..11    // September, October, November
                        "winter" -> month == 12 || month in 1..2  // December, January, February
                        else -> true
                    }
                } else {
                    true // If we can't parse month, include the show
                }
            } else {
                decadeMatches
            }
        }
    } else {
        shows
    }
}

/**
 * Extract month number from date string (YYYY-MM-DD format)
 */
private fun extractMonthFromDate(date: String): Int? {
    return try {
        val parts = date.split("-")
        if (parts.size >= 2) {
            parts[1].toIntOrNull()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}