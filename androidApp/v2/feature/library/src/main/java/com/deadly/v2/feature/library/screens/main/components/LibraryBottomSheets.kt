package com.deadly.v2.feature.library.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.model.LibraryShowViewModel
import com.deadly.v2.core.model.LibrarySortOption
import com.deadly.v2.core.model.LibrarySortDirection
import com.deadly.v2.core.model.LibraryDownloadStatus

/**
 * V2 Add to Library Bottom Sheet Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToLibraryBottomSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Add to Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Coming soon! This will allow you to add shows to your library.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * V2 Sort Options Bottom Sheet Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionsBottomSheet(
    currentSortOption: LibrarySortOption,
    currentSortDirection: LibrarySortDirection,
    onSortOptionSelected: (LibrarySortOption, LibrarySortDirection) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sort library by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Sort options
            LibrarySortOption.values().forEach { option ->
                Column {
                    // Sort option header
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    // Direction options
                    LibrarySortDirection.values().forEach { direction ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSortOptionSelected(option, direction)
                                }
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentSortOption == option && currentSortDirection == direction,
                                onClick = {
                                    onSortOptionSelected(option, direction)
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = direction.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    if (option != LibrarySortOption.values().last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * V2 Show Actions Bottom Sheet Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowActionsBottomSheet(
    show: LibraryShowViewModel,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = show.displayDate,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = show.venue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = show.location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                
                // Share
                ListItem(
                    headlineContent = { Text("Share") },
                    leadingContent = {
                        Icon(
                            painter = IconResources.Content.Share(),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { onShare() }
                )
                
                // Pin/Unpin
                if (show.isPinned) {
                    ListItem(
                        headlineContent = { Text("Unpin") },
                        leadingContent = {
                            Icon(
                                painter = IconResources.Content.StarBorder(),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable { onUnpin() }
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("Pin to Top") },
                        leadingContent = {
                            Icon(
                                painter = IconResources.Content.PushPin(),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable { onPin() }
                    )
                }
                
                // Download/Remove Download
                when (show.downloadStatus) {
                    LibraryDownloadStatus.NOT_DOWNLOADED, LibraryDownloadStatus.FAILED -> {
                        ListItem(
                            headlineContent = { Text("Download") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Content.FileDownload(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable { onDownload() }
                        )
                    }
                    LibraryDownloadStatus.COMPLETED -> {
                        ListItem(
                            headlineContent = { Text("Remove Download") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Content.Delete(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable { onRemoveDownload() }
                        )
                    }
                    LibraryDownloadStatus.DOWNLOADING, LibraryDownloadStatus.QUEUED -> {
                        ListItem(
                            headlineContent = { Text("Cancel Download") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Navigation.Close(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable { onRemoveDownload() }
                        )
                    }
                    else -> {
                        // Other states - show download option
                        ListItem(
                            headlineContent = { Text("Download") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Content.FileDownload(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable { onDownload() }
                        )
                    }
                }
                
                HorizontalDivider()
                
                // Remove from Library
                ListItem(
                    headlineContent = { 
                        Text(
                            "Remove from Library",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = IconResources.Content.Delete(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { onRemoveFromLibrary() }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}