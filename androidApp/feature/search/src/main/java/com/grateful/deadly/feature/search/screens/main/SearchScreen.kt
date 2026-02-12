package com.grateful.deadly.feature.search.screens.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.debug.DebugActivator
import com.grateful.deadly.core.design.component.debug.DebugBottomSheet
import com.grateful.deadly.core.design.component.debug.DebugData
import com.grateful.deadly.core.design.component.debug.DebugSection
import com.grateful.deadly.core.design.component.debug.DebugItem
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.feature.search.screens.main.models.SearchViewModel
import com.grateful.deadly.core.model.SearchUiState

// Data classes for UI components
data class DecadeBrowse(
    val title: String,
    val gradient: List<Color>,
    val era: String
)

/**
 * SearchScreen - Next-generation search and discovery interface
 * 
 * This is the V2 implementation of the search/browse experience following
 * the V2 architecture pattern. Built using UI-first development methodology
 * where the UI drives the discovery of service requirements.
 * 
 * Architecture:
 * - Material3 design system with Search-specific enhancements
 * - Debug integration following PlayerV2 patterns
 * - Feature flag enabled foundation ready for UI development
 * - Clean navigation callbacks matching V1 interface
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
    val debounceDelayMs by viewModel.debounceDelayMs.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val refreshCounter by viewModel.refreshCounter.collectAsState()
    
    // Debug panel state - hard-coded to true for V2
    var showDebugPanel by remember { mutableStateOf(false) }
    
    // QR Scanner coming soon dialog state
    var showQrComingSoonDialog by remember { mutableStateOf(false) }
    
    // Simplified debug data
    val debugData = collectSearchDebugData(
        uiState = uiState,
        initialEra = initialEra,
        debounceDelayMs = debounceDelayMs,
        onDebounceDelayChange = viewModel::updateDebounceDelay
    )
    
    // Simple content layout with debug overlay like HomeScreen and SettingsScreen
    Box(modifier = Modifier.fillMaxSize()) {
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

        // Debug activator overlay (always enabled in V2)
        DebugActivator(
            isVisible = true,
            onClick = { showDebugPanel = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
    
    // Debug bottom sheet with search controls
    SearchDebugBottomSheet(
        debugData = debugData,
        isVisible = showDebugPanel,
        onDismiss = { showDebugPanel = false },
        debounceDelayMs = debounceDelayMs,
        onDebounceDelayChange = viewModel::updateDebounceDelay
    )
    
    // QR Scanner coming soon dialog
    if (showQrComingSoonDialog) {
        QrScannerComingSoonDialog(
            onDismiss = { showQrComingSoonDialog = false }
        )
    }
}

/**
 * Collect debug data for SearchScreen
 * Following the established PlayerV2 debug data pattern
 */
@Composable
private fun collectSearchDebugData(
    uiState: SearchUiState,
    initialEra: String?,
    debounceDelayMs: Long,
    onDebounceDelayChange: (Long) -> Unit
): DebugData {
    return DebugData(
        screenName = "SearchScreen",
        sections = listOf(
            DebugSection(
                title = "Search State",
                items = listOf(
                    DebugItem.KeyValue("Is Loading", uiState.isLoading.toString()),
                    DebugItem.KeyValue("Error State", uiState.error ?: "None"),
                    DebugItem.KeyValue("Initial Era", initialEra ?: "None"),
                    DebugItem.KeyValue("Feature Flag", "useSearchV2 = true"),
                    DebugItem.KeyValue("Scaffold Mode", "Pure Content (MainNavigation AppScaffold)")
                )
            ),
            DebugSection(
                title = "Search Settings",
                items = listOf(
                    DebugItem.NumericValue("Debounce Delay", debounceDelayMs, "ms"),
                    DebugItem.KeyValue("Test Delay", "Tap buttons below to test different delays")
                )
            ),
            DebugSection(
                title = "Development Status",
                items = listOf(
                    DebugItem.KeyValue("Implementation", "Foundation Complete"),
                    DebugItem.KeyValue("UI State", "Basic scaffold ready"),
                    DebugItem.KeyValue("Navigation", "Feature flag routing active"),
                    DebugItem.KeyValue("Next Phase", "UI-first development"),
                    DebugItem.KeyValue("Navigation", "Integrated with MainNavigation scaffold system")
                )
            )
        )
    )
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

/**
 * QR Scanner Coming Soon Dialog
 * Shows when user taps camera icon before QrScannerV2 is implemented
 */
@Composable
private fun QrScannerComingSoonDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "QR Scanner",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "QR code scanning is coming soon!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "This feature will let you scan QR codes to instantly discover and play Grateful Dead recordings shared by other users.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        icon = {
            Icon(
                painter = IconResources.Content.QrCodeScanner(),
                contentDescription = "QR Code Scanner",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    )
}

/**
 * Enhanced debug bottom sheet for SearchScreen with debounce controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDebugBottomSheet(
    debugData: DebugData,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    debounceDelayMs: Long,
    onDebounceDelayChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            // Custom drag handle with debug indicator
            Surface(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(width = 32.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = Color(0xFFFF5722) // Debug red color
            ) {}
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🐛 Search Debug Panel",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5722)
                        )
                        Text(
                            text = "SearchScreen • ${debugData.getTotalItemCount()} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Debounce delay slider control
            item {
                DebounceDelayControl(
                    currentDelayMs = debounceDelayMs,
                    onDelayChange = onDebounceDelayChange
                )
            }
            
            // Standard debug data sections
            items(debugData.sections) { section ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        section.items.forEach { item ->
                            when (item) {
                                is DebugItem.KeyValue -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.key}:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.4f)
                                        )
                                        Text(
                                            text = item.value,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(0.6f)
                                        )
                                    }
                                }
                                is DebugItem.NumericValue -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.key}:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.4f)
                                        )
                                        Text(
                                            text = "${item.value}${item.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(0.6f)
                                        )
                                    }
                                }
                                is DebugItem.BooleanValue -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.key}:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.4f)
                                        )
                                        Text(
                                            text = if (item.value) "✅ ${item.value}" else "❌ ${item.value}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(0.6f)
                                        )
                                    }
                                }
                                is DebugItem.Multiline,
                                is DebugItem.JsonData,
                                is DebugItem.Error,
                                is DebugItem.Timestamp -> {
                                    // For other types, just show key-value as text
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.toFormattedText(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Debounce delay control with slider and preset buttons
 */
@Composable
private fun DebounceDelayControl(
    currentDelayMs: Long,
    onDelayChange: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF5722).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "⚡ Debounce Delay Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF5722)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Current value display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Delay:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${currentDelayMs}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFF5722)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Slider
            Slider(
                value = currentDelayMs.toFloat(),
                onValueChange = { onDelayChange(it.toLong()) },
                valueRange = 0f..2000f,
                steps = 19, // 100ms increments
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF5722),
                    activeTrackColor = Color(0xFFFF5722)
                )
            )
            
            // Range labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "2000ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Preset buttons
            Text(
                text = "Quick Presets:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(0L, 300L, 800L, 1000L)
                presets.forEach { preset ->
                    val isSelected = currentDelayMs == preset
                    OutlinedButton(
                        onClick = { onDelayChange(preset) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Color(0xFFFF5722).copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = if (isSelected) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            1.dp, 
                            if (isSelected) Color(0xFFFF5722) else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(
                            text = "${preset}ms",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}