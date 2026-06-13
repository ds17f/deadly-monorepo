package com.grateful.deadly.feature.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.api.auth.AuthState
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.PlayerControlsStyle
import com.grateful.deadly.core.model.SourceBadgeStyle
import com.grateful.deadly.feature.settings.BuildConfig
import com.grateful.deadly.feature.settings.screens.bugreport.BugReportScreen

// ADR-0014: Settings is a short landing of stable categories, each its own
// screen — Account · Playback & Audio · Home Layout · Library & Data ·
// About & Support. The flat "scroll-forever" list is gone; every control now
// lives behind the category it belongs to, with all home-layout knobs gathered
// onto one dedicated screen.
//
// Android renders Settings inside the navigation drawer (not a nav destination),
// so landing → subscreen is a local state drill-down with a back affordance.
// The leaf screens (Equalizer, Connect, Legal, …) stay full-screen routes,
// reached through the callbacks the host wires up.
private enum class SettingsCategory { Account, PlaybackAudio, HomeLayout, LibraryData }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToLegal: () -> Unit = {},
    onNavigateToMission: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
    onNavigateToPrivacyData: () -> Unit = {},
    onNavigateToConnect: () -> Unit = {}
) {
    var category by remember { mutableStateOf<SettingsCategory?>(null) }
    var showBugReport by remember { mutableStateOf(false) }
    BackHandler(enabled = category != null || showBugReport) {
        if (showBugReport) showBugReport = false else category = null
    }

    if (showBugReport) {
        BugReportScreen(
            onBack = { showBugReport = false },
            headerBar = { title, onBack -> SubscreenHeader(title, onBack) }
        )
        return
    }

    when (category) {
        null -> SettingsLanding(
            viewModel,
            onSelect = { category = it },
            onNavigateToMission = onNavigateToMission,
            onNavigateToLegal = onNavigateToLegal,
            onNavigateToPrivacyData = onNavigateToPrivacyData,
            onNavigateToDeveloper = onNavigateToDeveloper,
            onShowBugReport = { showBugReport = true }
        )
        SettingsCategory.Account ->
            AccountSettingsScreen(viewModel, onBack = { category = null })
        SettingsCategory.PlaybackAudio ->
            PlaybackAudioSettingsScreen(
                viewModel,
                onBack = { category = null },
                onNavigateToEqualizer = onNavigateToEqualizer,
                onNavigateToConnect = onNavigateToConnect
            )
        SettingsCategory.HomeLayout ->
            HomeLayoutSettingsScreen(viewModel, onBack = { category = null })
        SettingsCategory.LibraryData ->
            LibraryDataSettingsScreen(
                viewModel,
                onBack = { category = null },
                onNavigateToDownloads = onNavigateToDownloads
            )
    }
}

