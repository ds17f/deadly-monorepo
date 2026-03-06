import Foundation

struct LibraryImportExportService {
    private let libraryDAO: LibraryDAO
    private let showDAO: ShowDAO
    private let showReviewDAO: ShowReviewDAO
    private let trackReviewDAO: TrackReviewDAO
    private let playerTagDAO: ShowPlayerTagDAO
    private let recordingPreferenceDAO: RecordingPreferenceDAO

    init(libraryDAO: LibraryDAO, showDAO: ShowDAO, showReviewDAO: ShowReviewDAO, trackReviewDAO: TrackReviewDAO, playerTagDAO: ShowPlayerTagDAO, recordingPreferenceDAO: RecordingPreferenceDAO) {
        self.libraryDAO = libraryDAO
        self.showDAO = showDAO
        self.showReviewDAO = showReviewDAO
        self.trackReviewDAO = trackReviewDAO
        self.playerTagDAO = playerTagDAO
        self.recordingPreferenceDAO = recordingPreferenceDAO
    }

    // MARK: - Export (v3)

    func exportLibrary() throws -> Data {
        // Favorites: library shows
        let libraryRecords = try libraryDAO.fetchAll()
        let favoriteShows = libraryRecords.map { record in
            FavoriteShowEntry(
                showId: record.showId,
                addedAt: record.addedToLibraryAt,
                isPinned: record.isPinned,
                lastAccessedAt: record.lastAccessedAt,
                tags: record.tags.flatMap { parseTags($0) }
            )
        }

        // Favorites: thumbs-up tracks
        let thumbsUpTracks = try trackReviewDAO.fetchThumbsUp()
        let favoriteTracks = thumbsUpTracks.map { track in
            FavoriteTrackEntry(
                showId: track.showId,
                trackTitle: track.trackTitle,
                trackNumber: track.trackNumber,
                recordingId: track.recordingId
            )
        }

        // Reviews (all, independent of library)
        let allReviews = try showReviewDAO.fetchAll()
        let allPlayerTags = try playerTagDAO.fetchAll()
        let tagsByShow = Dictionary(grouping: allPlayerTags, by: \.showId)

        let reviewEntries = allReviews.map { review in
            let tags = tagsByShow[review.showId]
            return ReviewExportEntry(
                showId: review.showId,
                notes: review.notes,
                overallRating: review.customRating,
                recordingQuality: review.recordingQuality,
                playingQuality: review.playingQuality,
                reviewedRecordingId: review.reviewedRecordingId,
                playerTags: tags?.isEmpty == false ? tags!.map { tag in
                    PlayerTagExportEntry(
                        playerName: tag.playerName,
                        instruments: tag.instruments,
                        isStandout: tag.isStandout,
                        notes: tag.notes
                    )
                } : nil
            )
        }

        // Recording preferences (all, independent of library)
        let allPrefs = try recordingPreferenceDAO.fetchAll()
        let prefEntries = allPrefs.map { pref in
            RecordingPreferenceExportEntry(showId: pref.showId, recordingId: pref.recordingId)
        }

        let export = BackupExportV3(
            version: 3,
            exportedAt: Int64(Date().timeIntervalSince1970 * 1000),
            app: "deadly-ios",
            favorites: FavoritesExport(shows: favoriteShows, tracks: favoriteTracks),
            reviews: reviewEntries,
            recordingPreferences: prefEntries
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(export)
    }

    func exportFilename() -> String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        return "the-deadly-backup-\(df.string(from: Date())).json"
    }

    // MARK: - Import (version-detecting)

    func importLibrary(from data: Data) throws -> BackupImportResult {
        // Peek at version to decide format
        let versionPeek = try JSONDecoder().decode(VersionPeek.self, from: data)

        if versionPeek.version >= 3 {
            return try importV3(from: data)
        } else {
            // v1/v2 legacy format
            let legacyResult = try importV2(from: data)
            return BackupImportResult(
                favoritesImported: legacyResult.imported,
                favoritesSkipped: legacyResult.alreadyInLibrary,
                reviewsImported: 0,
                tracksImported: 0,
                preferencesImported: 0,
                notFound: legacyResult.notFound
            )
        }
    }

    // MARK: - v3 Import

