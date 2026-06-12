package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Track information row with the show's primary per-use action: Favorite.
 *
 * Favorite takes the prominent "save" position (Spotify-style) that the dead
 * "add to playlist" stub used to occupy — the one frequent per-show action gets
 * a dedicated home (ADR-0014).
 */
@Composable
fun PlayerTrackInfoRow(
    trackTitle: String,
    showDate: String,
    venue: String,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Track info (takes most space)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = showDate,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = venue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Favorite — the prominent per-show save action
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = if (isFavorite) IconResources.Content.Favorite() else IconResources.Content.FavoriteBorder(),
                contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
