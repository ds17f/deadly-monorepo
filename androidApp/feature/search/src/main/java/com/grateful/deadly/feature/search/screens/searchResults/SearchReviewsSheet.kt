package com.grateful.deadly.feature.search.screens.searchResults

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.CompactStarRating
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.Review
import com.grateful.deadly.core.model.SearchResultShow
import com.grateful.deadly.feature.search.screens.main.models.ReviewsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchReviewsSheet(
    searchResult: SearchResultShow,
    reviewsState: ReviewsState,
    onDismiss: () -> Unit
) {
    val show = searchResult.show

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
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
                    Text(
                        text = "${show.date} • ${show.venue.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Rating summary
            item {
                val rating = show.averageRating ?: 0f
                val reviewCount = if (reviewsState is ReviewsState.Success) reviewsState.reviews.size else 0
                val distribution = if (reviewsState is ReviewsState.Success) {
                    buildRatingDistribution(reviewsState.reviews)
                } else emptyMap()

                RatingSummaryCard(
                    rating = rating,
                    reviewCount = reviewCount,
                    ratingDistribution = distribution.takeIf { it.isNotEmpty() }
                )
            }

            // Individual Reviews header
            item {
                Text(
                    text = "Individual Reviews",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when (reviewsState) {
                is ReviewsState.Loading -> {
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

                is ReviewsState.Error -> {
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
                                    text = reviewsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                is ReviewsState.Success -> {
                    if (reviewsState.reviews.isEmpty()) {
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
                    } else {
                        items(reviewsState.reviews) { review ->
                            ReviewItemCard(review = review)
                        }
                    }
                }

                is ReviewsState.Idle -> { }
            }
        }
    }
}

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

            ratingDistribution?.let { distribution ->
                Spacer(modifier = Modifier.height(12.dp))
                RatingDistributionChart(distribution = distribution)
            }
        }
    }
}

@Composable
private fun RatingDistributionChart(distribution: Map<Int, Int>) {
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

@Composable
private fun ReviewItemCard(review: Review) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = review.reviewer ?: "Anonymous",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    review.rating?.let { rating ->
                        CompactStarRating(
                            rating = rating.toFloat(),
                            confidence = null,
                            starSize = IconResources.Size.SMALL
                        )
                    }
                    review.reviewDate?.let { date ->
                        if (date.isNotBlank()) {
                            Text(
                                text = date.split(" ").firstOrNull() ?: date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            review.title?.let { title ->
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            review.body?.let { body ->
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun buildRatingDistribution(reviews: List<Review>): Map<Int, Int> {
    val distribution = mutableMapOf<Int, Int>()
    for (star in 1..5) distribution[star] = 0
    reviews.forEach { review ->
        review.rating?.let { rating ->
            if (rating in 1..5) {
                distribution[rating] = (distribution[rating] ?: 0) + 1
            }
        }
    }
    return distribution
}
