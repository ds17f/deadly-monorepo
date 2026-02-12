package com.deadly.v2.feature.playlist.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * PlaylistV2Header - Floating back button overlay
 * 
 * Clean V2 implementation of the circular back button that appears
 * as an overlay on top of the content, matching V1 visual appearance.
 */
@Composable
fun PlaylistHeader(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onNavigateBack,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                CircleShape
            )
            .zIndex(1f)
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}