package com.grateful.deadly.feature.home.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.api.home.TrendingWindow
import com.grateful.deadly.core.model.Show

/**
 * "Trending on The Deadly" home section. Renders a single time window's
 * worth of shows; the choice of window is a user preference set in
 * Settings (defaults to NOW = rolling 24h).
 */
@Composable
fun TrendingNowSection(
    shows: List<Show>,
    window: TrendingWindow,
    onShowClick: (Show) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shows.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Trending on The Deadly",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = window.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shows, key = { it.id }) { show ->
                CollectionItemCard(
                    item = HorizontalCollectionItem(
                        id = show.id,
                        displayText = "${show.date}\n${show.venue.name}\n${show.location.displayText}",
                        type = CollectionItemType.SHOW,
                        recordingId = show.bestRecordingId,
                        imageUrl = show.coverImageUrl,
                    ),
                    onItemClick = { onShowClick(show) },
                )
            }
        }
    }
}

private val TrendingWindow.subtitle: String
    get() = when (this) {
        TrendingWindow.NOW -> "Last 24 hours"
        TrendingWindow.WEEK -> "Last 7 days"
        TrendingWindow.MONTH -> "Last 30 days"
        TrendingWindow.ALL -> "All time"
    }
