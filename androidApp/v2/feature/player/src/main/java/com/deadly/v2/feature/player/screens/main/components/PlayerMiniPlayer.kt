package com.deadly.v2.feature.player.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.feature.player.screens.main.models.PlayerUiState

/**
 * Mini Player component that appears when scrolling past media controls
 * Shows current track, play/pause button, and progress bar with recording-based colors
 */
@Composable
fun PlayerMiniPlayer(
    uiState: PlayerUiState,
    recordingId: String?,
    onPlayPause: () -> Unit,
    onTapToExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use the medium color from the recording's color stack for consistency
    val colors = getRecordingColorStack(recordingId)
    val backgroundColor = colors[1] // Index 1: Medium color (alpha 0.4f)
    
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable { onTapToExpand() },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column {
            // Main content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track info (clickable area for expand)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTapToExpand() }
                ) {
                    Text(
                        text = uiState.trackDisplayInfo.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.trackDisplayInfo.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Play/Pause button (NOT clickable for expansion)
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = if (uiState.isPlaying) {
                            IconResources.PlayerControls.Pause()
                        } else {
                            IconResources.PlayerControls.Play()
                        },
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Progress bar at bottom (without thumb)
            LinearProgressIndicator(
                progress = uiState.progressDisplayInfo.progressPercentage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Get the complete color stack for a recording
 * Returns list of solid colors that can be used by different components consistently
 * Uses color blending instead of alpha transparency for better UI visibility
 */
@Composable
private fun getRecordingColorStack(recordingId: String?): List<Color> {
    val baseColor = recordingIdToColor(recordingId)
    val background = MaterialTheme.colorScheme.background
    
    return listOf(
        androidx.compose.ui.graphics.lerp(background, baseColor, 0.8f),  // Index 0: Strong blend
        androidx.compose.ui.graphics.lerp(background, baseColor, 0.4f),  // Index 1: Medium blend  
        androidx.compose.ui.graphics.lerp(background, baseColor, 0.1f),  // Index 2: Faint blend
        background,                                                      // Index 3: Background
        background                                                       // Index 4: Background
    )
}

/**
 * Convert recordingId to a consistent base color using hash function
 */
private fun recordingIdToColor(recordingId: String?): Color {
    if (recordingId.isNullOrEmpty()) return DeadRed
    
    val hash = recordingId.hashCode()
    val index = kotlin.math.abs(hash) % GradientColors.size
    return GradientColors[index]
}

// Grateful Dead inspired color palette for gradients (from Theme.kt)
private val DeadRed = Color(0xFFDC143C)      // Crimson red
private val DeadGold = Color(0xFFFFD700)     // Golden yellow  
private val DeadGreen = Color(0xFF228B22)    // Forest green
private val DeadBlue = Color(0xFF4169E1)     // Royal blue
private val DeadPurple = Color(0xFF8A2BE2)   // Blue violet

private val GradientColors = listOf(DeadGreen, DeadGold, DeadRed, DeadBlue, DeadPurple)