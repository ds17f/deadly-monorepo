package com.deadly.v2.feature.player.screens.main

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.deadly.v2.core.design.component.debug.DebugActivator
import com.deadly.v2.core.design.component.debug.DebugBottomSheet
import com.deadly.v2.core.design.component.debug.DebugData
import com.deadly.v2.core.design.component.debug.DebugSection
import com.deadly.v2.core.design.component.debug.DebugItem
import com.deadly.v2.feature.player.screens.main.components.PlayerTopBar
import com.deadly.v2.feature.player.screens.main.components.PlayerCoverArt
import com.deadly.v2.feature.player.screens.main.components.PlayerTrackInfoRow
import com.deadly.v2.feature.player.screens.main.components.PlayerProgressControl
import com.deadly.v2.feature.player.screens.main.components.PlayerEnhancedControls
import com.deadly.v2.feature.player.screens.main.components.PlayerSecondaryControls
import com.deadly.v2.feature.player.screens.main.components.PlayerMaterialPanels
import com.deadly.v2.feature.player.screens.main.components.PlayerTrackActionsSheet
import com.deadly.v2.feature.player.screens.main.components.PlayerConnectSheet
import com.deadly.v2.feature.player.screens.main.components.PlayerQueueSheet
import com.deadly.v2.feature.player.screens.main.components.PlayerMiniPlayer
import com.deadly.v2.feature.player.screens.main.components.RepeatMode
import com.deadly.v2.feature.player.screens.main.models.PlayerViewModel

/**
 * UI Color Generation Utilities for Recording-Based Gradients
 * 
 * These utilities generate consistent visual identities per recording
 * by hashing recordingId to select from the Grateful Dead color palette.
 */

// Grateful Dead inspired color palette for gradients (from Theme.kt)
private val DeadRed = Color(0xFFDC143C)      // Crimson red
private val DeadGold = Color(0xFFFFD700)     // Golden yellow  
private val DeadGreen = Color(0xFF228B22)    // Forest green
private val DeadBlue = Color(0xFF4169E1)     // Royal blue
private val DeadPurple = Color(0xFF8A2BE2)   // Blue violet

private val GradientColors = listOf(DeadGreen, DeadGold, DeadRed, DeadBlue, DeadPurple)

/**
 * Convert recordingId to a consistent base color using hash function
 */
private fun recordingIdToColor(recordingId: String?): Color {
    if (recordingId.isNullOrEmpty()) return DeadRed
    
    val hash = recordingId.hashCode()
    val index = kotlin.math.abs(hash) % GradientColors.size
    return GradientColors[index]
}

/**
 * Get the complete color stack for a recording
 * Returns list of solid colors that can be used by different components consistently
 * Uses color blending instead of alpha transparency for better UI visibility
 */
@Composable
private fun getRecordingColorStack(recordingId: String?): List<Color> {
    val baseColor = recordingIdToColor(recordingId)
    val background = MaterialTheme.colorScheme.background
    
    return listOf(
        androidx.compose.ui.graphics.lerp(background, baseColor, 0.8f),  // Index 0: Strong blend
        androidx.compose.ui.graphics.lerp(background, baseColor, 0.4f),  // Index 1: Medium blend  
        androidx.compose.ui.graphics.lerp(background, baseColor, 0.1f),  // Index 2: Faint blend
        background,                                                      // Index 3: Background
        background                                                       // Index 4: Background
    )
}

/**
 * Create a beautiful vertical gradient brush for the given recordingId
 * Uses alpha transparency to maintain readability and Material3 compatibility
 */
@Composable
private fun createRecordingGradient(recordingId: String?): Brush {
    val colors = getRecordingColorStack(recordingId)
    
    return Brush.verticalGradient(
        0f to colors[0],      // Strong color at top
        0.3f to colors[1],    // Medium color at 30%
        0.6f to colors[2],    // Faint color at 60%
        0.8f to colors[3],    // Background at 80%
        1f to colors[4]       // Full background at bottom
    )
}

