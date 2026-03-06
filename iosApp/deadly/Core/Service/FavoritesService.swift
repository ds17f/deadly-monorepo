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

    private(set) var shows: [FavoriteShow] = []
    private(set) var isLoading = false

    nonisolated init(
        database: AppDatabase,
        favoritesDAO: FavoritesDAO,
        showReviewDAO: ShowReviewDAO,
        showRepository: any ShowRepository
    ) {
        self.database = database
        self.favoritesDAO = favoritesDAO
        self.showReviewDAO = showReviewDAO
        self.showRepository = showRepository
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
            }
            return ascending ? result : !result
        }
    }
}
