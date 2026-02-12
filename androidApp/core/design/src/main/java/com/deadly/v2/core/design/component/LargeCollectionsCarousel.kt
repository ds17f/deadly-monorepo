package com.deadly.v2.core.design.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * LargeCollectionsCarousel - Large carousel for browsing all collections
 * 
 * Features:
 * - Larger cards (~1/2 screen height)
 * - Full collection descriptions
 * - Selection callback for showing collection shows
 * - Smooth navigation with parallax effects
 * - Filtered collections display
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LargeCollectionsCarousel(
    collections: List<DeadCollection>,
    onCollectionSelected: (DeadCollection) -> Unit,
    onCollectionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    selectedCollectionId: String? = null,
    pagerState: PagerState = rememberPagerState(
        pageCount = { collections.size }
    ),
    parallaxOffset: Dp = 40.dp
) {
    // Selection logic moved to CollectionsScreen level for better control
    // This component now focuses purely on display and click handling

    HorizontalPager(
        state = pagerState,
        modifier = modifier.height(360.dp), // Height to allow 2 title lines + 3 description lines
        contentPadding = PaddingValues(horizontal = 48.dp), // More padding to show peek of adjacent cards
        pageSpacing = 12.dp, // Slightly less spacing for better peek visibility
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState)
    ) { page ->
        // Offset: how far this page is from the current page (0 = centered)
        val pageOffset = (pagerState.currentPage - page) +
            pagerState.currentPageOffsetFraction

        // Scale: center = 1f, side pages shrink toward 0.85f (more visible side cards)
        val scale = 1f - 0.15f * pageOffset.absoluteValue

        // Alpha: center = 1f, side pages fade toward 0.7f (less aggressive fading for better peek)
        val alpha = 1f - 0.3f * pageOffset.absoluteValue

        // Parallax: side pages slide horizontally
        val translationX = parallaxOffset.value * pageOffset

        CollectionCard(
            collection = collections[page],
            onClick = { 
                onCollectionClick(collections[page].id)
                onCollectionSelected(collections[page])
            },
            showDescription = true, // Show full descriptions in large carousel
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)   // shrink side cards
                .alpha(alpha)   // fade side cards
                .offset { IntOffset(translationX.roundToInt(), 0) } // slide side cards
        )
    }
}
