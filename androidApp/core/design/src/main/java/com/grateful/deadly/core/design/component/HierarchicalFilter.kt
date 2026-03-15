package com.grateful.deadly.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind

/**
 * Data structure for hierarchical filter tree
 */
data class FilterNode(
    val id: String,
    val label: String,
    val children: List<FilterNode> = emptyList()
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

/**
 * Represents the current filter selection path
 */
data class FilterPath(
    val nodes: List<FilterNode> = emptyList()
) {
    val isNotEmpty: Boolean get() = nodes.isNotEmpty()
    val isEmpty: Boolean get() = nodes.isEmpty()

    /**
     * Get display text for the full path (e.g., "[70s] Early 70s | 1977")
     */
    fun getDisplayText(): String {
        return when (nodes.size) {
            0 -> ""
            1 -> nodes[0].label
            else -> "[${nodes[0].label}] " + nodes.drop(1).joinToString(" | ") { it.label }
        }
    }

    /**
     * Get combined ID for the full path (e.g., "70s_early_1977")
     */
    fun getCombinedId(): String = nodes.joinToString("_") { it.id }
}

/**
 * Spotify-style hierarchical filter component
 *
 * Supports N-level deep hierarchies. At each level:
 * - If a selected node has children, show it highlighted + its children as options
 * - If a selected node is a leaf, show a combined chip for the full path
 *
 * @param filterTree The root nodes of the filter hierarchy
 * @param selectedPath The current filter selection path
 * @param onSelectionChanged Callback when filter selection changes
 * @param modifier Modifier for the component
 */
@Composable
fun HierarchicalFilter(
    filterTree: List<FilterNode>,
    selectedPath: FilterPath,
    onSelectionChanged: (FilterPath) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // "All" chip - always visible, highlighted when nothing selected
        item {
            FilterOptionChip(
                node = FilterNode(id = "all", label = "All"),
                isSelected = selectedPath.isEmpty,
                onClick = { onSelectionChanged(FilterPath()) }
            )
        }

        when {
            selectedPath.isEmpty -> {
                // No selection — show root level options
                items(filterTree) { node ->
                    FilterOptionChip(
                        node = node,
                        isSelected = false,
                        onClick = {
                            onSelectionChanged(FilterPath(listOf(node)))
                        }
                    )
                }
            }

            selectedPath.nodes.last().isLeaf -> {
                // Deepest node is a leaf — show combined chip
                item {
                    CombinedSelectionChip(
                        path = selectedPath,
                        onClick = {
                            // Navigate back one level
                            onSelectionChanged(FilterPath(selectedPath.nodes.dropLast(1)))
                        }
                    )
                }
            }

            else -> {
                // Deepest node has children — show it highlighted + its children
                val deepestNode = selectedPath.nodes.last()

                item {
                    FilterOptionChip(
                        node = deepestNode,
                        isSelected = true,
                        onClick = {
                            // Navigate back one level
                            onSelectionChanged(FilterPath(selectedPath.nodes.dropLast(1)))
                        }
                    )
                }

                items(deepestNode.children) { child ->
                    FilterOptionChip(
                        node = child,
                        isSelected = false,
                        onClick = {
                            onSelectionChanged(FilterPath(selectedPath.nodes + child))
                        }
                    )
                }
            }
        }
    }
}

/**
 * Combined selection chip showing the full path with visual separators
 * between each segment (e.g., "70s | Early 70s | 1977")
 */
@Composable
private fun CombinedSelectionChip(
    path: FilterPath,
    onClick: () -> Unit
) {
    if (path.nodes.size < 2) return

    // Spacing configuration
    val separatorWidth = 16.dp
    val separatorHeight = 32.dp
    val separatorHorizontalPadding = 6.dp

    FilterChip(
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                path.nodes.forEachIndexed { index, node ->
                    Text(
                        text = node.label,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(
                            start = if (index == 0) 0.dp else 4.dp,
                            end = if (index == path.nodes.lastIndex) 0.dp else 6.dp
                        )
                    )

                    if (index < path.nodes.lastIndex) {
                        // Visual separator
                        Box(
                            modifier = Modifier
                                .width(separatorWidth)
                                .height(separatorHeight)
                                .padding(horizontal = separatorHorizontalPadding)
                                .drawBehind {
                                    val strokeWidth = 1.dp.toPx()
                                    val cornerRadius = 8.dp.toPx()
                                    val centerX = size.width / 2

                                    drawPath(
                                        path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(centerX - cornerRadius, 0f)
                                            cubicTo(
                                                centerX - cornerRadius * 0.448f, 0f,
                                                centerX - cornerRadius * 0.1f, cornerRadius * 0.448f,
                                                centerX - cornerRadius * 0.1f, cornerRadius
                                            )
                                            lineTo(centerX - cornerRadius * 0.1f, size.height - cornerRadius)
                                            cubicTo(
                                                centerX - cornerRadius * 0.1f, size.height - cornerRadius * 0.448f,
                                                centerX - cornerRadius * 0.448f, size.height,
                                                centerX - cornerRadius, size.height
                                            )
                                        },
                                        color = Color.Black,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                    )
                                }
                        )
                    }
                }
            }
        },
        selected = true,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color.Red,
            selectedLabelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = true,
            borderColor = Color.Red,
            selectedBorderColor = Color.Red,
            borderWidth = 1.dp
        )
    )
}

/**
 * Individual filter option chip
 */
@Composable
private fun FilterOptionChip(
    node: FilterNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        onClick = onClick,
        label = { Text(node.label) },
        selected = isSelected,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = Color.Red,
            selectedLabelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            selectedBorderColor = Color.Red
        )
    )
}

