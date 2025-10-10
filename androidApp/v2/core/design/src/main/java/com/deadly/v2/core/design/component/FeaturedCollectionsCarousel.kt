package com.deadly.v2.core.design.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.model.DeadCollection
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * FeaturedCollectionsCarousel - Horizontal pager for browsing featured collections
 * 
 * Displays collections in a swipeable carousel format with:
 * - Smooth HorizontalPager navigation with parallax effects
 * - Scale and fade animations for side cards
 * - Peek preview of adjacent cards
 * - Integration with CollectionCard component
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedCollectionsCarousel(
    collections: List<DeadCollection>,
    onCollectionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    parallaxOffset: Dp = 40.dp
) {
    val pagerState = rememberPagerState(
        pageCount = { collections.size }
    )

    HorizontalPager(
        state = pagerState,
        modifier = modifier.height(280.dp),
        contentPadding = PaddingValues(horizontal = 64.dp),
        pageSpacing = 16.dp,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
    ) { page ->
        // Offset: how far this page is from the current page (0 = centered)
        val pageOffset = (pagerState.currentPage - page) +
            pagerState.currentPageOffsetFraction

        // Scale: center = 1f, side pages shrink toward 0.85f
        val scale = 1f - 0.15f * pageOffset.absoluteValue

        // Alpha: center = 1f, side pages fade toward 0.5f
        val alpha = 1f - 0.5f * pageOffset.absoluteValue

        // Parallax: side pages slide horizontally
        val translationX = parallaxOffset.value * pageOffset

        CollectionCard(
            collection = collections[page],
            onClick = { onCollectionClick(collections[page].id) },
            showDescription = false, // Hide descriptions in carousel for better fit
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)   // shrink side cards
                .alpha(alpha)   // fade side cards
                .offset { IntOffset(translationX.roundToInt(), 0) } // slide side cards
        )
    }
}