package com.grateful.deadly.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.ShowArtwork

/**
 * ADR-0010 — end-of-show "Next up in Ns" announcement card. Shows the next show's
 * cover + details while the countdown runs, with Play now / Cancel. Rendered as a
 * docked card (active device or remote — fed by [AutoAdvanceCoordinator.countdown]).
 */
@Composable
fun AutoAdvanceOverlay(
    countdown: AutoAdvanceCoordinator.Countdown,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val show = countdown.nextShow
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShowArtwork(
                recordingId = show.bestRecordingId,
                imageUrl = show.coverImageUrl,
                contentDescription = show.date,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Next up in ${countdown.secondsRemaining}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = show.date,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = show.venue.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onPlayNow, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text("Play now", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onCancel, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
