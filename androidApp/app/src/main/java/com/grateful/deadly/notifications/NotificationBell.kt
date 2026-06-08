package com.grateful.deadly.notifications

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Top-bar bell with a persistent unread badge (decision A). Tapping navigates
 * to the full notifications inbox screen — opening it no longer clears the
 * badge; only reading/archiving does. See PLANS/in-app-messaging.md.
 */
@Composable
fun NotificationBell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val unread by viewModel.unread.collectAsState()

    IconButton(onClick = onClick, modifier = modifier) {
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
}
