import Foundation

enum FavoritesDownloadStatus: String, Codable, Sendable, Equatable, CaseIterable {
    case notDownloaded = "NOT_DOWNLOADED"
    case queued = "QUEUED"
    case downloading = "DOWNLOADING"
    case paused = "PAUSED"
    case completed = "COMPLETED"
    case failed = "FAILED"
    case cancelled = "CANCELLED"
}

struct FavoriteShow: Codable, Sendable, Equatable, Identifiable {
    let show: Show
    let addedToFavoritesAt: Int64
    let isPinned: Bool
    let downloadStatus: FavoritesDownloadStatus
    let notes: String?
    let customRating: Double?
    let recordingQuality: Int?
    let playingQuality: Int?

    init(show: Show, addedToFavoritesAt: Int64,
         isPinned: Bool = false,
         downloadStatus: FavoritesDownloadStatus = .notDownloaded,
         notes: String? = nil,
         customRating: Double? = nil,
         recordingQuality: Int? = nil,
         playingQuality: Int? = nil) {
        self.show = show
        self.addedToFavoritesAt = addedToFavoritesAt
        self.isPinned = isPinned
        self.downloadStatus = downloadStatus
        self.notes = notes
        self.customRating = customRating
        self.recordingQuality = recordingQuality
        self.playingQuality = playingQuality
    }

    var id: String { show.id }
    var showId: String { show.id }
    var date: String { show.date }
    var venue: String { show.venue.name }
    var location: String { show.location.displayText }
    var displayTitle: String { show.displayTitle }
    var displayLocation: String { show.location.displayText }
    var displayVenue: String { show.venue.name }
    var displayDate: String { DateFormatting.formatShowDate(show.date, style: .long) }
    var recordingCount: Int { show.recordingIds.count }
    var averageRating: Float? { show.averageRating }
    var totalReviews: Int { show.totalReviews }
    var isFavorite: Bool { true }

    var hasNotes: Bool { notes != nil && !notes!.isEmpty }
    var hasReview: Bool { customRating != nil || recordingQuality != nil || playingQuality != nil || hasNotes }

    var isPinnedAndDownloaded: Bool { isPinned && downloadStatus == .completed }
    var favoriteAge: Int64 { Int64(Date().timeIntervalSince1970 * 1000) - addedToFavoritesAt }
    var isDownloaded: Bool { downloadStatus == .completed }
    var isDownloading: Bool { downloadStatus == .downloading }

    var statusDescription: String {
        if isPinned && isDownloaded { return "Pinned & Downloaded" }
        if isPinned { return "Pinned" }
        if isDownloaded { return "Downloaded" }
        if isDownloading { return "Downloading..." }
        return "Favorited"
    }

    var sortableAddedDate: Int64 { addedToFavoritesAt }
    var sortableShowDate: String { show.date }
    var sortablePinStatus: Int { isPinned ? 0 : 1 }
}

struct ShowDownloadProgress: Sendable, Equatable {
    let showId: String
    let status: FavoritesDownloadStatus
    let overallProgress: Float   // 0.0–1.0
    let downloadedBytes: Int64
    let totalBytes: Int64
    let tracksCompleted: Int
    let tracksTotal: Int
}

struct FavoritesStats: Codable, Sendable, Equatable {
    let totalShows: Int
    let totalDownloaded: Int
    let totalStorageUsed: Int64
    let totalPinned: Int

    init(totalShows: Int, totalDownloaded: Int, totalStorageUsed: Int64, totalPinned: Int = 0) {
        self.totalShows = totalShows
        self.totalDownloaded = totalDownloaded
        self.totalStorageUsed = totalStorageUsed
        self.totalPinned = totalPinned
    }
}

enum FavoritesSortOption: String, Codable, Sendable, Equatable, CaseIterable {
    case dateOfShow = "DATE_OF_SHOW"
    case dateAdded = "DATE_ADDED"
    case venue = "VENUE"
    case rating = "RATING"

    var displayName: String {
        switch self {
        case .dateOfShow: return "Show Date"
        case .dateAdded: return "Date Added"
        case .venue: return "Venue"
        case .rating: return "Rating"
        }
    }
}

enum FavoritesSortDirection: String, Codable, Sendable, Equatable, CaseIterable {
    case ascending = "ASCENDING"
    case descending = "DESCENDING"

    var displayName: String {
        switch self {
        case .ascending: return "Ascending"
        case .descending: return "Descending"
        }
    }
}

enum FavoritesDisplayMode: String, Codable, Sendable, Equatable, CaseIterable {
    case list = "LIST"
    case grid = "GRID"

    var displayName: String {
        switch self {
        case .list: return "List"
        case .grid: return "Grid"
        }
    }
}
