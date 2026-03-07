package com.grateful.deadly.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a Show within the Favorites context.
 *
 * Combines core concert data with favorites-specific metadata following
 * Architecture patterns. This model provides clean separation between
 * core Show data and favorites-specific state (pin status, download status).
 */
@Serializable
data class FavoriteShow(
    val show: Show,
    val addedToFavoritesAt: Long,
    val isPinned: Boolean = false,
    val downloadStatus: FavoritesDownloadStatus = FavoritesDownloadStatus.NOT_DOWNLOADED,
    val notes: String? = null,
    val customRating: Float? = null,
    val recordingQuality: Int? = null,
    val playingQuality: Int? = null
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
    val isFavorite: Boolean get() = true

    val hasReview: Boolean get() = customRating != null || recordingQuality != null || playingQuality != null || !notes.isNullOrBlank()

    val isPinnedAndDownloaded: Boolean get() = isPinned && downloadStatus == FavoritesDownloadStatus.COMPLETED
    val favoriteAge: Long get() = System.currentTimeMillis() - addedToFavoritesAt
    val isDownloaded: Boolean get() = downloadStatus == FavoritesDownloadStatus.COMPLETED
    val isDownloading: Boolean get() = downloadStatus == FavoritesDownloadStatus.DOWNLOADING

    val statusDescription: String get() = when {
        isPinned && isDownloaded -> "Pinned & Downloaded"
        isPinned -> "Pinned"
        isDownloaded -> "Downloaded"
        isDownloading -> "Downloading..."
        else -> "Favorited"
    }

    val sortableAddedDate: Long get() = addedToFavoritesAt
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
 * Download status specific to favorites context
 */
@Serializable
enum class FavoritesDownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Aggregated download progress for a show.
 */
data class ShowDownloadProgress(
    val showId: String,
    val status: FavoritesDownloadStatus,
    val overallProgress: Float, // 0.0-1.0
    val downloadedBytes: Long,
    val totalBytes: Long,
    val tracksCompleted: Int,
    val tracksTotal: Int
)

/**
 * Favorites statistics
 */
@Serializable
data class FavoritesStats(
    val totalShows: Int,
    val totalDownloaded: Int,
    val totalStorageUsed: Long,
    val totalPinned: Int = 0
)

/**
 * UI state for Favorites screens
 */
data class FavoritesUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val shows: List<FavoriteShowViewModel> = emptyList(),
    val stats: FavoritesStats? = null,
    val selectedSortOption: FavoritesSortOption = FavoritesSortOption.DATE_ADDED,
    val selectedSortDirection: FavoritesSortDirection = FavoritesSortDirection.DESCENDING,
    val displayMode: FavoritesDisplayMode = FavoritesDisplayMode.LIST
)

/**
 * UI-specific ViewModel for FavoriteShow display
 */
@Serializable
data class FavoriteShowViewModel(
    val showId: String,
    val date: String,
    val displayDate: String,
    val venue: String,
    val location: String,
    val rating: Float?,
    val reviewCount: Int,
    val addedToFavoritesAt: Long,
    val isPinned: Boolean,
    val downloadStatus: FavoritesDownloadStatus,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val statusDescription: String,
    val bestRecordingId: String? = null,
    val coverImageUrl: String? = null,
    val recordingCount: Int = 0,
    val hasReview: Boolean = false,
    val customRating: Float? = null,
    val lineupMembers: List<String> = emptyList()
)

/**
 * UI-specific ViewModel for the Downloads screen
 */
data class DownloadedShowViewModel(
    val showId: String,
    val displayDate: String,
    val venue: String,
    val location: String,
    val storageBytes: Long,
    val status: FavoritesDownloadStatus,
    val progress: ShowDownloadProgress?,
    val recordingId: String?,
    val coverImageUrl: String?
)

/**
 * UI state for the Downloads screen
 */
data class DownloadsUiState(
    val isLoading: Boolean = true,
    val totalStorageUsed: Long = 0L,
    val activeDownloads: List<DownloadedShowViewModel> = emptyList(),
    val pausedDownloads: List<DownloadedShowViewModel> = emptyList(),
    val completedDownloads: List<DownloadedShowViewModel> = emptyList(),
    val showRemoveAllDialog: Boolean = false
) {
    val totalDownloadCount: Int get() = activeDownloads.size + pausedDownloads.size + completedDownloads.size
    val hasActiveDownloads: Boolean get() = activeDownloads.isNotEmpty()
    val hasPausedDownloads: Boolean get() = pausedDownloads.isNotEmpty()
    val isEmpty: Boolean get() = activeDownloads.isEmpty() && pausedDownloads.isEmpty() && completedDownloads.isEmpty()
}

/**
 * Sort options for favorites display
 */
enum class FavoritesSortOption(val displayName: String) {
    DATE_OF_SHOW("Show Date"),
    DATE_ADDED("Date Added"),
    VENUE("Venue"),
    RATING("Rating")
}

/**
 * Sort directions
 */
enum class FavoritesSortDirection(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

/**
 * Display modes for favorites
 */
enum class FavoritesDisplayMode {
    LIST,
    GRID
}

/**
 * Tab selection for favorites screen
 */
enum class FavoritesTab(val displayName: String) {
    SHOWS("Shows"),
    SONGS("Songs")
}

/**
 * Sort options for favorite songs
 */
enum class FavoritesSongSortOption(val displayName: String) {
    SONG_TITLE("Song Title"),
    SHOW_DATE("Show Date"),
    DATE_ADDED("Date Added")
}
