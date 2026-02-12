package com.deadly.v2.feature.playlist.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.design.component.CompactStarRating
import com.deadly.v2.core.model.PlaylistShowViewModel

/**
 * PlaylistV2InteractiveRating - V2 implementation of rating display
 * 
 * Copies V1 InteractiveRatingDisplay UI exactly but integrates with V2 architecture.
 * Always shows (even when no rating), with appropriate empty states.
 */
@Composable
fun PlaylistInteractiveRating(
    showData: PlaylistShowViewModel,
    onShowReviews: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onShowReviews() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star rating and numerical score
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStarRating(
                    rating = if (showData.rating > 0) showData.rating else null,
                    confidence = null, // V2 implementation doesn't have confidence yet
                    starSize = IconResources.Size.MEDIUM
                )
                
                Text(
                    text = if (showData.rating > 0) {
                        String.format("%.1f", showData.rating)
                    } else {
                        "N/A"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (showData.rating > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            // Review count and indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val reviewCount = showData.reviewCount
                Text(
                    text = if (reviewCount > 0) {
                        "($reviewCount)"
                    } else {
                        "No reviews"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Icon(
                    painter = IconResources.Navigation.ChevronRight(),
                    contentDescription = "View reviews",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}