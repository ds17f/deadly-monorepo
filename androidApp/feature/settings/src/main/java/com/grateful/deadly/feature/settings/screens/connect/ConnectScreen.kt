package com.grateful.deadly.feature.settings.screens.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = CircleShape,
                        color = if (isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                    ) {}
                }
            )
        }

        item {
            HorizontalDivider()
        }

        if (connectState?.showId != null) {
            item {
                ListItem(
                    headlineContent = { Text("Stop Session") },
                    supportingContent = { Text("End the shared listening session on all devices.") },
                    trailingContent = {
                        TextButton(onClick = { viewModel.sendStop() }) {
                            Text("Stop")
                        }
                    }
                )
            }
            item {
                HorizontalDivider()
            }
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
                ConnectDeviceRow(
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
