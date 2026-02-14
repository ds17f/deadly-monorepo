package com.grateful.deadly.feature.playlist.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.ShowArtwork

/**
 * PlaylistAlbumArt - Album artwork component
 *
 * Displays archive.org artwork for the recording, falling back
 * to the deadly logo when no recordingId is available.
 */
@Composable
fun PlaylistAlbumArt(
    recordingId: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        ShowArtwork(
            recordingId = recordingId,
            contentDescription = "Album Art",
            highRes = true,
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}
