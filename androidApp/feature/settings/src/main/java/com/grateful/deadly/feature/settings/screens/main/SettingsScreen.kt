package com.grateful.deadly.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.feature.settings.BuildConfig
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLegal: () -> Unit = {},
    onNavigateToMission: () -> Unit = {}
) {
    val context = LocalContext.current
    val showOnlyRecorded by viewModel.showOnlyRecordedShows.collectAsState()
    val forceOnline by viewModel.forceOnline.collectAsState()
    val devMode by viewModel.devMode.collectAsState()
    val version = BuildConfig.VERSION_NAME

    var tapCount by remember { mutableIntStateOf(0) }
    var showReleaseNotesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            delay(2000)
            tapCount = 0
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── PREFERENCES ──────────────────────────────────────────────
        item { SectionHeader("Preferences") }

        item {
            PreferenceToggleRow(
                title = "Hide shows without recordings",
                subtitle = "Only show concerts that have audio recordings available",
                checked = showOnlyRecorded,
                onCheckedChange = { viewModel.toggleShowOnlyRecordedShows() }
            )
        }

        item { HorizontalDivider() }

        // ── LIBRARY ──────────────────────────────────────────────────
        item { SectionHeader("Library") }

        item {
            ImportMigrationButton(viewModel = viewModel)
        }

        item { HorizontalDivider() }

        // ── ABOUT ────────────────────────────────────────────────────
        item { SectionHeader("About") }

        // App name — 5-tap easter egg
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        tapCount++
                        if (tapCount >= 5) {
                            tapCount = 0
                            viewModel.setDevMode(true)
                        }
                    }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Deadly",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Version $version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(onClick = { showReleaseNotesDialog = true })
                )
            }
        }

        item {
            PreferenceRow(
                title = "Donate to Internet Archive",
                subtitle = "Help cover hosting and bandwidth costs",
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://archive.org/donate/"))
                    )
                },
                trailing = {
                    Icon(
                        painter = IconResources.Navigation.ChevronRight(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        item {
            PreferenceRow(
                title = "Our Mission",
                onClick = onNavigateToMission,
                trailing = {
                    Icon(
                        painter = IconResources.Navigation.ChevronRight(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        item {
            PreferenceRow(
                title = "Legal & Policies",
                onClick = onNavigateToLegal,
                trailing = {
                    Icon(
                        painter = IconResources.Navigation.ChevronRight(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        item { HorizontalDivider() }

        // ── DEVELOPER (hidden until unlocked) ────────────────────────
        if (devMode) {
            item {
                SectionHeader(
                    title = "Developer",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                PreferenceToggleRow(
                    title = "Force online mode",
                    subtitle = "Override offline detection when the app incorrectly shows as offline",
                    checked = forceOnline,
                    onCheckedChange = { viewModel.toggleForceOnline() }
                )
            }

            item { ClearArchiveCacheRow() }

            item {
                DeleteDataZipRow(onDeleteDataZip = viewModel::onDeleteDataZip)
            }

            item {
                DeleteDatabaseFilesRow(onDeleteDatabaseFiles = viewModel::onDeleteDatabaseFiles)
            }

            item {
                PreferenceRow(
                    title = "Disable Dev Mode",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { viewModel.setDevMode(false) }
                )
            }

            item {
                Text(
                    text = "Advanced tools for debugging and data recovery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showReleaseNotesDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseNotesDialog = false },
            title = { Text("Release Notes") },
            text = { Text("View release notes for v$version?") },
            confirmButton = {
                TextButton(onClick = {
                    showReleaseNotesDialog = false
                    val url = "https://github.com/ds17f/deadly-monorepo/releases/tag/android%2Fv$version"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("View") }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseNotesDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

// ── Generic preference row ────────────────────────────────────────────────────

@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

// ── Toggle row ────────────────────────────────────────────────────────────────

@Composable
private fun PreferenceToggleRow(
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Developer action rows ─────────────────────────────────────────────────────

@Composable
private fun ClearArchiveCacheRow() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    PreferenceRow(title = "Clear Archive Cache", onClick = { showDialog = true })

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

    PreferenceRow(
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
                TextButton(onClick = {
                    showConfirm = false
                    onDeleteDataZip { s -> success = s; showResult = true }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
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

    PreferenceRow(
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
                TextButton(onClick = {
                    showConfirm = false
                    onDeleteDatabaseFiles { s -> success = s; showResult = true }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
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

// ── Import migration ──────────────────────────────────────────────────────────

@Composable
private fun ImportMigrationButton(viewModel: SettingsViewModel) {
    val importState by viewModel.migrationImportState.collectAsState()

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onImportMigration(uri) }

    PreferenceRow(
        title = if (importState is SettingsViewModel.MigrationImportState.Importing) "Importing…" else "Import Library from Old App",
        subtitle = "Import your library and play history from the old Dead Archive app",
        onClick = {
            if (importState !is SettingsViewModel.MigrationImportState.Importing) {
                safLauncher.launch(arrayOf("application/json"))
            }
        }
    )

    when (val state = importState) {
        is SettingsViewModel.MigrationImportState.Success -> {
            val r = state.result
            AlertDialog(
                onDismissRequest = { viewModel.onDismissMigrationResult() },
                title = { Text("Import Complete") },
                text = {
                    Text(buildString {
                        append("Imported ${r.libraryImported} library shows and ${r.recentImported} recent plays.")
                        if (r.skipped > 0) append("\n${r.skipped} shows could not be matched.")
                        if (r.errors.isNotEmpty()) append("\n${r.errors.size} errors occurred.")
                    })
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onDismissMigrationResult() }) { Text("OK") }
                }
            )
        }
        is SettingsViewModel.MigrationImportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.onDismissMigrationResult() },
                title = { Text("Import Failed") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.onDismissMigrationResult() }) { Text("OK") }
                }
            )
        }
        else -> {}
    }
}
