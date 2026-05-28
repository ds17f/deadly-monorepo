package com.grateful.deadly.feature.home.screens.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.ShowArtwork

/**
 * Reusable horizontal collection component for large square images
 */
@Composable
fun HorizontalCollection(
    title: String,
    items: List<HorizontalCollectionItem>,
    onItemClick: (HorizontalCollectionItem) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 160.dp,
    onItemLongPress: ((HorizontalCollectionItem) -> Unit)? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section title (with optional trailing action like "Show more")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onActionClick != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onActionClick),
                )
            }
        }

        // Horizontal scrolling row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                CollectionItemCard(
                    item = item,
                    cardWidth = cardWidth,
                    onItemClick = { onItemClick(item) },
                    onItemLongPress = onItemLongPress?.let { handler -> { handler(item) } }
                )
            }
        }
    }
}

/**
 * Individual item in horizontal collection
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionItemCard(
    item: HorizontalCollectionItem,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 160.dp,
    onItemLongPress: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(cardWidth)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongPress
            ),
        verticalArrangement = Arrangement.spacedBy(if (cardWidth < 140.dp) 6.dp else 12.dp)
    ) {
        // Large square image
        ShowArtwork(
            recordingId = item.recordingId,
            contentDescription = null,
            modifier = Modifier
                .size(cardWidth)
                .clip(RoundedCornerShape(12.dp)),
            imageUrl = item.imageUrl
        )

        // Descriptive text - parse lines and display each with truncation
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val lines = item.displayText.split("\n")
            lines.take(3).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Data class for horizontal collection items
 */
data class HorizontalCollectionItem(
    val id: String,
    val displayText: String,
    val type: CollectionItemType = CollectionItemType.SHOW,
    val recordingId: String? = null,
    val imageUrl: String? = null
)

enum class CollectionItemType {
    SHOW, COLLECTION
}
