import Foundation

struct PopularContent: Sendable {
    var shows: [Show] = []
}

@MainActor
protocol PopularService {
    var content: PopularContent { get }
    func refresh() async
}
