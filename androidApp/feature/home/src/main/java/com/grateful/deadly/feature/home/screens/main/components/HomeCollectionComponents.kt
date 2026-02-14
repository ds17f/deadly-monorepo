package com.grateful.deadly.feature.home.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        // Horizontal scrolling row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                CollectionItemCard(
                    item = item,
                    onItemClick = { onItemClick(item) }
                )
            }
        }
    }
}

/**
 * Individual item in horizontal collection
 */
@Composable
fun CollectionItemCard(
    item: HorizontalCollectionItem,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .clickable { onItemClick() },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Large square image
        ShowArtwork(
            recordingId = item.recordingId,
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
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
    val recordingId: String? = null
)

enum class CollectionItemType {
    SHOW, COLLECTION
}