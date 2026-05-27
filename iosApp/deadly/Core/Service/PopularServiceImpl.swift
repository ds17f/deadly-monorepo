import Foundation

/// Fetches /api/popular and resolves returned show IDs into Show domain
/// models from the local catalog. Single window (no trending-style
/// now/week/month/all variations) — "Fan Favorites" is an all-time
/// retention signal, not a time-window leaderboard.
@Observable
@MainActor
final class PopularServiceImpl: PopularService {
    private let appPreferences: AppPreferences
    private let showRepository: any ShowRepository
    private let session: URLSession

    private(set) var content = PopularContent()

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
            let ids = payload.shows.map(\.show_id)
            let byId: [String: Show] = Dictionary(
                uniqueKeysWithValues: try showRepository.getShowsByIds(ids).map { ($0.id, $0) }
            )
            content = PopularContent(shows: ids.compactMap { byId[$0] })
        } catch {
            // Leave previous content in place on failure.
        }
    }

    private func fetchPayload() async throws -> PopularPayload {
        let url = URL(string: "\(appPreferences.apiBaseUrl)/api/popular")!
        let (data, response) = try await session.data(from: url)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }
        return try JSONDecoder().decode(PopularPayload.self, from: data)
    }

    // MARK: - Wire format

    private struct PopularPayload: Decodable {
        let shows: [Entry]
        struct Entry: Decodable {
            let show_id: String
        }
    }
}
