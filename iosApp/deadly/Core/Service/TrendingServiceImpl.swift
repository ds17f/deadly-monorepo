import Foundation

/// Fetches /api/trending and resolves the returned show IDs into Show
/// domain models from the local catalog. Server returns four windows
/// (now=24h, week, month, all-time) — we hydrate each independently and
/// preserve server ranking order.
@Observable
@MainActor
final class TrendingServiceImpl: TrendingService {
    private let appPreferences: AppPreferences
    private let showRepository: any ShowRepository
    private let session: URLSession

    private(set) var content = TrendingContent()

    init(
        appPreferences: AppPreferences,
        showRepository: any ShowRepository,
        session: URLSession = .shared
    ) {
        self.appPreferences = appPreferences
        self.showRepository = showRepository
        self.session = session
    }

    func refresh() async {
        do {
            let payload = try await fetchPayload()
            let nowIds = payload.windows.now.map(\.show_id)
            let weekIds = payload.windows.week.map(\.show_id)
            let monthIds = payload.windows.month.map(\.show_id)
            let allIds = payload.windows.all.map(\.show_id)

            // Single batch lookup, then reorder per window to preserve
            // server ranking.
            let unique = Array(Set(nowIds + weekIds + monthIds + allIds))
            let byId: [String: Show] = Dictionary(
                uniqueKeysWithValues: try showRepository.getShowsByIds(unique).map { ($0.id, $0) }
            )

            content = TrendingContent(
                now: nowIds.compactMap { byId[$0] },
                week: weekIds.compactMap { byId[$0] },
                month: monthIds.compactMap { byId[$0] },
                all: allIds.compactMap { byId[$0] }
            )
        } catch {
            // Leave previous content in place on failure.
        }
    }

    private func fetchPayload() async throws -> TrendingPayload {
        let url = URL(string: "\(appPreferences.apiBaseUrl)/api/trending")!
        let (data, response) = try await session.data(from: url)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(TrendingPayload.self, from: data)
    }

    // MARK: - Wire format

    private struct TrendingPayload: Decodable {
        let windows: Windows
        struct Windows: Decodable {
            let now: [Entry]
            let week: [Entry]
            let month: [Entry]
            let all: [Entry]
        }
        struct Entry: Decodable {
            let show_id: String
        }
    }
}
