import Foundation

@Observable
final class HomeServiceImpl: HomeService {
    private let showRepository: any ShowRepository
    private let collectionsDAO: CollectionsDAO
    private let recentShowsService: RecentShowsService

    private(set) var content = HomeContent()
    private(set) var isLoading = false

    nonisolated init(
        showRepository: any ShowRepository,
        collectionsDAO: CollectionsDAO,
        recentShowsService: RecentShowsService
    ) {
        self.showRepository = showRepository
        self.collectionsDAO = collectionsDAO
        self.recentShowsService = recentShowsService
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

            let recentShows = await recentShowsService.getRecentShows(limit: 8)

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
