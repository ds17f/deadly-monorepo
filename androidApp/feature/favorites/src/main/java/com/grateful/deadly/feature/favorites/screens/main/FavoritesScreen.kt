package com.grateful.deadly.feature.favorites.screens.main

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
import com.grateful.deadly.core.design.resources.IconResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.HierarchicalFilter
import com.grateful.deadly.core.design.component.FilterPath
import com.grateful.deadly.core.design.component.FilterTrees
import com.grateful.deadly.core.model.*
import com.grateful.deadly.core.design.component.QrCodeDisplay
import com.grateful.deadly.core.design.component.ShowReviewSheet
import com.grateful.deadly.core.model.ShowReview
import com.grateful.deadly.feature.favorites.screens.main.components.*
import com.grateful.deadly.feature.favorites.screens.main.models.FavoritesViewModel

/**
 * Favorites Screen - Main favorites interface
 *
 * Features:
 * - Shows/Songs tab switching
 * - Hierarchical decade/season filtering (applies to both tabs)
 * - Advanced sorting with pin priority
 * - List/grid display modes (shows only)
 * - Favorites management actions
 * - Real-time download status integration
 *
 * Note: Scaffold-free content designed for use within MainNavigation's AppScaffold.
 * TopBar configuration handled by FavoritesBarConfiguration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onNavigateToShow: (String) -> Unit = {},
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToPlaylist: (showId: String, recordingId: String?, trackNumber: Int?, autoPlay: Boolean) -> Unit = { _, _, _, _ -> },
    onNavigateToFavorites: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val songsLoading by viewModel.songsLoading.collectAsState()

    // UI State
    var selectedTab by remember { mutableStateOf(FavoritesTab.SHOWS) }
    var filterPath by remember { mutableStateOf(FilterPath()) }
    var sortBy by remember { mutableStateOf(FavoritesSortOption.DATE_ADDED) }
    var songSortBy by remember { mutableStateOf(FavoritesSongSortOption.DATE_ADDED) }
    var sortDirection by remember { mutableStateOf(FavoritesSortDirection.DESCENDING) }
    val displayMode by viewModel.displayMode.collectAsState()
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showSongSortBottomSheet by remember { mutableStateOf(false) }
    var selectedShowForActions by remember { mutableStateOf<FavoriteShowViewModel?>(null) }
    var qrCodeShow by remember { mutableStateOf<FavoriteShowViewModel?>(null) }
    var reviewShowTarget by remember { mutableStateOf<FavoriteShowViewModel?>(null) }
    val reviewState by viewModel.currentReview.collectAsState()

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

            // Tab Picker
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                FavoritesTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = FavoritesTab.entries.size
                        )
                    ) {
                        Text(tab.displayName)
                    }
                }
            }

            // Sort Controls and Display Toggle
            if (selectedTab == FavoritesTab.SHOWS) {
                SortAndDisplayControls(
                    sortBy = sortBy,
                    sortDirection = sortDirection,
                    displayMode = displayMode,
                    onSortSelectorClick = { showSortBottomSheet = true },
                    onDisplayModeChanged = { viewModel.setDisplayMode(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                SongSortControls(
                    sortBy = songSortBy,
                    sortDirection = sortDirection,
                    onSortSelectorClick = { showSongSortBottomSheet = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Main Content
            if (selectedTab == FavoritesTab.SHOWS) {
                ShowsTabContent(
                    uiState = uiState,
                    filterPath = filterPath,
                    sortBy = sortBy,
                    sortDirection = sortDirection,
                    displayMode = displayMode,
                    onShowClick = onNavigateToShow,
                    onPlayClick = onNavigateToPlayer,
                    onShowLongPress = { show -> selectedShowForActions = show },
                    onRetry = viewModel::retry,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SongsTabContent(
                    songs = favoriteSongs,
                    isLoading = songsLoading,
                    filterPath = filterPath,
                    sortBy = songSortBy,
                    sortDirection = sortDirection,
                    onSongClick = { track ->
                        onNavigateToPlaylist(
                            track.showId,
                            track.recordingId,
                            track.trackNumber,
                            true
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

    // Bottom Sheets
    if (showAddBottomSheet) {
        AddToFavoritesBottomSheet(
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

    if (showSongSortBottomSheet) {
        SongSortOptionsBottomSheet(
            currentSortOption = songSortBy,
            currentSortDirection = sortDirection,
            onSortOptionSelected = { option, direction ->
                songSortBy = option
                sortDirection = direction
                showSongSortBottomSheet = false
            },
            onDismiss = { showSongSortBottomSheet = false }
        )
    }

    selectedShowForActions?.let { show ->
        ShowActionsBottomSheet(
            show = show,
            onDismiss = { selectedShowForActions = null },
            onShowQrCode = {
                qrCodeShow = show
                selectedShowForActions = null
            },
            onReviewShow = {
                viewModel.loadReview(show.showId)
                reviewShowTarget = show
                selectedShowForActions = null
            },
            onRemoveFromFavorites = {
                viewModel.removeFromFavorites(show.showId)
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

    reviewShowTarget?.let { show ->
        ShowReviewSheet(
            showDate = show.displayDate,
            venue = show.venue,
            location = show.location,
            review = reviewState,
            lineupMembers = show.lineupMembers,
            onSave = { notes, rating, recQuality, playQuality, standouts ->
                viewModel.saveReview(show.showId, notes, rating, recQuality, playQuality, standouts)
            },
            onDismiss = { reviewShowTarget = null }
        )
    }

    qrCodeShow?.let { show ->
        val url = if (show.bestRecordingId != null) {
            "https://share.thedeadly.app/show/${show.showId}/recording/${show.bestRecordingId}"
        } else {
            "https://share.thedeadly.app/show/${show.showId}"
        }
        QrCodeDisplay(
            url = url,
            showDate = show.displayDate,
            venue = show.venue,
            location = show.location,
            recordingId = show.bestRecordingId,
            coverImageUrl = show.coverImageUrl,
            onDismiss = { qrCodeShow = null }
        )
    }
}

/**
 * Shows tab content
 */
