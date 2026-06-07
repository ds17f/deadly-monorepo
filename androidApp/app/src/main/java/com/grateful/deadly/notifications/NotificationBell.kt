package com.grateful.deadly.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.notifications.CachedNotification
import com.grateful.deadly.core.notifications.active
import com.grateful.deadly.core.notifications.dismissed
import com.grateful.deadly.core.notifications.unreadCount

/**
 * Top-bar bell with an unread badge. Tapping opens the in-app messaging inbox
 * (active list + a dismissed archive) and clears the badge. Faithful port of
 * the web `NotificationBell`. See PLANS/in-app-messaging.md.
 */
@Composable
fun NotificationBell(
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()
    var showInbox by remember { mutableStateOf(false) }
    val unread = remember(notifications) { notifications.unreadCount() }

    IconButton(
        onClick = {
            showInbox = true
            viewModel.markAllSeen()
        },
        modifier = modifier,
    ) {
        BadgedBox(
            badge = {
                if (unread > 0) {
                    Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                }
            }
        ) {
            Icon(
                painter = IconResources.vectorIcon(Icons.Outlined.Notifications),
                contentDescription = if (unread > 0) "Notifications, $unread unread" else "Notifications",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (showInbox) {
        NotificationInboxSheet(
            notifications = notifications,
            onDismissMessage = viewModel::dismiss,
            onClose = { showInbox = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationInboxSheet(
    notifications: List<CachedNotification>,
    onDismissMessage: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val active = remember(notifications) { notifications.active() }
    val archived = remember(notifications) { notifications.dismissed() }
    var showArchive by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (active.isEmpty()) {
                Text(
                    text = "You're all caught up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                active.forEach { message ->
                    NotificationRow(
                        message = message,
                        onDismiss = { onDismissMessage(message.id) },
                    )
                }
            }

            if (archived.isNotEmpty()) {
                TextButton(onClick = { showArchive = !showArchive }) {
                    Text(if (showArchive) "Hide dismissed (${archived.size})" else "Show dismissed (${archived.size})")
                }
                if (showArchive) {
                    archived.forEach { message ->
                        NotificationRow(message = message, onDismiss = null, muted = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    message: CachedNotification,
    onDismiss: (() -> Unit)?,
    muted: Boolean = false,
) {
    val accent = if (message.level == "warn") {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentAlpha = if (muted) 0.6f else 1f

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent.copy(alpha = contentAlpha),
                )
                // Body renders newlines as-is (Text honors \n by default).
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.Top),
                ) {
                    Icon(
                        painter = IconResources.Navigation.Close(),
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
