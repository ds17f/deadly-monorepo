import Foundation
import GRDB

struct ReviewService: Sendable {
    private let showReviewDAO: ShowReviewDAO
    private let favoriteSongDAO: FavoriteSongDAO
    private let showPlayerTagDAO: ShowPlayerTagDAO
    private let showDAO: ShowDAO

    init(showReviewDAO: ShowReviewDAO, favoriteSongDAO: FavoriteSongDAO, showPlayerTagDAO: ShowPlayerTagDAO, showDAO: ShowDAO) {
        self.showReviewDAO = showReviewDAO
        self.favoriteSongDAO = favoriteSongDAO
        self.showPlayerTagDAO = showPlayerTagDAO
        self.showDAO = showDAO
    }

    // MARK: - Show-level review

    func getShowReview(_ showId: String) throws -> ShowReview {
        let reviewRecord = try showReviewDAO.fetchByShowId(showId)
        let tagRecords = try showPlayerTagDAO.fetchForShow(showId)

        return ShowReview(
            showId: showId,
            notes: reviewRecord?.notes,
            overallRating: reviewRecord?.customRating,
            recordingQuality: reviewRecord?.recordingQuality,
            playingQuality: reviewRecord?.playingQuality,
            reviewedRecordingId: reviewRecord?.reviewedRecordingId,
            playerTags: tagRecords.map { $0.toDomain() }
        )
    }

    func updateShowNotes(_ showId: String, notes: String?) throws {
        try ensureShowReviewExists(showId)
        try showReviewDAO.updateNotes(showId, notes: notes)
    }

    func updateShowRating(_ showId: String, rating: Double?) throws {
        try ensureShowReviewExists(showId)
        try showReviewDAO.updateCustomRating(showId, rating: rating)
    }

    func updateRecordingQuality(_ showId: String, quality: Int?, recordingId: String? = nil) throws {
        try ensureShowReviewExists(showId)
        try showReviewDAO.updateRecordingQuality(showId, quality: quality, recordingId: recordingId)
    }

    func updatePlayingQuality(_ showId: String, quality: Int?) throws {
        try ensureShowReviewExists(showId)
        try showReviewDAO.updatePlayingQuality(showId, quality: quality)
    }

    // MARK: - Favorite songs

    func toggleFavoriteSong(
        showId: String, trackTitle: String, trackNumber: Int? = nil, recordingId: String? = nil
    ) throws {
        let isFav = try favoriteSongDAO.isFavorite(showId: showId, trackTitle: trackTitle, recordingId: recordingId)
        if isFav {
            try favoriteSongDAO.delete(showId: showId, trackTitle: trackTitle, recordingId: recordingId)
        } else {
            let record = FavoriteSongRecord(
                id: nil,
                showId: showId,
                trackTitle: trackTitle,
                trackNumber: trackNumber,
                recordingId: recordingId,
                createdAt: Int64(Date().timeIntervalSince1970 * 1000)
            )
            try favoriteSongDAO.insert(record)
        }
    }

    func isSongFavorite(showId: String, trackTitle: String, recordingId: String?) throws -> Bool {
        try favoriteSongDAO.isFavorite(showId: showId, trackTitle: trackTitle, recordingId: recordingId)
    }

    func observeFavoriteTitles(showId: String) -> AsyncValueObservation<Set<String>> {
        favoriteSongDAO.database.observe(favoriteSongDAO.observeFavoriteTitles(showId: showId))
    }

    func observeIsSongFavorite(showId: String, trackTitle: String, recordingId: String?) -> AsyncValueObservation<Bool> {
        favoriteSongDAO.database.observe(favoriteSongDAO.observeIsFavorite(showId: showId, trackTitle: trackTitle, recordingId: recordingId))
    }

    // MARK: - Player tags

    func getPlayerTags(_ showId: String) throws -> [PlayerTag] {
        try showPlayerTagDAO.fetchForShow(showId).map { $0.toDomain() }
    }

    func upsertPlayerTag(
        showId: String, playerName: String,
        instruments: String? = nil, isStandout: Bool = true, notes: String? = nil
    ) throws {
        let existing = try showPlayerTagDAO.fetchForShow(showId).first { $0.playerName == playerName }
        let record = ShowPlayerTagRecord(
            id: existing?.id,
            showId: showId,
            playerName: playerName,
            instruments: instruments,
            isStandout: isStandout,
            notes: notes,
            createdAt: existing?.createdAt ?? Int64(Date().timeIntervalSince1970 * 1000)
        )
        try showPlayerTagDAO.upsert(record)
    }

    func removePlayerTag(showId: String, playerName: String) throws {
        try showPlayerTagDAO.remove(showId: showId, playerName: playerName)
    }

    // MARK: - Favorites

    func getFavoriteTracks() throws -> [FavoriteTrack] {
        let records = try favoriteSongDAO.fetchAll()
        let showIds = Array(Set(records.map { $0.showId }))
        let shows = try showDAO.fetchByIds(showIds)
        let showMap = Dictionary(uniqueKeysWithValues: shows.map { ($0.showId, $0) })
        return records.compactMap { record in
            guard let show = showMap[record.showId] else { return nil }
            return FavoriteTrack(
                showId: record.showId,
                showDate: show.date,
                venue: show.venueName,
                trackTitle: record.trackTitle,
                trackNumber: record.trackNumber,
                recordingId: record.recordingId,
                addedAt: record.createdAt
            )
        }
    }

    // MARK: - Delete

    func deleteShowReview(_ showId: String) throws {
        try showPlayerTagDAO.removeForShow(showId)
        try favoriteSongDAO.deleteForShow(showId)
        try showReviewDAO.deleteByShowId(showId)
    }

    // MARK: - Private

    private func ensureShowReviewExists(_ showId: String) throws {
        if try showReviewDAO.fetchByShowId(showId) == nil {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            try showReviewDAO.upsert(ShowReviewRecord(
                showId: showId,
                createdAt: now,
                updatedAt: now
            ))
        }
    }
}

// MARK: - Record → Domain mapping

private extension ShowPlayerTagRecord {
    func toDomain() -> PlayerTag {
        PlayerTag(
            playerName: playerName,
            instruments: instruments,
            isStandout: isStandout,
            notes: notes
        )
    }
}
