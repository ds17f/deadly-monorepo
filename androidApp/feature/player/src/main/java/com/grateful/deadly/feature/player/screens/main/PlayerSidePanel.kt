package com.grateful.deadly.feature.player.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.QrCodeDisplay
import com.grateful.deadly.core.design.component.ShareChooserSheet
import com.grateful.deadly.core.design.component.ShowActionsMenuSheet
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.feature.player.screens.main.components.PlayerEnhancedControls
import com.grateful.deadly.feature.player.screens.main.components.PlayerEqualizerSheet
import com.grateful.deadly.feature.player.screens.main.components.PlayerProgressControl
import com.grateful.deadly.feature.player.screens.main.models.PlayerViewModel
import com.grateful.deadly.feature.settings.screens.connect.ConnectSheet

/**
 * Side player for wide layouts (landscape phones, tablets) — the docked
 * right-column "more than mini, less than full" player from the tablet/landscape
 * design.
 *
 * Sized to fit a single landscape screen without scrolling. Layout (top →
 * bottom): a mini-player-style header (small thumbnail + track info + favorite),
 * a seekable scrubber, then — pushed to the bottom — a secondary action row
 * (Connect · Share · Equalizer · ⋯ overflow) above the primary transport
 * controls, which stay pinned at the bottom.
 *
 * Contextual: renders nothing when no track is loaded, so the column collapses
 * and the content pane takes the full width when idle. Reuses [PlayerViewModel]
 * (which observes the shared playback service), so it stays in sync with the
 * mini and full players.
 *
 * @param onTapToExpand tapping the header opens the full player (Phase 3 will make
 *   this the in-place "full-wide" expand instead of the full-screen route).
 * @param onNavigateToPlaylist target for the "⋯" menu's This Show / Choose
 *   Recording actions (the playlist is their home).
 */
@Composable
fun PlayerSidePanel(
    onTapToExpand: () -> Unit,
    onNavigateToPlaylist: (showId: String, recordingId: String?, openSheet: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isCurrentTrackFavorite.collectAsState()
    val equalizerState by viewModel.equalizerState.collectAsState()
    val connectRemoteDeviceName by viewModel.connectRemoteDeviceName.collectAsState()
    val autoAdvanceEnabled by viewModel.autoAdvanceEnabled.collectAsState()
    val showCollectionsCount by viewModel.showCollectionsCount.collectAsState()

    var showTrackActionsBottomSheet by remember { mutableStateOf(false) }
    var showQrCode by remember { mutableStateOf(false) }
    var showShareChooser by remember { mutableStateOf(false) }
    var showEqualizerBottomSheet by remember { mutableStateOf(false) }
    var showConnectSheet by remember { mutableStateOf(false) }

    // No track loaded → emit nothing so the content pane is full width.
    if (uiState.navigationInfo.showId == null) return

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Mini-player-style header: thumbnail + title/subtitle + favorite.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTapToExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    ShowArtwork(
                        recordingId = uiState.trackDisplayInfo.recordingId,
                        imageUrl = uiState.trackDisplayInfo.coverImageUrl,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.trackDisplayInfo.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.trackDisplayInfo.showDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (uiState.trackDisplayInfo.venue.isNotEmpty()) {
                        Text(
                            text = uiState.trackDisplayInfo.venue,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleCurrentTrackFavorite() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = if (isFavorite) IconResources.Content.Favorite() else IconResources.Content.FavoriteBorder(),
                        contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            PlayerProgressControl(
                currentTime = uiState.progressDisplayInfo.currentPosition,
                totalTime = uiState.progressDisplayInfo.totalDuration,
                progress = uiState.progressDisplayInfo.progressPercentage,
                onSeek = viewModel::onSeek
            )

            // Flexible space between the jog and the bottom-pinned controls.
            Spacer(modifier = Modifier.weight(1f))

            // Secondary actions: Connect (left) · Share · Equalizer · ⋯ (right).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showConnectSheet = true }
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        painter = IconResources.Content.Cast(),
                        contentDescription = "Connect",
                        tint = if (connectRemoteDeviceName != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (connectRemoteDeviceName != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = connectRemoteDeviceName!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 80.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showShareChooser = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = IconResources.Content.Share(),
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showEqualizerBottomSheet = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = IconResources.PlayerControls.Equalizer(),
                            contentDescription = "Equalizer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showTrackActionsBottomSheet = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = IconResources.Navigation.MoreVertical(),
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary transport — pinned at the bottom.
            PlayerEnhancedControls(
                isPlaying = uiState.isPlaying,
                isLoading = uiState.isLoading,
                hasNext = uiState.hasNext,
                onPlayPause = viewModel::onPlayPauseClicked,
                onPrevious = viewModel::onPreviousClicked,
                onNext = viewModel::onNextClicked
            )
        }
    }

    // Bottom sheets (mirrors PlayerScreen).
    if (showTrackActionsBottomSheet) {
        val navShowId = uiState.navigationInfo.showId
        val navRecordingId = uiState.navigationInfo.recordingId
        ShowActionsMenuSheet(
            recordingId = navRecordingId,
            title = uiState.trackDisplayInfo.title,
            showDate = uiState.trackDisplayInfo.showDate,
            venue = uiState.trackDisplayInfo.venue,
            isAutoplayEnabled = autoAdvanceEnabled,
            collectionsCount = showCollectionsCount,
            onChooseRecording = navShowId?.let { sid -> { onNavigateToPlaylist(sid, navRecordingId, "recording") } },
            onAutoplay = { viewModel.toggleAutoAdvance() },
            onSetlist = navShowId?.let { sid -> { onNavigateToPlaylist(sid, navRecordingId, "setlist") } },
            onCollections = navShowId?.let { sid -> { onNavigateToPlaylist(sid, navRecordingId, "collections") } },
            onDownload = { viewModel.downloadCurrentShow() },
            onDismiss = { showTrackActionsBottomSheet = false },
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

    if (showConnectSheet) {
        ConnectSheet(
            onDismiss = { showConnectSheet = false }
        )
    }
}
