package com.deadly.v2.feature.playlist.screens.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.component.CompactStarRating
import com.deadly.v2.core.model.RecordingOptionViewModel

/**
 * PlaylistV2RecordingOptionCard - Individual recording option card for V2 architecture
 * 
 * Based on V1 RecordingOptionCard but uses V2 View Models and follows V2 patterns.
 * Displays recording information with selection state and recommendation status.
 */
@Composable
fun PlaylistRecordingOptionCard(
    recordingOption: RecordingOptionViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                recordingOption.isSelected -> MaterialTheme.colorScheme.primaryContainer
                recordingOption.isRecommended -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (recordingOption.isSelected) {
            BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Line 1: Source type (bold) + rating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recordingOption.sourceType,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    recordingOption.rating?.let { rating ->
                        CompactStarRating(
                            rating = rating,
                            starSize = 12.dp
                        )
                    }
                }
                
                // Line 2: Taper info (only when we have actual taper name data)
                recordingOption.taperInfo?.let { taper ->
                    // Check if we have actual content after "Taper: "
                    val taperName = if (taper.startsWith("Taper: ")) {
                        taper.substring(7).trim()
                    } else {
                        taper.trim()
                    }
                    
                    val hasValidTaper = taperName.isNotBlank() && 
                                       !taperName.equals("unknown", ignoreCase = true) &&
                                       !taperName.equals("n/a", ignoreCase = true) &&
                                       taperName.length > 0
                    
                    if (hasValidTaper) {
                        Text(
                            text = taper,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Line 3: Technical details (equipment, quality) - if available
                recordingOption.technicalDetails?.let { details ->
                    val cleanDetails = details
                        .replace(Regex("<[^>]*>"), "") // Strip HTML tags
                        .replace(Regex("\\s+"), " ") // Normalize whitespace
                        .trim()
                    
                    if (cleanDetails.isNotBlank()) {
                        Text(
                            text = cleanDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Line 4: Archive ID (red-tinted, muted)
                Text(
                    text = recordingOption.identifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (recordingOption.isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}