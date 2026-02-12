package com.deadly.v2.feature.player.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources

/**
 * Secondary controls row (connections, share, queue)
 */
@Composable
fun PlayerSecondaryControls(
    onConnectClick: () -> Unit,
    onShareClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connections (left)
        IconButton(
            onClick = onConnectClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = IconResources.Content.Cast(),
                contentDescription = "Connect",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Right section
        Row {
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