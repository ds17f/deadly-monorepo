package com.deadly.v2.feature.library.screens.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.model.LibraryShowViewModel
import com.deadly.v2.core.model.LibraryDownloadStatus

/**
 * V2 Library Show List Item Component
 * 
 * Focused component for displaying library shows in list view.
 * Shows date, venue, location, pin status, and download status.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryShowListItem(
    show: LibraryShowViewModel,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album cover placeholder
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = IconResources.PlayerControls.AlbumArt(),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Show info - 2 line layout
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Line 1: Date • City, State with status icons
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pin indicator
                    if (show.isPinned) {
                        Icon(
                            painter = IconResources.Content.PushPin(),
                            contentDescription = "Pinned",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    // Download indicator
                    if (show.downloadStatus == LibraryDownloadStatus.COMPLETED) {
                        Icon(
                            painter = IconResources.Status.CheckCircle(),
                            contentDescription = "Downloaded",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    Text(
                        text = "${show.displayDate} • ${show.location}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Line 2: Venue
                Text(
                    text = show.venue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Line 3: Rating if available
                show.rating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            painter = IconResources.Content.Star(),
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (show.reviewCount > 0) {
                            Text(
                                text = " (${show.reviewCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * V2 Library Show Grid Item Component
 * 
 * Focused component for displaying library shows in grid view.
 * Compact layout showing essential information.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryShowGridItem(
    show: LibraryShowViewModel,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Album cover - square aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = IconResources.PlayerControls.AlbumArt(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Text section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Line 1: Date with status icons
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pin indicator
                    if (show.isPinned) {
                        Icon(
                            painter = IconResources.Content.PushPin(),
                            contentDescription = "Pinned",
                            modifier = Modifier.size(8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    
                    // Download indicator
                    if (show.downloadStatus == LibraryDownloadStatus.COMPLETED) {
                        Icon(
                            painter = IconResources.Status.CheckCircle(),
                            contentDescription = "Downloaded",
                            modifier = Modifier.size(8.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    
                    Text(
                        text = show.date,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Line 2: Venue
                Text(
                    text = show.venue,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Line 3: Location
                Text(
                    text = show.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}