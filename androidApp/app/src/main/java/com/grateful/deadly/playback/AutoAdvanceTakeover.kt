package com.grateful.deadly.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grateful.deadly.core.design.component.ShowArtwork

/**
 * ADR-0010 — end-of-show "Up Next" takeover. The full-player counterpart to the
 * docked [AutoAdvanceOverlay]: while the countdown runs and the player screen is
 * open, the whole player is replaced by a preview of the NEXT show — the same
 * large cover-art / date / venue layout as the now-playing player, under an
 * "Up Next in Ns" header, with Play now / Cancel. Mirrors web's HeaderPlayer
 * takeover ([ui/.../HeaderPlayer.tsx]). Fed by [AutoAdvanceCoordinator.Countdown].
 */
@Composable
fun AutoAdvanceTakeover(
    countdown: AutoAdvanceCoordinator.Countdown,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val show = countdown.nextShow
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // "Up Next in Ns" header — the countdown the user is watching tick.
            Text(
                text = "UP NEXT IN ${countdown.secondsRemaining}s",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(24.dp))

            // Large cover art of the next show — same prominence as the player's.
            ShowArtwork(
                recordingId = show.bestRecordingId,
                imageUrl = show.coverImageUrl,
                contentDescription = show.date,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            )

            Spacer(Modifier.height(24.dp))

            // Next show date / venue / location.
            Text(
                text = show.date,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (show.venue.name.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = show.venue.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (show.location.displayText.isNotBlank()) {
                Text(
                    text = show.location.displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(32.dp))

            // Actions in the transport's place: start now, or stay on this show.
            Button(
                onClick = onPlayNow,
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            ) {
                Text("Play now", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
