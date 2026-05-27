package com.grateful.deadly.core.api.home

import com.grateful.deadly.core.model.Show
import kotlinx.coroutines.flow.StateFlow

/**
 * "Fan Favorites" shows for the home screen, ranked server-side by net
 * favorites / all-time logical listens (see /api/popular). Surfaces what
 * listeners *kept*, complementing Trending's what-just-got-played signal.
 *
 * Single all-time list (no windowing — the retention signal is meant to
 * change slowly). The implementation hydrates server-returned show IDs
 * into full Show objects from the local catalog.
 */
interface PopularService {
    val popular: StateFlow<PopularContent>
    suspend fun refresh(): Result<Unit>
}

data class PopularContent(
    val shows: List<Show>,
    val lastRefresh: Long,
) {
    companion object {
        fun initial() = PopularContent(shows = emptyList(), lastRefresh = 0L)
    }
}
