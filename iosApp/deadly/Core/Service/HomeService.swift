import Foundation

struct HomeContent: Sendable {
    var todayInHistory: [Show] = []
    var featuredCollections: [CollectionSummary] = []
    var recentShows: [Show] = []
}

struct CollectionSummary: Identifiable, Sendable {
    let id: String
    let name: String
    let description: String
    let totalShows: Int
    let tags: [String]

    var showCountText: String {
        "\(totalShows) \(totalShows == 1 ? "show" : "shows")"
    }

    var formattedName: String { name }
}

@MainActor
protocol HomeService {
    var content: HomeContent { get }
    var isLoading: Bool { get }
    func refresh() async
}
