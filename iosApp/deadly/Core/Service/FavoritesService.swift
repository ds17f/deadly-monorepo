import Foundation
import GRDB

// MARK: - FavoritesServiceImpl

/// Observation-driven favorites cache.
///
/// `shows` and `songs` are kept current automatically: a long-lived
/// observation task subscribes to GRDB ValueObservations on the underlying
/// tables, re-enriches on each emission, and re-publishes a sorted snapshot.
/// Anything that writes to favorite_shows / favorite_songs — local mutations,
/// sync-apply, import — propagates without needing an explicit refresh hook.
///
/// Sort is held in-memory. UI sort changes go through `refresh` / `refreshSongs`
/// and trigger a cheap re-sort of the cached snapshot (no DB hit).
@Observable
@MainActor
final class FavoritesServiceImpl {
    private let database: AppDatabase
    private let favoritesDAO: FavoritesDAO
    private let favoriteSongDAO: FavoriteSongDAO
    private let showReviewDAO: ShowReviewDAO
    private let showRepository: any ShowRepository
    private let analyticsService: AnalyticsService?
    /// Optional so tests / preview builds without auth still work.
    var favoritesPushService: FavoritesPushService?

    private(set) var shows: [FavoriteShow] = []
    private(set) var songs: [FavoriteTrack] = []
    private(set) var isLoading = false

    private var sortOption: FavoritesSortOption = .dateAdded
    private var sortDirection: FavoritesSortDirection = .descending
    private var songSortOption: FavoritesSongSortOption = .dateAdded
    private var songSortDirection: FavoritesSortDirection = .descending

    /// Most recent unsorted snapshots, re-published with the current sort.
    private var rawShows: [FavoriteShow] = []
    private var rawSongs: [FavoriteTrack] = []

    /// Owns the observation lifecycle. Lives as long as the service does.
    private var observationTasks: [Task<Void, Never>] = []

    nonisolated init(
        database: AppDatabase,
        favoritesDAO: FavoritesDAO,
        favoriteSongDAO: FavoriteSongDAO,
        showReviewDAO: ShowReviewDAO,
        showRepository: any ShowRepository,
        analyticsService: AnalyticsService? = nil
    ) {
        self.database = database
        self.favoritesDAO = favoritesDAO
        self.favoriteSongDAO = favoriteSongDAO
        self.showReviewDAO = showReviewDAO
        self.showRepository = showRepository
        self.analyticsService = analyticsService
    }

    /// Called by AppContainer after construction to start the observation.
    /// Kept out of init so the @MainActor task is started in a defined place.
    func startObserving() {
        guard observationTasks.isEmpty else { return }
        let showsObs = favoritesDAO.observeAll()
        let songsObs = favoriteSongDAO.observeAll()

        observationTasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await records in self.database.observe(showsObs) {
                self.rawShows = self.enrichShows(records: records)
                self.republishShows()
            }
        })
        observationTasks.append(Task { @MainActor [weak self] in
            guard let self else { return }
            for await records in self.database.observe(songsObs) {
                self.rawSongs = self.enrichSongs(records: records)
                self.republishSongs()
            }
        })
    }

    // MARK: - Mutations

    func addToFavorites(showId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try database.write { db in
            var record = FavoriteShowRecord(
                showId: showId,
                addedToFavoritesAt: now,
                isPinned: false,
                updatedAt: now,
                deletedAt: nil
            )
            try record.insert(db, onConflict: .replace)
            try ShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("isFavorite").set(to: true),
                    Column("favoritedAt").set(to: now)
                )
        }
        favoritesPushService?.enqueueAndPush(showId: showId)
        analyticsService?.track("feature_use", props: [
            "feature": "add_favorite",
            "category": "action",
            "target_type": "show",
            "target_id": showId,
        ])
    }

    func removeFromFavorites(showId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try database.write { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("deletedAt").set(to: now),
                    Column("updatedAt").set(to: now)
                )
            try ShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("isFavorite").set(to: false),
                    Column("favoritedAt").set(to: nil as Int64?)
                )
        }
        favoritesPushService?.enqueueAndPush(showId: showId)
        analyticsService?.track("feature_use", props: [
            "feature": "remove_favorite",
            "category": "action",
            "target_type": "show",
            "target_id": showId,
        ])
    }

    func isFavorite(showId: String) throws -> Bool {
        try favoritesDAO.isFavorite(showId)
    }

    func togglePin(showId: String) throws {
        guard let existing = try favoritesDAO.fetchById(showId) else { return }
        try favoritesDAO.updatePinStatus(showId, isPinned: !existing.isPinned)
    }

    // MARK: - Sort

    /// Set the shows sort. Does a one-shot direct fetch + re-publish so that
    /// callers can rely on `shows` being current synchronously (tests, or
    /// screens that mount before the first observation emission lands). The
    /// observation task subsequently keeps the buffer fresh on its own.
    func refresh(sortedBy option: FavoritesSortOption = .dateAdded, direction: FavoritesSortDirection = .descending) {
        sortOption = option
        sortDirection = direction
        if let records = try? favoritesDAO.fetchAll() {
            rawShows = enrichShows(records: records)
        }
        republishShows()
    }

    /// Set the songs sort. Same shape as `refresh` — direct fetch + re-sort.
    func refreshSongs(sortedBy option: FavoritesSongSortOption = .dateAdded, direction: FavoritesSortDirection = .descending) {
        songSortOption = option
        songSortDirection = direction
        if let records = try? favoriteSongDAO.fetchAll() {
            rawSongs = enrichSongs(records: records)
        }
        republishSongs()
    }

    // MARK: - Enrichment + Sort

    private func enrichShows(records: [FavoriteShowRecord]) -> [FavoriteShow] {
        let ids = records.map(\.showId)
        let fetched = (try? showRepository.getShowsByIds(ids)) ?? []
        let showMap = Dictionary(uniqueKeysWithValues: fetched.map { ($0.id, $0) })
        let reviewRecords = (try? showReviewDAO.fetchByShowIds(ids)) ?? []
        let reviewMap = Dictionary(uniqueKeysWithValues: reviewRecords.map { ($0.showId, $0) })

        return records.compactMap { record in
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
    }

    private func enrichSongs(records: [FavoriteSongRecord]) -> [FavoriteTrack] {
        let showIds = Array(Set(records.map(\.showId)))
        let fetched = (try? showRepository.getShowsByIds(showIds)) ?? []
        let showMap = Dictionary(uniqueKeysWithValues: fetched.map { ($0.id, $0) })

        return records.compactMap { record in
            guard let show = showMap[record.showId] else { return nil }
            return FavoriteTrack(
                showId: record.showId,
                showDate: show.date,
                venue: show.venue.name,
                trackTitle: record.trackTitle,
                trackNumber: record.trackNumber,
                recordingId: record.recordingId,
                addedAt: record.createdAt
            )
        }
    }

    private func republishShows() {
        shows = sort(rawShows, by: sortOption, direction: sortDirection)
    }

    private func republishSongs() {
        songs = sortSongs(rawSongs, by: songSortOption, direction: songSortDirection)
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
