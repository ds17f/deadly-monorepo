import Foundation

struct LibraryImportExportService {
    private let libraryDAO: LibraryDAO
    private let showDAO: ShowDAO

    init(libraryDAO: LibraryDAO, showDAO: ShowDAO) {
        self.libraryDAO = libraryDAO
        self.showDAO = showDAO
    }

    // MARK: - Export

    func exportLibrary() throws -> Data {
        let records = try libraryDAO.fetchAll()
        let entries = records.map { record in
            LibraryExportEntry(
                showId: record.showId,
                addedToLibraryAt: record.addedToLibraryAt,
                isPinned: record.isPinned,
                libraryNotes: record.libraryNotes,
                customRating: record.customRating,
                lastAccessedAt: record.lastAccessedAt,
                tags: record.tags.flatMap { parseTags($0) }
            )
        }
        let export = LibraryExport(
            version: 1,
            exportedAt: Int64(Date().timeIntervalSince1970 * 1000),
            app: "deadly-ios",
            library: entries
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(export)
    }

    func exportFilename() -> String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        return "grateful-dead-library-\(df.string(from: Date())).json"
    }

    // MARK: - Import

    func importLibrary(from data: Data) throws -> LibraryImportResult {
        let export = try JSONDecoder().decode(LibraryExport.self, from: data)
        guard export.version == 1 else {
            throw LibraryImportError.unsupportedVersion(export.version)
        }

        var imported = 0
        var alreadyInLibrary = 0
        var notFound = 0

        for entry in export.library {
            // Check show exists in the local DB
            guard let _ = try? showDAO.fetchById(entry.showId) else {
                notFound += 1
                continue
            }
            // Skip if already in library — don't overwrite existing preferences
            if (try? libraryDAO.isInLibrary(entry.showId)) == true {
                alreadyInLibrary += 1
                continue
            }
            let record = LibraryShowRecord(
                showId: entry.showId,
                addedToLibraryAt: entry.addedToLibraryAt,
                isPinned: entry.isPinned,
                libraryNotes: entry.libraryNotes,
                preferredRecordingId: nil,
                downloadedRecordingId: nil,
                downloadedFormat: nil,
                customRating: entry.customRating,
                lastAccessedAt: entry.lastAccessedAt,
                tags: entry.tags.map { $0.joined(separator: ",") }
            )
            try? libraryDAO.add(record)
            imported += 1
        }

        return LibraryImportResult(
            imported: imported,
            alreadyInLibrary: alreadyInLibrary,
            notFound: notFound
        )
    }

    // MARK: - Private

    private func parseTags(_ raw: String) -> [String] {
        raw.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }
}

// MARK: - Errors

enum LibraryImportError: LocalizedError {
    case unsupportedVersion(Int)

    var errorDescription: String? {
        switch self {
        case .unsupportedVersion(let v):
            return "Unsupported library export version (\(v)). Please update the app."
        }
    }
}
