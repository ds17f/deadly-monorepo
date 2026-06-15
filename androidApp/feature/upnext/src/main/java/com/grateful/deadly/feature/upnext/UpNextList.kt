package com.grateful.deadly.feature.upnext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.Show

/**
 * Up Next list body — the single source of truth for the backlog UI. Hosted by
 * the standalone [UpNextScreen] (menu push) and the Favorites "Up Next" tab.
 *
 * Tap to play, reorder via up/down, remove, clear. The row is deliberately
 * marker-agnostic (no "now playing" row pinned to a fixed index) so Part 2's
 * collection view can reuse it with a mid-list pointer marker.
 */
@Composable
fun UpNextList(
    modifier: Modifier = Modifier,
    viewModel: UpNextViewModel = hiltViewModel(),
) {
    val shows by viewModel.shows.collectAsState()

    if (shows.isEmpty()) {
        EmptyUpNext(modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (shows.size == 1) "1 show" else "${shows.size} shows",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::clear) { Text("Clear") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(shows, key = { it.id }) { show ->
                val index = shows.indexOfFirst { it.id == show.id }
                UpNextRow(
                    show = show,
                    canMoveUp = index > 0,
                    canMoveDown = index < shows.lastIndex,
                    onPlay = { viewModel.play(show) },
                    onRemove = { viewModel.remove(show.id) },
                    onMoveUp = { viewModel.reorder(shows.swapped(index, index - 1).map { it.id }) },
                    onMoveDown = { viewModel.reorder(shows.swapped(index, index + 1).map { it.id }) },
                )
            }
        }
    }
}

@Composable
private fun UpNextRow(
    show: Show,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            ShowArtwork(
                recordingId = show.bestRecordingId,
                contentDescription = "Cover",
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show.venue.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${show.date} • ${show.location.displayText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(IconResources.Navigation.KeyboardArrowUp(), contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(IconResources.Navigation.KeyboardArrowDown(), contentDescription = "Move down")
        }
        IconButton(onClick = onRemove) {
            Icon(IconResources.Navigation.Close(), contentDescription = "Remove")
        }
    }
}

@Composable
private fun EmptyUpNext(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                IconResources.PlayerControls.Queue(),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "Your show queue is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Long-press a show or use \"Add to Show Queue\" and it plays after the current one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun <T> List<T>.swapped(i: Int, j: Int): List<T> =
    toMutableList().apply { val t = this[i]; this[i] = this[j]; this[j] = t }
