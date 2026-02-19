package com.grateful.deadly.feature.playlist.screens.main

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.grateful.deadly.core.model.LibraryDownloadStatus
import com.grateful.deadly.core.design.component.QrCodeDisplay
import com.grateful.deadly.feature.playlist.screens.main.models.PlaylistViewModel
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
    var showQrCode by remember { mutableStateOf(false) }
    val isOffline by viewModel.isOffline.collectAsState()

    // Load show data when screen opens - include recordingId for Player→Playlist navigation
    LaunchedEffect(showId, recordingId) {
        viewModel.loadShow(showId, recordingId)
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
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
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
                    if (uiState.trackData.isEmpty() && isOffline) {
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
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "You're offline",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Only downloaded shows are available while offline.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (uiState.showData?.recordingCount == 0) {
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
                onShareClick = { viewModel.shareShow() },
                onShowQrCode = { showQrCode = true },
                onChooseRecordingClick = viewModel::chooseRecording,
                onDismiss = viewModel::hideMenu
            )
        }
    }

    // QR Code Display
    if (showQrCode) {
        uiState.showData?.let { showData ->
            val url = if (showData.currentRecordingId != null) {
                "https://share.thedeadly.app/show/${showData.showId}/recording/${showData.currentRecordingId}"
            } else {
                "https://share.thedeadly.app/show/${showData.showId}"
            }
            QrCodeDisplay(
                url = url,
                showDate = showData.displayDate,
                venue = showData.venue,
                location = showData.location,
                recordingId = showData.currentRecordingId,
                coverImageUrl = showData.coverImageUrl,
                onDismiss = { showQrCode = false }
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
    
    // Recording Change + Download Conflict Dialog
    if (uiState.showDownloadConflictDialog) {
        val conflictMessage = when (uiState.showData?.downloadStatus) {
            LibraryDownloadStatus.COMPLETED -> "This show is downloaded with a different recording. Switching will remove the download."
            LibraryDownloadStatus.PAUSED -> "This show has a paused download for a different recording. Switching will remove it."
            else -> "This show is being downloaded with a different recording. Switching will cancel and remove it."
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissDownloadConflictDialog,
            title = { Text("Switch Recording?") },
            text = { Text(conflictMessage) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRecordingChange) {
                    Text("Switch Recording")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDownloadConflictDialog) {
                    Text("Cancel")
                }
            }
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