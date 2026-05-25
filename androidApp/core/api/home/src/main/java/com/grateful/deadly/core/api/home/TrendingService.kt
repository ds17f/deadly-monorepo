package com.grateful.deadly.core.api.home

import com.grateful.deadly.core.model.Show
import kotlinx.coroutines.flow.StateFlow

/**
 * Trending shows for the home screen across four time windows.
 *
 * Backed by /api/trending which returns the most-played shows per window;
 * the implementation hydrates those show IDs into full Show objects from
 * the local catalog so callers only deal in domain models.
 */
interface TrendingService {
    val trending: StateFlow<TrendingContent>
    suspend fun refresh(): Result<Unit>
}

enum class TrendingWindow {
    NOW, WEEK, MONTH, ALL;

    /** Short label rendered on the home chip. */
    val shortLabel: String get() = when (this) {
        NOW -> "Day"
        WEEK -> "Week"
        MONTH -> "Month"
        ALL -> "All"
    }

    /** Persisted preference key. Stable across renames of shortLabel. */
    val key: String get() = when (this) {
        NOW -> "now"
        WEEK -> "week"
        MONTH -> "month"
        ALL -> "all"
    }

    /** Next window in cycle order. ALL wraps back to NOW. */
    fun next(): TrendingWindow = when (this) {
        NOW -> WEEK
        WEEK -> MONTH
        MONTH -> ALL
        ALL -> NOW
    }

    companion object {
        fun fromKey(key: String): TrendingWindow = when (key) {
            "week" -> WEEK
            "month" -> MONTH
            "all" -> ALL
            else -> NOW
        }
    }
}

data class TrendingContent(
    val now: List<Show>,
    val week: List<Show>,
    val month: List<Show>,
    val all: List<Show>,
    val lastRefresh: Long,
) {
    fun forWindow(window: TrendingWindow): List<Show> = when (window) {
        TrendingWindow.NOW -> now
        TrendingWindow.WEEK -> week
        TrendingWindow.MONTH -> month
        TrendingWindow.ALL -> all
    }

    companion object {
        fun initial() = TrendingContent(
            now = emptyList(),
            week = emptyList(),
            month = emptyList(),
            all = emptyList(),
            lastRefresh = 0L,
        )
    }
}
