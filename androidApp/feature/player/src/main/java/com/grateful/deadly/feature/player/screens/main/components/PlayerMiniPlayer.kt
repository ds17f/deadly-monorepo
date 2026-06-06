package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.feature.player.screens.main.models.PlayerUiState

/**
 * Mini Player component that appears when scrolling past media controls
 * Shows current track, play/pause button, and progress bar
 */
@Composable
fun PlayerMiniPlayer(
    uiState: PlayerUiState,
    connectDeviceName: String?,
    onPlayPause: () -> Unit,
    onConnectClick: () -> Unit,
    onTapToExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable { onTapToExpand() },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column {
            // Main content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork thumbnail
                ShowArtwork(
                    recordingId = uiState.trackDisplayInfo.recordingId,
                    imageUrl = uiState.trackDisplayInfo.coverImageUrl,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    placeholderContent = {
                        Icon(
                            painter = IconResources.PlayerControls.AlbumArt(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Track info (clickable area for expand)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTapToExpand() }
                ) {
                    Text(
                        text = uiState.trackDisplayInfo.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.trackDisplayInfo.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Connect button
                IconButton(
                    onClick = onConnectClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = IconResources.Content.Cast(),
                        contentDescription = "Connect",
                        tint = if (connectDeviceName != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play/Pause button (NOT clickable for expansion)
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = if (uiState.isPlaying) {
                            IconResources.PlayerControls.Pause()
                        } else {
                            IconResources.PlayerControls.Play()
                        },
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Progress bar at bottom (without thumb)
            LinearProgressIndicator(
                progress = uiState.progressDisplayInfo.progressPercentage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
