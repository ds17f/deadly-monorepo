package com.grateful.deadly.feature.player.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
 * Layout (top → bottom): a header card — album art with the show date + venue
 * stacked beside it — then the song title and a seekable scrubber. The art
 * scales with the column height so the upper block fills the available space
 * instead of leaving a gap. A secondary action row (Connect · Share ·
 * Equalizer · ⋯ overflow) sits above the primary transport controls, which
 * stay pinned at the bottom.
 *
 * Contextual: renders nothing when no track is loaded, so the column collapses
 * and the content pane takes the full width when idle. Reuses [PlayerViewModel]
 * (which observes the shared playback service), so it stays in sync with the
 * mini and full players.
 *
 * @param onTapToExpand tapping the cover opens the full player (Phase 3 will make
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
        BoxWithConstraints {
            // Art grows with the column height so the upper block fills the
            // space (small on a short landscape phone, large on a tall tablet).
            // Capped by the height left after reserving room for the title,
            // scrubber, and the bottom-pinned controls, so transport is never
            // pushed off-screen on short landscape phones.
            val cap = minOf(180.dp, this.maxHeight - 280.dp).coerceAtLeast(56.dp)
            val coverSize = (this.maxHeight * 0.22f).coerceIn(56.dp, cap)

            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 12.dp)
            ) {
                // Header card: album art + date / venue beside it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .size(coverSize)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTapToExpand() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        ShowArtwork(
                            recordingId = uiState.trackDisplayInfo.recordingId,
                            imageUrl = uiState.trackDisplayInfo.coverImageUrl,
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.trackDisplayInfo.showDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.trackDisplayInfo.venue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Song title + favorite.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.trackDisplayInfo.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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

                Spacer(modifier = Modifier.height(18.dp))

                PlayerProgressControl(
                    currentTime = uiState.progressDisplayInfo.currentPosition,
                    totalTime = uiState.progressDisplayInfo.totalDuration,
                    progress = uiState.progressDisplayInfo.progressPercentage,
                    onSeek = viewModel::onSeek
                )

                // Absorbs any residual height above the bottom-pinned controls.
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
