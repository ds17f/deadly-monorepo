import Foundation

// MARK: - Export envelope

struct LibraryExport: Codable {
    let version: Int
    let exportedAt: Int64
    let app: String
    let library: [LibraryExportEntry]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        version = try container.decode(Int.self, forKey: .version)
        exportedAt = try container.decode(Int64.self, forKey: .exportedAt)
        app = try container.decode(String.self, forKey: .app)
        library = try container.decode([LibraryExportEntry].self, forKey: .library)
    }

    init(version: Int, exportedAt: Int64, app: String, library: [LibraryExportEntry]) {
        self.version = version
        self.exportedAt = exportedAt
        self.app = app
        self.library = library
    }
}

// MARK: - Per-show entry

struct LibraryExportEntry: Codable {
    let showId: String
    let addedToLibraryAt: Int64
    let isPinned: Bool
    let libraryNotes: String?
    let customRating: Double?
    let recordingQuality: Int?
    let playingQuality: Int?
    let lastAccessedAt: Int64?
    let tags: [String]?
    let trackReviews: [TrackReviewExportEntry]?
    let playerTags: [PlayerTagExportEntry]?

    init(showId: String, addedToLibraryAt: Int64, isPinned: Bool, libraryNotes: String?, customRating: Double?, recordingQuality: Int?, playingQuality: Int?, lastAccessedAt: Int64?, tags: [String]?, trackReviews: [TrackReviewExportEntry]?, playerTags: [PlayerTagExportEntry]?) {
        self.showId = showId
        self.addedToLibraryAt = addedToLibraryAt
        self.isPinned = isPinned
        self.libraryNotes = libraryNotes
        self.customRating = customRating
        self.recordingQuality = recordingQuality
        self.playingQuality = playingQuality
        self.lastAccessedAt = lastAccessedAt
        self.tags = tags
        self.trackReviews = trackReviews
        self.playerTags = playerTags
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        showId = try container.decode(String.self, forKey: .showId)
        addedToLibraryAt = try container.decode(Int64.self, forKey: .addedToLibraryAt)
        isPinned = try container.decodeIfPresent(Bool.self, forKey: .isPinned) ?? false
        libraryNotes = try container.decodeIfPresent(String.self, forKey: .libraryNotes)
        customRating = try container.decodeIfPresent(Double.self, forKey: .customRating)
        recordingQuality = try container.decodeIfPresent(Int.self, forKey: .recordingQuality)
        playingQuality = try container.decodeIfPresent(Int.self, forKey: .playingQuality)
        lastAccessedAt = try container.decodeIfPresent(Int64.self, forKey: .lastAccessedAt)
        tags = try container.decodeIfPresent([String].self, forKey: .tags)
        trackReviews = try container.decodeIfPresent([TrackReviewExportEntry].self, forKey: .trackReviews)
        playerTags = try container.decodeIfPresent([PlayerTagExportEntry].self, forKey: .playerTags)
    }
}

// MARK: - Track review export entry

struct TrackReviewExportEntry: Codable {
    let trackTitle: String
    let trackNumber: Int?
    let recordingId: String?
    let thumbs: Int?
    let starRating: Int?
    let notes: String?
}

// MARK: - Player tag export entry

struct PlayerTagExportEntry: Codable {
    let playerName: String
    let instruments: String?
    let isStandout: Bool
    let notes: String?
}

// MARK: - Import result

struct LibraryImportResult {
    let imported: Int
    let alreadyInLibrary: Int
    let notFound: Int
}

// MARK: - v3 Export Format

struct BackupExportV3: Codable {
    let version: Int
    let exportedAt: Int64
    let app: String
    let favorites: FavoritesExport
    let reviews: [ReviewExportEntry]
    let recordingPreferences: [RecordingPreferenceExportEntry]
}

struct FavoritesExport: Codable {
    let shows: [FavoriteShowEntry]
    let tracks: [FavoriteTrackEntry]
}

struct FavoriteShowEntry: Codable {
    let showId: String
    let addedAt: Int64
    let isPinned: Bool
    let lastAccessedAt: Int64?
    let tags: [String]?
}

struct FavoriteTrackEntry: Codable {
    let showId: String
    let trackTitle: String
    let trackNumber: Int?
    let recordingId: String?
}

struct ReviewExportEntry: Codable {
    let showId: String
    let notes: String?
    let overallRating: Double?
    let recordingQuality: Int?
    let playingQuality: Int?
    let reviewedRecordingId: String?
    let playerTags: [PlayerTagExportEntry]?
}

struct RecordingPreferenceExportEntry: Codable {
    let showId: String
    let recordingId: String
}

struct BackupImportResult {
    let favoritesImported: Int
    let favoritesSkipped: Int
    let reviewsImported: Int
    let tracksImported: Int
    let preferencesImported: Int
    let notFound: Int
}
