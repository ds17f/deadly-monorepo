package com.grateful.deadly.core.api.home

import com.grateful.deadly.core.model.Show
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * "Fan Favorites" pools for the home screen. The server returns four
 * decade pools (60s/70s/80s/90s); the home rail picks its 4-show display
 * set from those pools locally via [PopularContent.displayShows], so the
 * "Show more" action can re-roll without a re-fetch.
 */
interface PopularService {
    val popular: StateFlow<PopularContent>
    suspend fun refresh(): Result<Unit>
}

enum class PopularDecade {
    ALL, S60, S70, S80, S90;

    /** Short label rendered on the Settings picker and the rail header. */
    val label: String get() = when (this) {
        ALL -> "all"
        S60 -> "60s"
        S70 -> "70s"
        S80 -> "80s"
        S90 -> "90s"
    }

    /** Persisted preference key. Stable across label tweaks. */
    val key: String get() = when (this) {
        ALL -> "all"
        S60 -> "60s"
        S70 -> "70s"
        S80 -> "80s"
        S90 -> "90s"
    }

    companion object {
        fun fromKey(key: String): PopularDecade = when (key) {
            "60s" -> S60
            "70s" -> S70
            "80s" -> S80
            "90s" -> S90
            else -> ALL
        }
    }
}

data class PopularContent(
    val pool60: List<Show>,
    val pool70: List<Show>,
    val pool80: List<Show>,
    val pool90: List<Show>,
    val lastRefresh: Long,
) {
    val hasAnyContent: Boolean get() =
        pool60.isNotEmpty() || pool70.isNotEmpty() || pool80.isNotEmpty() || pool90.isNotEmpty()

    /**
     * Compute the home-rail display set.
     * - [PopularDecade.ALL]: one show from each non-empty decade pool (≤4).
     * - Specific decade: up to [DISPLAY_COUNT] shows from that pool,
     *   biased toward distinct years within the decade so the rail spans
     *   the era.
     * [seed] controls the random selection; bumping it on "Show more"
     * re-rolls without re-fetching. Returned shows are date-sorted.
     */
    fun displayShows(decade: PopularDecade, seed: Int): List<Show> {
        val picks: List<Show> = when (decade) {
            PopularDecade.ALL -> pickOnePerDecade(seed)
            PopularDecade.S60 -> pickWithYearSpread(pool60, DISPLAY_COUNT, seed)
            PopularDecade.S70 -> pickWithYearSpread(pool70, DISPLAY_COUNT, seed)
            PopularDecade.S80 -> pickWithYearSpread(pool80, DISPLAY_COUNT, seed)
            PopularDecade.S90 -> pickWithYearSpread(pool90, DISPLAY_COUNT, seed)
        }
        return picks.sortedBy { it.date }
    }

    private fun pickOnePerDecade(seed: Int): List<Show> {
        val pools = listOf("60s" to pool60, "70s" to pool70, "80s" to pool80, "90s" to pool90)
        return pools.mapNotNull { (name, pool) ->
            if (pool.isEmpty()) null
            else pool.shuffled(Random(combinedSeed(seed, name))).first()
        }
    }

    private fun pickWithYearSpread(pool: List<Show>, count: Int, seed: Int): List<Show> {
        if (pool.isEmpty()) return emptyList()
        val shuffled = pool.shuffled(Random(combinedSeed(seed, "decade")))
        val seenYears = mutableSetOf<Int>()
        val primary = mutableListOf<Show>()
        val fallback = mutableListOf<Show>()
        for (show in shuffled) {
            if (seenYears.contains(show.year)) {
                fallback += show
            } else {
                primary += show
                seenYears += show.year
            }
        }
        return (primary + fallback).take(count)
    }

    companion object {
        const val DISPLAY_COUNT = 4
        fun initial() = PopularContent(
            pool60 = emptyList(),
            pool70 = emptyList(),
            pool80 = emptyList(),
            pool90 = emptyList(),
            lastRefresh = 0L,
        )
    }
}

private fun combinedSeed(a: Int, b: String): Int {
    // Mix the caller seed with a per-decade tag so "60s" and "70s"
    // produce distinct streams from the same caller seed.
    var h = a
    for (c in b) {
        h = h * 31 + c.code
    }
    return h
}
