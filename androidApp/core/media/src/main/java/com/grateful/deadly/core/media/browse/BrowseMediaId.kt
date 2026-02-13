package com.grateful.deadly.core.media.browse

/**
 * Media ID scheme for the Android Auto browse tree.
 *
 * [ROOT]
 * ├── recent              → Recently Played
 * ├── library             → My Library
 * ├── top_rated           → Top Rated Shows
 * ├── today               → Today in Dead History
 * ├── collections         → Collections list
 * │   └── collection/{id} → Shows in collection
 * ├── years               → Browse by Year
 * │   └── year/{year}     → Shows for year
 * └── show/{showId}       → Tracks for show
 *
 * Track items: track/{showId}/{recordingId}/{index}
 */
object BrowseMediaId {

    const val ROOT = "[ROOT]"
    const val RECENT = "recent"
    const val LIBRARY = "library"
    const val TOP_RATED = "top_rated"
    const val TODAY = "today"
    const val COLLECTIONS = "collections"
    const val YEARS = "years"

    private const val COLLECTION_PREFIX = "collection/"
    private const val YEAR_PREFIX = "year/"
    private const val SHOW_PREFIX = "show/"
    private const val TRACK_PREFIX = "track/"

    fun collection(id: String): String = "$COLLECTION_PREFIX$id"
    fun year(year: Int): String = "$YEAR_PREFIX$year"
    fun show(showId: String): String = "$SHOW_PREFIX$showId"
    fun track(showId: String, recordingId: String, index: Int): String =
        "$TRACK_PREFIX$showId/$recordingId/$index"

    fun isCollection(mediaId: String): Boolean = mediaId.startsWith(COLLECTION_PREFIX)
    fun isYear(mediaId: String): Boolean = mediaId.startsWith(YEAR_PREFIX)
    fun isShow(mediaId: String): Boolean = mediaId.startsWith(SHOW_PREFIX)
    fun isTrack(mediaId: String): Boolean = mediaId.startsWith(TRACK_PREFIX)

    fun parseCollectionId(mediaId: String): String = mediaId.removePrefix(COLLECTION_PREFIX)
    fun parseYear(mediaId: String): Int? = mediaId.removePrefix(YEAR_PREFIX).toIntOrNull()
    fun parseShowId(mediaId: String): String = mediaId.removePrefix(SHOW_PREFIX)

    data class TrackId(val showId: String, val recordingId: String, val index: Int)

    fun parseTrack(mediaId: String): TrackId? {
        val parts = mediaId.removePrefix(TRACK_PREFIX).split("/")
        if (parts.size != 3) return null
        val index = parts[2].toIntOrNull() ?: return null
        return TrackId(parts[0], parts[1], index)
    }
}
