package com.grateful.deadly.core.media.browse

import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.grateful.deadly.core.api.collections.DeadCollectionsService
import com.grateful.deadly.core.api.library.LibraryService
import com.grateful.deadly.core.api.recent.RecentShowsService
import com.grateful.deadly.core.api.search.SearchService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.model.DeadCollection
import com.grateful.deadly.core.model.Show
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
    private val libraryService: LibraryService,
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
        buildBrowsableItem(BrowseMediaId.LIBRARY, "Library", "Shows you've saved"),
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
            parentId == BrowseMediaId.LIBRARY -> {
                val shows = if (libraryService.getCurrentShows().value.isNotEmpty()) {
                    libraryService.getCurrentShows().value
                } else {
                    withTimeoutOrNull(3_000L) {
                        libraryService.getCurrentShows().first { it.isNotEmpty() }
                    } ?: emptyList()
                }
                shows.map { buildShowItem(it.show) }
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
            buildBrowsableItem(BrowseMediaId.LIBRARY, "Library", "Shows you've saved")
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
        searchService.updateSearchQuery(query)
        return withTimeoutOrNull(5_000L) {
            searchService.searchResults
                .first { it.isNotEmpty() }
                .map { buildShowItem(it.show) }
        } ?: emptyList()
    }

    suspend fun resolveShowToPlayableTracks(showId: String): List<MediaItem> {
        val show = showRepository.getShowById(showId) ?: return emptyList()
        val recording = showRepository.getBestRecordingForShow(showId)
            ?: showRepository.getRecordingsForShow(showId).firstOrNull()
            ?: return emptyList()

        val allTracks = archiveService.getRecordingTracks(recording.identifier)
            .getOrNull() ?: return emptyList()

        val format = FORMAT_PRIORITY.firstOrNull { fmt ->
            allTracks.any { it.format.equals(fmt, ignoreCase = true) }
        } ?: return emptyList()

        return allTracks
            .filter { it.format.equals(format, ignoreCase = true) }
            .mapIndexed { index, track ->
                buildTrackItem(show, recording.identifier, format, track, index)
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
                    })
                    .build()
            )
            .build()
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
