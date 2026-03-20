package com.grateful.deadly.feature.player.screens.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.api.connect.ConnectConnectionState
import com.grateful.deadly.core.api.connect.ConnectDevice
import com.grateful.deadly.core.api.connect.ConnectService

/**
 * Connect device picker sheet.
 * Shows all connected devices and allows transferring playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerConnectSheet(
    connectService: ConnectService,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectService.connectionState.collectAsState()
    val devices by connectService.devices.collectAsState()
    val userState by connectService.userState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Devices",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            when (connectionState) {
                ConnectConnectionState.DISCONNECTED -> {
                    Text(
                        text = "Sign in to connect devices",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ConnectConnectionState.CONNECTING,
                ConnectConnectionState.RECONNECTING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Connecting...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ConnectConnectionState.CONNECTED -> {
                    if (devices.isEmpty()) {
                        Text(
                            text = "No other devices connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        devices.forEach { device ->
                            val isActive = device.deviceId == userState?.activeDeviceId
                            DeviceRow(
                                device = device,
                                isActive = isActive,
                                onClick = {
                                    if (!isActive) {
                                        // Transfer playback or claim
                                        val state = userState
                                        if (state != null) {
                                            connectService.playOnDevice(
                                                targetDeviceId = device.deviceId,
                                                state = com.grateful.deadly.core.api.connect.ConnectPlaybackState(
                                                    showId = state.showId,
                                                    recordingId = state.recordingId,
                                                    trackIndex = state.trackIndex,
                                                    positionMs = state.positionMs,
                                                    durationMs = state.durationMs,
                                                    trackTitle = state.trackTitle,
                                                    status = if (state.isPlaying) "playing" else "paused",
                                                    date = state.date,
                                                    venue = state.venue,
                                                    location = state.location,
                                                ),
                                            )
                                        }
                                        onDismiss()
                                    }
                                },
                            )
                        }

                        // Show parked state option — claim session locally
                        if (userState != null && userState?.activeDeviceId == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    connectService.claimSession()
                                    onDismiss()
                                },
                            ) {
                                Text("Resume playback here")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceRow(
    device: ConnectDevice,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Device icon
        val icon = when (device.type) {
            "ios" -> Icons.Default.PhoneIphone
            "android" -> Icons.Default.PhoneAndroid
            "web" -> Icons.Default.Computer
            else -> Icons.Default.Smartphone
        }
        Icon(
            imageVector = icon,
            contentDescription = device.type,
            tint = if (isActive) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Device name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = device.type.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Active indicator
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
