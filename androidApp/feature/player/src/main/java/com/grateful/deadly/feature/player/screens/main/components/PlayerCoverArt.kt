package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.ShowArtwork

/**
 * Large cover art section (~40% of screen height)
 */
@Composable
fun PlayerCoverArt(
    recordingId: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            ShowArtwork(
                recordingId = recordingId,
                contentDescription = "Album Art",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
