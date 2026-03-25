import Foundation

/// Media ID scheme for the CarPlay browse tree, mirroring Android's BrowseMediaId.
///
/// Hierarchy:
/// - recent              → Recently Played
/// - library_shows       → Favorite Shows
/// - library_songs       → Favorite Songs
/// - today               → Today In History
/// - years               → Browse by Year
///   └── year/{year}     → Shows for year
/// - top_rated           → Top Rated Shows
/// - show/{showId}       → Tracks for show
enum CarPlayBrowseMediaId {
    static let recent = "recent"
    static let libraryShows = "library_shows"
    static let librarySongs = "library_songs"
    static let today = "today"
    static let years = "years"
    static let topRated = "top_rated"

    private static let yearPrefix = "year/"
    private static let showPrefix = "show/"

    static func year(_ year: Int) -> String { "\(yearPrefix)\(year)" }
    static func show(_ showId: String) -> String { "\(showPrefix)\(showId)" }

    static func isYear(_ mediaId: String) -> Bool { mediaId.hasPrefix(yearPrefix) }
    static func isShow(_ mediaId: String) -> Bool { mediaId.hasPrefix(showPrefix) }

    static func parseYear(_ mediaId: String) -> Int? {
        Int(mediaId.replacingOccurrences(of: yearPrefix, with: ""))
    }

    static func parseShowId(_ mediaId: String) -> String {
        mediaId.replacingOccurrences(of: showPrefix, with: "")
    }
}
