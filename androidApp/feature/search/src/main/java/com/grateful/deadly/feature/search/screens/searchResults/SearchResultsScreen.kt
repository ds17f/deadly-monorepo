package com.grateful.deadly.feature.search.screens.searchResults

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.feature.search.screens.main.models.SearchViewModel
import com.grateful.deadly.core.model.*

/**
 * SearchResultsScreen - Full-screen search interface
 * 
 * This screen provides a comprehensive search experience with:
 * - Search input with back navigation
 * - Recent searches for quick access
 * - Suggested search terms
 * - Search results with card layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    initialQuery: String = "",
    onNavigateBack: () -> Unit,
    onNavigateToShow: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Sort state (local to composable, resets on new search session)
    var sortBy by remember { mutableStateOf(SearchSortOption.RELEVANCE) }
    var sortDirection by remember { mutableStateOf(SearchSortDirection.DESCENDING) }

    // Apply sorting to results
    val sortedResults = remember(uiState.searchResults, sortBy, sortDirection) {
        applySorting(uiState.searchResults, sortBy, sortDirection)
    }

    // Pre-fill search when navigating from browse buttons
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            viewModel.onSearchQueryChanged(initialQuery)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Fixed top bar with back arrow and search input (like Spotify)
        SearchResultsTopBar(
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = viewModel::onSearchQueryChanged,
            onNavigateBack = onNavigateBack
        )

        // Pinned results header with title, count, and sort control
        if (uiState.searchQuery.isNotEmpty() &&
            uiState.searchStatus == SearchStatus.SUCCESS &&
            uiState.searchResults.isNotEmpty()
        ) {
            var showSortSheet by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search Results (${uiState.searchStats.totalResults})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                SearchSortButton(
                    sortBy = sortBy,
                    sortDirection = sortDirection,
                    onClick = { showSortSheet = true }
                )
            }

            if (showSortSheet) {
                SearchSortBottomSheet(
                    currentSortOption = sortBy,
                    currentSortDirection = sortDirection,
                    onSortOptionSelected = { option, direction ->
                        sortBy = option
                        sortDirection = direction
                        showSortSheet = false
                    },
                    onDismiss = { showSortSheet = false }
                )
            }
        }

        // Search content that scrolls underneath the fixed header
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.searchQuery.isEmpty()) {
                // Show recent searches when no query
                item {
                    RecentSearchesSection(
                        recentSearches = uiState.recentSearches,
                        onSearchSelected = { recentSearch ->
                            viewModel.onRecentSearchSelected(recentSearch)
                        }
                    )
                }
            } else {
                // Show suggestions and results when typing
                item {
                    SuggestedSearchesSection(
                        suggestedSearches = uiState.suggestedSearches,
                        onSuggestionSelected = { suggestion ->
                            viewModel.onSuggestionSelected(suggestion)
                        }
                    )
                }

                item {
                    SearchResultsSection(
                        searchResults = sortedResults,
                        searchStatus = uiState.searchStatus,
                        onShowSelected = onNavigateToShow
                    )
                }
            }
        }
    }
}

/**
 * Top bar with back arrow and search input
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultsTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Manage TextFieldValue state for proper cursor positioning
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    
    // Update textFieldValue when searchQuery changes from external sources (like suggestions)
    LaunchedEffect(searchQuery) {
        if (textFieldValue.text != searchQuery) {
            textFieldValue = TextFieldValue(
                text = searchQuery,
                selection = TextRange(searchQuery.length) // Place cursor at end
            )
        }
    }
    
    // Auto-focus when this composable is first shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
                //.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Back arrow
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            //Spacer(modifier = Modifier.width(12.dp))
            
            // Search input - transparent background, compact vertical design with clear button
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onSearchQueryChange(newValue.text)
                },
                placeholder = { 
                    Text(
                        text = "What do you want to listen to?",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                // No leading icon (removed magnifying glass)
                trailingIcon = if (textFieldValue.text.isNotEmpty()) {
                    @Composable {
                        IconButton(
                            onClick = { 
                                textFieldValue = TextFieldValue()
                                onSearchQueryChange("")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = (-8).dp) // Move the entire text field 8dp to the left
                    .focusRequester(focusRequester), // Auto-focus support
                singleLine = true,
                // More compact shape
                shape = RoundedCornerShape(8.dp),
                // Compact text style to fit in reduced height
                textStyle = MaterialTheme.typography.bodyMedium,//.copy(fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    // Transparent/invisible background - user types directly into the interface
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    // Invisible borders
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    // Visible text
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password// or .Ascii, .Password, etc.
                )
            )
        }
    }
}

/**
 * Recent searches section
 */
