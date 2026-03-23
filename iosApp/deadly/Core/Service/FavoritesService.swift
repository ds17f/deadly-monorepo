import Foundation
import GRDB

// MARK: - FavoritesServiceImpl

@Observable
@MainActor
final class FavoritesServiceImpl {
    private let database: AppDatabase
    private let favoritesDAO: FavoritesDAO
    private let showReviewDAO: ShowReviewDAO
    private let showRepository: any ShowRepository
    private let reviewService: ReviewService
    private let analyticsService: AnalyticsService?

    private(set) var shows: [FavoriteShow] = []
    private(set) var songs: [FavoriteTrack] = []
    private(set) var isLoading = false

    nonisolated init(
        database: AppDatabase,
        favoritesDAO: FavoritesDAO,
        showReviewDAO: ShowReviewDAO,
        showRepository: any ShowRepository,
        reviewService: ReviewService,
        analyticsService: AnalyticsService? = nil
    ) {
        self.database = database
        self.favoritesDAO = favoritesDAO
        self.showReviewDAO = showReviewDAO
        self.showRepository = showRepository
        self.reviewService = reviewService
        self.analyticsService = analyticsService
    }

    // MARK: - Mutations

    func addToFavorites(showId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try database.write { db in
            var record = FavoriteShowRecord(
                showId: showId,
                addedToFavoritesAt: now,
                isPinned: false,
                notes: nil,
                preferredRecordingId: nil,
                downloadedRecordingId: nil,
                downloadedFormat: nil,
                customRating: nil,
                lastAccessedAt: nil,
                tags: nil
            )
            try record.insert(db)
            try ShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("isFavorite").set(to: true),
                    Column("favoritedAt").set(to: now)
                )
        }
        analyticsService?.track("feature_use", props: ["feature": "add_favorite"])
    }

    func removeFromFavorites(showId: String) throws {
        try database.write { db in
            try FavoriteShowRecord.deleteOne(db, key: showId)
            try ShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("isFavorite").set(to: false),
                    Column("favoritedAt").set(to: nil as Int64?)
                )
        }
        analyticsService?.track("feature_use", props: ["feature": "remove_favorite"])
    }

    func isFavorite(showId: String) throws -> Bool {
        try favoritesDAO.isFavorite(showId)
    }

    func togglePin(showId: String) throws {
        guard let existing = try favoritesDAO.fetchById(showId) else { return }
        try favoritesDAO.updatePinStatus(showId, isPinned: !existing.isPinned)
    }

    // MARK: - Fetch

    func refresh(sortedBy option: FavoritesSortOption = .dateAdded, direction: FavoritesSortDirection = .descending) {
        isLoading = true
        defer { isLoading = false }
        do {
            let records = try favoritesDAO.fetchAll()
            let ids = records.map(\.showId)
            let fetched = try showRepository.getShowsByIds(ids)
            let showMap = Dictionary(uniqueKeysWithValues: fetched.map { ($0.id, $0) })

            // Fetch review data from show_reviews table
            let reviewRecords = try showReviewDAO.fetchByShowIds(ids)
            let reviewMap = Dictionary(uniqueKeysWithValues: reviewRecords.map { ($0.showId, $0) })

            let favoriteShows: [FavoriteShow] = records.compactMap { record in
                guard let show = showMap[record.showId] else { return nil }
                let review = reviewMap[record.showId]
                return FavoriteShow(
                    show: show,
                    addedToFavoritesAt: record.addedToFavoritesAt,
                    isPinned: record.isPinned,
                    notes: review?.notes,
                    customRating: review?.customRating,
                    recordingQuality: review?.recordingQuality,
                    playingQuality: review?.playingQuality
                )
            }
            shows = sort(favoriteShows, by: option, direction: direction)
        } catch {
            // Leave existing shows on error
        }
    }

    // MARK: - Songs

    func refreshSongs(sortedBy option: FavoritesSongSortOption = .dateAdded, direction: FavoritesSortDirection = .descending) {
        do {
            let tracks = try reviewService.getFavoriteTracks()
            songs = sortSongs(tracks, by: option, direction: direction)
        } catch {
            // Leave existing songs on error
        }
    }

    private func sortSongs(_ tracks: [FavoriteTrack], by option: FavoritesSongSortOption, direction: FavoritesSortDirection) -> [FavoriteTrack] {
        let ascending = direction == .ascending
        return tracks.sorted { a, b in
            let result: Bool
            switch option {
            case .songTitle:
                result = a.trackTitle.localizedCompare(b.trackTitle) == .orderedAscending
            case .showDate:
                result = a.showDate < b.showDate
            case .dateAdded:
                result = a.addedAt < b.addedAt
            }
            return ascending ? result : !result
        }
    }

    // MARK: - Private

    private func sort(_ shows: [FavoriteShow], by option: FavoritesSortOption, direction: FavoritesSortDirection) -> [FavoriteShow] {
        let ascending = direction == .ascending
        return shows.sorted { a, b in
            // Pinned always first, regardless of sort direction
            if a.isPinned != b.isPinned {
                return a.isPinned
            }
            let result: Bool
            switch option {
            case .dateAdded:
                result = a.addedToFavoritesAt < b.addedToFavoritesAt
            case .dateOfShow:
                result = a.show.date < b.show.date
            case .venue:
                result = a.show.venue.name.localizedCompare(b.show.venue.name) == .orderedAscending
            case .rating:
                let aVal = a.show.averageRating ?? 0
                let bVal = b.show.averageRating ?? 0
                result = aVal < bVal
            case .hasReview:
                result = !a.hasReview && b.hasReview
            }
            return ascending ? result : !result
        }
    }
}
