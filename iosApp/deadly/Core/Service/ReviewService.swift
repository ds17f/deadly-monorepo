import Foundation
import GRDB

struct ReviewService: Sendable {
    private let showReviewDAO: ShowReviewDAO
    private let favoriteSongDAO: FavoriteSongDAO
    private let showPlayerTagDAO: ShowPlayerTagDAO
    private let showDAO: ShowDAO
    private let analyticsService: AnalyticsService?
    /// Optional so tests / preview builds without auth still work. Wired by
    /// AppContainer after both services exist. See PLANS/mobile-server-sync.md.
    private let favoritesPushService: FavoritesPushService?

    init(
        showReviewDAO: ShowReviewDAO,
        favoriteSongDAO: FavoriteSongDAO,
        showPlayerTagDAO: ShowPlayerTagDAO,
        showDAO: ShowDAO,
        analyticsService: AnalyticsService? = nil,
        favoritesPushService: FavoritesPushService? = nil
    ) {
        self.showReviewDAO = showReviewDAO
        self.favoriteSongDAO = favoriteSongDAO
        self.showPlayerTagDAO = showPlayerTagDAO
        self.showDAO = showDAO
        self.analyticsService = analyticsService
        self.favoritesPushService = favoritesPushService
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
        enqueueReviewPush(showId)
    }

    func updateShowRating(_ showId: String, rating: Double?) throws {
        try ensureShowReviewExists(showId)
        try showReviewDAO.updateCustomRating(showId, rating: rating)
        enqueueReviewPush(showId)
    }

    func updateRecordingQuality(_ showId: String, quality: Int?, recordingId: String? = nil) throws {
        try ensureShowReviewExists(showId)
        try showReviewDAO.updateRecordingQuality(showId, quality: quality, recordingId: recordingId)
        enqueueReviewPush(showId)
    }

    func updatePlayingQuality(_ showId: String, quality: Int?) throws {
        try ensureShowReviewExists(showId)
        try showReviewDAO.updatePlayingQuality(showId, quality: quality)
        enqueueReviewPush(showId)
    }

    // MARK: - Favorite songs

    func toggleFavoriteSong(
        showId: String, trackTitle: String, trackNumber: Int? = nil, recordingId: String? = nil
    ) throws {
        // Identity is (showId, trackTitle) — recordingId is recorded as
        // metadata on add (so the favorites screen knows which recording to
        // navigate to) but doesn't participate in matching.
        let isFav = try favoriteSongDAO.isFavorite(showId: showId, trackTitle: trackTitle)
        let localId: Int64?
        if isFav {
            localId = try favoriteSongDAO.softDelete(showId: showId, trackTitle: trackTitle)
        } else {
            localId = try favoriteSongDAO.upsertOrResurrect(
                showId: showId, trackTitle: trackTitle,
                trackNumber: trackNumber, recordingId: recordingId
            )
        }
        if let localId, let push = favoritesPushService {
            Task { @MainActor in
                push.enqueueAndPushFavoriteSong(localId: localId)
            }
        }
        let targetId = "\(showId)/\(recordingId ?? "")/\(trackNumber ?? 0)"
        analyticsService?.track("feature_use", props: [
            "feature": isFav ? "remove_favorite" : "add_favorite",
            "category": "action",
            "target_type": "recording_track",
            "target_id": targetId,
        ])
    }

    func isSongFavorite(showId: String, trackTitle: String) throws -> Bool {
        try favoriteSongDAO.isFavorite(showId: showId, trackTitle: trackTitle)
    }

    func observeFavoriteTitles(showId: String) -> AsyncValueObservation<Set<String>> {
        favoriteSongDAO.database.observe(favoriteSongDAO.observeFavoriteTitles(showId: showId))
    }

    /// Live view of a show's review (record + player tags). Re-emits when sync
    /// applies a remote review, so the "has review" indicator updates without
    /// reopening the show. Excludes tombstoned reviews.
    func observeShowReview(showId: String) -> AsyncValueObservation<ShowReview> {
        showReviewDAO.database.observe(
            ValueObservation.tracking { db in
                let reviewRecord = try ShowReviewRecord
                    .filter(Column("showId") == showId && Column("deletedAt") == nil)
                    .fetchOne(db)
                let tagRecords = try ShowPlayerTagRecord
                    .filter(Column("showId") == showId)
                    .order(Column("playerName").asc)
                    .fetchAll(db)
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
        )
    }

    func observeIsSongFavorite(showId: String, trackTitle: String) -> AsyncValueObservation<Bool> {
        favoriteSongDAO.database.observe(favoriteSongDAO.observeIsFavorite(showId: showId, trackTitle: trackTitle))
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
        // Tags travel with the review on sync — ensure a review row exists and
        // bump its timestamp so the change carries an LWW stamp, then push.
        try ensureShowReviewExists(showId)
        try showReviewDAO.touchUpdatedAt(showId)
        enqueueReviewPush(showId)
    }

    func removePlayerTag(showId: String, playerName: String) throws {
        try showPlayerTagDAO.remove(showId: showId, playerName: playerName)
        try ensureShowReviewExists(showId)
        try showReviewDAO.touchUpdatedAt(showId)
        enqueueReviewPush(showId)
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
        // Tombstone (not hard-delete) so the deletion syncs. The server DELETE
        // clears the review and its player tags together.
        try showReviewDAO.softDelete(showId)
        enqueueReviewPush(showId)
    }

    // MARK: - Private

    /// Fire-and-forget review push, hopping to the @MainActor push service
    /// (mirrors the favorite-song path). No-op when push isn't wired (tests).
    private func enqueueReviewPush(_ showId: String) {
        guard let push = favoritesPushService else { return }
        Task { @MainActor in push.enqueueAndPushReview(showId: showId) }
    }

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