@Composable
private fun RecentSearchesSection(
    recentSearches: List<RecentSearch>,
    onSearchSelected: (RecentSearch) -> Unit
) {
    if (recentSearches.isNotEmpty()) {
        Column {
            Text(
                text = "Recent Searches",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            recentSearches.forEach { recentSearch ->
                RecentSearchCard(
                    search = recentSearch,
                    onClick = { onSearchSelected(recentSearch) }
                )
            }
        }
    }
}

/**
 * Suggested searches section
 */
@Composable
private fun SuggestedSearchesSection(
    suggestedSearches: List<SuggestedSearch>,
    onSuggestionSelected: (SuggestedSearch) -> Unit
) {
    if (suggestedSearches.isNotEmpty()) {
        Column {
            Text(
                text = "Suggested Searches",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            suggestedSearches.forEach { suggestion ->
                SuggestedSearchCard(
                    suggestion = suggestion,
                    onClick = { onSuggestionSelected(suggestion) }
                )
            }
        }
    }
}

/**
 * Search results section with card layout
 */
@Composable
private fun SearchResultsSection(
    searchResults: List<SearchResultShow>,
    searchStatus: SearchStatus,
    onShowSelected: (String) -> Unit
) {
    Column {
        when (searchStatus) {
            SearchStatus.SEARCHING -> {
                Text(
                    text = "Searching...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            SearchStatus.NO_RESULTS -> {
                Text(
                    text = "No results found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            SearchStatus.ERROR -> {
                Text(
                    text = "Search failed. Please try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            SearchStatus.SUCCESS -> {
                // Search results using card layout
                searchResults.forEach { result ->
                    SearchResultCard(
                        searchResult = result,
                        onShowSelected = onShowSelected,
                        onShowLongPress = { show ->
                            // TODO: Implement show actions bottom sheet
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            SearchStatus.IDLE -> {
                // Show nothing when idle
            }
        }
    }
}

/**
 * Search result card component
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SearchResultCard(
    searchResult: SearchResultShow,
    onShowSelected: (String) -> Unit,
    onShowLongPress: (String) -> Unit
) {
    val hasRecordings = searchResult.show.recordingCount > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onShowSelected(searchResult.show.id) },
                onLongClick = { onShowLongPress(searchResult.show.id) }
            )
            .then(if (!hasRecordings) Modifier.alpha(0.5f) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album art
            ShowArtwork(
                recordingId = searchResult.show.bestRecordingId,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                imageUrl = searchResult.show.coverImageUrl
            )
            
            // Text content column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Status indicators row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Match type indicator
                    SearchMatchIndicator(matchType = searchResult.matchType)
                    
                    // Download indicator (if available)
                    if (searchResult.hasDownloads) {
                        Icon(
                            painter = IconResources.Status.CheckCircle(),
                            contentDescription = "Has downloads",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    // Date and location line
                    Text(
                        text = "${searchResult.show.date} • ${searchResult.show.location.displayText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Venue line
                Text(
                    text = searchResult.show.venue.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!hasRecordings) {
                    Text(
                        text = "No recordings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }
    }
}

/**
 * Search match type indicator
 */
@Composable
private fun SearchMatchIndicator(matchType: SearchMatchType) {
    val (icon, color, description) = when (matchType) {
        SearchMatchType.TITLE -> Triple(IconResources.Content.Search(), MaterialTheme.colorScheme.primary, "Title match")
        SearchMatchType.VENUE -> Triple(IconResources.Content.Search(), MaterialTheme.colorScheme.secondary, "Venue match")
        SearchMatchType.YEAR -> Triple(IconResources.Content.Search(), MaterialTheme.colorScheme.tertiary, "Year match")
        SearchMatchType.SETLIST -> Triple(IconResources.Content.Search(), MaterialTheme.colorScheme.error, "Setlist match")
        SearchMatchType.LOCATION -> Triple(IconResources.Content.Search(), MaterialTheme.colorScheme.secondary, "Location match")
        SearchMatchType.GENERAL -> Triple(IconResources.Content.Search(), MaterialTheme.colorScheme.onSurfaceVariant, "Generic match")
    }
    
    Icon(
        painter = icon,
        contentDescription = description,
        modifier = Modifier.size(12.dp),
        tint = color
    )
    Spacer(modifier = Modifier.width(4.dp))
}


/**
 * Sort selector button for search results
 */
@Composable
private fun SearchSortButton(
    sortBy: SearchSortOption,
    sortDirection: SearchSortDirection,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
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
                painter = if (sortDirection == SearchSortDirection.ASCENDING) {
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

/**
 * Bottom sheet for selecting search sort option and direction
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSortBottomSheet(
    currentSortOption: SearchSortOption,
    currentSortDirection: SearchSortDirection,
    onSortOptionSelected: (SearchSortOption, SearchSortDirection) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Sort results by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Sort option rows — one radio per option
            SearchSortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val direction = defaultDirectionFor(option)
                            onSortOptionSelected(option, direction)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSortOption == option,
                        onClick = {
                            val direction = defaultDirectionFor(option)
                            onSortOptionSelected(option, direction)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Direction toggle
            Text(
                text = "Direction",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            SearchSortDirection.entries.forEach { direction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSortOptionSelected(currentSortOption, direction)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSortDirection == direction,
                        onClick = {
                            onSortOptionSelected(currentSortOption, direction)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = direction.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun defaultDirectionFor(option: SearchSortOption): SearchSortDirection {
    return when (option) {
        SearchSortOption.VENUE, SearchSortOption.STATE -> SearchSortDirection.ASCENDING
        else -> SearchSortDirection.DESCENDING
    }
}

/**
 * Apply sorting to search results
 */
private fun applySorting(
    results: List<SearchResultShow>,
    sortBy: SearchSortOption,
    sortDirection: SearchSortDirection
): List<SearchResultShow> {
    if (results.isEmpty()) return results

    return when (sortBy) {
        SearchSortOption.RELEVANCE -> {
            if (sortDirection == SearchSortDirection.DESCENDING)
                results.sortedByDescending { it.relevanceScore }
            else
                results.sortedBy { it.relevanceScore }
        }
        SearchSortOption.DATE_OF_SHOW -> {
            if (sortDirection == SearchSortDirection.DESCENDING)
                results.sortedByDescending { it.show.date }
            else
                results.sortedBy { it.show.date }
        }
        SearchSortOption.VENUE -> {
            if (sortDirection == SearchSortDirection.ASCENDING)
                results.sortedBy { it.show.venue.name }
            else
                results.sortedByDescending { it.show.venue.name }
        }
        SearchSortOption.STATE -> {
            if (sortDirection == SearchSortDirection.ASCENDING)
                results.sortedBy { it.show.location.state ?: "" }
            else
                results.sortedByDescending { it.show.location.state ?: "" }
        }
        SearchSortOption.RATING -> {
            if (sortDirection == SearchSortDirection.DESCENDING)
                results.sortedByDescending { it.show.averageRating ?: 0f }
            else
                results.sortedBy { it.show.averageRating ?: 0f }
        }
    }
}

/**
 * Recent search card component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentSearchCard(
    search: RecentSearch,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search icon in grey circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = search.query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Suggested search card component with fill text arrow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedSearchCard(
    suggestion: SuggestedSearch,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Magnifying glass in grey circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = suggestion.query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Arrow pointing up to fill text
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Fill search text",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
