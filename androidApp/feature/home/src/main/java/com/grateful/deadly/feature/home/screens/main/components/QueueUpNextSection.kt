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
import com.grateful.deadly.feature.home.screens.main.HomeViewModel

/**
 * "Your Queue" home rail — the persistent show queue (ADR-0010), fixed order
 * with the head (next to play) leftmost. Tap opens the show (no autoplay);
 * long-press offers to remove it (it's already queued). Hidden when empty.
 */
@Composable
fun QueueUpNextSection(
    items: List<HomeViewModel.QueuedShowUi>,
    onOpenShow: (String) -> Unit,
    onLongPress: (HomeViewModel.QueuedShowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Your Queue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { it.entryId }) { item ->
                val show = item.show
                CollectionItemCard(
                    item = HorizontalCollectionItem(
                        id = show.id,
                        displayText = "${show.date}\n${show.venue.name}\n${show.location.displayText}",
                        type = CollectionItemType.SHOW,
                        recordingId = show.bestRecordingId,
                        imageUrl = show.coverImageUrl,
                    ),
                    cardWidth = 160.dp,
                    onItemClick = { onOpenShow(show.id) },
                    onItemLongPress = { onLongPress(item) },
                )
            }
        }
    }
}
