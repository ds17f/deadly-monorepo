import Foundation

/// Cleans raw Archive.org track titles for API searches and setlist matching.
///
/// Archive titles often contain track numbers, segue markers, durations, etc.
/// that interfere with search accuracy.
enum SongTitleScrubber {
    // Swift regex literals (requires iOS 16+, deployment target is iOS 18.5)
    private static let leadingTrackNumber = /^\d+[\s.\-]+/
    private static let trailingSegue = /\s*>+\s*$/
    private static let leadingSegue = /^>+\s*/
    private static let bracketedSuffix = /\s*\[.*?\]\s*$/
    private static let parenDuration = /\s*\(\d+:\d+\)\s*$/
    private static let multiSpace = /\s{2,}/
    private static let inlineSegue = " > "

    /// Scrubs a raw Archive.org track title to a clean song name.
    ///
    /// Applied in order:
    /// 1. Handle inline segues ("Scarlet > Fire" → "Scarlet")
    /// 2. Strip leading track numbers ("02 Scarlet Begonias" → "Scarlet Begonias")
    /// 3. Strip trailing segue markers ("Scarlet Begonias >" → "Scarlet Begonias")
    /// 4. Strip leading segue markers ("> Fire on the Mountain" → "Fire on the Mountain")
    /// 5. Strip bracketed suffixes ("Scarlet Begonias [10:23]" → "Scarlet Begonias")
    /// 6. Strip parenthesized durations ("Scarlet Begonias (10:23)" → "Scarlet Begonias")
    /// 7. Normalize whitespace
    static func scrub(_ rawTitle: String) -> String {
        var title = rawTitle.trimmingCharacters(in: .whitespaces)
        guard !title.isEmpty else { return "" }

        if title.contains(inlineSegue) {
            title = (title.components(separatedBy: inlineSegue).first ?? title)
                .trimmingCharacters(in: .whitespaces)
        }

        title = title
            .replacing(leadingTrackNumber, with: "")
            .replacing(trailingSegue, with: "")
            .replacing(leadingSegue, with: "")
            .replacing(bracketedSuffix, with: "")
            .replacing(parenDuration, with: "")
            .replacing(multiSpace, with: " ")
            .trimmingCharacters(in: .whitespaces)

        return title
    }

    /// Checks if a scrubbed track title matches a setlist song name.
    ///
    /// Uses case-insensitive exact match first, then falls back to a contains
    /// check for partial titles (e.g. "Scarlet" matching "Scarlet Begonias").
    static func matchesSetlistSong(_ scrubbedTitle: String, setlistSongName: String) -> Bool {
        guard !scrubbedTitle.isEmpty, !setlistSongName.isEmpty else { return false }
        if scrubbedTitle.caseInsensitiveCompare(setlistSongName) == .orderedSame { return true }
        return setlistSongName.localizedCaseInsensitiveContains(scrubbedTitle)
    }
}
