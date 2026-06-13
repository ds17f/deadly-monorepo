package com.grateful.deadly.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.grateful.deadly.core.notifications.CachedNotification
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen in-app messaging inbox (decision B). A list of messages →
 * tap a row to read its detail (marks it read). Inbox/Archived toggle, bulk
 * mark-all-read / archive-all, and a community footer. See
 * PLANS/in-app-messaging.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val active by viewModel.active.collectAsState()
    val archived by viewModel.archived.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    var showArchive by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val list = if (showArchive) archived else active
    val selected = remember(selectedId, active, archived) {
        (active + archived).firstOrNull { it.id == selectedId }
    }

    // Sync whenever the inbox is opened, so it reflects cross-device state +
    // retirements immediately (not just on cold start / foreground / pull).
    LaunchedEffect(Unit) { viewModel.syncOnOpen() }

    // Detail open → back closes detail first, then leaves the screen.
    BackHandler(enabled = selected != null) { selectedId = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected != null) "Message" else if (showArchive) "Archived" else "Notifications") },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) selectedId = null else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selected == null && !showArchive && archived.isNotEmpty()) {
                        TextButton(onClick = { showArchive = true }) { Text("Archived (${archived.size})") }
                    } else if (selected == null && showArchive) {
                        TextButton(onClick = { showArchive = false }) { Text("Inbox") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (selected != null) {
                NotificationDetail(
                    message = selected,
                    archived = showArchive,
                    onArchive = {
                        viewModel.archive(selected)
                        selectedId = null
                    },
                    onLinkTap = { url -> viewModel.onLinkTap(selected.id, url) },
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (!showArchive && active.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { viewModel.markAllSeen() },
                                    enabled = active.any { it.seenAt == null },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                ) { Text("Mark all read", style = MaterialTheme.typography.labelLarge) }
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = { viewModel.archiveAll() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                ) { Text("Archive all", style = MaterialTheme.typography.labelLarge) }
                            }
                        }

                        // Always a LazyColumn so the pull-to-refresh nested scroll
                        // works even when the inbox is empty (pull to check for new).
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            if (list.isEmpty()) {
                                item {
                                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            if (showArchive) "Nothing archived." else "You're all caught up.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                // One inset, rounded card holding all rows — matches
                                // the iOS grouped List look (separators inset past
                                // the leading icon).
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 2.dp,
                                    ) {
                                        Column {
                                            list.forEachIndexed { index, m ->
                                                // First time this row renders, count one impression.
                                                LaunchedEffect(m.id) { viewModel.onImpression(m) }
                                                NotificationRow(
                                                    message = m,
                                                    unread = !showArchive && m.seenAt == null,
                                                    onClick = {
                                                        viewModel.open(m)
                                                        selectedId = m.id
                                                    },
                                                )
                                                if (index < list.lastIndex) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(start = 44.dp),
                                                        color = MaterialTheme.colorScheme.outlineVariant
                                                            .copy(alpha = 0.4f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Always present (empty inbox + archive too), matching iOS.
                        CommunityFooter(onTap = { viewModel.onCommunityTap() })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    message: CachedNotification,
    unread: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = categoryIcon(message.category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
        )
        if (unread) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                    // Explicit, since the card's surfaceVariant container would
                    // otherwise mute the title to onSurfaceVariant.
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = timeAgo(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = preview(message.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NotificationDetail(
    message: CachedNotification,
    archived: Boolean,
    onArchive: () -> Unit,
    onLinkTap: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = categoryIcon(message.category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                message.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            timeAgo(message.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Body renders newlines as-is and turns links tappable.
        Text(
            text = notificationBody(message.body, MaterialTheme.colorScheme.primary) { url ->
                onLinkTap(url)
                uriHandler.openUri(url)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!archived) {
            OutlinedButton(onClick = onArchive) { Text("Archive") }
        }
    }
}

@Composable
private fun CommunityFooter(onTap: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = {
            onTap()
            uriHandler.openUri(Community.SUBREDDIT_URL)
        }) {
            Text("More at ${Community.SUBREDDIT_HANDLE} →")
        }
    }
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "release" -> Icons.Outlined.RocketLaunch
    "feature" -> Icons.Outlined.AutoAwesome
    "outage" -> Icons.Outlined.WarningAmber
    else -> Icons.Outlined.Campaign
}

private fun preview(body: String): String {
    val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
    return if (firstLine.length > 120) firstLine.take(120) + "…" else firstLine
}

private fun timeAgo(createdAtSecs: Long): String {
    val secs = System.currentTimeMillis() / 1000 - createdAtSecs
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60}m ago"
        secs < 86_400 -> "${secs / 3600}h ago"
        secs < 604_800 -> "${secs / 86_400}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(createdAtSecs * 1000))
    }
}