@Composable
private fun ShowsTabContent(
    uiState: FavoritesUiState,
    filterPath: FilterPath,
    sortBy: FavoritesSortOption,
    sortDirection: FavoritesSortDirection,
    displayMode: FavoritesDisplayMode,
    onShowClick: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onShowLongPress: (FavoriteShowViewModel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> {
            LoadingContent(modifier = modifier)
        }
        uiState.error != null -> {
            ErrorContent(
                message = uiState.error!!,
                onRetry = onRetry,
                modifier = modifier
            )
        }
        uiState.shows.isEmpty() -> {
            EmptyContent(
                title = "No Favorite Shows",
                message = "Import your favorites from a previous backup or add shows manually.",
                modifier = modifier
            )
        }
        else -> {
            val filteredAndSortedShows = remember(uiState.shows, filterPath, sortBy, sortDirection) {
                applyFiltersAndSorting(
                    shows = uiState.shows,
                    filterPath = filterPath,
                    sortBy = sortBy,
                    sortDirection = sortDirection
                )
            }

            if (filteredAndSortedShows.isEmpty()) {
                EmptyContent(
                    title = "No Matching Shows",
                    message = "Try adjusting the filter.",
                    modifier = modifier
                )
            } else {
                FavoritesContent(
                    shows = filteredAndSortedShows,
                    displayMode = displayMode,
                    onShowClick = onShowClick,
                    onPlayClick = onPlayClick,
                    onShowLongPress = onShowLongPress,
                    modifier = modifier
                )
            }
        }
    }
}

/**
 * Songs tab content
 */
