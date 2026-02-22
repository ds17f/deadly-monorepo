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
}

// MARK: - Import result

struct LibraryImportResult {
    let imported: Int
    let alreadyInLibrary: Int
    let notFound: Int
}
