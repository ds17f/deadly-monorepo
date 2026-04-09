package com.grateful.deadly.feature.settings.screens.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.model.ConnectDevice
import com.grateful.deadly.core.model.ConnectState

@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val connectState by viewModel.connectState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isActiveDevice by viewModel.isActiveDevice.collectAsState()
    val pendingTransfer by viewModel.pendingTransfer.collectAsState()
    val installId = viewModel.installId

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = {
                    Text(if (isConnected) "Connected" else "Disconnected")
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .then(
                                Modifier.wrapContentSize(Alignment.Center)
                            )
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = if (isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                        ) {}
                    }
                }
            )
        }

        item {
            HorizontalDivider()
        }

        if (devices.isEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text("No devices connected") },
                    supportingContent = {
                        Text(
                            "Other devices signed in to the same account will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        } else {
            item {
                Text(
                    text = "DEVICES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            val hasSession = connectState?.showId != null
            val isRemoteControlling = connectState?.activeDeviceId != null && !isActiveDevice
            items(devices) { device ->
                val isMe = device.deviceId == installId
                val isDeviceActive = device.deviceId == connectState?.activeDeviceId
                val isPending = pendingTransfer == device.deviceId
                DeviceRow(
                    device = device,
                    isMe = isMe,
                    isDeviceActive = isDeviceActive,
                    hasSession = hasSession,
                    isPending = isPending,
                    transferDisabled = pendingTransfer != null,
                    isRemoteControlling = isRemoteControlling,
                    onTransfer = { viewModel.transferTo(device.deviceId) },
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: ConnectDevice,
    isMe: Boolean,
    isDeviceActive: Boolean,
    hasSession: Boolean,
    isPending: Boolean,
    transferDisabled: Boolean,
    isRemoteControlling: Boolean,
    onTransfer: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(device.deviceName) },
        supportingContent = {
            Text(
                if (isMe) "This Device" else device.deviceType.replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = if (hasSession) {
            {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (isDeviceActive) {
                    Text(
                        "Playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (isMe && isRemoteControlling) {
                    TextButton(onClick = onTransfer, enabled = !transferDisabled) {
                        Text("Play here", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (!isMe) {
                    TextButton(onClick = onTransfer, enabled = !transferDisabled) {
                        Text("Play", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else if (isMe) {
            { Text("This Device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null
    )
}
