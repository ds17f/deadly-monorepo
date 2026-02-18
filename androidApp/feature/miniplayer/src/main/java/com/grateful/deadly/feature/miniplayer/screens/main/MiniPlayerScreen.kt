package com.grateful.deadly.feature.miniplayer.screens.main

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.feature.miniplayer.screens.main.models.MiniPlayerViewModel
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/**
 * MiniPlayer Screen
 * 
 * MiniPlayer implementation.
 * Direct ViewModel integration following established patterns.
 * 
 * No container component - service layer handles business logic.
 */
@Composable
fun MiniPlayerScreen(
    onTapToExpand: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle errors with auto-clear
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            Log.e("MiniPlayerScreen", "Error: $error")
            delay(3000) // Show error for 3 seconds
            viewModel.clearError()
        }
    }
    
    // Only show MiniPlayer when there's a current track
    if (!uiState.shouldShow || uiState.currentTrack == null) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                viewModel.onTapToExpand()
                onTapToExpand(uiState.showId) // Use showId for navigation
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D1B1B) // Custom dark red-brown for MiniPlayer
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork thumbnail
                ShowArtwork(
                    recordingId = uiState.currentTrack?.recordingId,
                    imageUrl = uiState.currentTrack?.coverImageUrl,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Track information
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    uiState.currentTrack?.let { track ->
                        Text(
                            text = track.songTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = track.displaySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Play/pause button with loading state
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(40.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (uiState.isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Progress bar at bottom - always show when track is loaded
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        }
    }
}