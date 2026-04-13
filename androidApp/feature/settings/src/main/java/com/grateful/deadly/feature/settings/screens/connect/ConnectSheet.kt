package com.grateful.deadly.feature.settings.screens.connect

import androidx.compose.foundation.clickable
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
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.ConnectDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSheet(
    onDismiss: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val connectState by viewModel.connectState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isActiveDevice by viewModel.isActiveDevice.collectAsState()
    val pendingTransfer by viewModel.pendingTransfer.collectAsState()
    val activeDeviceVolume by viewModel.activeDeviceVolume.collectAsState()
    val installId = viewModel.installId

    var localVolume by remember { mutableFloatStateOf(100f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(activeDeviceVolume) {
        if (!isDragging) {
            localVolume = activeDeviceVolume.toFloat()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Title
            Text(
                text = "Devices",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val hasSession = connectState?.showId != null
            val isRemoteControlling = connectState?.activeDeviceId != null && !isActiveDevice
            val localDevice = devices.find { it.deviceId == installId }
            val otherDevices = devices.filter { it.deviceId != installId }

            // This Device section
            Text(
                text = "This Device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (localDevice != null) {
                val isDeviceActive = localDevice.deviceId == connectState?.activeDeviceId
                val isPending = pendingTransfer == localDevice.deviceId
                ConnectDeviceRow(
                    device = localDevice,
                    isMe = true,
                    isDeviceActive = isDeviceActive,
                    hasSession = hasSession,
                    isPending = isPending,
                    transferDisabled = pendingTransfer != null,
                    isRemoteControlling = isRemoteControlling,
                    onTransfer = { viewModel.transferTo(localDevice.deviceId) },
                )
            } else {
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

            HorizontalDivider()

            // Other Devices section
            Text(
                text = "Other Devices",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (otherDevices.isEmpty()) {
                ListItem(
                    headlineContent = { Text("No other devices connected") },
                    supportingContent = {
                        Text(
                            "Other devices signed in to the same account will appear here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                otherDevices.forEach { device ->
                    val isDeviceActive = device.deviceId == connectState?.activeDeviceId
                    val isPending = pendingTransfer == device.deviceId
                    ConnectDeviceRow(
                        device = device,
                        isMe = false,
                        isDeviceActive = isDeviceActive,
                        hasSession = hasSession,
                        isPending = isPending,
                        transferDisabled = pendingTransfer != null,
                        isRemoteControlling = isRemoteControlling,
                        onTransfer = { viewModel.transferTo(device.deviceId) },
                    )
                }
            }

            if (hasSession && connectState?.activeDeviceId != null) {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painter = IconResources.PlayerControls.VolumeMute(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = localVolume,
                        onValueChange = { newValue ->
                            localVolume = newValue
                            isDragging = true
                            debounceJob?.cancel()
                            debounceJob = scope.launch {
                                delay(150)
                                viewModel.sendVolume(newValue.toInt())
                            }
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            debounceJob?.cancel()
                            debounceJob = null
                            viewModel.sendVolume(localVolume.toInt())
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = IconResources.PlayerControls.VolumeUp(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (connectState?.showId != null) {
                HorizontalDivider()
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
        }
    }
}

@Composable
internal fun ConnectDeviceRow(
    device: ConnectDevice,
    isMe: Boolean,
    isDeviceActive: Boolean,
    hasSession: Boolean,
    isPending: Boolean,
    transferDisabled: Boolean,
    isRemoteControlling: Boolean,
    onTransfer: () -> Unit,
) {
    val isTappable = hasSession && !isPending && !isDeviceActive && !transferDisabled

    ListItem(
        headlineContent = { Text(device.deviceName) },
        supportingContent = {
            Text(
                if (isMe) "This Device" else device.deviceType.replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                painter = IconResources.Content.Cast(),
                contentDescription = null,
                tint = if (isDeviceActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = IconResources.PlayerControls.MusicNote(),
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Playing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        } else null,
        modifier = Modifier.clickable(enabled = isTappable, onClick = onTransfer)
    )
}
