package com.grateful.deadly.feature.search.screens.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.feature.search.screens.main.models.SearchViewModel

// Data classes for UI components
data class DecadeBrowse(
    val title: String,
    val gradient: List<Color>,
    val era: String
)

/**
 * SearchScreen - Next-generation search and discovery interface
 * 
 * This is the search/browse experience following
 * the service architecture pattern. Built using UI-first development methodology
 * where the UI drives the discovery of service requirements.
 * 
 * Architecture:
 * - Material3 design system with Search-specific enhancements
 * - Debug integration following Player patterns
 * - Feature flag enabled foundation ready for UI development
 * - Clean navigation callbacks
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToShow: (String) -> Unit,
    onNavigateToSearchResults: (String) -> Unit,
    initialEra: String? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshCounter by viewModel.refreshCounter.collectAsState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refreshShortcuts,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 2: Search box
            item {
                SearchSearchBox(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChanged,
                    onFocusReceived = { onNavigateToSearchResults("") },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Row 3 & 4: Browse by decades
            item {
                SearchBrowseSection(
                    onDecadeClick = { era -> onNavigateToSearchResults(era) }
                )
            }

            // Row 5 & 6: Discover section (3 random shortcuts)
            item {
                SearchDiscoverSection(
                    refreshCounter = refreshCounter,
                    onDiscoverClick = { shortcut -> onNavigateToSearchResults(shortcut.searchQuery) }
                )
            }

            // Row 7 & 8: Browse All section
            item {
                SearchBrowseAllSection(
                    refreshCounter = refreshCounter,
                    onBrowseAllClick = { shortcut -> onNavigateToSearchResults(shortcut.searchQuery) }
                )
            }
        }
    }
}

/**
 * Row 2: Search box with search icon and placeholder text
 */
@Composable
private fun SearchSearchBox(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFocusReceived: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Navigate to search results when field receives focus
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocusReceived()
        }
    }
    
    OutlinedTextField(
        value = "", // Always empty so placeholder always shows
        onValueChange = { /* No-op - this is just a button */ },
        interactionSource = interactionSource,
        readOnly = true, // Make it clear this isn't for typing
        placeholder = { 
            Text(
                text = "What do you want to listen to?",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = Color.Black,
                modifier = Modifier.size(28.dp)
            )
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        //shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * Rows 3 & 4: Browse section with 2x2 decade grid.
 * Uses plain Column/Row instead of LazyVerticalGrid to avoid nesting lazy containers.
 */
@Composable
private fun SearchBrowseSection(
    onDecadeClick: (String) -> Unit
) {
    val decades = listOf(
        DecadeBrowse("1960s", listOf(Color(0xFF1976D2), Color(0xFF42A5F5)), "196*"),
        DecadeBrowse("1970s", listOf(Color(0xFF388E3C), Color(0xFF66BB6A)), "197*"),
        DecadeBrowse("1980s", listOf(Color(0xFFD32F2F), Color(0xFFEF5350)), "198*"),
        DecadeBrowse("1990s", listOf(Color(0xFF7B1FA2), Color(0xFFAB47BC)), "199*")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "By Decade",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        decades.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { decade ->
                    DecadeCard(
                        decade = decade,
                        onClick = { onDecadeClick(decade.era) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Individual decade card component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecadeCard(
    decade: DecadeBrowse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(decade.gradient),
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Background alt logo image (right justified)
            Image(
                painter = painterResource(com.grateful.deadly.core.design.R.drawable.alt_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                alpha = 0.3f
            )
            
            // Decade text
            Text(
                text = decade.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Rows 5 & 6: Discover section — 3 shortcuts, rotated every 4 hours.
 *
 * Uses a deterministic shuffle seeded by wall-clock time bucketed into 4-hour windows.
 * Same seed = same shuffle order, so the cards stay stable across recompositions,
 * navigation, and even app restarts within the same window. After 4 hours the seed
 * changes and users see a fresh set.
 */
@Composable
private fun SearchDiscoverSection(
    refreshCounter: Int = 0,
    onDiscoverClick: (SearchShortcut) -> Unit
) {
    val discoverItems = remember(refreshCounter) {
        val seed = System.currentTimeMillis() / (4 * 60 * 60 * 1000L)
        allSearchShortcuts.shuffled(Random(seed + refreshCounter)).take(3)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Discover Something New",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            discoverItems.forEach { shortcut ->
                DiscoverCard(
                    shortcut = shortcut,
                    onClick = { onDiscoverClick(shortcut) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// Gradient palette for Discover cards (cycles through these)
private val discoverGradients = listOf(
    listOf(Color(0xFF1976D2), Color(0xFF42A5F5)),
    listOf(Color(0xFF388E3C), Color(0xFF66BB6A)),
    listOf(Color(0xFFD32F2F), Color(0xFFEF5350)),
    listOf(Color(0xFF7B1FA2), Color(0xFFAB47BC)),
    listOf(Color(0xFFE64A19), Color(0xFFFF7043)),
    listOf(Color(0xFF00796B), Color(0xFF26A69A)),
)

/**
 * Individual discover card — tall design with gradient + title
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverCard(
    shortcut: SearchShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientIndex = remember(shortcut.title) {
        shortcut.title.hashCode().and(0x7fffffff) % discoverGradients.size
    }
    val gradient = discoverGradients[gradientIndex]

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(gradient),
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Background alt logo
            Image(
                painter = painterResource(com.grateful.deadly.core.design.R.drawable.alt_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                alpha = 0.2f
            )

            // Title + subtitle at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = shortcut.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (shortcut.subtitle.isNotBlank()) {
                    Text(
                        text = shortcut.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Rows 7 & 8: Browse All section — 8 shortcuts drawn from the catalog, rotated every 4 hours.
 *
 * Uses the same time-based deterministic shuffle as Discover, but with a seed offset of +1
 * so the two sections rotate independently. Same seed = same 8 cards across recompositions,
 * navigation, and app restarts within the same 4-hour window.
 * Uses plain Column/Row instead of LazyVerticalGrid to avoid nesting lazy containers.
 */
@Composable
private fun SearchBrowseAllSection(
    refreshCounter: Int = 0,
    onBrowseAllClick: (SearchShortcut) -> Unit
) {
    val browseAllItems = remember(refreshCounter) {
        val seed = System.currentTimeMillis() / (4 * 60 * 60 * 1000L)
        allSearchShortcuts
            .filter { it.priority >= 5 }
            .shuffled(Random(seed + 1 + refreshCounter))
            .take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Browse All",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        browseAllItems.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { shortcut ->
                    BrowseAllCard(
                        shortcut = shortcut,
                        onClick = { onBrowseAllClick(shortcut) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Individual browse all card — title/subtitle on primaryContainer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseAllCard(
    shortcut: SearchShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = shortcut.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = shortcut.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
