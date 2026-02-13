package com.grateful.deadly.core.api.player

import com.grateful.deadly.core.model.LineupMember

interface PanelContentService {
    suspend fun getCredits(showId: String): List<LineupMember>?
    suspend fun getVenueInfo(showId: String): String?
    suspend fun getLyrics(songTitle: String): String?
}
