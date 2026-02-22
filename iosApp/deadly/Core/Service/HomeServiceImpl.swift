import Foundation

@Observable
final class HomeServiceImpl: HomeService {
    private let showRepository: any ShowRepository
    private let collectionsDAO: CollectionsDAO
    private let recentShowDAO: RecentShowDAO

    private(set) var content = HomeContent()
    private(set) var isLoading = false

    nonisolated init(
        showRepository: any ShowRepository,
        collectionsDAO: CollectionsDAO,
        recentShowDAO: RecentShowDAO
    ) {
        self.showRepository = showRepository
        self.collectionsDAO = collectionsDAO
        self.recentShowDAO = recentShowDAO
    }

    func refresh() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let calendar = Calendar.current
            let now = Date()
            let month = calendar.component(.month, from: now)
            let day = calendar.component(.day, from: now)

            let todayShows = try showRepository.getShowsForDate(month: month, day: day)

            let collectionRecords = try collectionsDAO.fetchFeatured(limit: 10)
            let collections = collectionRecords.map { record in
                let tags = (try? JSONDecoder().decode([String].self, from: Data(record.tagsJson.utf8))) ?? []
                return CollectionSummary(
                    id: record.id,
                    name: record.name,
                    description: record.description,
                    totalShows: record.totalShows,
                    tags: tags
                )
            }

            let recentRecords = try recentShowDAO.fetchRecent(limit: 8)
            let recentIds = recentRecords.map(\.showId)
            let fetchedShows = try showRepository.getShowsByIds(recentIds)
            let showsById = Dictionary(fetchedShows.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            let recentShows = recentIds.compactMap { showsById[$0] }

            content = HomeContent(
                todayInHistory: todayShows,
                featuredCollections: collections,
                recentShows: recentShows
            )
        } catch {
            // Leave existing content on error
        }
    }
}
