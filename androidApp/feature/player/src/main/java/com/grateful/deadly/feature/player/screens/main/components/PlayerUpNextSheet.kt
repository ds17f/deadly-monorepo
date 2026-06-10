package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.feature.player.screens.main.models.PlayerViewModel

/**
 * "Up Next" — the persistent show queue (ADR-0010 §6), opened from the player.
 * Distinct from [PlayerQueueSheet], which lists tracks within the current show.
 * Tapping a show plays it (and pops it from the queue via the playback layer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerUpNextSheet(
    items: List<PlayerViewModel.QueuedShowUi>,
    onPlayShow: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (items.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "Queue is empty. Add shows from a show's menu — they play in order, and each leaves the queue once it plays.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(items, key = { it.entryId }) { item ->
                        ListItem(
                            headlineContent = { Text(item.show.date, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    "${item.show.venue.name} • ${item.show.location.displayText}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemove(item.entryId) }) {
                                    Icon(
                                        painter = IconResources.Navigation.Close(),
                                        contentDescription = "Remove from queue"
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onPlayShow(item.show.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
