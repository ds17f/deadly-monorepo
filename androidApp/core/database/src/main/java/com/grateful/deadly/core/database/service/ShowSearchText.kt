package com.grateful.deadly.core.database.service

/**
 * Builds the `show_search` FTS `searchText` blob for a show.
 *
 * Extracted so the two import paths produce **identical** search text:
 *  - [DataImportService] (JSON `data.zip` import) builds inputs from the raw
 *    show JSON.
 *  - [SeedDatabaseImportService] (prebuilt `catalog.db`) builds the same inputs
 *    from the copied `shows` row plus the show's distinct recording source types.
 *
 * Keeping this in one place means search behaves the same regardless of how the
 * catalog was populated. If you change the indexed text, change it here only.
 */
object ShowSearchText {

    /**
     * @param date         full date, e.g. "1977-05-08"
     * @param venue        venue name
     * @param locationRaw  original location string, e.g. "Ithaca, NY"
     * @param memberListCsv comma-joined member names ("Jerry Garcia,Bob Weir") or null
     * @param songListCsv  comma-joined song names or null
     * @param sourceTypeKeys uppercase source-type keys present for the show
     *                       (e.g. {"SBD","AUD"}); used for soundboard/audience/matrix tags
     * @param avgRating    average rating (0.0 if none)
     * @param totalReviews high + low review counts across recordings
     */
    fun build(
        date: String,
        venue: String,
        locationRaw: String?,
        memberListCsv: String?,
        songListCsv: String?,
        sourceTypeKeys: Set<String>,
        avgRating: Double,
        totalReviews: Int
    ): String = buildList {
        // Enhanced date indexing for comprehensive search support
        add(date) // Original: "1977-05-08"

        // Parse date components for alternative formats
        val dateParts = date.split("-")
        val year = dateParts[0]      // "1977"
        val month = dateParts[1]     // "05"
        val day = dateParts[2]       // "08"

        // Core date components
        add(year)                    // "1977"
        add(year.takeLast(2))        // "77"

        // Original delimiters: -, /, .
        val delimiters = listOf("-", "/", ".")

        // Day / month / year formats
        delimiters.forEach { delim ->
            add("${month.toInt()}$delim${day.toInt()}$delim${year.takeLast(2)}")  // 5-8-77
            add("${month}$delim${day}$delim${year}")                               // 05-08-1977
            add("${year}$delim${month.toInt()}$delim${day.toInt()}")               // 1977-5-8
        }

        // Month / year formats
        delimiters.forEach { delim ->
            add("${month.toInt()}$delim${year.takeLast(2)}")  // 5-77
            add("${year}$delim${month}")                      // 1977-05
            add("${year}$delim${month.toInt()}")             // 1977-5
            add("${year.takeLast(2)}$delim${month.toInt()}") // 77-5
        }

        // Century prefix for decade searches
        add(year.take(3))            // "197" (enables 1970s searches)

        // Venue information
        add(venue)

        // Consolidated location
        locationRaw?.let { add(it) } // "Ithaca, NY"

        // Band member names (no instruments - reduces noise)
        if (!memberListCsv.isNullOrBlank()) {
            add(memberListCsv.replace(",", " ")) // "Jerry Garcia Bob Weir Phil Lesh"
        }

        // Song list for setlist searches
        if (!songListCsv.isNullOrBlank()) {
            add(songListCsv.replace(",", " ")) // "Scarlet Begonias Fire On The Mountain"
        }

        // Source type tags
        if (sourceTypeKeys.contains("SBD")) add("soundboard sbd")
        if (sourceTypeKeys.contains("AUD")) add("audience aud")
        if (sourceTypeKeys.contains("MATRIX")) add("matrix")

        // Quality/popularity tags
        if (avgRating >= 4.0 && totalReviews >= 10) add("top-rated")
        if (totalReviews >= 50) add("popular")
    }.joinToString(" ")
}
