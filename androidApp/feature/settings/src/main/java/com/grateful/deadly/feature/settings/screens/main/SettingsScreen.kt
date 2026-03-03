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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.feature.settings.BuildConfig

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLegal: () -> Unit = {},
    onNavigateToMission: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {}
) {
    val context = LocalContext.current
    val showOnlyRecorded by viewModel.showOnlyRecordedShows.collectAsState()
    val version = BuildConfig.VERSION_NAME

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

        item {
            PreferenceRow(
                title = "Version $version",
                onClick = {
                    val url = "https://github.com/ds17f/deadly-monorepo/releases/tag/android%2Fv$version"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

        item {
            PreferenceRow(
                title = "Developer",
                onClick = onNavigateToDeveloper,
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
                    style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
