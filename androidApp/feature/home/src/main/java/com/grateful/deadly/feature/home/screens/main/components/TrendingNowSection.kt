package com.grateful.deadly.feature.home.screens.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.api.home.TrendingWindow
import com.grateful.deadly.core.model.Show

/**
 * "Trending <window>" home section. The title itself is tappable and cycles
 * Day → Week → Month → All on tap; the chosen window persists via
 * [onCycleWindow] which writes to AppPreferences, keeping in sync with the
 * Settings selector.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrendingNowSection(
    shows: List<Show>,
    window: TrendingWindow,
    cardSize: String,
    onShowClick: (Show) -> Unit,
    onShowLongPress: (Show) -> Unit,
    onCycleWindow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shows.isEmpty()) return
    val isCompact = cardSize == "small"

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
                text = "Trending ${window.titleLabel}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onCycleWindow),
            )
            Text(
                text = "Show ${window.next().titleLabel}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.clickable(onClick = onCycleWindow),
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shows, key = { it.id }) { show ->
                val display = if (isCompact) {
                    show.date
                } else {
                    "${show.date}\n${show.venue.name}\n${show.location.displayText}"
                }
                CollectionItemCard(
                    item = HorizontalCollectionItem(
                        id = show.id,
                        displayText = display,
                        type = CollectionItemType.SHOW,
                        recordingId = show.bestRecordingId,
                        imageUrl = show.coverImageUrl,
                    ),
                    cardWidth = if (isCompact) 100.dp else 160.dp,
                    onItemClick = { onShowClick(show) },
                    onItemLongPress = { onShowLongPress(show) },
                )
            }
        }
    }
}
