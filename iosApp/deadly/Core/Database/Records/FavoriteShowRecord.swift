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
    /// Seconds-since-epoch LWW comparator. See PLANS/mobile-server-sync.md.
    var updatedAt: Int64
    /// Tombstone — non-nil means soft-deleted; row stays so sync can propagate the delete.
    var deletedAt: Int64?

    init(
        showId: String,
        addedToFavoritesAt: Int64,
        isPinned: Bool = false,
        notes: String? = nil,
        preferredRecordingId: String? = nil,
        downloadedRecordingId: String? = nil,
        downloadedFormat: String? = nil,
        recordingQuality: Int? = nil,
        playingQuality: Int? = nil,
        customRating: Double? = nil,
        lastAccessedAt: Int64? = nil,
        tags: String? = nil,
        updatedAt: Int64,
        deletedAt: Int64? = nil
    ) {
        self.showId = showId
        self.addedToFavoritesAt = addedToFavoritesAt
        self.isPinned = isPinned
        self.notes = notes
        self.preferredRecordingId = preferredRecordingId
        self.downloadedRecordingId = downloadedRecordingId
        self.downloadedFormat = downloadedFormat
        self.recordingQuality = recordingQuality
        self.playingQuality = playingQuality
        self.customRating = customRating
        self.lastAccessedAt = lastAccessedAt
        self.tags = tags
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
    }
}
