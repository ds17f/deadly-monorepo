import Foundation

struct HomeContent: Sendable {
    var todayInHistory: [Show] = []
    var recentShows: [Show] = []
}

@MainActor
protocol HomeService {
    var content: HomeContent { get }
    var isLoading: Bool { get }
    func refresh() async
}
