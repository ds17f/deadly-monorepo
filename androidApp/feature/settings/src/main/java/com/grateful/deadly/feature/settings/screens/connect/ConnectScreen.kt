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

@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
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
            items(devices) { device ->
                DeviceRow(device = device, isMe = device.deviceId == installId)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: ConnectDevice, isMe: Boolean) {
    ListItem(
        headlineContent = { Text(device.deviceName) },
        supportingContent = {
            Text(
                device.deviceType.replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = if (isMe) {
            { Text("This Device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null
    )
}
