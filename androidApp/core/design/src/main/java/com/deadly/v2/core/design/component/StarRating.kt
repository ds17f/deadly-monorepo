package com.deadly.v2.core.design.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deadly.v2.core.design.resources.IconResources
import kotlin.math.floor

/**
 * A composable that displays a star rating with optional text information.
 *
 * @param rating The rating value (0.0 to 5.0)
 * @param maxRating The maximum rating value (default: 5)
 * @param modifier Modifier for the component
 * @param showRatingText Whether to show the numeric rating text
 * @param showReviewCount Whether to show the review count (if available)
 * @param reviewCount The number of reviews (optional)
 * @param starSize The size of each star icon
 * @param textSize The size of the rating text
 * @param starColor The color of filled stars
 * @param emptyStarColor The color of empty stars
 * @param textColor The color of the rating text
 * @param confidence The confidence level of the rating (0.0 to 1.0)
 * @param showConfidence Whether to show confidence as opacity
 */
@Composable
fun StarRating(
    rating: Float?,
    maxRating: Int = 5,
    modifier: Modifier = Modifier,
    showRatingText: Boolean = true,
    showReviewCount: Boolean = false,
    reviewCount: Int? = null,
    ratingContext: String? = null,
    showRatingContext: Boolean = false,
    starSize: Dp = IconResources.Size.MEDIUM,
    textSize: TextUnit = 14.sp,
    starColor: Color = MaterialTheme.colorScheme.primary,
    emptyStarColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    confidence: Float? = null,
    showConfidence: Boolean = true
) {
    // Handle null or invalid ratings
    val safeRating = rating?.coerceIn(0f, maxRating.toFloat()) ?: 0f
    val safeConfidence = confidence?.coerceIn(0f, 1f) ?: 1f
    
    // Calculate alpha based on confidence if enabled
    val alpha = if (showConfidence && confidence != null) {
        0.4f + (safeConfidence * 0.6f) // Range from 0.4 to 1.0
    } else {
        1f
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Star display
        StarDisplay(
            rating = safeRating,
            maxRating = maxRating,
            starSize = starSize,
            starColor = starColor.copy(alpha = alpha),
            emptyStarColor = emptyStarColor
        )
        
        // Rating text, review count, and context
        if (showRatingText || (showReviewCount && reviewCount != null) || (showRatingContext && !ratingContext.isNullOrEmpty())) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Rating text and review count
                if (showRatingText || (showReviewCount && reviewCount != null)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showRatingText && rating != null && rating > 0f) {
                            Text(
                                text = String.format("%.1f", safeRating),
                                fontSize = textSize,
                                color = textColor.copy(alpha = alpha),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        if (showReviewCount && reviewCount != null && reviewCount > 0) {
                            Text(
                                text = "($reviewCount)",
                                fontSize = textSize,
                                color = textColor.copy(alpha = alpha * 0.7f)
                            )
                        }
                    }
                }
                
                // Rating context indicator
                if (showRatingContext && !ratingContext.isNullOrEmpty()) {
                    Text(
                        text = ratingContext,
                        fontSize = (textSize.value * 0.85).sp,
                        color = when (ratingContext.lowercase()) {
                            "fan favorite" -> MaterialTheme.colorScheme.primary
                            "mixed reactions" -> MaterialTheme.colorScheme.tertiary
                            "polarizing" -> MaterialTheme.colorScheme.error
                            else -> textColor.copy(alpha = alpha * 0.8f)
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * A comprehensive star rating component that displays rating with context indicators.
 * Ideal for show cards and detailed views where context is important.
 */
@Composable
fun DetailedStarRating(
    rating: Float?,
    reviewCount: Int? = null,
    ratingContext: String? = null,
    modifier: Modifier = Modifier,
    starSize: Dp = IconResources.Size.MEDIUM,
    textSize: TextUnit = 14.sp,
    starColor: Color = MaterialTheme.colorScheme.primary,
    confidence: Float? = null
) {
    StarRating(
        rating = rating,
        modifier = modifier,
        showRatingText = true,
        showReviewCount = reviewCount != null && reviewCount > 0,
        reviewCount = reviewCount,
        ratingContext = ratingContext,
        showRatingContext = !ratingContext.isNullOrEmpty(),
        starSize = starSize,
        textSize = textSize,
        starColor = starColor,
        confidence = confidence
    )
}

/**
 * A composable that displays only the star icons for a rating.
 */
@Composable
private fun StarDisplay(
    rating: Float,
    maxRating: Int,
    starSize: Dp,
    starColor: Color,
    emptyStarColor: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(maxRating) { index ->
            val starRating = rating - index
            
            Icon(
                painter = when {
                    starRating >= 1f -> IconResources.Content.Star()
                    starRating >= 0.5f -> IconResources.Content.StarHalf()
                    else -> IconResources.Content.StarBorder()
                },
                contentDescription = null,
                modifier = Modifier.size(starSize),
                tint = if (starRating > 0f) starColor else emptyStarColor
            )
        }
    }
}

/**
 * A compact version of StarRating that shows only stars without text.
 */
@Composable
fun CompactStarRating(
    rating: Float?,
    maxRating: Int = 5,
    modifier: Modifier = Modifier,
    starSize: Dp = IconResources.Size.SMALL,
    starColor: Color = MaterialTheme.colorScheme.primary,
    emptyStarColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    confidence: Float? = null
) {
    StarRating(
        rating = rating,
        maxRating = maxRating,
        modifier = modifier,
        showRatingText = false,
        showReviewCount = false,
        starSize = starSize,
        starColor = starColor,
        emptyStarColor = emptyStarColor,
        confidence = confidence
    )
}


/**
 * A star rating badge that's suitable for cards and compact layouts.
 */
@Composable
fun StarRatingBadge(
    rating: Float?,
    reviewCount: Int? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    if (rating != null && rating > 0f) {
        Surface(
            modifier = modifier,
            color = backgroundColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = IconResources.Content.Star(),
                    contentDescription = null,
                    modifier = Modifier.size(IconResources.Size.SMALL),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = String.format("%.1f", rating),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                
                if (reviewCount != null && reviewCount > 0) {
                    Text(
                        text = "($reviewCount)",
                        fontSize = 10.sp,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}