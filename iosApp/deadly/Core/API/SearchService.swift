import Foundation

/// Core search service protocol. Feature modules depend on this, never on implementations.
/// Implementation: SearchServiceImpl (DEAD-103)
@MainActor
protocol SearchService {
    // Observable state
    var results: [SearchResultShow] { get }
    var isLoading: Bool { get }
    var query: String { get }

    // Text search (updates observable state)
    func search(query: String) async
    func clearResults()

    // Direct-return queries (for Home / Browse screens)
    func searchByEra(_ decade: String) throws -> [Show]
    func getRandomShow() throws -> Show?
    func getTopRatedShows(limit: Int) throws -> [Show]
}
