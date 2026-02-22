import Foundation

struct DeadCollection: Codable, Sendable, Equatable, Identifiable {
    let id: String
    let name: String
    let description: String
    let tags: [String]
    let shows: [Show]

    var totalShows: Int { shows.count }
    var hasShows: Bool { !shows.isEmpty }
    var showCountText: String {
        switch totalShows {
        case 0: return "No shows"
        case 1: return "1 show"
        default: return "\(totalShows) shows"
        }
    }
    var primaryTag: String? { tags.first }
    var isEraCollection: Bool { tags.contains("era") }
    var isOfficialRelease: Bool { tags.contains("official") || tags.contains("dicks-picks") }
}
