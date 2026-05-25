package com.grateful.deadly.feature.home.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.ShowDetailBottomSheet
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.feature.home.screens.main.components.HorizontalCollection
import com.grateful.deadly.feature.home.screens.main.components.HorizontalCollectionItem
import com.grateful.deadly.feature.home.screens.main.components.CollectionItemType
import com.grateful.deadly.feature.home.screens.main.components.RecentShowsGrid
import com.grateful.deadly.feature.home.screens.main.components.TrendingNowSection

/**
 * HomeScreen - Rich home interface with content discovery
 *
 * Implementation featuring:
 * - Recent Shows Grid (2x4 layout)
 * - Today In Grateful Dead History (horizontal scroll)
 * - Featured Collections (horizontal scroll)
 *
 * Scaffold-free content designed for use within AppScaffold.
 * Follows architecture with single HomeService dependency.
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
    var detailShow by remember { mutableStateOf<Show?>(null) }

    detailShow?.let { show ->
        ShowDetailBottomSheet(
            date = show.date,
            venue = show.venue.name,
            location = show.location.displayText,
            rating = if (show.hasRating) show.displayRating else null,
            onDismiss = { detailShow = null }
        )
    }

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
                    rows = uiState.homeContent.recentRows,
                    onShowClick = onNavigateToShow,
                    onShowLongPress = { show ->
                        detailShow = show
                    }
                )
            }
        }

        // Trending + Today In History — order toggled by the
        // "Show trending above Today" preference.
        val trendingItem: @Composable () -> Unit = {
            TrendingNowSection(
                shows = uiState.homeContent.trendingShows,
                window = uiState.homeContent.trendingWindow,
                onShowClick = { show -> onNavigateToShow(show.id) },
                onCycleWindow = { viewModel.cycleTrendingWindow() },
            )
        }
        val todayItem: @Composable () -> Unit = {
            val todayItems = uiState.homeContent.todayInHistory.map { show ->
                HorizontalCollectionItem(
                    id = show.id,
                    displayText = "${show.date}\n${show.venue.name}\n${show.location.displayText}",
                    type = CollectionItemType.SHOW,
                    recordingId = show.bestRecordingId,
                    imageUrl = show.coverImageUrl
                )
            }

            HorizontalCollection(
                title = "Today In Grateful Dead History",
                items = todayItems,
                onItemClick = { item ->
                    val show = uiState.homeContent.todayInHistory.find { it.id == item.id }
                    show?.let { onNavigateToShow(it.id) }
                }
            )
        }
        if (uiState.homeContent.trendingAboveToday) {
            item { trendingItem() }
            item { todayItem() }
        } else {
            item { todayItem() }
            item { trendingItem() }
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
}
