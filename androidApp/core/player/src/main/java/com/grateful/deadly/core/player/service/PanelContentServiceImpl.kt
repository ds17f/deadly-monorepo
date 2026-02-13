package com.grateful.deadly.core.player.service

import android.util.Log
import com.grateful.deadly.core.api.player.PanelContentService
import com.grateful.deadly.core.domain.repository.ShowRepository
import com.grateful.deadly.core.model.LineupMember
import com.grateful.deadly.core.model.SongTitleScrubber
import com.grateful.deadly.core.network.genius.service.GeniusService
import com.grateful.deadly.core.network.wikipedia.service.WikipediaService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanelContentServiceImpl @Inject constructor(
    private val showRepository: ShowRepository,
    private val wikipediaService: WikipediaService,
    private val geniusService: GeniusService
) : PanelContentService {

    companion object {
        private const val TAG = "PanelContentService"
        private const val ARTIST = "Grateful Dead"
    }

    override suspend fun getCredits(showId: String): List<LineupMember>? {
        return try {
            showRepository.getShowById(showId)
                ?.lineup
                ?.members
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading credits for show: $showId", e)
            null
        }
    }

    override suspend fun getVenueInfo(showId: String): String? {
        return try {
            val show = showRepository.getShowById(showId) ?: return null
            val venueName = show.venue.name.takeIf { it.isNotBlank() } ?: return null
            wikipediaService.getVenueSummary(venueName, show.venue.city)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading venue info for show: $showId", e)
            null
        }
    }

    override suspend fun getLyrics(songTitle: String): String? {
        return try {
            val scrubbed = SongTitleScrubber.scrub(songTitle)
            if (scrubbed.isBlank()) return null
            geniusService.getLyrics(scrubbed, ARTIST)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading lyrics for: $songTitle", e)
            null
        }
    }
}
