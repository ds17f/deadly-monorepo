package com.deadly.v2.feature.player.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources

/**
 * Queue Bottom Sheet for V2 Player
 * Shows current queue with track list and play indicators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(32.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "${mockQueueItems.size} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            
            // Mock queue items
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mockQueueItems) { item ->
                    PlayerQueueItem(
                        trackTitle = item.title,
                        isCurrentTrack = item.isPlaying,
                        duration = item.duration
                    )
                }
                
                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Individual queue item component
 */
@Composable
private fun PlayerQueueItem(
    trackTitle: String,
    isCurrentTrack: Boolean,
    duration: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play indicator for current track
            if (isCurrentTrack) {
                Icon(
                    painter = IconResources.PlayerControls.Play(),
                    contentDescription = "Currently playing",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Spacer(modifier = Modifier.width(28.dp))
            }
            
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrentTrack) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isCurrentTrack) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Text(
            text = duration,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Mock data for queue (following V1 structure)
private data class QueueItemData(
    val title: String,
    val isPlaying: Boolean,
    val duration: String
)

private val mockQueueItems = listOf(
    QueueItemData("Scarlet Begonias", true, "7:32"),
    QueueItemData("Fire on the Mountain", false, "12:05"),
    QueueItemData("Estimated Prophet", false, "9:18")
)