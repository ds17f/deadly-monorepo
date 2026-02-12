package com.deadly.v2.feature.player.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.design.component.ShareMenuRow

/**
 * Track Actions Bottom Sheet for V2 Player
 * Shows current track info and available actions (Share, Add to Playlist, Download, etc.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTrackActionsSheet(
    trackTitle: String,
    showDate: String,
    venue: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Track card (similar to V2 playlist patterns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Album cover placeholder
                Card(
                    modifier = Modifier.size(60.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = IconResources.PlayerControls.AlbumArt(),
                            contentDescription = "Album Art",
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // Track info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = showDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = venue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            HorizontalDivider()
            
            // Action buttons using V2 patterns
            ShareMenuRow(
                onClick = {
                    onShare()
                    onDismiss()
                }
            )
            
            ActionMenuRow(
                text = "Add to Playlist",
                icon = IconResources.Content.PlaylistAdd(),
                onClick = {
                    onAddToPlaylist()
                    onDismiss()
                }
            )
            
            ActionMenuRow(
                text = "Download",
                icon = IconResources.Content.CloudDownload(),
                onClick = {
                    onDownload()
                    onDismiss()
                }
            )
            
            ActionMenuRow(
                text = "More Options",
                icon = IconResources.Navigation.MoreVertical(),
                onClick = {
                    // TODO: Implement more options
                    onDismiss()
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Action menu row component following V2 patterns
 */
@Composable
private fun ActionMenuRow(
    text: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableWithoutRipple { onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = text,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Clickable without ripple effect to match V1 design
 */
@Composable
private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    return this.clickable(
        onClick = onClick,
        indication = null,
        interactionSource = MutableInteractionSource()
    )
}