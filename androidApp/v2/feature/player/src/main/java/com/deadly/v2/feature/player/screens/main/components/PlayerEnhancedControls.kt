package com.deadly.v2.feature.player.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources

// Temporary enum for repeat mode (will be moved to proper model later)
enum class RepeatMode {
    NONE, ALL, ONE
}

/**
 * Enhanced primary controls with larger buttons and proper layout
 */
@Composable
fun PlayerEnhancedControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatModeChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle - Far left
        IconButton(
            onClick = onShuffleToggle,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = IconResources.PlayerControls.Shuffle(),
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        
        // Spacer to push center controls
        Spacer(modifier = Modifier.weight(1f))
        
        // Previous - Larger (always enabled)
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                painter = IconResources.PlayerControls.SkipPrevious(),
                contentDescription = "Previous",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Play/Pause - Large circular FAB-style button with loading state
        FloatingActionButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = if (isPlaying) {
                        IconResources.PlayerControls.Pause()
                    } else {
                        IconResources.PlayerControls.Play()
                    },
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Next - Larger
        IconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                painter = IconResources.PlayerControls.SkipNext(),
                contentDescription = "Next",
                modifier = Modifier.size(36.dp),
                tint = if (hasNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
        
        // Spacer to push repeat to far right
        Spacer(modifier = Modifier.weight(1f))
        
        // Repeat - Far right
        IconButton(
            onClick = onRepeatModeChange,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = when (repeatMode) {
                    RepeatMode.ONE -> IconResources.PlayerControls.RepeatOne()
                    else -> IconResources.PlayerControls.Repeat()
                },
                contentDescription = "Repeat mode",
                tint = when (repeatMode) {
                    RepeatMode.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                    RepeatMode.ALL, RepeatMode.ONE -> MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}