package com.grateful.deadly.feature.home.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.api.home.TrendingWindow
import com.grateful.deadly.core.model.Show

/**
 * "Trending on The Deadly" home section. Renders one time window's shows
 * with a chip on the right that cycles Day → Week → Month → All on tap.
 * The chosen window persists via [onCycleWindow] which writes to
 * AppPreferences, keeping in sync with the Settings selector.
 */
@Composable
fun TrendingNowSection(
    shows: List<Show>,
    window: TrendingWindow,
    onShowClick: (Show) -> Unit,
    onCycleWindow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shows.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Trending on The Deadly",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            WindowChip(window = window, onClick = onCycleWindow)
        }
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

@Composable
private fun WindowChip(window: TrendingWindow, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = window.shortLabel,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        shape = RoundedCornerShape(20.dp),
    )
}
