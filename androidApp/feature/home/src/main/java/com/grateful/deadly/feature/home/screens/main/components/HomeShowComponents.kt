package com.grateful.deadly.feature.home.screens.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.ShowArtwork

import com.grateful.deadly.core.model.Show

/**
 * Recent Shows Grid - 2-column layout. The number of rows is a user
 * preference (1..4); each row holds 2 shows. Set in Settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentShowsGrid(
    shows: List<Show>,
    rows: Int,
    onShowClick: (String) -> Unit,
    onShowLongPress: (Show) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedRows = rows.coerceIn(1, 4)
    val displayShows = shows.take(clampedRows * 2)
    val rowCount = (displayShows.size + 1) / 2
    if (rowCount == 0) return
    val gridHeight = (rowCount * 80 + (rowCount - 1) * 4).dp

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Recently Played",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .height(gridHeight)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            userScrollEnabled = false
        ) {
            items(displayShows) { show ->
                RecentShowCard(
                    show = show,
                    onShowClick = { onShowClick(show.id) },
                    onShowLongPress = { onShowLongPress(show) }
                )
            }
        }
    }
}

/**
 * Individual show card for recent shows grid
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentShowCard(
    show: Show,
    onShowClick: () -> Unit,
    onShowLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasRecordings = show.recordingCount > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {}
            .combinedClickable(
                onClick = onShowClick,
                onLongClick = onShowLongPress
            )
            .then(if (!hasRecordings) Modifier.alpha(0.5f) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album cover
            ShowArtwork(
                recordingId = show.bestRecordingId,
                contentDescription = "Show artwork for ${show.date}",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp)),
                imageUrl = show.coverImageUrl
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Show metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = show.date,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Location
                Text(
                    text = show.location.displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!hasRecordings) {
                    Text(
                        text = "No recordings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}