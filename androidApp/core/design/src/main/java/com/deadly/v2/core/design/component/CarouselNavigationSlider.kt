package com.deadly.v2.core.design.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * CarouselNavigationSlider - iPod-style slider for quick carousel navigation
 * 
 * Features:
 * - Quick navigation through large item sets (shows when itemCount > threshold)
 * - Position indicator showing current position and total count
 * - Smooth animation when sliding to new positions
 * - Material3 styling with proper color theming
 * 
 * @param pagerState The PagerState to control and observe
 * @param itemCount Total number of items in the carousel
 * @param visibilityThreshold Show slider only when itemCount > threshold (default: 10)
 * @param modifier Modifier for the slider container
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarouselNavigationSlider(
    pagerState: PagerState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    visibilityThreshold: Int = 10
) {
    // Only show slider for large item sets
    if (itemCount <= visibilityThreshold) return
    
    val coroutineScope = rememberCoroutineScope()
    
    // Shuttle scrubber for quick navigation
    ShuttleScrubber(
        currentIndex = pagerState.currentPage,
        total = itemCount,
        onIndexChange = { newIndex ->
            coroutineScope.launch {
                pagerState.animateScrollToPage(newIndex)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 0.dp)
    )
}
