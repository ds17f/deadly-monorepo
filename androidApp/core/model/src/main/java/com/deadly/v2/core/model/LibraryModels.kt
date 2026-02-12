package com.deadly.v2.core.model

import kotlinx.serialization.Serializable

/**
 * V2 Domain model representing a Show within the Library context.
 * 
 * Combines core concert data with library-specific metadata following
 * V2 architecture patterns. This model provides clean separation between
 * core Show data and library-specific state (pin status, download status).
 */
@Serializable
data class LibraryShow(
    val show: Show,
    val addedToLibraryAt: Long,
    val isPinned: Boolean = false,
    val downloadStatus: LibraryDownloadStatus = LibraryDownloadStatus.NOT_DOWNLOADED
) {
    val showId: String get() = show.id
    val date: String get() = show.date
    val venue: String get() = show.venue.name
    val location: String get() = show.location.displayText
    val displayTitle: String get() = show.displayTitle
    val displayLocation: String get() = show.location.displayText
    val displayVenue: String get() = show.venue.name
    val displayDate: String get() = formatDisplayDate(show.date)
    val recordingCount: Int get() = show.recordingIds.size
    val averageRating: Float? get() = show.averageRating
    val totalReviews: Int get() = show.totalReviews
    val isInLibrary: Boolean get() = true
    
    val isPinnedAndDownloaded: Boolean get() = isPinned && downloadStatus == LibraryDownloadStatus.COMPLETED
    val libraryAge: Long get() = System.currentTimeMillis() - addedToLibraryAt
    val isDownloaded: Boolean get() = downloadStatus == LibraryDownloadStatus.COMPLETED
    val isDownloading: Boolean get() = downloadStatus == LibraryDownloadStatus.DOWNLOADING
    
    val libraryStatusDescription: String get() = when {
        isPinned && isDownloaded -> "Pinned & Downloaded"
        isPinned -> "Pinned"
        isDownloaded -> "Downloaded"
        isDownloading -> "Downloading..."
        else -> "In Library"
    }
    
    val sortableAddedDate: Long get() = addedToLibraryAt
    val sortableShowDate: String get() = show.date
    val sortablePinStatus: Int get() = if (isPinned) 0 else 1
    
    private fun formatDisplayDate(date: String): String {
        return try {
            val parts = date.split("-")
            val year = parts[0]
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            
            val monthNames = arrayOf(
                "", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            
            "${monthNames[month]} $day, $year"
        } catch (e: Exception) {
            date
        }
    }
}

/**
 * Download status specific to library context
 */
@Serializable
enum class LibraryDownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Library statistics for the V2 library service
 */
@Serializable
data class LibraryStats(
    val totalShows: Int,
    val totalDownloaded: Int,
    val totalStorageUsed: Long,
    val totalPinned: Int = 0
)

/**
 * UI state for Library screens
 */
data class LibraryUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val shows: List<LibraryShowViewModel> = emptyList(),
    val stats: LibraryStats? = null,
    val selectedSortOption: LibrarySortOption = LibrarySortOption.DATE_ADDED,
    val selectedSortDirection: LibrarySortDirection = LibrarySortDirection.DESCENDING,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.LIST
)

/**
 * UI-specific ViewModel for LibraryShow display
 */
@Serializable
data class LibraryShowViewModel(
    val showId: String,
    val date: String,
    val displayDate: String,
    val venue: String,
    val location: String,
    val rating: Float?,
    val reviewCount: Int,
    val addedToLibraryAt: Long,
    val isPinned: Boolean,
    val downloadStatus: LibraryDownloadStatus,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val libraryStatusDescription: String
)

/**
 * Sort options for library display
 */
enum class LibrarySortOption(val displayName: String) {
    DATE_OF_SHOW("Show Date"),
    DATE_ADDED("Date Added"),
    VENUE("Venue"),
    RATING("Rating")
}

/**
 * Sort directions
 */
enum class LibrarySortDirection(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

/**
 * Display modes for library
 */
enum class LibraryDisplayMode {
    LIST,
    GRID
}

