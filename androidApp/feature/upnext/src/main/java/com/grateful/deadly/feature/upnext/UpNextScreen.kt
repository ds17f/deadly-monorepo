package com.grateful.deadly.feature.upnext

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Standalone Up Next screen — what the player/playlist "View Up Next" menu link
 * pushes. Just a top bar around the shared [UpNextList] (the single source of
 * truth, also embedded in the Favorites "Up Next" tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextScreen(
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Up Next") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(IconResources.Navigation.Back(), contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        UpNextList(modifier = Modifier.padding(padding))
    }
}
