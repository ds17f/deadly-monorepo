package com.grateful.deadly.feature.playlist.screens.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.AutoplayModeIcon
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.AdvanceMode
import com.grateful.deadly.core.model.FavoritesAction
import com.grateful.deadly.core.model.FavoritesDownloadStatus
import com.grateful.deadly.core.model.PlaylistShowViewModel

/**
 * PlaylistActionRow - Action buttons row (ADR-0014)
 *
 * Spotify album layout plus a Setlist affordance:
 *   Left:  Setlist · Favorite · Download · ⋯ Menu
 *   Right: Autoplay · Play
 *
 * Autoplay rides next to Play as a play-*mode* (the slot Spotify gives shuffle).
 * Collections left the inline row for the "⋯" menu (shown only when the show is
 * in ≥1 collection).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistActionRow(
    showData: PlaylistShowViewModel,
    isPlaying: Boolean,
    isLoading: Boolean,
    isCurrentShowAndRecording: Boolean,
    advanceMode: AdvanceMode,
    isInQueue: Boolean,
    onFavoritesAction: (FavoritesAction) -> Unit,
    onDownload: () -> Unit,
    onShowSetlist: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onToggleQueue: () -> Unit,
    onOpenQueue: () -> Unit,
    onShowMenu: () -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Setlist · Favorite · Download · Menu
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Setlist button
            IconButton(
                onClick = onShowSetlist,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = IconResources.Content.FormatListBulleted(),
                    contentDescription = "Show Setlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Favorites button
            IconButton(
                onClick = {
                    if (showData.isFavorite) {
                        onFavoritesAction(FavoritesAction.REMOVE_FROM_FAVORITES)
                    } else {
                        onFavoritesAction(FavoritesAction.ADD_TO_FAVORITES)
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = if (showData.isFavorite) {
                        IconResources.Content.Favorite()
                    } else {
                        IconResources.Content.FavoriteBorder()
                    },
                    contentDescription = if (showData.isFavorite) "Remove from Favorites" else "Add to Favorites",
                    tint = if (showData.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            // Download button
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(40.dp)
            ) {
                val downloadProgress = showData.downloadProgress
                val downloadStatus = showData.downloadStatus
                when {
                    downloadProgress == null -> {
                        // Not downloaded
                        Icon(
                            painter = IconResources.Content.FileDownload(),
                            contentDescription = "Download Show",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    downloadStatus == FavoritesDownloadStatus.PAUSED -> {
                        // Paused - grey ring with play icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 2.dp,
                            )
                            Icon(
                                painter = IconResources.PlayerControls.Play(),
                                contentDescription = "Paused — tap to resume",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    downloadProgress < 1.0f -> {
                        // Downloading - show progress
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                            )
                            Icon(
                                painter = IconResources.Content.FileDownload(),
                                contentDescription = "Downloading ${(downloadProgress * 100).toInt()}%",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    else -> {
                        // Downloaded - show completed state
                        Icon(
                            painter = IconResources.Status.CheckCircle(),
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Show Queue toggle — tap to add/remove (remove confirms upstream),
            // long-press to jump to the queue. The list glyph matches the ∞
            // Show-Queue badge so the mark reads the same everywhere.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleQueue,
                        onLongClick = onOpenQueue,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = IconResources.PlayerControls.ShowQueueMark(),
                    contentDescription = if (isInQueue) "In Show Queue" else "Add to Show Queue",
                    tint = if (isInQueue) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            // Menu button
            IconButton(
                onClick = onShowMenu,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = IconResources.Navigation.MoreVertical(),
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Right side: Autoplay (play-mode) · Play/Pause
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Autoplay button (auto-advance to the next show when one ends).
            // The ∞ carries a mode badge: list = Show Queue, calendar = Chrono.
            IconButton(
                onClick = onToggleAutoplay,
                modifier = Modifier.size(40.dp)
            ) {
                AutoplayModeIcon(mode = advanceMode)
            }

            // Play/Pause button (large) with loading state
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(56.dp)
            ) {
                if (isLoading) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(56.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    // Show pause icon only if currently playing this exact show/recording
                    val showPauseIcon = isCurrentShowAndRecording && isPlaying

                    Icon(
                        painter = if (showPauseIcon) {
                            IconResources.PlayerControls.PauseCircleFilled()
                        } else {
                            IconResources.PlayerControls.PlayCircleFilled()
                        },
                        contentDescription = if (showPauseIcon) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}
