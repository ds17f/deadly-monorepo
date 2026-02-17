package com.grateful.deadly.feature.downloads.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.DownloadedShowViewModel
import com.grateful.deadly.core.model.DownloadsUiState
import com.grateful.deadly.feature.downloads.screens.main.models.DownloadsViewModel

@UnstableApi
@Composable
fun DownloadsScreen(
    onNavigateToPlaylist: (showId: String, recordingId: String) -> Unit = { _, _ -> },
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showRemoveAllDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveAllDialog,
            title = { Text("Remove All Downloads") },
            text = { Text("This will remove all downloaded shows and free up ${formatStorageSize(uiState.totalStorageUsed)} of storage. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::removeAllDownloads) {
                    Text("Remove All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveAllDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.isEmpty -> {
            EmptyDownloadsContent()
        }

        else -> {
            DownloadsContent(
                uiState = uiState,
                onShowClick = { show ->
                    val recordingId = show.recordingId
                    if (recordingId != null) {
                        onNavigateToPlaylist(show.showId, recordingId)
                    }
                },
                onCancelDownload = viewModel::cancelDownload,
                onRemoveDownload = viewModel::removeDownload,
                onRemoveAll = viewModel::showRemoveAllDialog
            )
        }
    }
}

@Composable
private fun EmptyDownloadsContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = IconResources.Content.DownloadForOffline(),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No downloads yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Download shows from your library or a playlist to listen offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun DownloadsContent(
    uiState: DownloadsUiState,
    onShowClick: (DownloadedShowViewModel) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onRemoveAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Header
        item {
            DownloadsHeader(
                totalStorageUsed = uiState.totalStorageUsed,
                downloadCount = uiState.totalDownloadCount
            )
        }

        // Active Downloads Section
        if (uiState.hasActiveDownloads) {
            item {
                Text(
                    text = "Downloading",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(uiState.activeDownloads, key = { "active-${it.showId}" }) { show ->
                ActiveDownloadItem(
                    show = show,
                    onClick = { onShowClick(show) },
                    onCancel = { onCancelDownload(show.showId) }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        // Completed Downloads Section
        if (uiState.completedDownloads.isNotEmpty()) {
            item {
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(uiState.completedDownloads, key = { "completed-${it.showId}" }) { show ->
                CompletedDownloadItem(
                    show = show,
                    onClick = { onShowClick(show) },
                    onRemove = { onRemoveDownload(show.showId) }
                )
            }
        }

        // Remove All Button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onRemoveAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    painter = IconResources.Content.Delete(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remove All Downloads")
            }
        }
    }
}

@Composable
private fun DownloadsHeader(
    totalStorageUsed: Long,
    downloadCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = formatStorageSize(totalStorageUsed) + " used",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$downloadCount show${if (downloadCount != 1) "s" else ""} downloaded",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveDownloadItem(
    show: DownloadedShowViewModel,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkWithBadge(
            recordingId = show.recordingId,
            coverImageUrl = show.coverImageUrl,
            isCompleted = false
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show.displayDate,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (show.venue.isNotEmpty()) {
                Text(
                    text = show.venue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val progress = show.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.overallProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${progress.tracksCompleted} of ${progress.tracksTotal} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onCancel) {
            Icon(
                painter = IconResources.Navigation.Close(),
                contentDescription = "Cancel download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompletedDownloadItem(
    show: DownloadedShowViewModel,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkWithBadge(
            recordingId = show.recordingId,
            coverImageUrl = show.coverImageUrl,
            isCompleted = true
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show.displayDate,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    if (show.venue.isNotEmpty()) append(show.venue)
                    if (show.location.isNotEmpty()) {
                        if (isNotEmpty()) append(" — ")
                        append(show.location)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatStorageSize(show.storageBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                painter = IconResources.Content.Delete(),
                contentDescription = "Remove download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArtworkWithBadge(
    recordingId: String?,
    coverImageUrl: String?,
    isCompleted: Boolean
) {
    Box {
        ShowArtwork(
            recordingId = recordingId,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            imageUrl = coverImageUrl
        )
        Icon(
            painter = if (isCompleted) {
                IconResources.Status.CheckCircle()
            } else {
                IconResources.Content.ArrowCircleDown()
            },
            contentDescription = if (isCompleted) "Downloaded" else "Downloading",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(50)
                ),
            tint = if (isCompleted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceAtMost(units.size - 1)
    return "%.1f %s".format(bytes / Math.pow(1024.0, index.toDouble()), units[index])
}
