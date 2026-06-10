import Foundation

/// Fetches /api/popular and resolves returned show IDs into Show domain
/// models from the local catalog. The server returns four decade *pools*
/// (60s/70s/80s/90s); the home rail picks its display set from those
/// pools locally via PopularContent.displayShows(for:seed:) — see ADR /
/// PLANS for the design.
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
            let allIds = Set(
                payload.decades.s60.map(\.show_id)
                + payload.decades.s70.map(\.show_id)
                + payload.decades.s80.map(\.show_id)
                + payload.decades.s90.map(\.show_id)
            )
            let byId: [String: Show] = Dictionary(
                uniqueKeysWithValues: try showRepository.getShowsByIds(Array(allIds)).map { ($0.id, $0) }
            )
            // First-launch race guard: IDs returned but none resolve means the
            // catalog isn't populated yet — keep previous content (see Trending).
            if !allIds.isEmpty && byId.isEmpty {
                return
            }
            content = PopularContent(
                pool60: payload.decades.s60.compactMap { byId[$0.show_id] },
                pool70: payload.decades.s70.compactMap { byId[$0.show_id] },
                pool80: payload.decades.s80.compactMap { byId[$0.show_id] },
                pool90: payload.decades.s90.compactMap { byId[$0.show_id] }
            )
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
        let decades: Decades
        struct Decades: Decodable {
            let s60: [Entry]
            let s70: [Entry]
            let s80: [Entry]
            let s90: [Entry]
            enum CodingKeys: String, CodingKey {
                case s60 = "60s"
                case s70 = "70s"
                case s80 = "80s"
                case s90 = "90s"
            }
        }
        struct Entry: Decodable {
            let show_id: String
        }
    }
}
