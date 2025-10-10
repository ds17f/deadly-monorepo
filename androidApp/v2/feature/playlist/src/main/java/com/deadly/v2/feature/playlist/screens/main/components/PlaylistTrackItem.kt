package com.deadly.v2.feature.playlist.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.model.PlaylistTrackViewModel

/**
 * PlaylistV2TrackItem - Individual track row matching V1 visual design exactly
 * 
 * V2 implementation that replicates V1's TrackItem visual layout and behavior.
 * Simple, clean design focused on browsing tracks with minimal visual complexity.
 * Maintains V2 data patterns and callback structure.
 */
@Composable
fun PlaylistTrackItem(
    track: PlaylistTrackViewModel,
    onPlayClick: (PlaylistTrackViewModel) -> Unit,
    onDownloadClick: (PlaylistTrackViewModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlayClick(track) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Music note icon (only shown for current track that is playing)
        if (track.isCurrentTrack && track.isPlaying) {
            Icon(
                painter = IconResources.PlayerControls.MusicNote(),
                contentDescription = "Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        
        // Track info
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (track.isCurrentTrack) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (track.isCurrentTrack && track.isPlaying) {
                    // Currently playing track - blue
                    MaterialTheme.colorScheme.primary
                } else if (track.isCurrentTrack && !track.isPlaying) {
                    // Current track but paused - red highlight
                    Color.Red
                } else {
                    // Normal track
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Start
            )
            
            // Format and duration line
            Text(
                text = "${track.format} • ${track.duration}",
                style = MaterialTheme.typography.bodySmall,
                color = if (track.isCurrentTrack) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Start
            )
        }
        
        // Download indicator - only shown if track is downloaded
        if (track.isDownloaded) {
            Icon(
                painter = IconResources.Status.CheckCircle(),
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}