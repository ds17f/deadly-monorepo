import GRDB

/// Flat DB record for the `recent_shows` table.
/// One row per show (UPSERT pattern): lastPlayedTimestamp updated each play.
/// Metadata columns (band, showDate, venue, location, coverImageUrl, recordingId)
/// are populated at record time so non-local shows (non-GD) can be displayed
/// without a network call.
struct RecentShowRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "recent_shows"

    var showId: String
    var lastPlayedTimestamp: Int64
    var firstPlayedTimestamp: Int64
    var totalPlayCount: Int

    // Display metadata (nullable — pre-migration rows will have NULLs until re-played)
    var band: String?
    var showDate: String?
    var venue: String?
    var location: String?
    var coverImageUrl: String?
    var recordingId: String?

    /// Construct a lightweight Show from stored metadata.
    /// Returns nil if essential fields are missing (pre-migration rows).
    func toShow() -> Show? {
        guard let band, let showDate else { return nil }
        return Show(
            id: showId,
            date: showDate,
            year: Int(showDate.prefix(4)) ?? 0,
            band: band,
            venue: Venue(name: venue ?? "Unknown Venue", city: nil, state: nil, country: ""),
            location: Location(displayText: location ?? "", city: nil, state: nil),
            setlist: nil,
            lineup: nil,
            recordingIds: recordingId.map { [$0] } ?? [showId],
            bestRecordingId: recordingId ?? showId,
            bestSourceType: .unknown,
            recordingCount: 1,
            averageRating: nil,
            totalReviews: 0,
            coverImageUrl: coverImageUrl ?? "https://archive.org/services/img/\(showId)",
            isFavorite: false,
            favoritedAt: nil
        )
    }
}
