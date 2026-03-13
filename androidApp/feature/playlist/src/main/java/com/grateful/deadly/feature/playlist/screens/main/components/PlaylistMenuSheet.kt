package com.grateful.deadly.feature.playlist.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Playlist menu bottom sheet that appears when triple dot menu is tapped.
 * Follows standard design patterns with Share and Choose Recording options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistMenuSheet(
    showDate: String?,
    venue: String?,
    location: String?,
    isFavorite: Boolean,
    onFavoritesClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSetlistClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onShareClick: () -> Unit,
    onChooseRecordingClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Favorites option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onFavoritesClick()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = if (isFavorite) IconResources.Content.Favorite() else IconResources.Content.FavoriteBorder(),
                    contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Download option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDownloadClick()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = IconResources.Content.FileDownload(),
                    contentDescription = "Download",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Download",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Setlist option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSetlistClick()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = IconResources.Content.FormatListBulleted(),
                    contentDescription = "Setlist",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Setlist",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Collections option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onCollectionsClick()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = IconResources.Navigation.Collections(),
                    contentDescription = "Collections",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Share option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onShareClick()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = IconResources.Content.Share(),
                    contentDescription = "Share",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Share",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Choose Recording option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChooseRecordingClick()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = IconResources.Content.Favorite(),
                    contentDescription = "Choose Recording",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Choose Recording",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Equalizer option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEqualizerClick()
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = IconResources.PlayerControls.Equalizer(),
                    contentDescription = "Equalizer",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
