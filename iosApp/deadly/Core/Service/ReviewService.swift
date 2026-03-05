import Foundation
import GRDB

struct ReviewService: Sendable {
    private let showReviewDAO: ShowReviewDAO
    private let trackReviewDAO: TrackReviewDAO
    private let showPlayerTagDAO: ShowPlayerTagDAO
    private let showDAO: ShowDAO

    init(showReviewDAO: ShowReviewDAO, trackReviewDAO: TrackReviewDAO, showPlayerTagDAO: ShowPlayerTagDAO, showDAO: ShowDAO) {
        self.showReviewDAO = showReviewDAO
        self.trackReviewDAO = trackReviewDAO
        self.showPlayerTagDAO = showPlayerTagDAO
        self.showDAO = showDAO
    }

    // MARK: - Show-level review

    func getShowReview(_ showId: String) throws -> ShowReview {
        let reviewRecord = try showReviewDAO.fetchByShowId(showId)
        let trackRecords = try trackReviewDAO.fetchForShow(showId)
        let tagRecords = try showPlayerTagDAO.fetchForShow(showId)

        return ShowReview(
            showId: showId,
            notes: reviewRecord?.notes,
            overallRating: reviewRecord?.customRating,
            recordingQuality: reviewRecord?.recordingQuality,
            playingQuality: reviewRecord?.playingQuality,
            reviewedRecordingId: reviewRecord?.reviewedRecordingId,
            trackReviews: trackRecords.map { $0.toDomain() },
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

    // MARK: - Track reviews

    func getTrackReviews(_ showId: String) throws -> [TrackReview] {
        try trackReviewDAO.fetchForShow(showId).map { $0.toDomain() }
    }

    func observeThumbsUpTitles(showId: String) -> AsyncValueObservation<Set<String>> {
        trackReviewDAO.database.observe(trackReviewDAO.observeThumbsUpTitles(showId: showId))
    }

    func getTrackReview(showId: String, trackTitle: String, recordingId: String?) throws -> TrackReview? {
        try trackReviewDAO.fetch(showId: showId, trackTitle: trackTitle, recordingId: recordingId)?.toDomain()
    }

    func upsertTrackReview(
        showId: String, trackTitle: String, trackNumber: Int? = nil,
        recordingId: String? = nil, thumbs: Int? = nil, starRating: Int? = nil, notes: String? = nil
    ) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let existing = try trackReviewDAO.fetch(showId: showId, trackTitle: trackTitle, recordingId: recordingId)
        let record = TrackReviewRecord(
            id: existing?.id,
            showId: showId,
            trackTitle: trackTitle,
            trackNumber: trackNumber,
            recordingId: recordingId,
            thumbs: thumbs,
            starRating: starRating,
            notes: notes,
            createdAt: existing?.createdAt ?? now,
            updatedAt: now
        )
        try trackReviewDAO.upsert(record)
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

    func getThumbsUpTracks() throws -> [FavoriteTrack] {
        let records = try trackReviewDAO.fetchThumbsUp()
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
                recordingId: record.recordingId
            )
        }
    }

    // MARK: - Delete

    func deleteShowReview(_ showId: String) throws {
        try showPlayerTagDAO.removeForShow(showId)
        try trackReviewDAO.deleteForShow(showId)
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

private extension TrackReviewRecord {
    func toDomain() -> TrackReview {
        TrackReview(
            trackTitle: trackTitle,
            trackNumber: trackNumber,
            recordingId: recordingId,
            thumbs: thumbs,
            starRating: starRating,
            notes: notes
        )
    }
}

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