    private func importV3(from data: Data) throws -> BackupImportResult {
        let export = try JSONDecoder().decode(BackupExportV3.self, from: data)
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        var favoritesImported = 0
        var favoritesSkipped = 0
        var reviewsImported = 0
        var tracksImported = 0
        var preferencesImported = 0
        var notFound = 0

        // Import favorite shows
        for fav in export.favorites.shows {
            guard (try? showDAO.fetchById(fav.showId)) != nil else {
                notFound += 1
                continue
            }
            if (try? libraryDAO.isInLibrary(fav.showId)) == true {
                favoritesSkipped += 1
                continue
            }
            let record = LibraryShowRecord(
                showId: fav.showId,
                addedToLibraryAt: fav.addedAt,
                isPinned: fav.isPinned,
                libraryNotes: nil,
                preferredRecordingId: nil,
                downloadedRecordingId: nil,
                downloadedFormat: nil,
                customRating: nil,
                lastAccessedAt: fav.lastAccessedAt,
                tags: fav.tags.map { $0.joined(separator: ",") }
            )
            try? libraryDAO.add(record)
            favoritesImported += 1
        }

        // Import favorite tracks (ensure thumbs=1 review exists)
        for track in export.favorites.tracks {
            guard (try? showDAO.fetchById(track.showId)) != nil else { continue }
            let existing = try? trackReviewDAO.fetch(
                showId: track.showId, trackTitle: track.trackTitle, recordingId: track.recordingId
            )
            if existing == nil || existing?.thumbs != 1 {
                let reviewRecord = TrackReviewRecord(
                    id: existing?.id,
                    showId: track.showId,
                    trackTitle: track.trackTitle,
                    trackNumber: track.trackNumber,
                    recordingId: track.recordingId,
                    thumbs: 1,
                    starRating: existing?.starRating,
                    notes: existing?.notes,
                    createdAt: existing?.createdAt ?? now,
                    updatedAt: now
                )
                try? trackReviewDAO.upsert(reviewRecord)
                tracksImported += 1
            }
        }

        // Import reviews (upsert)
        for review in export.reviews {
            guard (try? showDAO.fetchById(review.showId)) != nil else { continue }
            let reviewRecord = ShowReviewRecord(
                showId: review.showId,
                notes: review.notes,
                customRating: review.overallRating,
                recordingQuality: review.recordingQuality,
                playingQuality: review.playingQuality,
                reviewedRecordingId: review.reviewedRecordingId,
                createdAt: now,
                updatedAt: now
            )
            try? showReviewDAO.upsert(reviewRecord)
            reviewsImported += 1

            // Import player tags for this review
            if let playerTags = review.playerTags {
                for pt in playerTags {
                    let tagRecord = ShowPlayerTagRecord(
                        id: nil,
                        showId: review.showId,
                        playerName: pt.playerName,
                        instruments: pt.instruments,
                        isStandout: pt.isStandout,
                        notes: pt.notes,
                        createdAt: now
                    )
                    try? playerTagDAO.upsert(tagRecord)
                }
            }
        }

        // Import recording preferences (upsert)
        for pref in export.recordingPreferences {
            guard (try? showDAO.fetchById(pref.showId)) != nil else { continue }
            let prefRecord = RecordingPreferenceRecord(
                showId: pref.showId,
                recordingId: pref.recordingId,
                updatedAt: now
            )
            try? recordingPreferenceDAO.upsert(prefRecord)
            preferencesImported += 1
        }

        return BackupImportResult(
            favoritesImported: favoritesImported,
            favoritesSkipped: favoritesSkipped,
            reviewsImported: reviewsImported,
            tracksImported: tracksImported,
            preferencesImported: preferencesImported,
            notFound: notFound
        )
    }

    // MARK: - v2 Legacy Import

    private func importV2(from data: Data) throws -> LibraryImportResult {
        let export = try JSONDecoder().decode(LibraryExport.self, from: data)
        guard export.version >= 1 && export.version <= 2 else {
            throw LibraryImportError.unsupportedVersion(export.version)
        }

        var imported = 0
        var alreadyInLibrary = 0
        var notFound = 0

        for entry in export.library {
            guard let _ = try? showDAO.fetchById(entry.showId) else {
                notFound += 1
                continue
            }
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

// MARK: - Helpers

private struct VersionPeek: Decodable {
    let version: Int
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
