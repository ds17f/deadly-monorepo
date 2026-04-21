package com.grateful.deadly.feature.settings.screens.developer

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.feature.settings.SettingsViewModel

@Composable
fun DeveloperScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val forceOnline by viewModel.forceOnline.collectAsState()

    val serverEnvironment by viewModel.serverEnvironment.collectAsState()
    val customServerUrl by viewModel.customServerUrl.collectAsState()
    val customDevEmail by viewModel.customDevEmail.collectAsState()

    val environments = listOf("prod" to "Production", "beta" to "Beta", "custom" to "Custom")

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(text = "Server", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    environments.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = serverEnvironment == value,
                            onClick = { viewModel.setServerEnvironment(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, environments.size)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        if (serverEnvironment == "custom") {
            item {
                OutlinedTextField(
                    value = customServerUrl,
                    onValueChange = { viewModel.setCustomServerUrl(it) },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.100:3000") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = customDevEmail,
                    onValueChange = { viewModel.setCustomDevEmail(it) },
                    label = { Text("Email") },
                    placeholder = { Text("your@email.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        item { HorizontalDivider() }

        item {
            DevToggleRow(
                title = "Force online mode",
                subtitle = "Override offline detection when the app incorrectly shows as offline",
                checked = forceOnline,
                onCheckedChange = { viewModel.toggleForceOnline() }
            )
        }

        item { HorizontalDivider() }

        item {
            DevRow(
                title = "Trigger review prompt",
                onClick = { viewModel.triggerReviewPrompt() }
            )
        }

        item { HorizontalDivider() }

        item { ClearArchiveCacheRow() }

        item { HorizontalDivider() }

        item {
            DeleteDataZipRow(onDeleteDataZip = viewModel::onDeleteDataZip)
        }

        item { HorizontalDivider() }

        item {
            DeleteDatabaseFilesRow(onDeleteDatabaseFiles = viewModel::onDeleteDatabaseFiles)
        }

        item {
            Text(
                text = "Advanced tools for debugging and data recovery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun DevToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DevRow(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor
        )
    }
}

@Composable
private fun ClearArchiveCacheRow() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    DevRow(title = "Clear Archive Cache", onClick = { showDialog = true })

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Clear Archive Cache") },
            text = { Text("This will delete all cached track lists and reviews. The data will be re-downloaded when needed.") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    try {
                        val dir = File(context.cacheDir, "archive")
                        if (dir.exists()) dir.deleteRecursively()
                    } catch (_: Exception) {}
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DeleteDataZipRow(onDeleteDataZip: (onComplete: (Boolean) -> Unit) -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }

    DevRow(
        title = "Delete Data.zip",
        titleColor = MaterialTheme.colorScheme.error,
        onClick = { showConfirm = true }
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete Data.zip File") },
            text = { Text("The app will need to re-download show data. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        onDeleteDataZip { s -> success = s; showResult = true }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showResult) {
        AlertDialog(
            onDismissRequest = { showResult = false },
            title = { Text(if (success) "Deleted" else "Failed") },
            text = { Text(if (success) "Data.zip has been deleted." else "Failed to delete Data.zip.") },
            confirmButton = { TextButton(onClick = { showResult = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun DeleteDatabaseFilesRow(onDeleteDatabaseFiles: (onComplete: (Boolean) -> Unit) -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }

    DevRow(
        title = "Delete Database Files",
        titleColor = MaterialTheme.colorScheme.error,
        onClick = { showConfirm = true }
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete Database Files") },
            text = { Text("All stored shows, favorites, and app data will be lost. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        onDeleteDatabaseFiles { s -> success = s; showResult = true }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showResult) {
        AlertDialog(
            onDismissRequest = { showResult = false },
            title = { Text(if (success) "Deleted" else "Failed") },
            text = { Text(if (success) "Database files deleted. Fresh data will be created on next use." else "Failed to delete some database files.") },
            confirmButton = { TextButton(onClick = { showResult = false }) { Text("OK") } }
        )
    }
}
