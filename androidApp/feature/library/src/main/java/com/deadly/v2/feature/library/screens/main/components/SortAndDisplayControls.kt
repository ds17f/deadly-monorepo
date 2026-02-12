package com.deadly.v2.feature.library.screens.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.model.LibrarySortOption
import com.deadly.v2.core.model.LibrarySortDirection
import com.deadly.v2.core.model.LibraryDisplayMode

/**
 * V2 Sort and Display Controls Component
 * 
 * Focused component for sort controls and grid/list display toggle
 * following V2 component architecture patterns.
 */
@Composable
fun SortAndDisplayControls(
    sortBy: LibrarySortOption,
    sortDirection: LibrarySortDirection,
    displayMode: LibraryDisplayMode,
    onSortSelectorClick: () -> Unit,
    onDisplayModeChanged: (LibraryDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort selector button
        SortSelectorButton(
            sortBy = sortBy,
            sortDirection = sortDirection,
            onClick = onSortSelectorClick
        )
        
        // Display mode toggle
        IconButton(
            onClick = {
                onDisplayModeChanged(
                    if (displayMode == LibraryDisplayMode.LIST) {
                        LibraryDisplayMode.GRID 
                    } else {
                        LibraryDisplayMode.LIST
                    }
                )
            }
        ) {
            Icon(
                painter = if (displayMode == LibraryDisplayMode.LIST) {
                    IconResources.Content.GridView() // Grid icon when in list mode
                } else {
                    IconResources.Content.FormatListBulleted() // List icon when in grid mode
                },
                contentDescription = if (displayMode == LibraryDisplayMode.LIST) "Grid View" else "List View"
            )
        }
    }
}

/**
 * Sort selector button component
 */
@Composable
private fun SortSelectorButton(
    sortBy: LibrarySortOption,
    sortDirection: LibrarySortDirection,
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
                painter = if (sortDirection == LibrarySortDirection.ASCENDING) {
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