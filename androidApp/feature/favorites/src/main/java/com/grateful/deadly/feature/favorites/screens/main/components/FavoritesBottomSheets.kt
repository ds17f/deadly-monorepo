package com.grateful.deadly.feature.favorites.screens.main.components

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
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.FavoriteShowViewModel
import com.grateful.deadly.core.model.FavoritesSortOption
import com.grateful.deadly.core.model.FavoritesSortDirection
import com.grateful.deadly.core.model.FavoritesDownloadStatus

/**
 * Add to Favorites Bottom Sheet Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToFavoritesBottomSheet(
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
                text = "Add to Favorites",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Coming soon! This will allow you to add shows to your favorites.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Sort Options Bottom Sheet Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionsBottomSheet(
    currentSortOption: FavoritesSortOption,
    currentSortDirection: FavoritesSortDirection,
    onSortOptionSelected: (FavoritesSortOption, FavoritesSortDirection) -> Unit,
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
                text = "Sort favorites by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Sort options
            FavoritesSortOption.values().forEach { option ->
                Column {
                    // Sort option header
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Direction options
                    FavoritesSortDirection.values().forEach { direction ->
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

                    if (option != FavoritesSortOption.values().last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Show Actions Bottom Sheet Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowActionsBottomSheet(
    show: FavoriteShowViewModel,
    onDismiss: () -> Unit,
    onShowQrCode: () -> Unit,
    onReviewShow: () -> Unit,
    onRemoveFromFavorites: () -> Unit,
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

                // Review
                ListItem(
                    headlineContent = { Text(if (show.hasReview) "Edit Review" else "Add Review") },
                    leadingContent = {
                        Icon(
                            painter = if (show.hasReview) IconResources.Content.Star() else IconResources.Content.StarBorder(),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { onReviewShow() }
                )

                // Share
                ListItem(
                    headlineContent = { Text("Share") },
                    leadingContent = {
                        Icon(
                            painter = IconResources.Content.Share(),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { onShowQrCode() }
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
                    FavoritesDownloadStatus.NOT_DOWNLOADED, FavoritesDownloadStatus.FAILED -> {
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
                    FavoritesDownloadStatus.COMPLETED -> {
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
                    FavoritesDownloadStatus.DOWNLOADING, FavoritesDownloadStatus.QUEUED -> {
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

                // Remove from Favorites
                ListItem(
                    headlineContent = {
                        Text(
                            "Remove from Favorites",
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
                    modifier = Modifier.clickable { onRemoveFromFavorites() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