// ── Landing ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsLanding(
    viewModel: SettingsViewModel,
    onSelect: (SettingsCategory) -> Unit,
    onNavigateToMission: () -> Unit,
    onNavigateToLegal: () -> Unit,
    onNavigateToPrivacyData: () -> Unit,
    onNavigateToDeveloper: () -> Unit,
    onShowBugReport: () -> Unit
) {
    val context = LocalContext.current
    val developerModeUnlocked by viewModel.developerModeUnlocked.collectAsState()
    val version = BuildConfig.VERSION_NAME
    var versionTapCount by remember(developerModeUnlocked) { mutableIntStateOf(0) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
            )
        }
        item {
            CategoryRow(
                "Account", "Sign-in & profile",
                leading = { LeadingIcon(Icons.Filled.AccountCircle) }
            ) { onSelect(SettingsCategory.Account) }
        }
        item {
            CategoryRow(
                "Playback & Audio", "Controls, equalizer, devices",
                leading = { LeadingIcon(IconResources.PlayerControls.VolumeUp()) }
            ) { onSelect(SettingsCategory.PlaybackAudio) }
        }
        item {
            CategoryRow(
                "Home Layout", "Rails, card sizes, trending",
                leading = { LeadingIcon(IconResources.Content.GridView()) }
            ) { onSelect(SettingsCategory.HomeLayout) }
        }
        item {
            CategoryRow(
                "Library & Data", "Downloads, import & export",
                leading = { LeadingIcon(IconResources.Content.Folder()) }
            ) { onSelect(SettingsCategory.LibraryData) }
        }

        item { HorizontalDivider() }

        // About & Support — informational links live right on the root
        // (low-traffic, no need to bury them behind a category).
        item {
            PreferenceRow(
                title = "Community (r/thedeadlyapp)",
                leading = { LeadingIcon(Icons.AutoMirrored.Filled.Message) },
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/thedeadlyapp"))
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
                title = "Donate to Internet Archive",
                subtitle = "Help cover hosting and bandwidth costs",
                leading = { LeadingIcon(IconResources.Content.Favorite()) },
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
                title = "Support TheDeadly",
                subtitle = "Support the project",
                leading = { LeadingIcon(IconResources.Content.Coffee()) },
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/dsilberg"))
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
                title = "Send Bug Report",
                leading = { LeadingIcon(Icons.Filled.BugReport) },
                onClick = onShowBugReport,
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
        if (developerModeUnlocked) {
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
        }

        item { HorizontalDivider() }

        // Version footer — kept on the root so the build is always visible at a
        // glance (and the tap-to-unlock developer mode stays reachable), with
        // Release Notes one row below it.
        item {
            PreferenceRow(
                title = "Version $version",
                onClick = {
                    versionTapCount++
                    val remaining = 7 - versionTapCount
                    if (remaining <= 0 && !developerModeUnlocked) {
                        viewModel.unlockDeveloperMode()
                        Toast.makeText(context, "Developer mode enabled", Toast.LENGTH_SHORT).show()
                    } else if (remaining in 1..3 && !developerModeUnlocked) {
                        Toast.makeText(context, "$remaining taps to enable developer mode", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        item {
            PreferenceRow(
                title = "Release Notes",
                leading = { LeadingIcon(IconResources.Content.FilePresent()) },
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
    }
}

@Composable
private fun CategoryRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit
) {
    PreferenceRow(
        title = title,
        subtitle = subtitle,
        leading = leading,
        onClick = onClick,
        trailing = {
            Icon(
                painter = IconResources.Navigation.ChevronRight(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

// Leading icon for a settings row — tinted with the accent, matching iOS.
@Composable
private fun LeadingIcon(painter: androidx.compose.ui.graphics.painter.Painter) {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LeadingIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
}

// Header bar shown atop each subscreen so it can return to the landing list.
@Composable
private fun SubscreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubscreenHeader(title, onBack)
        LazyColumn(modifier = Modifier.fillMaxSize(), content = content)
    }
}

// Just the back-bar row, reused by both [SubscreenScaffold] and screens that
// manage their own (non-LazyColumn) body, like the bug report screen.
@Composable
private fun SubscreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = IconResources.Navigation.Back(),
                contentDescription = "Back"
            )
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

// ── Account ─────────────────────────────────────────────────────────────────

@Composable
private fun AccountSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val serverEnvironment by viewModel.serverEnvironment.collectAsState()

    SubscreenScaffold("Account", onBack) {
        when (val state = authState) {
            is AuthState.SignedIn -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
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
    }
}

// ── Playback & Audio ────────────────────────────────────────────────────────

@Composable
private fun PlaybackAudioSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToConnect: () -> Unit
) {
    val sourceBadgeStyle by viewModel.sourceBadgeStyle.collectAsState()
    val playerControlsStyle by viewModel.playerControlsStyle.collectAsState()

    SubscreenScaffold("Playback & Audio", onBack) {
        item { SectionHeader("Controls") }

        item {
            PlayerControlsStyleRow(
                currentStyle = PlayerControlsStyle.fromString(playerControlsStyle),
                onStyleSelected = { viewModel.setPlayerControlsStyle(it.name) }
            )
        }

        item {
            SourceBadgeStyleRow(
                currentStyle = SourceBadgeStyle.fromString(sourceBadgeStyle),
                onStyleSelected = { viewModel.setSourceBadgeStyle(it.name) }
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader("Audio") }

        item {
            PreferenceRow(
                title = "Equalizer",
                subtitle = "Adjust audio profile and presets",
                leading = { LeadingIcon(IconResources.PlayerControls.Equalizer()) },
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

        item {
            PreferenceRow(
                title = "Connected Devices",
                subtitle = "View devices connected to your account",
                leading = { LeadingIcon(IconResources.Content.Cast()) },
                onClick = onNavigateToConnect,
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

// ── Home Layout ─────────────────────────────────────────────────────────────

@Composable
private fun HomeLayoutSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val homeTrendingWindow by viewModel.homeTrendingWindow.collectAsState()
    val homeTrendingAboveToday by viewModel.homeTrendingAboveToday.collectAsState()
    val homeTrendingIncludeAnniversaries by viewModel.homeTrendingIncludeAnniversaries.collectAsState()
    val homeRecentRows by viewModel.homeRecentRows.collectAsState()
    val homeTrendingCardSize by viewModel.homeTrendingCardSize.collectAsState()
    val homeTodayCardSize by viewModel.homeTodayCardSize.collectAsState()
    val homeCollectionsCardSize by viewModel.homeCollectionsCardSize.collectAsState()
    val homePopularEnabled by viewModel.homePopularEnabled.collectAsState()
    val homePopularCardSize by viewModel.homePopularCardSize.collectAsState()
    val homePopularDecade by viewModel.homePopularDecade.collectAsState()

    SubscreenScaffold("Home Layout", onBack) {
        item { SectionHeader("Trending") }

        item {
            HomeTrendingWindowRow(
                currentKey = homeTrendingWindow,
                onSelected = { viewModel.setHomeTrendingWindow(it) }
            )
        }

        item {
            PreferenceToggleRow(
                title = "Show Trending above Today",
                subtitle = "Move the Trending section below \"Today In Grateful Dead History\" by turning this off.",
                checked = homeTrendingAboveToday,
                onCheckedChange = { viewModel.toggleHomeTrendingAboveToday() }
            )
        }

        item {
            PreferenceToggleRow(
                title = "Include \"Today in History\" in Trending",
                subtitle = "Off by default — recommended for variety. The 24-hour Trending window is otherwise dominated by anniversary plays.",
                checked = homeTrendingIncludeAnniversaries,
                onCheckedChange = { viewModel.toggleHomeTrendingIncludeAnniversaries() }
            )
        }

        item {
            HomeCardSizeRow(
                title = "Trending card size",
                subtitle = "Size of cards in the Trending carousel.",
                current = homeTrendingCardSize,
                onSelected = { viewModel.setHomeTrendingCardSize(it) }
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader("Rails") }

        item {
            HomeRecentRowsRow(
                currentRows = homeRecentRows,
                onSelected = { viewModel.setHomeRecentRows(it) }
            )
        }

        item {
            HomeCardSizeRow(
                title = "Today In History card size",
                subtitle = "Size of cards in the Today In Grateful Dead History carousel.",
                current = homeTodayCardSize,
                onSelected = { viewModel.setHomeTodayCardSize(it) }
            )
        }

        item {
            HomeCardSizeRow(
                title = "Featured Collections card size",
                subtitle = "Size of cards in the Featured Collections carousel.",
                current = homeCollectionsCardSize,
                onSelected = { viewModel.setHomeCollectionsCardSize(it) }
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader("Fan Favorites") }

        item {
            PreferenceToggleRow(
                title = "Show Fan Favorites",
                subtitle = "Shows other listeners kept — ranked by saved-vs-played ratio.",
                checked = homePopularEnabled,
                onCheckedChange = { viewModel.toggleHomePopularEnabled() }
            )
        }

        item {
            HomePopularDecadeRow(
                currentKey = homePopularDecade,
                onSelected = { viewModel.setHomePopularDecade(it) }
            )
        }

        item {
            HomeCardSizeRow(
                title = "Fan Favorites card size",
                subtitle = "Size of cards in the Fan Favorites carousel.",
                current = homePopularCardSize,
                onSelected = { viewModel.setHomePopularCardSize(it) }
            )
        }

        item { HorizontalDivider() }

        item {
            HomeResetRow(onReset = { viewModel.resetHomePreferences() })
        }
    }
}

// ── Library & Data ──────────────────────────────────────────────────────────

@Composable
private fun LibraryDataSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val includeShowsWithoutRecordings by viewModel.includeShowsWithoutRecordings.collectAsState()

    SubscreenScaffold("Library & Data", onBack) {
        item { SectionHeader("Content") }

        item {
            PreferenceToggleRow(
                title = "Include shows without recordings",
                subtitle = "Show concerts even if they have no audio recordings available",
                checked = includeShowsWithoutRecordings,
                onCheckedChange = { viewModel.toggleIncludeShowsWithoutRecordings() }
            )
        }

        item { HorizontalDivider() }
        item { SectionHeader("Downloads") }

        item {
            PreferenceRow(
                title = "Manage Downloads",
                leading = { LeadingIcon(IconResources.Content.FileDownload()) },
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

        item { HorizontalDivider() }
        item { SectionHeader("Favorites Backup") }

        item {
            BackupImportExportButtons(viewModel = viewModel)
        }

        item {
            ImportMigrationButton(viewModel = viewModel)
        }
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
    leading: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
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
            leading = { LeadingIcon(IconResources.DataManagement.Restore()) },
            onClick = { safLauncher.launch(arrayOf("application/json")) }
        )
        PreferenceRow(
            title = "Export Favorites",
            subtitle = "Export to Downloads folder",
            leading = { LeadingIcon(IconResources.DataManagement.Backup()) },
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
        leading = { LeadingIcon(IconResources.DataManagement.SettingsBackupRestore()) },
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

// ── Player controls style selector ───────────────────────────────────────────

@Composable
private fun PlayerControlsStyleRow(
    currentStyle: PlayerControlsStyle,
    onStyleSelected: (PlayerControlsStyle) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "Notification & Android Auto controls", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Choose between previous/next track, 15-second skip, or both.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PlayerControlsStyle.entries.forEachIndexed { index, style ->
                SegmentedButton(
                    selected = currentStyle == style,
                    onClick = { onStyleSelected(style) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = PlayerControlsStyle.entries.size
                    )
                ) {
                    Text(style.label)
                }
            }
        }
    }
}

// ── Source badge style selector ──────────────────────────────────────────────

@Composable
private fun HomeRecentRowsRow(
    currentRows: Int,
    onSelected: (Int) -> Unit
) {
    val options = listOf(1, 2, 3, 4)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "Recently Played rows", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "How many rows of recent shows on the home screen (2 shows per row)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, rows ->
                SegmentedButton(
                    selected = currentRows == rows,
                    onClick = { onSelected(rows) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    )
                ) {
                    Text(rows.toString())
                }
            }
        }
    }
}

@Composable
private fun HomeResetRow(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextButton(onClick = onReset) {
            Text(
                text = "Reset Home Screen to Defaults",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun HomeCardSizeRow(
    title: String,
    subtitle: String,
    current: String,
    onSelected: (String) -> Unit,
) {
    val options = listOf("small" to "Small", "large" to "Large")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = current == key,
                    onClick = { onSelected(key) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    )
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun HomePopularDecadeRow(
    currentKey: String,
    onSelected: (String) -> Unit
) {
    val options = listOf(
        "all" to "All",
        "60s" to "60s",
        "70s" to "70s",
        "80s" to "80s",
        "90s" to "90s",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "Fan Favorites decade", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Default decade filter for the Fan Favorites rail. Tap the header to cycle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = currentKey == key,
                    onClick = { onSelected(key) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    )
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun HomeTrendingWindowRow(
    currentKey: String,
    onSelected: (String) -> Unit
) {
    val options = listOf(
        "now" to "Day",
        "week" to "Week",
        "month" to "Month",
        "all" to "All",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = "Trending window on home", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Which time range \"Trending on The Deadly\" shows",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = currentKey == key,
                    onClick = { onSelected(key) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    )
                ) {
                    Text(label)
                }
            }
        }
    }
}

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
