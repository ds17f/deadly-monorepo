package com.grateful.deadly.core.media.browse

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.grateful.deadly.core.api.collections.DeadCollectionsService
import com.grateful.deadly.core.api.favorites.FavoritesService
import com.grateful.deadly.core.api.favorites.ReviewService
import com.grateful.deadly.core.api.recent.RecentShowsService
import com.grateful.deadly.core.api.search.SearchService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.model.DeadCollection
import com.grateful.deadly.core.model.FavoriteTrack
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.core.model.RecordingSourceType
import com.grateful.deadly.core.model.Track
import com.grateful.deadly.core.network.archive.service.ArchiveService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowseTreeProvider @Inject constructor(
    private val showRepository: ShowRepository,
    private val recentShowsService: RecentShowsService,
    private val collectionsService: DeadCollectionsService,
    private val favoritesService: FavoritesService,
    private val reviewService: ReviewService,
    private val searchService: SearchService,
    private val archiveService: ArchiveService
) {
    companion object {
        private const val TAG = "BrowseTreeProvider"
        private val FORMAT_PRIORITY = listOf("VBR MP3", "MP3", "Ogg Vorbis")
        private const val DEAD_FIRST_YEAR = 1965
        private const val DEAD_LAST_YEAR = 1995
    }

    fun getRootItems(): List<MediaItem> = listOf(
        buildBrowsableItem(BrowseMediaId.RECENT, "Recent", "Recently played shows"),
        buildBrowsableItem(BrowseMediaId.LIBRARY, "Favorites", "Shows you've saved"),
        buildBrowsableItem(BrowseMediaId.TODAY, "TIGDH", "Today in Grateful Dead History"),
        buildBrowsableItem(BrowseMediaId.YEARS, "Browse by Year", "1965\u20131995"),
        buildBrowsableItem(BrowseMediaId.TOP_RATED, "Top Rated", "Highest rated shows"),
    )

    suspend fun getChildren(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val allItems = when {
            parentId == BrowseMediaId.ROOT -> getRootItems()
            parentId == BrowseMediaId.RECENT -> {
                recentShowsService.getRecentShows(20).map(::buildShowItem)
            }
            parentId == BrowseMediaId.LIBRARY -> listOf(
                buildBrowsableItem(BrowseMediaId.LIBRARY_SHOWS, "Shows", "Favorite shows"),
                buildBrowsableItem(BrowseMediaId.LIBRARY_SONGS, "Songs", "Favorite songs")
            )
            parentId == BrowseMediaId.LIBRARY_SHOWS -> {
                val shows = if (favoritesService.getCurrentShows().value.isNotEmpty()) {
                    favoritesService.getCurrentShows().value
                } else {
                    withTimeoutOrNull(3_000L) {
                        favoritesService.getCurrentShows().first { it.isNotEmpty() }
                    } ?: emptyList()
                }
                shows.map { buildShowItem(it.show) }
            }
            parentId == BrowseMediaId.LIBRARY_SONGS -> {
                reviewService.getFavoriteTracks().map(::buildFavoriteSongItem)
            }
            parentId == BrowseMediaId.TOP_RATED -> {
                showRepository.getTopRatedShows(50).map(::buildShowItem)
            }
            parentId == BrowseMediaId.TODAY -> {
                val cal = Calendar.getInstance()
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                showRepository.getShowsForDate(month, day).map(::buildShowItem)
            }
            parentId == BrowseMediaId.COLLECTIONS -> {
                collectionsService.getAllCollections()
                    .getOrNull()
                    ?.map(::buildCollectionItem)
                    ?: emptyList()
            }
            parentId == BrowseMediaId.YEARS -> {
                (DEAD_FIRST_YEAR..DEAD_LAST_YEAR).map(::buildYearItem)
            }
            BrowseMediaId.isCollection(parentId) -> {
                val id = BrowseMediaId.parseCollectionId(parentId)
                collectionsService.getCollectionShows(id)
                    .getOrNull()
                    ?.map(::buildShowItem)
                    ?: emptyList()
            }
            BrowseMediaId.isYear(parentId) -> {
                val year = BrowseMediaId.parseYear(parentId) ?: return emptyList()
                showRepository.getShowsByYear(year).map(::buildShowItem)
            }
            BrowseMediaId.isShow(parentId) -> {
                resolveShowToPlayableTracks(BrowseMediaId.parseShowId(parentId))
            }
            else -> emptyList()
        }

        val start = page * pageSize
        if (start >= allItems.size) return emptyList()
        return allItems.subList(start, minOf(start + pageSize, allItems.size))
    }

    suspend fun getItem(mediaId: String): MediaItem? = when {
        mediaId == BrowseMediaId.ROOT ->
            buildBrowsableItem(BrowseMediaId.ROOT, "Grateful Dead", "Browse the archive")
        mediaId == BrowseMediaId.RECENT ->
            buildBrowsableItem(BrowseMediaId.RECENT, "Recent", "Recently played shows")
        mediaId == BrowseMediaId.LIBRARY ->
            buildBrowsableItem(BrowseMediaId.LIBRARY, "Favorites", "Shows you've saved")
        mediaId == BrowseMediaId.LIBRARY_SHOWS ->
            buildBrowsableItem(BrowseMediaId.LIBRARY_SHOWS, "Shows", "Favorite shows")
        mediaId == BrowseMediaId.LIBRARY_SONGS ->
            buildBrowsableItem(BrowseMediaId.LIBRARY_SONGS, "Songs", "Favorite songs")
        mediaId == BrowseMediaId.TOP_RATED ->
            buildBrowsableItem(BrowseMediaId.TOP_RATED, "Top Rated", "Highest rated shows")
        mediaId == BrowseMediaId.TODAY ->
            buildBrowsableItem(BrowseMediaId.TODAY, "TIGDH", "Today in Grateful Dead History")
        mediaId == BrowseMediaId.COLLECTIONS ->
            buildBrowsableItem(BrowseMediaId.COLLECTIONS, "Collections", "Curated show collections")
        mediaId == BrowseMediaId.YEARS ->
            buildBrowsableItem(BrowseMediaId.YEARS, "Browse by Year", "1965\u20131995")
        BrowseMediaId.isShow(mediaId) ->
            showRepository.getShowById(BrowseMediaId.parseShowId(mediaId))?.let(::buildShowItem)
        BrowseMediaId.isCollection(mediaId) -> {
            val id = BrowseMediaId.parseCollectionId(mediaId)
            collectionsService.getCollectionDetails(id)
                .getOrNull()
                ?.collection
                ?.let(::buildCollectionItem)
        }
        BrowseMediaId.isYear(mediaId) -> {
            BrowseMediaId.parseYear(mediaId)?.let(::buildYearItem)
        }
        else -> null
    }

    suspend fun search(query: String): List<MediaItem> {
        // Try date parse first for voice queries like "5/8/77"
        val dateMatch = parseDateQuery(query)
        if (dateMatch != null) {
            val shows = showRepository.getShowsByDate(dateMatch)
            if (shows.isNotEmpty()) return shows.map(::buildShowItem)
        }

        // Fall through to existing FTS5 search
        searchService.updateSearchQuery(normalizeSearchQuery(query))
        return withTimeoutOrNull(5_000L) {
            searchService.searchResults
                .first { it.isNotEmpty() }
                .map { buildShowItem(it.show) }
        } ?: emptyList()
    }

    suspend fun resolveShowToPlayableTracks(showId: String): List<MediaItem> {
        val show = showRepository.getShowById(showId) ?: return emptyList()
        val recordingId = show.bestRecordingId
            ?: showRepository.getBestRecordingForShow(showId)?.identifier
            ?: showRepository.getRecordingsForShow(showId).firstOrNull()?.identifier
            ?: return emptyList()

        val allTracks = archiveService.getRecordingTracks(recordingId)
            .getOrNull() ?: return emptyList()

        val format = FORMAT_PRIORITY.firstOrNull { fmt ->
            allTracks.any { it.format.equals(fmt, ignoreCase = true) }
        } ?: return emptyList()

        return allTracks
            .filter { it.format.equals(format, ignoreCase = true) }
            .mapIndexed { index, track ->
                buildTrackItem(show, recordingId, format, track, index)
            }
    }

    suspend fun resolveRecordingToPlayableTracks(
        showId: String,
        recordingId: String,
        format: String
    ): List<MediaItem> {
        val show = showRepository.getShowById(showId) ?: return emptyList()
        val allTracks = archiveService.getRecordingTracks(recordingId)
            .getOrNull() ?: return emptyList()

        return allTracks
            .filter { it.format.equals(format, ignoreCase = true) }
            .mapIndexed { index, track ->
                buildTrackItem(show, recordingId, format, track, index)
            }
    }

    // -- MediaItem builders --------------------------------------------------

    private fun buildBrowsableItem(
        mediaId: String,
        title: String,
        subtitle: String
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private fun buildShowItem(show: Show): MediaItem {
        val subtitle = buildString {
            append(show.venue.name)
            show.location.displayText.takeIf { it.isNotBlank() }?.let {
                append(" \u2022 $it")
            }
            show.averageRating?.let {
                append(" \u2022 ${"%.1f".format(it)}\u2605")
            }
            if (show.bestSourceType != RecordingSourceType.UNKNOWN) {
                append(" \u2022 ${show.bestSourceType.displayName}")
            }
        }
        return MediaItem.Builder()
            .setMediaId(BrowseMediaId.show(show.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(formatShowDate(show.date))
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setIsPlayable(true)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                    .apply {
                        if (!show.coverImageUrl.isNullOrBlank()) {
                            setArtworkUri(Uri.parse(show.coverImageUrl))
                        }
                    }
                    .build()
            )
            .build()
    }

    private fun buildCollectionItem(collection: DeadCollection): MediaItem =
        MediaItem.Builder()
            .setMediaId(BrowseMediaId.collection(collection.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(collection.name)
                    .setSubtitle(collection.showCountText)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                    .build()
            )
            .build()

    private fun buildYearItem(year: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(BrowseMediaId.year(year))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(year.toString())
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                    .build()
            )
            .build()

    private fun buildTrackItem(
        show: Show,
        recordingId: String,
        format: String,
        track: Track,
        index: Int
    ): MediaItem {
        val uri = "https://archive.org/download/$recordingId/${track.name}"
        return MediaItem.Builder()
            .setMediaId(BrowseMediaId.track(show.id, recordingId, index))
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title ?: track.name)
                    .setArtist("${formatShowDate(show.date)} - ${show.venue.name}")
                    .setAlbumTitle("${formatShowDate(show.date)} - ${show.venue.name}")
                    .setTrackNumber(track.trackNumber)
                    .setArtworkUri(
                        if (!show.coverImageUrl.isNullOrBlank()) {
                            Uri.parse(show.coverImageUrl)
                        } else {
                            com.grateful.deadly.core.media.artwork.ArtworkProvider.buildUri(recordingId)
                        }
                    )
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(Bundle().apply {
                        putString("showId", show.id)
                        putString("recordingId", recordingId)
                        putString("showDate", show.date)
                        putString("venue", show.venue.name)
                        putString("location", show.location.displayText)
                        putString("filename", track.name)
                        putString("format", format)
                        putString("trackUrl", uri)
                        show.coverImageUrl?.let { putString("coverImageUrl", it) }
                    })
                    .build()
            )
            .build()
    }

    private fun buildFavoriteSongItem(track: FavoriteTrack): MediaItem {
        val mediaId = BrowseMediaId.favoriteSong(
            track.showId,
            track.recordingId ?: "",
            track.trackNumber ?: 0
        )
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.trackTitle)
                    .setArtist("${formatShowDate(track.showDate)} \u2014 ${track.venue}")
                    .setAlbumTitle("${formatShowDate(track.showDate)} \u2014 ${track.venue}")
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(Bundle().apply {
                        putString("showId", track.showId)
                        track.recordingId?.let { putString("recordingId", it) }
                        putString("showDate", track.showDate)
                        putString("venue", track.venue)
                    })
                    .build()
            )
            .build()
    }

    // -- Voice search helpers ------------------------------------------------

    private val venueAliases = mapOf(
        "msg" to "Madison Square Garden",
        "the garden" to "Madison Square Garden",
        "the fillmore" to "Fillmore",
        "fillmore east" to "Fillmore East",
        "fillmore west" to "Fillmore West",
        "red rocks" to "Red Rocks Amphitheatre",
        "winterland" to "Winterland",
        "barton hall" to "Barton Hall",
    )

    private fun normalizeSearchQuery(query: String): String {
        val lower = query.trim().lowercase()
        return venueAliases[lower] ?: query
    }

    /**
     * Attempts to parse date formats commonly spoken via voice assistants:
     * "5/8/77", "5-8-77", "5/8/1977", "May 8 1977", etc.
     * Returns ISO date "YYYY-MM-DD" or null.
     */
    private fun parseDateQuery(query: String): String? {
        val trimmed = query.trim()

        // M/D/YY or M/D/YYYY
        tryParseSeparatedDate(trimmed, "/")?.let { return it }
        // M-D-YY or M-D-YYYY (only if first part is 1-2 digits, to avoid YYYY-MM-DD confusion)
        val dashParts = trimmed.split("-")
        if (dashParts.size == 3 && dashParts[0].length <= 2) {
            tryParseSeparatedDate(trimmed, "-")?.let { return it }
        }

        // "Month D YYYY" or "Month D, YYYY"
        tryParseWrittenDate(trimmed)?.let { return it }

        return null
    }

    private fun tryParseSeparatedDate(input: String, separator: String): String? {
        val parts = input.split(separator)
        if (parts.size != 3) return null
        val month = parts[0].toIntOrNull() ?: return null
        val day = parts[1].toIntOrNull() ?: return null
        val yearRaw = parts[2].toIntOrNull() ?: return null
        val year = expandYear(yearRaw) ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private val monthNames = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4,
        "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private fun tryParseWrittenDate(input: String): String? {
        val cleaned = input.replace(",", "")
        val parts = cleaned.split(" ").filter { it.isNotBlank() }
        if (parts.size != 3) return null
        val month = monthNames[parts[0].lowercase()] ?: return null
        val day = parts[1].toIntOrNull() ?: return null
        val yearRaw = parts[2].toIntOrNull() ?: return null
        val year = expandYear(yearRaw) ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun expandYear(raw: Int): Int? = when {
        raw in 1900..2100 -> raw
        raw in 65..99 -> 1900 + raw
        raw in 0..5 -> 2000 + raw
        else -> null
    }

    private fun formatShowDate(dateString: String): String = try {
        val parts = dateString.split("-")
        if (parts.size == 3) {
            val year = parts[0]
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            val monthNames = arrayOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
            "${monthNames[month - 1]} $day, $year"
        } else dateString
    } catch (e: Exception) {
        dateString
    }
}
