package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * UI models for setlist display
 * 
 * These models are for UI presentation and are separate from domain models.
 * They represent exactly what the setlist UI components need to display.
 */

/**
 * SetlistViewModel - UI representation of a show's setlist
 * 
 * Contains show information and all sets for display in the setlist modal.
 */
@Serializable
data class SetlistViewModel(
    val showDate: String,      // "May 8, 1977"
    val venue: String,         // "Barton Hall, Cornell University"
    val location: String,      // "Ithaca, NY"
    val sets: List<SetlistSetViewModel>
)

/**
 * SetlistSetViewModel - UI representation of a single set
 * 
 * Contains set name and all songs in that set.
 */
@Serializable
data class SetlistSetViewModel(
    val name: String,          // "Set One", "Set Two", "Encore"
    val songs: List<SetlistSongViewModel>
)

/**
 * SetlistSongViewModel - UI representation of a single song in a setlist
 * 
 * Contains song information as needed for display, including segue indicators.
 */
@Serializable
data class SetlistSongViewModel(
    val position: Int?,        // Song position in set (1, 2, 3, etc.) - null if not available
    val name: String,          // "Playing in the Band"
    val hasSegue: Boolean = false,     // True if this song segues into the next
    val segueSymbol: String? = null    // ">" for segue, "->" for jam transition
) {
    /**
     * Display name with segue symbol if applicable
     */
    val displayName: String
        get() = if (hasSegue && segueSymbol != null) {
            "$name $segueSymbol"
        } else {
            name
        }
}