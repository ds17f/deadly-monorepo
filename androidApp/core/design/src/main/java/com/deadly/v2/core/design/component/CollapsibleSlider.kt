package com.deadly.v2.core.design.component

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * CollapsibleSlider - Minimalist slider that collapses to a handle
 * 
 * Features:
 * - Starts as a small rounded handle (120dp x 8dp)
 * - Expands to full slider when tapped
 * - Auto-collapses after 3 seconds of inactivity
 * - AnimatedContent with fade + expand/shrink transitions
 * - Material3 theming with proper color schemes
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CollapsibleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn() + expandVertically() with fadeOut() + shrinkVertically()
            },
            label = "sliderExpand"
        ) { isExpanded ->
            if (isExpanded) {
                // Full slider
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
            } else {
                // Collapsed "handle"
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        .clickable { expanded = true }
                )
            }
        }
    }

    // Auto-collapse when not touching slider
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(3000) // give user a few seconds
            expanded = false
        }
    }
}

/**
 * ShuttleScrubber - iPod-inspired drag-to-scrub navigation control
 * 
 * Features:
 * - Horizontal drag gesture with direct finger tracking
 * - Visual progress indicator that slides horizontally
 * - Count display that updates during interaction
 * - Thin rounded track with sliding indicator
 * - Perfect for large item sets requiring quick navigation
 */
@Composable
fun ShuttleScrubber(
    currentIndex: Int,
    total: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Count display
        Text(
            text = "${currentIndex + 1} of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            val trackWidth = 200.dp
            val indicatorWidth = 40.dp
            val trackWidthPx = with(LocalDensity.current) { trackWidth.toPx() }
            
            // Calculate progress (0f to 1f)
            val progress = if (total > 1) currentIndex.toFloat() / (total - 1) else 0f
            
            // Track background
            Box(
                modifier = Modifier
                    .width(trackWidth)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    .pointerInput(total) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                // Calculate position from absolute touch offset
                                val trackStartX = (size.width - trackWidthPx) / 2
                                val touchX = (offset.x - trackStartX).coerceIn(0f, trackWidthPx)
                                val newProgress = touchX / trackWidthPx
                                val newIndex = (newProgress * (total - 1)).toInt().coerceIn(0, total - 1)
                                onIndexChange(newIndex)
                            },
                            onDrag = { change, _ ->
                                // Use absolute position, not delta
                                val trackStartX = (size.width - trackWidthPx) / 2
                                val touchX = (change.position.x - trackStartX).coerceIn(0f, trackWidthPx)
                                val newProgress = touchX / trackWidthPx
                                val newIndex = (newProgress * (total - 1)).toInt().coerceIn(0, total - 1)
                                onIndexChange(newIndex)
                            },
                            onDragEnd = {
                                isDragging = false
                            }
                        )
                    }
            )
            
            // Sliding indicator
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(10.dp)
                    .offset(
                        x = ((trackWidth - indicatorWidth) * progress) - ((trackWidth - indicatorWidth) / 2)
                    )
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isDragging) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
            )
        }
    }
}