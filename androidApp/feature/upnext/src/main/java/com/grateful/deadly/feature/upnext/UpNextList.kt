package com.grateful.deadly.feature.upnext

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.Show
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Show Queue list body — the single source of truth for the backlog UI, hosted
 * by the Favorites "Show Queue" tab.
 *
 * Drag the handle to reorder, swipe a row away to remove, tap to play, Clear (with
 * confirmation) to empty. The row is deliberately marker-agnostic (no "now playing"
 * row pinned to a fixed index) so Part 2's collection view can reuse it with a
 * mid-list pointer marker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextList(
    modifier: Modifier = Modifier,
    onNavigateToShow: (String) -> Unit = {},
    viewModel: UpNextViewModel = hiltViewModel(),
) {
    val shows by viewModel.shows.collectAsState()

    if (shows.isEmpty()) {
        EmptyShowQueue(modifier)
        return
    }

    // Local copy so a drag reorders smoothly; persisted to the repo on each move
    // (the observed flow re-emits the same order, so there's no fight).
    var ordered by remember { mutableStateOf(shows) }
    LaunchedEffect(shows) { ordered = shows }

    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<Show?>(null) }
    var selectedShow by remember { mutableStateOf<Show?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
        viewModel.reorder(ordered.map { it.id })
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (ordered.size == 1) "1 show" else "${ordered.size} shows",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showClearConfirm = true }) { Text("Clear") }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(ordered, key = { it.id }) { show ->
                ReorderableItem(reorderState, key = show.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
                    UpNextRow(
                        show = show,
                        elevation = elevation,
                        onPlay = { selectedShow = show },
                        onRequestRemove = { pendingRemove = show },
                        dragHandle = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(),
                            ) {
                                Icon(
                                    IconResources.Navigation.Menu(),
                                    contentDescription = "Reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    selectedShow?.let { show ->
        ModalBottomSheet(onDismissRequest = { selectedShow = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    text = "${show.date} — ${show.venue.name}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                QueueActionRow("Play Now", IconResources.PlayerControls.Play()) {
                    selectedShow = null; viewModel.playNow(show)
                }
                QueueActionRow("Move to Top", IconResources.Navigation.KeyboardArrowUp()) {
                    selectedShow = null; viewModel.moveToTop(show)
                }
                QueueActionRow("Go to Show", IconResources.Navigation.Forward()) {
                    selectedShow = null; onNavigateToShow(show.id)
                }
                QueueActionRow("Remove from Queue", IconResources.Navigation.Close(), destructive = true) {
                    selectedShow = null; pendingRemove = show
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear show queue?") },
            text = { Text("This removes all ${ordered.size} shows from your queue. Shows you're playing aren't affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clear()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }

    pendingRemove?.let { show ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove from queue?") },
            text = { Text("${show.date} — ${show.venue.name}") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemove = null
                    viewModel.remove(show.id)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpNextRow(
    show: Show,
    elevation: androidx.compose.ui.unit.Dp,
    onPlay: () -> Unit,
    onRequestRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    // Swipe asks for removal; the row snaps back (return false) and a confirm
    // dialog decides — so a swipe never deletes without confirmation.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onRequestRemove() }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    IconResources.Navigation.Close(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface(tonalElevation = elevation, shadowElevation = elevation) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
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
                        text = show.date,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = show.venue.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = show.location.displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                dragHandle()
            }
        }
    }
}

@Composable
private fun QueueActionRow(
    label: String,
    icon: Painter,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painter = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun EmptyShowQueue(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                IconResources.PlayerControls.ShowQueueMark(),
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
