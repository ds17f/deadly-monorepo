import Foundation

struct FavoritesImportExportService {
    private let favoritesDAO: FavoritesDAO
    private let showDAO: ShowDAO
    private let showReviewDAO: ShowReviewDAO
    private let favoriteSongDAO: FavoriteSongDAO
    private let playerTagDAO: ShowPlayerTagDAO
    private let recordingPreferenceDAO: RecordingPreferenceDAO

    init(favoritesDAO: FavoritesDAO, showDAO: ShowDAO, showReviewDAO: ShowReviewDAO, favoriteSongDAO: FavoriteSongDAO, playerTagDAO: ShowPlayerTagDAO, recordingPreferenceDAO: RecordingPreferenceDAO) {
        self.favoritesDAO = favoritesDAO
        self.showDAO = showDAO
        self.showReviewDAO = showReviewDAO
        self.favoriteSongDAO = favoriteSongDAO
        self.playerTagDAO = playerTagDAO
        self.recordingPreferenceDAO = recordingPreferenceDAO
    }

    // MARK: - Export (v3)

    func exportFavorites() throws -> Data {
        // Favorites: favorite shows
        let favoriteRecords = try favoritesDAO.fetchAll()
        let favoriteShows = favoriteRecords.map { record in
            FavoriteShowEntry(
                showId: record.showId,
                addedAt: record.addedToFavoritesAt,
                isPinned: record.isPinned,
                lastAccessedAt: record.lastAccessedAt,
                tags: record.tags.flatMap { parseTags($0) }
            )
        }

        // Favorite tracks
        let favoriteTrackRecords = try favoriteSongDAO.fetchAll()
        let favoriteTracks = favoriteTrackRecords.map { record in
            FavoriteTrackEntry(
                showId: record.showId,
                trackTitle: record.trackTitle,
                trackNumber: record.trackNumber,
                recordingId: record.recordingId
            )
        }

        // Reviews (all, independent of favorites)
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

        // Recording preferences (all, independent of favorites)
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

    func importFavorites(from data: Data) throws -> BackupImportResult {
        // Peek at version to decide format
        let versionPeek = try JSONDecoder().decode(VersionPeek.self, from: data)

        if versionPeek.isV3 {
            return try importV3(from: data)
        } else {
            // v1/v2 legacy format
            let legacyResult = try importV2(from: data)
            return BackupImportResult(
                favoritesImported: legacyResult.imported,
                favoritesSkipped: legacyResult.alreadyFavorited,
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
            if (try? favoritesDAO.isFavorite(fav.showId)) == true {
                favoritesSkipped += 1
                continue
            }
            let record = FavoriteShowRecord(
                showId: fav.showId,
                addedToFavoritesAt: fav.addedAt,
                isPinned: fav.isPinned,
                notes: nil,
                preferredRecordingId: nil,
                downloadedRecordingId: nil,
                downloadedFormat: nil,
                customRating: nil,
                lastAccessedAt: fav.lastAccessedAt,
                tags: fav.tags.map { $0.joined(separator: ",") }
            )
            try? favoritesDAO.add(record)
            favoritesImported += 1
        }

        // Import favorite tracks
        for track in export.favorites.tracks {
            guard (try? showDAO.fetchById(track.showId)) != nil else { continue }
            let record = FavoriteSongRecord(
                id: nil,
                showId: track.showId,
                trackTitle: track.trackTitle,
                trackNumber: track.trackNumber,
                recordingId: track.recordingId,
                createdAt: now
            )
            try? favoriteSongDAO.insert(record)
            tracksImported += 1
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

    private func importV2(from data: Data) throws -> FavoritesImportResult {
        let export = try JSONDecoder().decode(FavoritesExportLegacy.self, from: data)
        guard export.version >= 1 && export.version <= 2 else {
            throw FavoritesImportError.unsupportedVersion(export.version)
        }

        var imported = 0
        var alreadyFavorited = 0
        var notFound = 0

        for entry in export.library {
            guard let _ = try? showDAO.fetchById(entry.showId) else {
                notFound += 1
                continue
            }
            if (try? favoritesDAO.isFavorite(entry.showId)) == true {
                alreadyFavorited += 1
                continue
            }
            let record = FavoriteShowRecord(
                showId: entry.showId,
                addedToFavoritesAt: entry.addedToLibraryAt,
                isPinned: entry.isPinned,
                notes: nil,
                preferredRecordingId: nil,
                downloadedRecordingId: nil,
                downloadedFormat: nil,
                customRating: nil,
                lastAccessedAt: entry.lastAccessedAt,
                tags: entry.tags.map { $0.joined(separator: ",") }
            )
            try? favoritesDAO.add(record)

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
                for tr in trackReviews where tr.thumbs == 1 {
                    let now = Int64(Date().timeIntervalSince1970 * 1000)
                    let record = FavoriteSongRecord(
                        id: nil,
                        showId: entry.showId,
                        trackTitle: tr.trackTitle,
                        trackNumber: tr.trackNumber,
                        recordingId: tr.recordingId,
                        createdAt: now
                    )
                    try? favoriteSongDAO.insert(record)
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

        return FavoritesImportResult(
            imported: imported,
            alreadyFavorited: alreadyFavorited,
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
    var version: Int = 0
    var favorites: AnyCodable? = nil

    private struct AnyCodable: Decodable {}

    /// True if this looks like a v3 export (has `favorites` key or version >= 3)
    var isV3: Bool { version >= 3 || favorites != nil }
}

// MARK: - Errors

enum FavoritesImportError: LocalizedError {
    case unsupportedVersion(Int)

    var errorDescription: String? {
        switch self {
        case .unsupportedVersion(let v):
            return "Unsupported backup export version (\(v)). Please update the app."
        }
    }
}
