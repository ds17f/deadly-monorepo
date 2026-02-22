import Foundation

enum LibraryDownloadStatus: String, Codable, Sendable, Equatable, CaseIterable {
    case notDownloaded = "NOT_DOWNLOADED"
    case queued = "QUEUED"
    case downloading = "DOWNLOADING"
    case paused = "PAUSED"
    case completed = "COMPLETED"
    case failed = "FAILED"
    case cancelled = "CANCELLED"
}

struct LibraryShow: Codable, Sendable, Equatable, Identifiable {
    let show: Show
    let addedToLibraryAt: Int64
    let isPinned: Bool
    let downloadStatus: LibraryDownloadStatus

    init(show: Show, addedToLibraryAt: Int64,
         isPinned: Bool = false,
         downloadStatus: LibraryDownloadStatus = .notDownloaded) {
        self.show = show
        self.addedToLibraryAt = addedToLibraryAt
        self.isPinned = isPinned
        self.downloadStatus = downloadStatus
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
    var isInLibrary: Bool { true }

    var isPinnedAndDownloaded: Bool { isPinned && downloadStatus == .completed }
    var libraryAge: Int64 { Int64(Date().timeIntervalSince1970 * 1000) - addedToLibraryAt }
    var isDownloaded: Bool { downloadStatus == .completed }
    var isDownloading: Bool { downloadStatus == .downloading }

    var libraryStatusDescription: String {
        if isPinned && isDownloaded { return "Pinned & Downloaded" }
        if isPinned { return "Pinned" }
        if isDownloaded { return "Downloaded" }
        if isDownloading { return "Downloading..." }
        return "In Library"
    }

    var sortableAddedDate: Int64 { addedToLibraryAt }
    var sortableShowDate: String { show.date }
    var sortablePinStatus: Int { isPinned ? 0 : 1 }
}

struct ShowDownloadProgress: Sendable, Equatable {
    let showId: String
    let status: LibraryDownloadStatus
    let overallProgress: Float   // 0.0–1.0
    let downloadedBytes: Int64
    let totalBytes: Int64
    let tracksCompleted: Int
    let tracksTotal: Int
}

struct LibraryStats: Codable, Sendable, Equatable {
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

enum LibrarySortOption: String, Codable, Sendable, Equatable, CaseIterable {
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

enum LibrarySortDirection: String, Codable, Sendable, Equatable, CaseIterable {
    case ascending = "ASCENDING"
    case descending = "DESCENDING"

    var displayName: String {
        switch self {
        case .ascending: return "Ascending"
        case .descending: return "Descending"
        }
    }
}

enum LibraryDisplayMode: String, Codable, Sendable, Equatable, CaseIterable {
    case list = "LIST"
    case grid = "GRID"

    var displayName: String {
        switch self {
        case .list: return "List"
        case .grid: return "Grid"
        }
    }
}
