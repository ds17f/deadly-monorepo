import Foundation

// MARK: - Export envelope

struct LibraryExport: Codable {
    let version: Int
    let exportedAt: Int64
    let app: String
    let library: [LibraryExportEntry]
}

// MARK: - Per-show entry

struct LibraryExportEntry: Codable {
    let showId: String
    let addedToLibraryAt: Int64
    let isPinned: Bool
    let libraryNotes: String?
    let customRating: Double?
    let lastAccessedAt: Int64?
    let tags: [String]?

    init(showId: String, addedToLibraryAt: Int64, isPinned: Bool, libraryNotes: String?, customRating: Double?, lastAccessedAt: Int64?, tags: [String]?) {
        self.showId = showId
        self.addedToLibraryAt = addedToLibraryAt
        self.isPinned = isPinned
        self.libraryNotes = libraryNotes
        self.customRating = customRating
        self.lastAccessedAt = lastAccessedAt
        self.tags = tags
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        showId = try container.decode(String.self, forKey: .showId)
        addedToLibraryAt = try container.decode(Int64.self, forKey: .addedToLibraryAt)
        isPinned = try container.decodeIfPresent(Bool.self, forKey: .isPinned) ?? false
        libraryNotes = try container.decodeIfPresent(String.self, forKey: .libraryNotes)
        customRating = try container.decodeIfPresent(Double.self, forKey: .customRating)
        lastAccessedAt = try container.decodeIfPresent(Int64.self, forKey: .lastAccessedAt)
        tags = try container.decodeIfPresent([String].self, forKey: .tags)
    }
}

// MARK: - Import result

struct LibraryImportResult {
    let imported: Int
    let alreadyInLibrary: Int
    let notFound: Int
}