/**
 * PlayerScreen - Clean V2 player interface
 * 
 * Full-screen immersive player experience that fits within AppScaffold.
 * AppScaffold is configured to hide topBar, bottomBar, and miniPlayer for this route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlaylist: (String, String?) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    Log.d("PlayerScreen", "=== V2 PLAYER SCREEN LOADED ===")
    
    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    
    // For now we'll use a default recordingId for gradients - will be dynamic later
    val recordingId = "default-recording"
    
    // Scroll state for mini player detection
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Bottom sheet state
    var showTrackActionsBottomSheet by remember { mutableStateOf(false) }
    var showConnectBottomSheet by remember { mutableStateOf(false) }
    var showQueueBottomSheet by remember { mutableStateOf(false) }
    
    // Debug mode hardcoded to true for v2 development
    val showDebugInfo = true
    
    // Debug panel state - only when debug mode is enabled
    var showDebugPanel by remember { mutableStateOf(false) }
    
    // Mini player visibility based on scroll position
    // Show mini player only when player controls are completely off screen
    val showMiniPlayer by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0 || 
            (scrollState.firstVisibleItemIndex == 0 && scrollState.firstVisibleItemScrollOffset > 1200)
        }
    }
    
    // Debug data collection - collect MediaMetadata info for inspection
    var debugMetadata by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    val debugData = if (showDebugInfo) {
        LaunchedEffect(uiState) {
            debugMetadata = viewModel.getDebugMetadata()
        }
        
        DebugData(
            screenName = "V2PlayerScreen",
            sections = listOf(
                DebugSection(
                    title = "Player State",
                    items = listOf(
                        DebugItem.BooleanValue("isPlaying", uiState.isPlaying),
                        DebugItem.BooleanValue("isLoading", uiState.isLoading),
                        DebugItem.BooleanValue("hasNext", uiState.hasNext),
                        DebugItem.BooleanValue("hasPrevious", uiState.hasPrevious),
                        DebugItem.KeyValue("currentPosition", uiState.progressDisplayInfo.currentPosition),
                        DebugItem.KeyValue("totalDuration", uiState.progressDisplayInfo.totalDuration),
                        DebugItem.NumericValue("progress", (uiState.progressDisplayInfo.progressPercentage * 100).toInt(), "%")
                    )
                ),
                DebugSection(
                    title = "Track Info (from UI State)",
                    items = listOf(
                        DebugItem.KeyValue("title", uiState.trackDisplayInfo.title),
                        DebugItem.KeyValue("artist", uiState.trackDisplayInfo.artist),
                        DebugItem.KeyValue("album", uiState.trackDisplayInfo.album),
                        DebugItem.KeyValue("duration", uiState.trackDisplayInfo.duration),
                        DebugItem.KeyValue("error", uiState.error ?: "none")
                    )
                ),
                DebugSection(
                    title = "MediaMetadata Core Fields",
                    items = debugMetadata.filter { (key, _) ->
                        listOf("title", "artist", "albumTitle", "albumArtist", "genre", "trackNumber",
                               "totalTrackCount", "recordingYear", "releaseYear", "writer", "composer", 
                               "conductor", "discNumber", "totalDiscCount").contains(key)
                    }.map { (key, value) ->
                        DebugItem.KeyValue(key, value ?: "null")
                    }
                ),
                DebugSection(
                    title = "Custom Extras (Grateful Dead Data)",
                    items = debugMetadata.filter { (key, _) ->
                        listOf("trackUrl", "recordingId", "showId", "showDate", "venue", "location", "filename").contains(key)
                    }.map { (key, value) ->
                        DebugItem.KeyValue(key, value ?: "null")
                    }
                ),
                DebugSection(
                    title = "Media URIs & IDs",
                    items = debugMetadata.filter { (key, _) ->
                        listOf("artworkUri", "extrasKeys").contains(key)
                    }.map { (key, value) ->
                        DebugItem.KeyValue(key, value ?: "null")
                    }
                ),
                DebugSection(
                    title = "All Other Fields",
                    items = debugMetadata.filter { (key, _) ->
                        !listOf("title", "artist", "albumTitle", "albumArtist", "genre", "trackNumber",
                               "totalTrackCount", "recordingYear", "releaseYear", "writer", "composer",
                               "conductor", "discNumber", "totalDiscCount", "trackUrl", "recordingId", 
                               "showId", "showDate", "venue", "location", "filename", 
                               "artworkUri", "extrasKeys").contains(key)
                    }.map { (key, value) ->
                        DebugItem.KeyValue(key, value ?: "null")
                    }
                )
            ).filter { it.items.isNotEmpty() } // Only show sections that have data
        )
    } else { null }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrollable content with gradient as part of the scrolling items
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Gradient section containing top navigation, cover art, track info, progress, and controls
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(createRecordingGradient(recordingId))
                ) {
                    Column {
                        // Top navigation bar
                        PlayerTopBar(
                            contextText = "Playing from Show", // TODO: Make dynamic
                            onNavigateBack = onNavigateBack,
                            onMoreOptionsClick = { showTrackActionsBottomSheet = true },
                            onContextClick = {
                                // Navigate to playlist with current show and recording
                                val showId = uiState.navigationInfo.showId
                                val recordingId = uiState.navigationInfo.recordingId
                                if (showId != null) {
                                    onNavigateToPlaylist(showId, recordingId)
                                }
                            },
                            recordingId = recordingId,
                        )
                        
                        // Large cover art section with generous vertical padding
                        PlayerCoverArt(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp) // V1 exact height
                                .padding(horizontal = 24.dp)
                        )
                        
                        // Track information with add to playlist button
                        PlayerTrackInfoRow(
                            trackTitle = uiState.trackDisplayInfo.title,
                            showDate = uiState.trackDisplayInfo.showDate,
                            venue = uiState.trackDisplayInfo.venue,
                            onAddToPlaylist = {
                                // TODO: Show snackbar "Playlists are coming soon"
                            },
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        // Progress control section  
                        PlayerProgressControl(
                            currentTime = uiState.progressDisplayInfo.currentPosition,
                            totalTime = uiState.progressDisplayInfo.totalDuration,
                            progress = uiState.progressDisplayInfo.progressPercentage,
                            onSeek = viewModel::onSeek,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        // Enhanced primary controls row
                        PlayerEnhancedControls(
                            isPlaying = uiState.isPlaying,
                            isLoading = uiState.isLoading,
                            shuffleEnabled = false, // TODO: Make dynamic
                            repeatMode = RepeatMode.NONE, // TODO: Make dynamic
                            hasNext = uiState.hasNext,
                            onPlayPause = viewModel::onPlayPauseClicked,
                            onPrevious = viewModel::onPreviousClicked,
                            onNext = viewModel::onNextClicked,
                            onShuffleToggle = { /* TODO */ },
                            onRepeatModeChange = { /* TODO */ },
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
            
            // Secondary controls row (updated for queue sheet)
            item {
                PlayerSecondaryControls(
                    onConnectClick = { showConnectBottomSheet = true },
                    onShareClick = { viewModel.onShareClicked() },
                    onQueueClick = { showQueueBottomSheet = true },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
            
            // Extended content as Material panels - let gradient show through
            item {
                PlayerMaterialPanels(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            
            // Bottom padding for last item
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        // Bottom Sheets
        if (showTrackActionsBottomSheet) {
            PlayerTrackActionsSheet(
                trackTitle = uiState.trackDisplayInfo.title,
                showDate = uiState.trackDisplayInfo.showDate,
                venue = uiState.trackDisplayInfo.venue,
                onDismiss = { showTrackActionsBottomSheet = false },
                onShare = { /* TODO: Share track */ },
                onAddToPlaylist = { /* TODO: Add to playlist */ },
                onDownload = { /* TODO: Download track */ }
            )
        }
        
        if (showConnectBottomSheet) {
            PlayerConnectSheet(
                onDismiss = { showConnectBottomSheet = false }
            )
        }
        
        if (showQueueBottomSheet) {
            PlayerQueueSheet(
                onDismiss = { showQueueBottomSheet = false }
            )
        }
        
        // Mini Player overlay when scrolled
        if (showMiniPlayer) {
            PlayerMiniPlayer(
                uiState = uiState,
                recordingId = recordingId,
                onPlayPause = viewModel::onPlayPauseClicked,
                onTapToExpand = {
                    // Use a coroutine scope to handle the scroll
                    coroutineScope.launch {
                        scrollState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
        }
        
        // Debug activator button (bottom-right when debug enabled)
        if (showDebugInfo && debugData != null) {
            DebugActivator(
                isVisible = true,
                onClick = { showDebugPanel = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
    
    // Debug bottom sheet
    if (showDebugPanel && debugData != null) {
        DebugBottomSheet(
            debugData = debugData,
            isVisible = showDebugPanel,
            onDismiss = { showDebugPanel = false }
        )
    }
}