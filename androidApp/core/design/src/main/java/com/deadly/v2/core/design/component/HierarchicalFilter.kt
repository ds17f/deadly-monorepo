package com.deadly.v2.core.design.component

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
)

/**
 * Represents the current filter selection path
 */
data class FilterPath(
    val nodes: List<FilterNode> = emptyList()
) {
    val isNotEmpty: Boolean get() = nodes.isNotEmpty()
    val isEmpty: Boolean get() = nodes.isEmpty()
    
    /**
     * Get display text for the full path (e.g., "[70s] Spring")
     */
    fun getDisplayText(): String {
        return if (nodes.size >= 2) {
            "[${nodes[0].label}] ${nodes[1].label}"
        } else if (nodes.size == 1) {
            nodes[0].label
        } else {
            ""
        }
    }
    
    /**
     * Get combined ID for the full path (e.g., "70s_summer")
     */
    fun getCombinedId(): String = nodes.joinToString("_") { it.id }
}

/**
 * Spotify-style hierarchical filter component for V2
 * 
 * Features:
 * - Hierarchical navigation through filter tree
 * - Clear button to reset selection
 * - Combined display of selected path with visual separator
 * - Reusable across different screens with different filter trees
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
    // Current level being displayed (root or children of selected parent)
    val currentLevel = remember(selectedPath) {
        if (selectedPath.isEmpty) {
            filterTree
        } else {
            // Show children of the last selected node if we haven't completed the selection
            if (selectedPath.nodes.size == 1) {
                selectedPath.nodes.lastOrNull()?.children ?: emptyList()
            } else {
                emptyList()
            }
        }
    }
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        //contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Clear button - always show first
        item {
            FilterClearButton(
                onClick = { onSelectionChanged(FilterPath()) }
            )
        }
        
        // Show either the combined selection or individual options
        when {
            // Complete selection (2 levels) - show as single combined chip
            selectedPath.nodes.size >= 2 -> {
                item {
                    CombinedSelectionChip(
                        path = selectedPath,
                        onClick = { 
                            // Navigate back one level
                            val newPath = FilterPath(selectedPath.nodes.dropLast(1))
                            onSelectionChanged(newPath)
                        }
                    )
                }
            }
            
            // Partial selection (1 level) - show selected + options, with selected highlighted
            selectedPath.nodes.size == 1 -> {
                val selectedNode = selectedPath.nodes.first()
                
                // Show selected node as highlighted
                item {
                    FilterOptionChip(
                        node = selectedNode,
                        isSelected = true,
                        onClick = {
                            // Navigate back to root
                            onSelectionChanged(FilterPath())
                        }
                    )
                }
                
                // Show child options
                items(currentLevel) { node ->
                    FilterOptionChip(
                        node = node,
                        isSelected = false,
                        onClick = {
                            // Add this node to the path
                            val newPath = FilterPath(selectedPath.nodes + node)
                            onSelectionChanged(newPath)
                        }
                    )
                }
            }
            
            // No selection - show root level options
            else -> {
                items(filterTree) { node ->
                    FilterOptionChip(
                        node = node,
                        isSelected = false,
                        onClick = {
                            // Start new path with this node
                            val newPath = FilterPath(listOf(node))
                            onSelectionChanged(newPath)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Clear button (X) for resetting filter selection - styled like other filter chips
 */
@Composable
private fun FilterClearButton(
    onClick: () -> Unit
) {
    FilterChip(
        onClick = onClick,
        label = { 
            Text(
                text = "✕",
                style = MaterialTheme.typography.labelMedium
            )
        },
        selected = false,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false
        )
    )
}

/**
 * Combined selection chip that looks like one chip but shows visual separator
 * between the first and second selections (e.g., "70s] Spring")
 */
@Composable
private fun CombinedSelectionChip(
    path: FilterPath,
    onClick: () -> Unit
) {
    if (path.nodes.size < 2) return
    
    val firstNode = path.nodes[0]
    val secondNode = path.nodes[1]
    
    // Spacing configuration - adjust these values to fine-tune the appearance
    val firstTextStartPadding = 0.dp
    val firstTextEndPadding = 6.dp
    val separatorWidth = 16.dp
    val separatorHeight = 32.dp
    val separatorHorizontalPadding = 6.dp
    val secondTextStartPadding = 4.dp
    val secondTextEndPadding = 0.dp
    
    FilterChip(
        onClick = onClick,
        label = { 
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // First part (e.g., "70s")
                Text(
                    text = firstNode.label,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(start = firstTextStartPadding, end = firstTextEndPadding)
                )
                
                // Visual separator that looks like the right edge of a FilterChip
                Box(
                    modifier = Modifier
                        .width(separatorWidth)
                        .height(separatorHeight)
                        .padding(horizontal = separatorHorizontalPadding)
                        .drawBehind {
                            val strokeWidth = 1.dp.toPx()
                            val cornerRadius = 8.dp.toPx() // FilterChip corner radius  
                            val centerX = size.width / 2
                            
                            // Draw the right edge of a rounded rectangle (chip border)
                            // This creates a curved line that curves outward (right) like a chip edge
                            drawPath(
                                path = androidx.compose.ui.graphics.Path().apply {
                                    // Start at top left, curve outward to the right
                                    moveTo(centerX - cornerRadius, 0f)
                                    cubicTo(
                                        centerX - cornerRadius * 0.448f, 0f,  // Adjusted control point
                                        centerX - cornerRadius * 0.1f, cornerRadius * 0.448f,  // Better curve
                                        centerX - cornerRadius * 0.1f, cornerRadius
                                    )
                                    
                                    // Straight line down the right side
                                    lineTo(centerX - cornerRadius * 0.1f, size.height - cornerRadius)
                                    
                                    // Curve outward at bottom
                                    cubicTo(
                                        centerX - cornerRadius * 0.1f, size.height - cornerRadius * 0.448f,  // Better curve
                                        centerX - cornerRadius * 0.448f, size.height,  // Adjusted control point
                                        centerX - cornerRadius, size.height
                                    )
                                },
                                color = Color.Black,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                        }
                )
                
                // Second part (e.g., "Spring")
                Text(
                    text = secondNode.label,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(start = secondTextStartPadding, end = secondTextEndPadding)
                )
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
 * Utility functions for building common filter trees for V2
 */
object FilterTrees {
    
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
     * Build simple home filter tree starting with "All"
     * Future expansion ready for "Recent", "Popular", "Your Library", etc.
     */
    fun buildHomeFiltersTree(): List<FilterNode> {
        return listOf(
            FilterNode(
                id = "all",
                label = "All"
                // Future expansion:
                // FilterNode("recent", "Recent", children = listOf(
                //     FilterNode("recent_week", "This Week"),
                //     FilterNode("recent_month", "This Month")
                // )),
                // FilterNode("popular", "Popular"),
                // FilterNode("your_library", "Your Library")
            )
        )
    }
    
    /**
     * Build collections filter tree with Official/Guests/Eras structure
     * Official has sub-categories, Guests and Eras are single-level
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
                // No children - selecting "Guests" shows all guest collections
            ),
            FilterNode(
                id = "era",
                label = "Eras"
                // No children - selecting "Eras" shows all era collections
            )
        )
    }
}