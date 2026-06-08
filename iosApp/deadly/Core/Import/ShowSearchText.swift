import Foundation

/// Builds the `show_search` FTS `searchText` blob for a show.
///
/// Extracted so the two import paths produce **identical** search text:
///  - `ShowImporter.buildSearchText(from:)` (JSON `data.zip` import) builds the
///    inputs from the raw show JSON.
///  - `SeedImportService` (prebuilt `catalog.db`) builds the same inputs from the
///    copied `shows` row plus the show's distinct recording source types.
///
/// Keeping this in one place means search behaves the same regardless of how the
/// catalog was populated. If you change the indexed text, change it here only.
/// Mirrors the Android `ShowSearchText` builder.
enum ShowSearchText {

    /// - Parameters:
    ///   - date: full date, e.g. "1977-05-08"
    ///   - venue: venue name
    ///   - locationRaw: original location string, e.g. "Ithaca, NY"
    ///   - memberListCsv: comma-joined member names ("Jerry Garcia,Bob Weir") or nil
    ///   - songListCsv: comma-joined song names or nil
    ///   - sourceTypeKeys: uppercase source-type keys present for the show (e.g. ["SBD","AUD"])
    ///   - avgRating: average rating (0.0 if none)
    ///   - totalReviews: high + low review counts across recordings
    static func build(
        date: String,
        venue: String,
        locationRaw: String?,
        memberListCsv: String?,
        songListCsv: String?,
        sourceTypeKeys: Set<String>,
        avgRating: Double,
        totalReviews: Int
    ) -> String {
        var parts: [String] = []

        // Date variations
        let dateParts = date.split(separator: "-").map(String.init)
        guard dateParts.count == 3 else {
            return date
        }
        let yearStr = dateParts[0]
        let monthStr = dateParts[1]
        let dayStr = dateParts[2]
        let monthInt = Int(monthStr) ?? 0
        let dayInt = Int(dayStr) ?? 0
        let yearShort = String(yearStr.suffix(2))
        let yearPrefix = String(yearStr.prefix(3))

        parts.append(date)               // "1977-05-08"
        parts.append(yearStr)            // "1977"
        parts.append(yearShort)          // "77"
        parts.append(yearPrefix)         // "197"

        let delimiters = ["-", "/", "."]

        // Day/month/year formats
        for d in delimiters {
            parts.append("\(monthInt)\(d)\(dayInt)\(d)\(yearShort)")   // 5-8-77
            parts.append("\(monthStr)\(d)\(dayStr)\(d)\(yearStr)")     // 05-08-1977
            parts.append("\(yearStr)\(d)\(monthInt)\(d)\(dayInt)")     // 1977-5-8
        }

        // Month/year formats
        for d in delimiters {
            parts.append("\(monthInt)\(d)\(yearShort)")   // 5-77
            parts.append("\(yearStr)\(d)\(monthStr)")     // 1977-05
            parts.append("\(yearStr)\(d)\(monthInt)")     // 1977-5
            parts.append("\(yearShort)\(d)\(monthInt)")   // 77-5
        }

        // Location
        parts.append(venue)
        if let loc = locationRaw, !loc.isEmpty { parts.append(loc) }

        // Members (space-separated names)
        if let members = memberListCsv, !members.isEmpty {
            parts.append(members.replacingOccurrences(of: ",", with: " "))
        }

        // Songs (space-separated)
        if let songs = songListCsv, !songs.isEmpty {
            parts.append(songs.replacingOccurrences(of: ",", with: " "))
        }

        // Source type tags
        if sourceTypeKeys.contains("SBD") { parts.append("soundboard sbd") }
        if sourceTypeKeys.contains("AUD") { parts.append("audience aud") }
        if sourceTypeKeys.contains("MATRIX") { parts.append("matrix") }

        // Quality tags
        if avgRating >= 4.0 && totalReviews >= 10 { parts.append("top-rated") }
        if totalReviews >= 50 { parts.append("popular") }

        return parts.joined(separator: " ")
    }
}
