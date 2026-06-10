package com.grateful.deadly.feature.home.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.model.Show

/**
 * "Up Next" home rail — the persistent show queue (ADR-0010), fixed order with
 * the head (next to play) leftmost. Tap plays the show; long-press opens the
 * detail sheet. Hidden when the queue is empty.
 */
@Composable
fun QueueUpNextSection(
    shows: List<Show>,
    onPlayShow: (String) -> Unit,
    onShowLongPress: (Show) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shows.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Up Next",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
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
                    cardWidth = 160.dp,
                    onItemClick = { onPlayShow(show.id) },
                    onItemLongPress = { onShowLongPress(show) },
                )
            }
        }
    }
}
