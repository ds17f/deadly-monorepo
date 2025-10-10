package com.deadly.v2.feature.home.screens.main.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.model.Show

/**
 * Recent Shows Grid - Dynamic 2-column layout showing recently played shows
 * Automatically adjusts height based on number of shows (1-8 shows, 1-4 rows)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentShowsGrid(
    shows: List<Show>,
    onShowClick: (String) -> Unit,
    onShowLongPress: (Show) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayShows = shows.take(8) // Maximum 8 shows for 2x4 grid
    val rowCount = (displayShows.size + 1) / 2 // Calculate rows needed (ceiling division)
    val gridHeight = (rowCount * 64 + (rowCount - 1) * 4).dp // rows × card height + spacing
    
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
                .height(gridHeight) // Dynamic height based on content
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            userScrollEnabled = false // Disable scrolling to hold its size
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(
                onClick = onShowClick,
                onLongClick = onShowLongPress
            ),
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
            // Album cover placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = IconResources.PlayerControls.AlbumArt(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            // Show metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Date
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
            }
        }
    }
}