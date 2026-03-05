import Foundation

struct LibraryImportExportService {
    private let libraryDAO: LibraryDAO
    private let showDAO: ShowDAO
    private let showReviewDAO: ShowReviewDAO
    private let trackReviewDAO: TrackReviewDAO
    private let playerTagDAO: ShowPlayerTagDAO

    init(libraryDAO: LibraryDAO, showDAO: ShowDAO, showReviewDAO: ShowReviewDAO, trackReviewDAO: TrackReviewDAO, playerTagDAO: ShowPlayerTagDAO) {
        self.libraryDAO = libraryDAO
        self.showDAO = showDAO
        self.showReviewDAO = showReviewDAO
        self.trackReviewDAO = trackReviewDAO
        self.playerTagDAO = playerTagDAO
    }

    // MARK: - Export

    func exportLibrary() throws -> Data {
        let records = try libraryDAO.fetchAll()
        let allReviews = try showReviewDAO.fetchAll()
        let reviewMap = Dictionary(uniqueKeysWithValues: allReviews.map { ($0.showId, $0) })

        let entries = try records.map { record -> LibraryExportEntry in
            let review = reviewMap[record.showId]
            let trackReviews = try trackReviewDAO.fetchForShow(record.showId)
            let playerTags = try playerTagDAO.fetchForShow(record.showId)

            return LibraryExportEntry(
                showId: record.showId,
                addedToLibraryAt: record.addedToLibraryAt,
                isPinned: record.isPinned,
                libraryNotes: review?.notes,
                customRating: review?.customRating,
                recordingQuality: review?.recordingQuality,
                playingQuality: review?.playingQuality,
                lastAccessedAt: record.lastAccessedAt,
                tags: record.tags.flatMap { parseTags($0) },
                trackReviews: trackReviews.isEmpty ? nil : trackReviews.map { review in
                    TrackReviewExportEntry(
                        trackTitle: review.trackTitle,
                        trackNumber: review.trackNumber,
                        recordingId: review.recordingId,
                        thumbs: review.thumbs,
                        starRating: review.starRating,
                        notes: review.notes
                    )
                },
                playerTags: playerTags.isEmpty ? nil : playerTags.map { tag in
                    PlayerTagExportEntry(
                        playerName: tag.playerName,
                        instruments: tag.instruments,
                        isStandout: tag.isStandout,
                        notes: tag.notes
                    )
                }
            )
        }
        let export = LibraryExport(
            version: 2,
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
        guard export.version >= 1 && export.version <= 2 else {
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
                libraryNotes: nil,
                preferredRecordingId: nil,
                downloadedRecordingId: nil,
                downloadedFormat: nil,
                customRating: nil,
                lastAccessedAt: entry.lastAccessedAt,
                tags: entry.tags.map { $0.joined(separator: ",") }
            )
            try? libraryDAO.add(record)

            // Import show-level review into show_reviews table
            let hasReviewData = entry.libraryNotes != nil || entry.customRating != nil || entry.recordingQuality != nil || entry.playingQuality != nil
            if hasReviewData {
                let now = Int64(Date().timeIntervalSince1970 * 1000)
                let reviewRecord = ShowReviewRecord(
                    showId: entry.showId,
                    notes: entry.libraryNotes,
                    customRating: entry.customRating,
                    recordingQuality: entry.recordingQuality,
                    playingQuality: entry.playingQuality,
                    reviewedRecordingId: nil,
                    createdAt: now,
                    updatedAt: now
                )
                try? showReviewDAO.upsert(reviewRecord)
            }

            // Import track reviews
            if let trackReviews = entry.trackReviews {
                for tr in trackReviews {
                    let now = Int64(Date().timeIntervalSince1970 * 1000)
                    let reviewRecord = TrackReviewRecord(
                        id: nil,
                        showId: entry.showId,
                        trackTitle: tr.trackTitle,
                        trackNumber: tr.trackNumber,
                        recordingId: tr.recordingId,
                        thumbs: tr.thumbs,
                        starRating: tr.starRating,
                        notes: tr.notes,
                        createdAt: now,
                        updatedAt: now
                    )
                    try? trackReviewDAO.upsert(reviewRecord)
                }
            }

            // Import player tags
            if let playerTags = entry.playerTags {
                for pt in playerTags {
                    let tagRecord = ShowPlayerTagRecord(
                        id: nil,
                        showId: entry.showId,
                        playerName: pt.playerName,
                        instruments: pt.instruments,
                        isStandout: pt.isStandout,
                        notes: pt.notes,
                        createdAt: Int64(Date().timeIntervalSince1970 * 1000)
                    )
                    try? playerTagDAO.upsert(tagRecord)
                }
            }

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
