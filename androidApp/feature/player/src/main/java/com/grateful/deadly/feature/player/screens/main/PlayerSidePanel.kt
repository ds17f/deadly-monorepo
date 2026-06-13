package com.grateful.deadly.feature.player.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.feature.player.screens.main.components.PlayerCoverArt
import com.grateful.deadly.feature.player.screens.main.components.PlayerEnhancedControls
import com.grateful.deadly.feature.player.screens.main.components.PlayerProgressControl
import com.grateful.deadly.feature.player.screens.main.components.PlayerTrackInfoRow
import com.grateful.deadly.feature.player.screens.main.models.PlayerViewModel

/**
 * Side player for wide layouts (landscape phones, tablets) — the docked
 * right-column "more than mini, less than full" player from the tablet/landscape
 * design. A vertical compact arrangement reusing the full player's components:
 * cover art, track info (+favorite), a seekable scrubber, and transport.
 *
 * Contextual: renders nothing when no track is loaded, so the column collapses
 * and the content pane takes the full width when idle. Reuses [PlayerViewModel]
 * (which observes the shared playback service), so it stays in sync with the
 * mini and full players.
 *
 * @param onTapToExpand tapping the cover opens the full player (Phase 3 will make
 *   this the in-place "full-wide" expand instead of the full-screen route).
 */
@Composable
fun PlayerSidePanel(
    onTapToExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isCurrentTrackFavorite.collectAsState()

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
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            PlayerCoverArt(
                recordingId = uiState.trackDisplayInfo.recordingId,
                imageUrl = uiState.trackDisplayInfo.coverImageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { onTapToExpand() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PlayerTrackInfoRow(
                trackTitle = uiState.trackDisplayInfo.title,
                showDate = uiState.trackDisplayInfo.showDate,
                venue = uiState.trackDisplayInfo.venue,
                isFavorite = isFavorite,
                onFavoriteClick = { viewModel.toggleCurrentTrackFavorite() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PlayerProgressControl(
                currentTime = uiState.progressDisplayInfo.currentPosition,
                totalTime = uiState.progressDisplayInfo.totalDuration,
                progress = uiState.progressDisplayInfo.progressPercentage,
                onSeek = viewModel::onSeek
            )

            Spacer(modifier = Modifier.height(8.dp))

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
