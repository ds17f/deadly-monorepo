package com.deadly.v2.feature.playlist.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.component.CompactStarRating
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.model.PlaylistReview
import com.deadly.v2.core.model.PlaylistShowViewModel

/**
 * PlaylistV2ReviewDetailsSheet - V2 implementation of review details modal
 * 
 * Copies V1 ReviewDetailsSheet UI exactly but integrates with V2 architecture.
 * Gets data from PlaylistV2ViewModel instead of separate ReviewViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistReviewDetailsSheet(
    showData: PlaylistShowViewModel?,
    reviews: List<PlaylistReview>,
    ratingDistribution: Map<Int, Int>,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.8f)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = "Reviews",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    showData?.let { show ->
                        Text(
                            text = "${show.displayDate} - ${show.venue}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // Rating summary
            item {
                showData?.let { show ->
                    RatingSummaryCard(
                        rating = show.rating,
                        reviewCount = show.reviewCount,
                        ratingDistribution = ratingDistribution.takeIf { it.isNotEmpty() }
                    )
                }
            }
            
            // Reviews section
            item {
                Text(
                    text = "Individual Reviews",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            when {
                isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                
                errorMessage != null -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = IconResources.Status.Error(),
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                
                reviews.isEmpty() -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = IconResources.Status.Info(),
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "No reviews available for this recording yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                else -> {
                    // Show reviews
                    items(reviews) { review ->
                        ReviewItemCard(review = review)
                    }
                }
            }
        }
    }
}

/**
 * Rating summary card showing overall rating and distribution (V1 layout)
 */
@Composable
private fun RatingSummaryCard(
    rating: Float,
    reviewCount: Int,
    ratingDistribution: Map<Int, Int>?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Overall rating (centered)
            Text(
                text = if (rating > 0) String.format("%.1f", rating) else "N/A",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            CompactStarRating(
                rating = if (rating > 0) rating else null,
                confidence = null,
                starSize = IconResources.Size.MEDIUM
            )
            
            Text(
                text = "$reviewCount ratings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            // Rating distribution (below, centered)
            ratingDistribution?.let { distribution ->
                Spacer(modifier = Modifier.height(12.dp))
                RatingDistributionChart(distribution = distribution)
            }
        }
    }
}

/**
 * Rating distribution chart (V1 implementation)
 */
@Composable
private fun RatingDistributionChart(
    distribution: Map<Int, Int>
) {
    val totalRatings = distribution.values.sum()
    if (totalRatings == 0) return
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (stars in 5 downTo 1) {
            val count = distribution[stars] ?: 0
            val percentage = if (totalRatings > 0) count.toFloat() / totalRatings else 0f
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$stars★",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.width(24.dp)
                )
                
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
    }
}

/**
 * Individual review item card (V1 layout with stars and date together)
 */
@Composable
private fun ReviewItemCard(
    review: PlaylistReview
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Username on left, stars + date on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = review.username,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactStarRating(
                        rating = review.stars.toFloat(),
                        confidence = null,
                        starSize = IconResources.Size.SMALL
                    )
                    if (review.reviewDate.isNotBlank()) {
                        Text(
                            text = review.reviewDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Review text
            if (review.reviewText.isNotBlank()) {
                Text(
                    text = review.reviewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}