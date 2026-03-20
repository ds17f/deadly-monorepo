package com.grateful.deadly.feature.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.api.connect.ConnectConnectionState
import com.grateful.deadly.core.api.connect.ConnectDevice
import com.grateful.deadly.core.api.connect.UserPlaybackState
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.SourceBadgeStyle
import com.grateful.deadly.feature.settings.BuildConfig

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToLegal: () -> Unit = {},
    onNavigateToMission: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
    onNavigateToPrivacyData: () -> Unit = {}
) {
    val context = LocalContext.current
    val includeShowsWithoutRecordings by viewModel.includeShowsWithoutRecordings.collectAsState()
    val sourceBadgeStyle by viewModel.sourceBadgeStyle.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val serverEnvironment by viewModel.serverEnvironment.collectAsState()
    val connectState by viewModel.connectConnectionState.collectAsState()
    val connectDevices by viewModel.connectDevices.collectAsState()
    val connectUserState by viewModel.connectUserState.collectAsState()
    var showDeviceSheet by remember { mutableStateOf(false) }
    val version = BuildConfig.VERSION_NAME

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── ACCOUNT ───────────────────────────────────────────────────
        item { SectionHeader("Account") }

        when (val state = authState) {
            is AuthState.SignedIn -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            state.user.name?.let { name ->
                                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                            }
                            state.user.email?.let { email ->
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { showDeviceSheet = true }) {
                            Icon(
                                painter = IconResources.Content.Cast(),
                                contentDescription = "Connected devices",
                                tint = if (connectState == ConnectConnectionState.CONNECTED)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    PreferenceRow(
                        title = "Sign Out",
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { viewModel.signOut() }
                    )
                }
            }
            is AuthState.SignedOut -> {
                if (serverEnvironment == "custom") {
                    item {
                        PreferenceRow(
                            title = "Sign In (Dev)",
                            onClick = { viewModel.fetchDevToken() },
                            trailing = {
                                Icon(
                                    painter = IconResources.Navigation.ChevronRight(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                } else {
                    item {
                        PreferenceRow(
                            title = "Sign in with Google",
                            onClick = {
                                val activity = context as? Activity ?: return@PreferenceRow
                                viewModel.signInWithGoogle(activity) { error ->
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                }
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
                }
            }
        }

        item { HorizontalDivider() }

        // ── PREFERENCES ──────────────────────────────────────────────
        item { SectionHeader("Preferences") }

        item {
            PreferenceToggleRow(
                title = "Include shows without recordings",
                subtitle = "Show concerts even if they have no audio recordings available",
                checked = includeShowsWithoutRecordings,
                onCheckedChange = { viewModel.toggleIncludeShowsWithoutRecordings() }
            )
        }

        item {
            SourceBadgeStyleRow(
                currentStyle = SourceBadgeStyle.fromString(sourceBadgeStyle),
                onStyleSelected = { viewModel.setSourceBadgeStyle(it.name) }
            )
        }

        item { HorizontalDivider() }

        // ── AUDIO ────────────────────────────────────────────────────
        item { SectionHeader("Audio") }

        item {
            PreferenceRow(
                title = "Equalizer",
                subtitle = "Adjust audio profile and presets",
                onClick = onNavigateToEqualizer,
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

        // ── FAVORITES & DATA ────────────────────────────────────────
        item { SectionHeader("Favorites & Data") }

        item {
            PreferenceRow(
                title = "Manage Downloads",
                onClick = onNavigateToDownloads,
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
            BackupImportExportButtons(viewModel = viewModel)
        }

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

        item {
            PreferenceRow(
                title = "Privacy & Data",
                onClick = onNavigateToPrivacyData,
                trailing = {
                    Icon(
                        painter = IconResources.Navigation.ChevronRight(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }

    if (showDeviceSheet) {
        ConnectDeviceSheet(
            connectionState = connectState,
            devices = connectDevices,
            userState = connectUserState,
            onDismiss = { showDeviceSheet = false }
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

// ── Backup import/export ──────────────────────────────────────────────────────

@Composable
private fun BackupImportExportButtons(viewModel: SettingsViewModel) {
    val context = LocalContext.current

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFavorites(uri) { result ->
                result.onSuccess { message ->
                    Toast.makeText(context, "Favorites imported: $message", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(context, "Import failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column {
        PreferenceRow(
            title = "Import Favorites",
            subtitle = "Import from a backup file",
            onClick = { safLauncher.launch(arrayOf("application/json")) }
        )
        PreferenceRow(
            title = "Export Favorites",
            subtitle = "Export to Downloads folder",
            onClick = {
                viewModel.exportFavorites { result ->
                    result.onSuccess { filename ->
                        Toast.makeText(context, "Exported to $filename", Toast.LENGTH_SHORT).show()
                    }.onFailure { error ->
                        Toast.makeText(context, "Export failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
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
        title = if (importState is SettingsViewModel.MigrationImportState.Importing) "Importing…" else "Import Favorites from Old App",
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
                        append("Imported ${r.favoritesImported} favorite shows and ${r.recentImported} recent plays.")
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

// ── Source badge style selector ──────────────────────────────────────────────

@Composable
private fun SourceBadgeStyleRow(
    currentStyle: SourceBadgeStyle,
    onStyleSelected: (SourceBadgeStyle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "Source type badge", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Label style on artwork thumbnails",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SourceBadgeStyle.entries.forEachIndexed { index, style ->
                SegmentedButton(
                    selected = currentStyle == style,
                    onClick = { onStyleSelected(style) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SourceBadgeStyle.entries.size
                    )
                ) {
                    Text(style.label)
                }
            }
        }
    }
}

// ── Connect device sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectDeviceSheet(
    connectionState: ConnectConnectionState,
    devices: List<ConnectDevice>,
    userState: UserPlaybackState?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Connected Devices",
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
            val trackTitle = userState?.trackTitle
            if (userState != null && trackTitle != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = IconResources.PlayerControls.Play(),
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
                        userState.date?.let { date ->
                            Text(
                                text = date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            if (devices.isEmpty()) {
                Text(
                    text = "No devices connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { device ->
                    val isActive = userState?.activeDeviceId == device.deviceId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = IconResources.Content.Cast(),
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = device.type.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
