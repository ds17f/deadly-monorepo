package com.grateful.deadly.core.model

/**
 * Cleans raw archive.org track titles for use in API searches (Genius lyrics)
 * and setlist matching.
 *
 * Archive titles often contain track numbers, segue markers, durations, etc.
 * that interfere with search accuracy.
 */
object SongTitleScrubber {

    private val LEADING_TRACK_NUMBER = Regex("""^\d+[\s.\-]+""")
    private val TRAILING_SEGUE = Regex("""\s*>+\s*$""")
    private val LEADING_SEGUE = Regex("""^>+\s*""")
    private val BRACKETED_SUFFIX = Regex("""\s*\[.*?]\s*$""")
    private val PAREN_DURATION = Regex("""\s*\(\d+:\d+\)\s*$""")
    private val MULTI_SPACE = Regex("""\s{2,}""")
    private const val INLINE_SEGUE = " > "

    /**
     * Scrubs a raw archive.org track title to a clean song name.
     *
     * Applied in order:
     * 1. Strip leading track numbers ("02 Scarlet Begonias" → "Scarlet Begonias")
     * 2. Strip trailing segue markers ("Scarlet Begonias >" → "Scarlet Begonias")
     * 3. Strip leading segue markers ("> Fire on the Mountain" → "Fire on the Mountain")
     * 4. Strip bracketed suffixes ("Scarlet Begonias [10:23]" → "Scarlet Begonias")
     * 5. Strip parenthesized durations ("Scarlet Begonias (10:23)" → "Scarlet Begonias")
     * 6. Normalize whitespace
     * 7. Handle inline segues ("Scarlet > Fire" → "Scarlet") — take first segment
     */
    fun scrub(rawTitle: String): String {
        var title = rawTitle.trim()
        if (title.isBlank()) return ""

        // Handle inline segue first: "Scarlet > Fire" → take first segment
        if (title.contains(INLINE_SEGUE)) {
            title = title.substringBefore(INLINE_SEGUE).trim()
        }

        title = title
            .replace(LEADING_TRACK_NUMBER, "")
            .replace(TRAILING_SEGUE, "")
            .replace(LEADING_SEGUE, "")
            .replace(BRACKETED_SUFFIX, "")
            .replace(PAREN_DURATION, "")
            .replace(MULTI_SPACE, " ")
            .trim()

        return title
    }

    /**
     * Checks if a scrubbed track title matches a setlist song name.
     *
     * Uses case-insensitive exact match first, then falls back to a contains
     * check for partial titles (e.g., "Scarlet" matching "Scarlet Begonias").
     */
    fun matchesSetlistSong(scrubbedTitle: String, setlistSongName: String): Boolean {
        if (scrubbedTitle.isBlank() || setlistSongName.isBlank()) return false
        if (scrubbedTitle.equals(setlistSongName, ignoreCase = true)) return true
        return setlistSongName.contains(scrubbedTitle, ignoreCase = true)
    }
}
