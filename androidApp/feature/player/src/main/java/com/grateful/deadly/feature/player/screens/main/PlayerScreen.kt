package com.grateful.deadly.feature.player.screens.main

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.feature.player.screens.main.components.PlayerTopBar
import com.grateful.deadly.feature.player.screens.main.components.PlayerCoverArt
import com.grateful.deadly.feature.player.screens.main.components.PlayerTrackInfoRow
import com.grateful.deadly.feature.player.screens.main.components.PlayerProgressControl
import com.grateful.deadly.feature.player.screens.main.components.PlayerEnhancedControls
import com.grateful.deadly.feature.player.screens.main.components.PlayerSecondaryControls
import com.grateful.deadly.feature.player.screens.main.components.PlayerMaterialPanels
import com.grateful.deadly.feature.player.screens.main.components.PlayerTrackActionsSheet
import com.grateful.deadly.feature.player.screens.main.components.PlayerQueueSheet
import com.grateful.deadly.feature.player.screens.main.components.PlayerEqualizerSheet
import com.grateful.deadly.feature.player.screens.main.components.PlayerMiniPlayer

import com.grateful.deadly.core.design.component.QrCodeDisplay
import com.grateful.deadly.core.design.component.ShareChooserSheet
import com.grateful.deadly.feature.player.screens.main.models.PlayerViewModel
import com.grateful.deadly.feature.settings.screens.connect.ConnectSheet

/**
 * PlayerScreen - Clean player interface
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
    Log.d("PlayerScreen", "=== PLAYER SCREEN LOADED ===")

    val context = LocalContext.current

    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val panelState by viewModel.panelState.collectAsState()
    val isCurrentTrackFavorite by viewModel.isCurrentTrackFavorite.collectAsState()
    val equalizerState by viewModel.equalizerState.collectAsState()
    val connectRemoteDeviceName by viewModel.connectRemoteDeviceName.collectAsState()

    val recordingId = uiState.navigationInfo.recordingId

    // Scroll state for mini player detection
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Bottom sheet state
    var showTrackActionsBottomSheet by remember { mutableStateOf(false) }
    var showQrCode by remember { mutableStateOf(false) }
    var showShareChooser by remember { mutableStateOf(false) }
    var showEqualizerBottomSheet by remember { mutableStateOf(false) }
    var showQueueBottomSheet by remember { mutableStateOf(false) }
    var showConnectSheet by remember { mutableStateOf(false) }
    // Mini player visibility based on scroll position
    // Show mini player only when player controls are completely off screen
    val showMiniPlayer by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0 ||
            (scrollState.firstVisibleItemIndex == 0 && scrollState.firstVisibleItemScrollOffset > 1200)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrollable content with gradient as part of the scrolling items
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
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
                            recordingId = uiState.trackDisplayInfo.recordingId,
                            imageUrl = uiState.trackDisplayInfo.coverImageUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp)
                                .padding(horizontal = 24.dp)
                        )

                        // Track information with add to playlist button
                        PlayerTrackInfoRow(
                            trackTitle = uiState.trackDisplayInfo.title,
                            showDate = uiState.trackDisplayInfo.showDate,
                            venue = uiState.trackDisplayInfo.venue,
                            onAddToPlaylist = {
                                Toast.makeText(context, "Playlists are coming soon", Toast.LENGTH_SHORT).show()
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
                            hasNext = uiState.hasNext,
                            onPlayPause = viewModel::onPlayPauseClicked,
                            onPrevious = viewModel::onPreviousClicked,
                            onNext = viewModel::onNextClicked,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }

            // Secondary controls row (updated for queue sheet)
            item {
                PlayerSecondaryControls(
                    isFavorite = isCurrentTrackFavorite,
                    connectDeviceName = connectRemoteDeviceName,
                    onEqualizerClick = { showEqualizerBottomSheet = true },
                    onConnectClick = { showConnectSheet = true },
                    onFavoriteClick = { viewModel.toggleCurrentTrackFavorite() },
                    onShareClick = { showShareChooser = true },
                    onQueueClick = { showQueueBottomSheet = true },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            // Extended content as Material panels - let gradient show through
            item {
                PlayerMaterialPanels(
                    panelState = panelState,
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
                recordingId = uiState.navigationInfo.recordingId,
                trackTitle = uiState.trackDisplayInfo.title,
                showDate = uiState.trackDisplayInfo.showDate,
                venue = uiState.trackDisplayInfo.venue,
                isFavorite = isCurrentTrackFavorite,
                onDismiss = { showTrackActionsBottomSheet = false },
                onShare = { showShareChooser = true },
                onAddToPlaylist = { Toast.makeText(context, "Playlists are coming soon", Toast.LENGTH_SHORT).show() },
                onDownload = { viewModel.downloadCurrentShow() },
                onFavorite = { viewModel.toggleCurrentTrackFavorite() },
                onEqualizer = { showEqualizerBottomSheet = true },
                onQueue = { showQueueBottomSheet = true },
            )
        }

        if (showShareChooser) {
            ShareChooserSheet(
                onMessageShare = {
                    showShareChooser = false
                    viewModel.shareAsMessage()
                },
                onQrShare = {
                    showShareChooser = false
                    showQrCode = true
                },
                onDismiss = { showShareChooser = false }
            )
        }

        if (showQrCode) {
            val showId = uiState.navigationInfo.showId
            val recordingId = uiState.navigationInfo.recordingId
            if (showId != null) {
                val url = if (recordingId != null) {
                    buildString {
                        append("${viewModel.appPreferences.shareBaseUrl}/shows/$showId/recording/$recordingId")
                        val trackNumber = uiState.navigationInfo.trackNumber
                        if (trackNumber != null) append("/track/$trackNumber")
                    }
                } else {
                    "${viewModel.appPreferences.shareBaseUrl}/shows/$showId"
                }
                QrCodeDisplay(
                    url = url,
                    showDate = uiState.trackDisplayInfo.showDate,
                    venue = uiState.trackDisplayInfo.venue,
                    location = "",
                    recordingId = uiState.navigationInfo.recordingId,
                    coverImageUrl = uiState.trackDisplayInfo.coverImageUrl,
                    songTitle = uiState.trackDisplayInfo.title,
                    onDismiss = { showQrCode = false }
                )
            }
        }

        if (showEqualizerBottomSheet) {
            PlayerEqualizerSheet(
                state = equalizerState,
                onDismiss = { showEqualizerBottomSheet = false },
                onToggleEnabled = viewModel::setEqualizerEnabled,
                onPresetSelected = viewModel::selectEqualizerPreset,
                onBandLevelChanged = viewModel::setEqualizerBandLevel,
                onReset = viewModel::resetEqualizer
            )
        }

        if (showQueueBottomSheet) {
            PlayerQueueSheet(
                onDismiss = { showQueueBottomSheet = false }
            )
        }

        if (showConnectSheet) {
            ConnectSheet(
                onDismiss = { showConnectSheet = false }
            )
        }

        // Mini Player overlay when scrolled
        if (showMiniPlayer) {
            PlayerMiniPlayer(
                uiState = uiState,
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
    }
}
