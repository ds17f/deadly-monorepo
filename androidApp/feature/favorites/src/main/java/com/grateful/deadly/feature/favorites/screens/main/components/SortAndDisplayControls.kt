package com.grateful.deadly.feature.favorites.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.model.FavoritesSortOption
import com.grateful.deadly.core.model.FavoritesSortDirection
import com.grateful.deadly.core.model.FavoritesDisplayMode

/**
 * Sort and Display Controls Component
 * 
 * Focused component for sort controls and grid/list display toggle
 * following standard component architecture patterns.
 */
@Composable
fun SortAndDisplayControls(
    sortBy: FavoritesSortOption,
    sortDirection: FavoritesSortDirection,
    displayMode: FavoritesDisplayMode,
    onSortSelectorClick: () -> Unit,
    onDisplayModeChanged: (FavoritesDisplayMode) -> Unit,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort selector button
        SortSelectorButton(
            sortBy = sortBy,
            sortDirection = sortDirection,
            onClick = onSortSelectorClick
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )

        // Display mode toggle
        IconButton(
            onClick = {
                onDisplayModeChanged(
                    if (displayMode == FavoritesDisplayMode.LIST) {
                        FavoritesDisplayMode.GRID 
                    } else {
                        FavoritesDisplayMode.LIST
                    }
                )
            }
        ) {
            Icon(
                painter = if (displayMode == FavoritesDisplayMode.LIST) {
                    IconResources.Content.GridView() // Grid icon when in list mode
                } else {
                    IconResources.Content.FormatListBulleted() // List icon when in grid mode
                },
                contentDescription = if (displayMode == FavoritesDisplayMode.LIST) "Grid View" else "List View"
            )
        }
    }
}

/**
 * Sort selector button component
 */
@Composable
private fun SortSelectorButton(
    sortBy: FavoritesSortOption,
    sortDirection: FavoritesSortDirection,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = IconResources.Navigation.SwapVert(),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = sortBy.displayName)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = if (sortDirection == FavoritesSortDirection.ASCENDING) {
                    IconResources.Navigation.KeyboardArrowUp()
                } else {
                    IconResources.Navigation.KeyboardArrowDown()
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}