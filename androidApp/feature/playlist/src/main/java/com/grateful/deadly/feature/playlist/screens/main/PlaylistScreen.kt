package com.grateful.deadly.feature.playlist.screens.main

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.debug.DebugActivator
import com.grateful.deadly.core.design.component.debug.DebugBottomSheet
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistHeader
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistAlbumArt
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistShowInfo
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistInteractiveRating
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistActionRow
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistTrackList
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistReviewDetailsSheet
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistMenuSheet
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistRecordingSelectionSheet
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistCollectionsSheet
import com.grateful.deadly.feature.playlist.screens.main.components.PlaylistSetlistBottomSheet
import com.grateful.deadly.feature.playlist.screens.main.models.PlaylistViewModel
import com.grateful.deadly.core.design.component.debug.DebugData
import com.grateful.deadly.core.design.component.debug.DebugSection
import com.grateful.deadly.core.design.component.debug.DebugItem

/**
 * PlaylistScreen - Clean playlist interface
 * 
 * Playlist screen using Architecture patterns with
 * focused components and clean service integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToShow: (String, String) -> Unit = { _, _ -> },
    onNavigateToCollection: (String, String) -> Unit = { _, _ -> }, // collectionId, showId
    recordingId: String? = null,
    showId: String? = null,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    Log.d("PlaylistScreen", "=== PLAYLIST SCREEN LOADED === recordingId: $recordingId, showId: $showId")

    val uiState by viewModel.uiState.collectAsState()
    // Debug mode hardcoded to true for development
    val showDebugInfo = true
    
    // Load show data when screen opens - include recordingId for Player→Playlist navigation
    LaunchedEffect(showId, recordingId) {
        viewModel.loadShow(showId, recordingId)
    }
    
    // Debug panel state - only when debug mode is enabled
    var showDebugPanel by remember { mutableStateOf(false) }
    val debugData = if (showDebugInfo) {
        DebugData(
            screenName = "PlaylistScreen",
            sections = listOf(
                DebugSection(
                    title = "Navigation Parameters",
                    items = listOf(
                        DebugItem.KeyValue("showId", showId ?: "null"),
                        DebugItem.KeyValue("recordingId", recordingId ?: "null")
                    )
                ),
                DebugSection(
                    title = "UI State",
                    items = listOf(
                        DebugItem.BooleanValue("isLoading", uiState.isLoading),
                        DebugItem.KeyValue("error", uiState.error ?: "none"),
                        DebugItem.KeyValue("showData", if (uiState.showData != null) "loaded" else "null"),
                        DebugItem.NumericValue("tracks", uiState.trackData.size, " tracks")
                    )
                ),
                DebugSection(
                    title = "Debug Info",
                    items = listOf(
                        DebugItem.Timestamp("Screen loaded", System.currentTimeMillis()),
                        DebugItem.KeyValue("Architecture", "Compose")
                    )
                )
            )
        )
    } else {
        null
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        
        // Back arrow overlay at the top
        PlaylistHeader(
            onNavigateBack = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        
        // Main content - Spotify-style LazyColumn
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                
                uiState.error != null -> {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Error: ${uiState.error}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = { viewModel.loadShow(showId, recordingId) }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                
                else -> {
                    // Album cover image - fixed size at top
                    item {
                        PlaylistAlbumArt(
                            recordingId = uiState.showData?.currentRecordingId ?: recordingId,
                            imageUrl = uiState.showData?.coverImageUrl
                        )
                    }
                    
                    // Show info section - with navigation buttons
                    uiState.showData?.let { showData ->
                        item {
                            PlaylistShowInfo(
                                showData = showData,
                                // Navigation always enabled for responsive UX
                                onPreviousShow = viewModel::navigateToPreviousShow,
                                onNextShow = viewModel::navigateToNextShow
                            )
                        }
                        
                        // Interactive rating display - always show
                        item {
                            PlaylistInteractiveRating(
                                showData = showData,
                                onShowReviews = viewModel::showReviews,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                        
                        // Action buttons row
                        item {
                            PlaylistActionRow(
                                showData = showData,
                                isPlaying = uiState.isPlaying,
                                isLoading = uiState.mediaLoading,
                                isCurrentShowAndRecording = uiState.isCurrentShowAndRecording,
                                showCollections = uiState.showCollections,
                                onLibraryAction = viewModel::handleLibraryAction,
                                onDownload = { viewModel.downloadShow() },
                                onShowSetlist = viewModel::showSetlist,
                                onShowCollections = viewModel::showCollectionsSheet,
                                onShowMenu = viewModel::showMenu,
                                onTogglePlayback = viewModel::togglePlayback
                            )
                        }
                    }
                    
                    // Track list with progressive loading
                    if (uiState.showData?.recordingCount == 0) {
                        // No recordings available — show explicit message
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "No recordings available for this show",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "This concert was played but no audio recordings are known to exist.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (uiState.isTrackListLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = "Loading tracks...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        PlaylistTrackList(
                            tracks = uiState.trackData,
                            onPlayClick = viewModel::playTrack,
                            onDownloadClick = viewModel::downloadTrack
                        )
                    }
                }
            }
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
    
    // Review Details Modal
    if (uiState.showReviewDetails) {
        PlaylistReviewDetailsSheet(
            showData = uiState.showData,
            reviews = uiState.reviews,
            ratingDistribution = uiState.ratingDistribution,
            isLoading = uiState.reviewsLoading,
            errorMessage = uiState.reviewsError,
            onDismiss = viewModel::hideReviewDetails
        )
    }
    
    // Menu Bottom Sheet
    if (uiState.showMenu) {
        uiState.showData?.let { showData ->
            PlaylistMenuSheet(
                showDate = showData.displayDate,
                venue = showData.venue,
                location = showData.location,
                onShareClick = { 
                    viewModel.shareShow()
                },
                onChooseRecordingClick = viewModel::chooseRecording,
                onDismiss = viewModel::hideMenu
            )
        }
    }
    
    // Recording Selection Modal
    if (uiState.recordingSelection.isVisible) {
        PlaylistRecordingSelectionSheet(
            state = uiState.recordingSelection,
            onRecordingSelected = viewModel::selectRecording,
            onSetAsDefault = viewModel::setRecordingAsDefault,
            onResetToRecommended = if (uiState.recordingSelection.hasRecommended) {
                { viewModel.resetToRecommended() }
            } else null,
            onDismiss = viewModel::hideRecordingSelection
        )
    }
    
    // Collections Sheet
    if (uiState.showCollectionsSheet) {
        PlaylistCollectionsSheet(
            collections = uiState.showCollections,
            showTitle = uiState.showData?.displayDate ?: "Unknown Show",
            isVisible = uiState.showCollectionsSheet,
            onNavigateToCollection = { collectionId, showId ->
                onNavigateToCollection(collectionId, showId)
                viewModel.hideCollectionsSheet()
            },
            onDismiss = viewModel::hideCollectionsSheet
        )
    }
    
    // Remove Download Confirmation Dialog
    if (uiState.showRemoveDownloadDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveDownloadDialog,
            title = { Text("Remove Download") },
            text = { Text("Remove all downloaded files for this show? You can re-download them later.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRemoveDownload) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveDownloadDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Setlist Modal
    if (uiState.showSetlistModal) {
        PlaylistSetlistBottomSheet(
            setlistData = viewModel.getCurrentSetlistData(),
            isLoading = uiState.setlistLoading,
            errorMessage = uiState.setlistError,
            onDismiss = viewModel::hideSetlistModal
        )
    }
}