/**
 * Utility functions for building common filter trees
 */
object FilterTrees {

    /**
     * Build decade cascade tree for search results filtering.
     * Decades with fewer years (60s, 90s) go straight to individual years.
     * Full decades (70s, 80s) have an Early/Late intermediate level.
     */
    fun buildDecadeCascadeTree(): List<FilterNode> {
        return listOf(
            FilterNode(
                id = "60s",
                label = "60s",
                children = (1965..1969).map { year ->
                    FilterNode(id = year.toString(), label = year.toString())
                }
            ),
            FilterNode(
                id = "70s",
                label = "70s",
                children = listOf(
                    FilterNode(
                        id = "early_70s",
                        label = "Early 70s",
                        children = (1970..1974).map { year ->
                            FilterNode(id = year.toString(), label = year.toString())
                        }
                    ),
                    FilterNode(
                        id = "late_70s",
                        label = "Late 70s",
                        children = (1975..1979).map { year ->
                            FilterNode(id = year.toString(), label = year.toString())
                        }
                    )
                )
            ),
            FilterNode(
                id = "80s",
                label = "80s",
                children = listOf(
                    FilterNode(
                        id = "early_80s",
                        label = "Early 80s",
                        children = (1980..1984).map { year ->
                            FilterNode(id = year.toString(), label = year.toString())
                        }
                    ),
                    FilterNode(
                        id = "late_80s",
                        label = "Late 80s",
                        children = (1985..1989).map { year ->
                            FilterNode(id = year.toString(), label = year.toString())
                        }
                    )
                )
            ),
            FilterNode(
                id = "90s",
                label = "90s",
                children = (1990..1995).map { year ->
                    FilterNode(id = year.toString(), label = year.toString())
                }
            )
        )
    }

    /**
     * Build Grateful Dead era/tour filter tree for library
     */
    fun buildDeadToursTree(): List<FilterNode> {
        return listOf(
            FilterNode(
                id = "60s",
                label = "60s",
                children = listOf(
                    FilterNode("60s_spring", "Spring"),
                    FilterNode("60s_summer", "Summer"),
                    FilterNode("60s_fall", "Fall"),
                    FilterNode("60s_winter", "Winter")
                )
            ),
            FilterNode(
                id = "70s",
                label = "70s",
                children = listOf(
                    FilterNode("70s_spring", "Spring"),
                    FilterNode("70s_summer", "Summer"),
                    FilterNode("70s_fall", "Fall"),
                    FilterNode("70s_winter", "Winter")
                )
            ),
            FilterNode(
                id = "80s",
                label = "80s",
                children = listOf(
                    FilterNode("80s_spring", "Spring"),
                    FilterNode("80s_summer", "Summer"),
                    FilterNode("80s_fall", "Fall"),
                    FilterNode("80s_winter", "Winter")
                )
            ),
            FilterNode(
                id = "90s",
                label = "90s",
                children = listOf(
                    FilterNode("90s_spring", "Spring"),
                    FilterNode("90s_summer", "Summer"),
                    FilterNode("90s_fall", "Fall"),
                    FilterNode("90s_winter", "Winter")
                )
            )
        )
    }

    /**
     * Build venue/location filter tree for browse
     */
    fun buildVenueTree(): List<FilterNode> {
        return listOf(
            FilterNode(
                id = "west_coast",
                label = "West Coast",
                children = listOf(
                    FilterNode("california", "California"),
                    FilterNode("oregon", "Oregon"),
                    FilterNode("washington", "Washington")
                )
            ),
            FilterNode(
                id = "east_coast",
                label = "East Coast",
                children = listOf(
                    FilterNode("new_york", "New York"),
                    FilterNode("massachusetts", "Massachusetts"),
                    FilterNode("pennsylvania", "Pennsylvania")
                )
            ),
            FilterNode(
                id = "midwest",
                label = "Midwest",
                children = listOf(
                    FilterNode("illinois", "Illinois"),
                    FilterNode("ohio", "Ohio"),
                    FilterNode("michigan", "Michigan")
                )
            )
        )
    }

    /**
     * Build flat source type filter tree (SBD, AUD, FM, Matrix).
     */
    fun buildSourceTypeTree(): List<FilterNode> = listOf(
        FilterNode("SBD", "SBD"),
        FilterNode("FM", "FM"),
        FilterNode("MATRIX", "Matrix"),
        FilterNode("AUD", "AUD")
    )

    /**
     * Build simple home filter tree starting with "All"
     * Future expansion ready for "Recent", "Popular", "Favorites", etc.
     */
    fun buildHomeFiltersTree(): List<FilterNode> {
        return listOf(
            FilterNode(
                id = "all",
                label = "All"
            )
        )
    }

    /**
     * Build collections filter tree with Official/Guests/Eras structure
     */
    fun buildCollectionsTagsTree(): List<FilterNode> {
        return listOf(
            FilterNode(
                id = "official",
                label = "Official",
                children = listOf(
                    FilterNode("dicks-picks", "Dick's Picks"),
                    FilterNode("daves-picks", "Dave's Picks"),
                    FilterNode("box-set", "Box Sets"),
                    FilterNode("road-trips", "Road Trips"),
                    FilterNode("archive", "Archive Series"),
                    FilterNode("digital", "Digital Only"),
                    FilterNode("spotify", "Spotify Available")
                )
            ),
            FilterNode(
                id = "guest",
                label = "Guests"
            ),
            FilterNode(
                id = "era",
                label = "Eras"
            )
        )
    }
}
