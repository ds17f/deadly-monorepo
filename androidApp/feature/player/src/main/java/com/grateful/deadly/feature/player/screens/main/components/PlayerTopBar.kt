package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Custom top navigation bar with transparent background (gradient applied by parent)
 */
@Composable
fun PlayerTopBar(
    contextText: String,
    onNavigateBack: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onContextClick: () -> Unit,
    recordingId: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
            // Down chevron
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = IconResources.Navigation.KeyboardArrowDown(),
                    contentDescription = "Back",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Context text (clickable to navigate to playlist)
            Text(
                text = contextText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { onContextClick() }
            )
            
            // Queue ("Up Next") + 3-dot menu, grouped on the right
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onQueueClick) {
                    Icon(
                        painter = IconResources.PlayerControls.Queue(),
                        contentDescription = "Up Next",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onMoreOptionsClick) {
                    Icon(
                        painter = IconResources.Navigation.MoreVertical(),
                        contentDescription = "More options",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
    }
}