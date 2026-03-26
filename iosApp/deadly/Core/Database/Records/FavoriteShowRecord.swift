import GRDB

/// Flat DB record for the `favorite_shows` table.
/// showId is the PK and also a FK to shows(showId) with CASCADE DELETE.
struct FavoriteShowRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "favorite_shows"

    var showId: String
    var addedToFavoritesAt: Int64
    var isPinned: Bool
    var notes: String?
    var preferredRecordingId: String?
    var downloadedRecordingId: String?
    var downloadedFormat: String?
    var recordingQuality: Int?
    var playingQuality: Int?
    var customRating: Double?
    var lastAccessedAt: Int64?
    var tags: String?

    // Display metadata for non-local shows (populated at favorite time)
    var band: String?
    var showDate: String?
    var venue: String?
    var location: String?
    var coverImageUrl: String?

    /// Construct a lightweight Show from stored metadata.
    /// Returns nil if essential fields are missing (GD shows use ShowRecord instead).
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
            recordingIds: [showId],
            bestRecordingId: showId,
            bestSourceType: .unknown,
            recordingCount: 1,
            averageRating: nil,
            totalReviews: 0,
            coverImageUrl: coverImageUrl ?? "https://archive.org/services/img/\(showId)",
            isFavorite: true,
            favoritedAt: addedToFavoritesAt
        )
    }
}
