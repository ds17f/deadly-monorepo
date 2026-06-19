package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Secondary controls row: cast/Connect (left) and the two inline per-show
 * actions Share · Equalizer (right).
 *
 * Favorite moved up to the track-info row and Autoplay moved into the "⋯" menu
 * (ADR-0014); the "Queue" stub was removed until the queue feature ships.
 */
@Composable
fun PlayerSecondaryControls(
    connectDeviceName: String?,
    onEqualizerClick: () -> Unit,
    onConnectClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
    showConnect: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left section - Connect (icon + device label share one tap target).
        // Hidden when Connect is disabled on this device (off by default); an
        // empty placeholder keeps the right cluster right-aligned (SpaceBetween).
        if (showConnect) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onConnectClick)
                    .padding(horizontal = 8.dp)
            ) {
                Icon(
                    painter = IconResources.Content.Cast(),
                    contentDescription = "Connect",
                    tint = if (connectDeviceName != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (connectDeviceName != null) {
                    Spacer(Modifier.width(8.dp))
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
        } else {
            Spacer(Modifier)
        }

        // Right section — inline per-show actions
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
        }
    }
}
