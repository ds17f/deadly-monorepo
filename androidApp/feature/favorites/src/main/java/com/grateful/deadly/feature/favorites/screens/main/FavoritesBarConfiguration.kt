package com.grateful.deadly.feature.favorites.screens.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode
import com.grateful.deadly.feature.favorites.screens.main.models.FavoritesViewModel

object FavoritesBarConfiguration {

    fun getFavoritesBarConfig(
        onNavigateToDownloads: () -> Unit = {}
    ): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Favorites",
            mode = TopBarMode.SOLID,
            navigationIcon = null,
            actions = {
                FavoritesTopBarActions(
                    onNavigateToDownloads = onNavigateToDownloads
                )
            }
        ),
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = true)
    )
}

@Composable
private fun FavoritesTopBarActions(
    onNavigateToDownloads: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val showMenu = remember { mutableStateOf(false) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFavorites(uri) { result ->
                result.onSuccess { message ->
                    Toast.makeText(
                        context,
                        "Favorites imported: $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        "Import failed: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    IconButton(onClick = { showMenu.value = true }) {
        Icon(
            painter = IconResources.Navigation.MoreVertical(),
            contentDescription = "More options"
        )
    }

    DropdownMenu(
        expanded = showMenu.value,
        onDismissRequest = { showMenu.value = false }
    ) {
        DropdownMenuItem(
            text = { Text("Manage Downloads") },
            onClick = {
                showMenu.value = false
                onNavigateToDownloads()
            }
        )
        DropdownMenuItem(
            text = { Text("Import Favorites") },
            onClick = {
                showMenu.value = false
                safLauncher.launch(arrayOf("application/json"))
            }
        )
        DropdownMenuItem(
            text = { Text("Export Favorites") },
            onClick = {
                showMenu.value = false
                viewModel.exportFavorites { result ->
                    result.onSuccess { filename ->
                        Toast.makeText(
                            context,
                            "Favorites exported to $filename",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            "Export failed: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}
