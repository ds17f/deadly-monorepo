package com.grateful.deadly.feature.downloads.navigation

import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.grateful.deadly.feature.downloads.screens.main.DownloadsScreen

object DownloadsRoutes {
    const val DOWNLOADS = "downloads"
}

@UnstableApi
fun NavGraphBuilder.downloadsNavigation(navController: NavController) {
    composable(route = DownloadsRoutes.DOWNLOADS) {
        DownloadsScreen(
            onNavigateToPlaylist = { showId, recordingId ->
                navController.navigate("playlist/$showId/$recordingId")
            }
        )
    }
}

fun NavController.navigateToDownloads() {
    navigate(DownloadsRoutes.DOWNLOADS) {
        launchSingleTop = true
    }
}
