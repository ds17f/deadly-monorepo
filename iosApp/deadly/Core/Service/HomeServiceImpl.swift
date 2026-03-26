import Foundation
import os

@Observable
final class HomeServiceImpl: HomeService {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "HomeService")

    private let showRepository: any ShowRepository
    private let recentShowsService: RecentShowsService
    private let appPreferences: AppPreferences
    private let archiveSearchClient: any ArchiveSearchClient

    private(set) var content = HomeContent()
    private(set) var isLoading = false

    /// Day-level cache for archive shows from favorite non-GD artists.
    /// Keyed by date string + collection set so it invalidates on day change or favorites change.
    private var archiveCache: (dateKey: String, collections: Set<String>, shows: [ArchiveShow])?

    nonisolated init(
        showRepository: any ShowRepository,
        recentShowsService: RecentShowsService,
        appPreferences: AppPreferences,
        archiveSearchClient: any ArchiveSearchClient
    ) {
        self.showRepository = showRepository
        self.recentShowsService = recentShowsService
        self.appPreferences = appPreferences
        self.archiveSearchClient = archiveSearchClient
    }

    func refresh() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let calendar = Calendar.current
            let now = Date()
            let month = calendar.component(.month, from: now)
            let day = calendar.component(.day, from: now)

            // Only include local GD shows if Grateful Dead is a favorite
            let gdIsFavorite = appPreferences.favoriteArtists.contains { $0.hasLocalData }
            var todayShows = gdIsFavorite ? try showRepository.getShowsForDate(month: month, day: day) : [Show]()

            // Merge shows from favorite non-GD artists via Archive.org
            let archiveShows = await fetchArchiveTodayInHistory(month: month, day: day)
            todayShows.append(contentsOf: archiveShows)
            todayShows.sort { $0.year < $1.year }

            let recentShows = await recentShowsService.getRecentShows(limit: 8)

            content = HomeContent(
                todayInHistory: todayShows,
                recentShows: recentShows
            )

            // Prefetch artwork for Today In History carousel
            let imageUrls = todayShows.compactMap { show -> URL? in
                if let coverUrl = show.coverImageUrl, let url = URL(string: coverUrl) {
                    return url
                }
                if let recordingId = show.bestRecordingId {
                    return URL(string: "https://archive.org/services/img/\(recordingId)")
                }
                return nil
            }
            await ImageCache.shared.prefetch(urls: imageUrls)
        } catch {
            // Leave existing content on error
        }
    }

    // MARK: - Private

    /// Fetch shows from favorite non-GD artists that match today's month/day.
    /// Uses a day-level in-memory cache to avoid repeated API calls.
    private func fetchArchiveTodayInHistory(month: Int, day: Int) async -> [Show] {
        let favoriteNonGD = appPreferences.favoriteArtists.filter { !$0.hasLocalData }
        guard !favoriteNonGD.isEmpty else { return [] }

        let collections = favoriteNonGD.map(\.collection)
        let collectionsSet = Set(collections)
        let dateKey = String(format: "%04d-%02d-%02d", Calendar.current.component(.year, from: Date()), month, day)

        // Return cached results if same day and same favorites
        if let cache = archiveCache, cache.dateKey == dateKey, cache.collections == collectionsSet {
            return filterAndConvert(shows: cache.shows, month: month, day: day, artists: favoriteNonGD)
        }

        // Fetch from Archive.org — single combined query
        do {
            let allShows = try await archiveSearchClient.fetchAllShows(collections: collections)
            archiveCache = (dateKey: dateKey, collections: collectionsSet, shows: allShows)
            logger.info("Fetched \(allShows.count) archive shows for \(collections.count) favorite artists")
            return filterAndConvert(shows: allShows, month: month, day: day, artists: favoriteNonGD)
        } catch {
            logger.error("Failed to fetch archive today-in-history: \(error.localizedDescription)")
            return []
        }
    }

    /// Filter archive shows by month/day and convert to Show domain objects.
    private func filterAndConvert(shows: [ArchiveShow], month: Int, day: Int, artists: [Artist]) -> [Show] {
        // Build collection → artist name lookup
        let collectionToName = Dictionary(artists.map { ($0.collection, $0.name) }, uniquingKeysWith: { a, _ in a })

        let monthStr = String(format: "%02d", month)
        let dayStr = String(format: "%02d", day)
        let suffix = "-\(monthStr)-\(dayStr)"

        return shows.compactMap { show in
            // Date format from IA: "YYYY-MM-DD"
            guard let date = show.date, date.hasSuffix(suffix) else { return nil }
            let bandName = show.collection.flatMap { collectionToName[$0] } ?? "Unknown Artist"
            return show.toShow(band: bandName)
        }
    }
}
