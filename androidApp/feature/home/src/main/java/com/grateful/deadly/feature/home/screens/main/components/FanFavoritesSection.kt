package com.grateful.deadly.feature.home.screens.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.api.home.PopularContent
import com.grateful.deadly.core.api.home.PopularDecade
import com.grateful.deadly.core.model.Show

/**
 * "Fan Favorites" home section. Always 4 shows.
 *  - decade = ALL: one show per decade pool.
 *  - decade = a specific decade: 4 shows from that pool with year spread.
 *
 * "Show more" re-rolls the display set client-side (no re-fetch). The
 * decade choice itself lives in Settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FanFavoritesSection(
    popularContent: PopularContent,
    decade: PopularDecade,
    cardSize: String,
    onShowClick: (Show) -> Unit,
    onShowLongPress: (Show) -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = cardSize == "small"
    var seed by remember { mutableIntStateOf(0) }
    val shows = remember(popularContent.lastRefresh, decade, seed) {
        popularContent.displayShows(decade, seed)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val title = if (decade == PopularDecade.ALL) "Fan Favorites" else "Fan Favorites · ${decade.label}"
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Show more",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.clickable {
                    seed++
                    onShowMore()
                },
            )
        }

        if (shows.isEmpty()) {
            Text(
                text = "Nothing here yet. Try a different decade in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        val listState = rememberLazyListState()
        val firstId = shows.firstOrNull()?.id
        LaunchedEffect(firstId) {
            listState.scrollToItem(0)
        }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shows, key = { it.id }) { show ->
                CollectionItemCard(
                    item = HorizontalCollectionItem(
                        id = show.id,
                        displayText = show.date,
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
