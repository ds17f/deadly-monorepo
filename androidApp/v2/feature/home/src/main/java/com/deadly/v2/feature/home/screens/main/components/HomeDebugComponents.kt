package com.deadly.v2.feature.home.screens.main.components

import androidx.compose.runtime.Composable
import com.deadly.v2.core.design.component.debug.DebugData
import com.deadly.v2.core.design.component.debug.DebugSection
import com.deadly.v2.core.design.component.debug.DebugItem
import com.deadly.v2.feature.home.screens.main.HomeUiState

/**
 * Collect debug data for HomeScreen
 * Following the established V2 debug data pattern
 */
@Composable
fun collectHomeDebugData(
    uiState: HomeUiState,
    onRefresh: () -> Unit
): DebugData {
    return DebugData(
        screenName = "HomeScreen",
        sections = listOf(
            DebugSection(
                title = "Home State",
                items = listOf(
                    DebugItem.BooleanValue("Is Loading", uiState.isLoading),
                    DebugItem.BooleanValue("Has Error", uiState.hasError),
                    DebugItem.KeyValue("Error", uiState.error ?: "None"),
                    DebugItem.KeyValue("Last Refresh", 
                        if (uiState.homeContent.lastRefresh > 0) 
                            java.text.SimpleDateFormat("HH:mm:ss").format(uiState.homeContent.lastRefresh)
                        else "Never"
                    )
                )
            ),
            DebugSection(
                title = "Content Stats",
                items = listOf(
                    DebugItem.NumericValue("Recent Shows", uiState.homeContent.recentShows.size),
                    DebugItem.NumericValue("Today In History", uiState.homeContent.todayInHistory.size),
                    DebugItem.NumericValue("Collections", uiState.homeContent.featuredCollections.size)
                )
            ),
            DebugSection(
                title = "V2 Architecture",
                items = listOf(
                    DebugItem.KeyValue("Pattern", "Service Orchestration"),
                    DebugItem.KeyValue("Service", "HomeService (Stub)"),
                    DebugItem.KeyValue("Navigation", "Graph-based"),
                    DebugItem.KeyValue("Scaffold", "AppScaffold integrated")
                )
            )
        )
    )
}