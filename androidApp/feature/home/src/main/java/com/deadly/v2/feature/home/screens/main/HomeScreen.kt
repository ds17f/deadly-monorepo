package com.deadly.v2.feature.home.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deadly.v2.core.design.component.debug.DebugActivator
import com.deadly.v2.core.design.component.debug.DebugBottomSheet
import com.deadly.v2.feature.home.screens.main.components.HorizontalCollection
import com.deadly.v2.feature.home.screens.main.components.HorizontalCollectionItem
import com.deadly.v2.feature.home.screens.main.components.CollectionItemType
import com.deadly.v2.feature.home.screens.main.components.RecentShowsGrid
import com.deadly.v2.feature.home.screens.main.components.collectHomeDebugData

/**
 * HomeScreen - Rich home interface with content discovery
 * 
 * V2 implementation featuring:
 * - Recent Shows Grid (2x4 layout)
 * - Today In Grateful Dead History (horizontal scroll)
 * - Featured Collections (horizontal scroll)
 * - Debug integration for development
 * 
 * Scaffold-free content designed for use within AppScaffold.
 * Follows V2 architecture with single HomeService dependency.
 */
@Composable
fun HomeScreen(
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToShow: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToCollection: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Debug panel state - always enabled in V2
    var showDebugPanel by remember { mutableStateOf(false) }
    
    // Debug data collection
    val debugData = collectHomeDebugData(
        uiState = uiState,
        onRefresh = viewModel::refresh
    )
    
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Recent Shows Grid Section - only show if there are recent shows
            if (uiState.homeContent.recentShows.isNotEmpty()) {
                item {
                    RecentShowsGrid(
                        shows = uiState.homeContent.recentShows,
                        onShowClick = onNavigateToShow,
                        onShowLongPress = { show -> 
                            // TODO: Implement context menu
                        }
                    )
                }
            }
            
            // Today In Grateful Dead History Section
            item {
                val todayItems = uiState.homeContent.todayInHistory.map { show ->
                    HorizontalCollectionItem(
                        id = show.id,
                        displayText = "${show.date}\n${show.venue.name}\n${show.location.displayText}",
                        type = CollectionItemType.SHOW
                    )
                }
                
                HorizontalCollection(
                    title = "Today In Grateful Dead History",
                    items = todayItems,
                    onItemClick = { item ->
                        // Find the show and navigate
                        val show = uiState.homeContent.todayInHistory.find { it.id == item.id }
                        show?.let { onNavigateToShow(it.id) }
                    }
                )
            }
            
            // Featured Collections Section
            item {
                val collectionItems = uiState.homeContent.featuredCollections.map { collection ->
                    HorizontalCollectionItem(
                        id = collection.id,
                        displayText = "${collection.name}\n${collection.showCountText}",
                        type = CollectionItemType.COLLECTION
                    )
                }
                
                HorizontalCollection(
                    title = "Featured Collections",
                    items = collectionItems,
                    onItemClick = { item ->
                        onNavigateToCollection(item.id)
                    }
                )
            }
        }
        
        // Debug activator button (always visible in V2)
        DebugActivator(
            isVisible = true,
            onClick = { showDebugPanel = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
    
    // Debug Bottom Sheet
    DebugBottomSheet(
        debugData = debugData,
        isVisible = showDebugPanel,
        onDismiss = { showDebugPanel = false }
    )
}

