package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.api.connect.ConnectConnectionState
import com.grateful.deadly.core.api.connect.ConnectDevice
import com.grateful.deadly.core.api.connect.UserPlaybackState
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Connect Bottom Sheet for Player.
 *
 * Shows connected devices and allows transferring playback. Playback controls
 * (play/pause/next/prev) are intentionally omitted — they're already in the
 * player itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerConnectSheet(
    connectionState: ConnectConnectionState,
    devices: List<ConnectDevice>,
    userState: UserPlaybackState?,
    localDeviceId: String,
    onPlayOnDevice: (String) -> Unit,
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Connect",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = when (connectionState) {
                    ConnectConnectionState.CONNECTED -> "Connected"
                    ConnectConnectionState.CONNECTING -> "Connecting…"
                    ConnectConnectionState.RECONNECTING -> "Reconnecting…"
                    ConnectConnectionState.DISCONNECTED -> "Disconnected"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Now Playing — where the stream currently is
            val trackTitle = userState?.trackTitle
            if (userState != null && trackTitle != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = IconResources.Content.Cast(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = trackTitle,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val deviceLine = buildString {
                            userState.activeDeviceName?.let { append(it) }
                            userState.date?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        }
                        if (deviceLine.isNotEmpty()) {
                            Text(
                                text = deviceLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Device list
            if (devices.isEmpty()) {
                Text(
                    text = "No devices connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                devices.forEach { device ->
                    val isActive = userState?.activeDeviceId == device.deviceId
                    val isLocal = device.deviceId == localDeviceId
                    val canTransfer = !isActive

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (canTransfer) Modifier.clickable { onPlayOnDevice(device.deviceId) }
                                else Modifier
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = when (device.type) {
                                "ios" -> IconResources.Content.Cast()
                                "android" -> IconResources.Content.Cast()
                                else -> IconResources.Content.Cast()
                            },
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isLocal) "${device.type.replaceFirstChar { it.uppercase() }} · This device"
                                else device.type.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canTransfer) {
                            Icon(
                                painter = IconResources.PlayerControls.Play(),
                                contentDescription = "Play on this device",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
