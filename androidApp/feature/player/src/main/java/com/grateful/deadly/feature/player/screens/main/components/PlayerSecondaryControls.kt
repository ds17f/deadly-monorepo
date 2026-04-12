package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Secondary controls row (connections, share, queue)
 */
@Composable
fun PlayerSecondaryControls(
    isFavorite: Boolean,
    connectDeviceName: String?,
    onEqualizerClick: () -> Unit,
    onConnectClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left section - Connect
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(40.dp)
        ) {
            IconButton(
                onClick = onConnectClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = IconResources.Content.Cast(),
                    contentDescription = "Connect",
                    tint = if (connectDeviceName != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (connectDeviceName != null) {
                Text(
                    text = connectDeviceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp)
                )
            }
        }

        // Right section
        Row {
            // Equalizer
            IconButton(
                onClick = onEqualizerClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = IconResources.PlayerControls.Equalizer(),
                    contentDescription = "Equalizer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Thumbs Up
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = if (isFavorite) IconResources.Content.Favorite() else IconResources.Content.FavoriteBorder(),
                    contentDescription = if (isFavorite) "Favorite (active)" else "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Share
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = IconResources.Content.Share(),
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Queue
            IconButton(
                onClick = onQueueClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = IconResources.PlayerControls.Queue(),
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}