@Composable
private fun SongsTabContent(
    songs: List<FavoriteTrack>,
    isLoading: Boolean,
    filterPath: FilterPath,
    sortBy: FavoritesSongSortOption,
    sortDirection: FavoritesSortDirection,
    onSongClick: (FavoriteTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            LoadingContent(modifier = modifier)
        }
        songs.isEmpty() -> {
            EmptyContent(
                title = "No Favorite Songs",
                message = "Favorite songs while listening to build your collection.",
                modifier = modifier
            )
        }
        else -> {
            val filteredAndSortedSongs = remember(songs, filterPath, sortBy, sortDirection) {
                applyFiltersAndSortingSongs(
                    songs = songs,
                    filterPath = filterPath,
                    sortBy = sortBy,
                    sortDirection = sortDirection
                )
            }

            if (filteredAndSortedSongs.isEmpty()) {
                EmptyContent(
                    title = "No Matching Songs",
                    message = "Try adjusting the filter.",
                    modifier = modifier
                )
            } else {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filteredAndSortedSongs, key = { "${it.showId}_${it.trackTitle}_${it.recordingId}" }) { track ->
                        FavoriteSongListItem(
                            track = track,
                            onClick = { onSongClick(track) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Song sort controls (no display mode toggle)
 */
@Composable
private fun SongSortControls(
    sortBy: FavoritesSongSortOption,
    sortDirection: FavoritesSortDirection,
    onSortSelectorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onSortSelectorClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = IconResources.Navigation.SwapVert(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = sortBy.displayName)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = if (sortDirection == FavoritesSortDirection.ASCENDING) {
                        IconResources.Navigation.KeyboardArrowUp()
                    } else {
                        IconResources.Navigation.KeyboardArrowDown()
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Song sort options bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongSortOptionsBottomSheet(
    currentSortOption: FavoritesSongSortOption,
    currentSortDirection: FavoritesSortDirection,
    onSortOptionSelected: (FavoritesSongSortOption, FavoritesSortDirection) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Sort By",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            FavoritesSongSortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSortOption == option,
                        onClick = {
                            onSortOptionSelected(option, currentSortDirection)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = option.displayName)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Direction",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FavoritesSortDirection.entries.forEach { direction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSortDirection == direction,
                        onClick = {
                            onSortOptionSelected(currentSortOption, direction)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = direction.displayName)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Main favorites content with list or grid display
 */
@Composable
private fun FavoritesContent(
    shows: List<FavoriteShowViewModel>,
    displayMode: FavoritesDisplayMode,
    onShowClick: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onShowLongPress: (FavoriteShowViewModel) -> Unit,
    modifier: Modifier = Modifier
) {
    when (displayMode) {
        FavoritesDisplayMode.LIST -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shows) { show ->
                    FavoriteShowListItem(
                        show = show,
                        onClick = { onShowClick(show.showId) },
                        onLongPress = { onShowLongPress(show) }
                    )
                }
            }
        }
        FavoritesDisplayMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shows) { show ->
                    FavoriteShowGridItem(
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
                text = "Loading your favorites...",
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
            text = "Error Loading Favorites",
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
 * Empty content (used for both tabs)
 */
@Composable
private fun EmptyContent(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Apply filtering and sorting to the shows list (with pin priority)
 */
private fun applyFiltersAndSorting(
    shows: List<FavoriteShowViewModel>,
    filterPath: FilterPath,
    sortBy: FavoritesSortOption,
    sortDirection: FavoritesSortDirection
): List<FavoriteShowViewModel> {
    // Apply filtering first
    val filteredShows = if (filterPath.isEmpty) {
        shows
    } else {
        applyHierarchicalFiltering(shows, filterPath)
    }

    // Then apply sorting (with pin priority)
    return when (sortBy) {
        FavoritesSortOption.DATE_OF_SHOW -> {
            if (sortDirection == FavoritesSortDirection.ASCENDING) {
                filteredShows.sortedWith(compareBy<FavoriteShowViewModel> { !it.isPinned }.thenBy { it.date })
            } else {
                filteredShows.sortedWith(compareBy<FavoriteShowViewModel> { !it.isPinned }.thenByDescending { it.date })
            }
        }
        FavoritesSortOption.DATE_ADDED -> {
            if (sortDirection == FavoritesSortDirection.ASCENDING) {
                filteredShows.sortedWith(compareBy<FavoriteShowViewModel> { !it.isPinned }.thenBy { it.addedToFavoritesAt })
            } else {
                filteredShows.sortedWith(compareBy<FavoriteShowViewModel> { !it.isPinned }.thenByDescending { it.addedToFavoritesAt })
            }
        }
        FavoritesSortOption.VENUE -> {
            if (sortDirection == FavoritesSortDirection.ASCENDING) {
                filteredShows.sortedWith(compareBy<FavoriteShowViewModel> { !it.isPinned }.thenBy { it.venue })
            } else {
                filteredShows.sortedWith(compareBy<FavoriteShowViewModel> { !it.isPinned }.thenByDescending { it.venue })
            }
        }
        FavoritesSortOption.RATING -> {
            if (sortDirection == FavoritesSortDirection.ASCENDING) {
                filteredShows.sortedWith(compareBy<FavoriteShowViewModel> { !it.isPinned }.thenBy { it.rating ?: 0f })
            } else {
                filteredShows.sortedWith(compareBy<FavoriteShowViewModel> { !it.isPinned }.thenByDescending { it.rating ?: 0f })
            }
        }
    }
}

/**
 * Apply filtering and sorting to songs
 */
private fun applyFiltersAndSortingSongs(
    songs: List<FavoriteTrack>,
    filterPath: FilterPath,
    sortBy: FavoritesSongSortOption,
    sortDirection: FavoritesSortDirection
): List<FavoriteTrack> {
    val filtered = if (filterPath.isEmpty) {
        songs
    } else {
        applyHierarchicalFilteringSongs(songs, filterPath)
    }

    val comparator: Comparator<FavoriteTrack> = when (sortBy) {
        FavoritesSongSortOption.SONG_TITLE -> compareBy { it.trackTitle }
        FavoritesSongSortOption.SHOW_DATE -> compareBy { it.showDate }
        FavoritesSongSortOption.DATE_ADDED -> compareBy { it.addedAt }
    }

    return if (sortDirection == FavoritesSortDirection.ASCENDING) {
        filtered.sortedWith(comparator)
    } else {
        filtered.sortedWith(comparator.reversed())
    }
}

/**
 * Apply hierarchical filtering based on decade and season
 */
private fun applyHierarchicalFiltering(
    shows: List<FavoriteShowViewModel>,
    filterPath: FilterPath
): List<FavoriteShowViewModel> {
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
 * Apply hierarchical filtering to songs based on their show date
 */
private fun applyHierarchicalFilteringSongs(
    songs: List<FavoriteTrack>,
    filterPath: FilterPath
): List<FavoriteTrack> {
    val selectedDecadeNode = filterPath.nodes.firstOrNull()
    val selectedSeasonNode = filterPath.nodes.getOrNull(1)

    return if (selectedDecadeNode != null) {
        songs.filter { track ->
            val year = track.showDate.substring(0, 4).toIntOrNull() ?: 0
            val decadeMatches = when (selectedDecadeNode.id) {
                "60s" -> year in 1960..1969
                "70s" -> year in 1970..1979
                "80s" -> year in 1980..1989
                "90s" -> year in 1990..1999
                else -> true
            }
            if (decadeMatches && selectedSeasonNode != null) {
                val month = extractMonthFromDate(track.showDate)
                if (month != null) {
                    when (selectedSeasonNode.id.substringAfter("_")) {
                        "spring" -> month in 3..5
                        "summer" -> month in 6..8
                        "fall" -> month in 9..11
                        "winter" -> month == 12 || month in 1..2
                        else -> true
                    }
                } else true
            } else decadeMatches
        }
    } else songs
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
