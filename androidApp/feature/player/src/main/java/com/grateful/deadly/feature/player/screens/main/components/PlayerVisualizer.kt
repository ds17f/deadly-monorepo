package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val NUM_POINTS = 64
private const val SMOOTHING = 0.5f
private const val MIN_RADIUS_FRACTION = 0.20f
private const val MAX_RADIUS_FRACTION = 0.50f
private const val TWO_PI = (2.0 * PI).toFloat()

// Amplification: Android Visualizer FFT magnitudes are very small,
// so we boost them aggressively and clamp to 0–1.
private const val MAGNITUDE_BOOST = 4.0f

// Psychedelic Grateful Dead palette
private val BLOOM_COLORS_OUTER = listOf(
    Color(0xCCE53935), // Red
    Color(0xCCFF6F00), // Amber
    Color(0xCCFFD600), // Gold
    Color(0xCC43A047), // Green
    Color(0xCC1E88E5), // Blue
    Color(0xCC8E24AA), // Purple
    Color(0xCCE53935), // Red (wrap)
)

private val BLOOM_COLORS_INNER = listOf(
    Color(0x80FF8A80), // Light red
    Color(0x80FFD180), // Light amber
    Color(0x80FFFF8D), // Light gold
    Color(0x80A5D6A7), // Light green
    Color(0x8090CAF9), // Light blue
    Color(0x80CE93D8), // Light purple
    Color(0x80FF8A80), // Light red (wrap)
)

/**
 * Radial frequency bloom overlay. Draws an organic pulsing shape
 * driven by FFT magnitude data — 64 points arranged in a circle,
 * connected by smooth bezier curves. Dual-layer for depth.
 */
@Composable
fun PlayerVisualizer(
    fftMagnitudes: FloatArray,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "vizRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Downsample FFT bins to NUM_POINTS with exponential smoothing.
    // Use mutableStateOf so mutations trigger recomposition/redraw.
    var smoothed by remember { mutableStateOf(FloatArray(NUM_POINTS)) }
    val binCount = fftMagnitudes.size.coerceAtLeast(1)
    val newSmoothed = smoothed.copyOf()
    for (i in 0 until NUM_POINTS) {
        val fftIndex = (i.toFloat() / NUM_POINTS * binCount).toInt().coerceIn(0, binCount - 1)
        val raw = if (fftMagnitudes.isNotEmpty()) {
            (fftMagnitudes[fftIndex] * MAGNITUDE_BOOST).coerceIn(0f, 1f)
        } else 0f
        newSmoothed[i] = newSmoothed[i] + SMOOTHING * (raw - newSmoothed[i])
    }
    smoothed = newSmoothed

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = min(cx, cy)
        val minR = baseRadius * MIN_RADIUS_FRACTION
        val maxR = baseRadius * MAX_RADIUS_FRACTION

        // Outer bloom layer (thicker, more saturated)
        drawBloomPath(
            cx = cx,
            cy = cy,
            magnitudes = smoothed,
            minRadius = minR,
            maxRadius = maxR,
            rotationOffset = rotation,
            colors = BLOOM_COLORS_OUTER,
            strokeWidth = 4f
        )

        // Inner bloom layer (thinner, lighter, slightly smaller)
        drawBloomPath(
            cx = cx,
            cy = cy,
            magnitudes = smoothed,
            minRadius = minR * 0.85f,
            maxRadius = maxR * 0.85f,
            rotationOffset = -rotation * 0.7f,
            colors = BLOOM_COLORS_INNER,
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawBloomPath(
    cx: Float,
    cy: Float,
    magnitudes: FloatArray,
    minRadius: Float,
    maxRadius: Float,
    rotationOffset: Float,
    colors: List<Color>,
    strokeWidth: Float
) {
    val n = magnitudes.size
    if (n < 3) return

    val points = Array(n) { i ->
        val angle = (i.toFloat() / n) * TWO_PI + rotationOffset
        val r = minRadius + (maxRadius - minRadius) * magnitudes[i]
        Offset(cx + cos(angle) * r, cy + sin(angle) * r)
    }

    val path = Path().apply {
        // Start at midpoint between last and first point
        val startX = (points[n - 1].x + points[0].x) / 2f
        val startY = (points[n - 1].y + points[0].y) / 2f
        moveTo(startX, startY)

        for (i in 0 until n) {
            val current = points[i]
            val next = points[(i + 1) % n]
            val midX = (current.x + next.x) / 2f
            val midY = (current.y + next.y) / 2f
            quadraticBezierTo(current.x, current.y, midX, midY)
        }
        close()
    }

    val brush = Brush.sweepGradient(
        colors = colors,
        center = Offset(cx, cy)
    )

    drawPath(
        path = path,
        brush = brush,
        style = Stroke(width = strokeWidth)
    )
}
