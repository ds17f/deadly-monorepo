import Foundation

/// Client for the Internet Archive's Advanced Search API.
/// Used to browse and search shows for artists not in the local data pipeline.
protocol ArchiveSearchClient: Sendable {
    func searchShows(artist: Artist, page: Int, pageSize: Int) async throws -> (shows: [ArchiveShow], totalCount: Int)
    func searchAllArtists(query: String, page: Int, pageSize: Int) async throws -> (shows: [ArchiveShow], totalCount: Int)
    func fetchAllShows(collections: [String]) async throws -> [ArchiveShow]
}

struct URLSessionArchiveSearchClient: ArchiveSearchClient {
    private let session: URLSession
    private let baseURL = "https://archive.org/advancedsearch.php"
    private let fields = "identifier,date,title,venue,coverage,avg_rating,num_reviews,collection"

    init(session: URLSession = .shared) {
        self.session = session
    }

    func searchShows(artist: Artist, page: Int, pageSize: Int) async throws -> (shows: [ArchiveShow], totalCount: Int) {
        let query = "collection:\(artist.collection) AND mediatype:etree"
        return try await performSearch(query: query, page: page, pageSize: pageSize)
    }

    func searchAllArtists(query: String, page: Int, pageSize: Int) async throws -> (shows: [ArchiveShow], totalCount: Int) {
        // Search across all etree collections
        let iaQuery = "collection:etree AND (\(query)) AND mediatype:etree"
        return try await performSearch(query: iaQuery, page: page, pageSize: pageSize)
    }

    func fetchAllShows(collections: [String]) async throws -> [ArchiveShow] {
        guard !collections.isEmpty else { return [] }
        let collectionClause = collections.map { "collection:\($0)" }.joined(separator: " OR ")
        let query = "(\(collectionClause)) AND mediatype:etree"
        let (shows, _) = try await performSearch(query: query, page: 1, pageSize: 5000)
        return shows
    }

    // MARK: - Private

    private func performSearch(query: String, page: Int, pageSize: Int) async throws -> (shows: [ArchiveShow], totalCount: Int) {
        var components = URLComponents(string: baseURL)!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "fl", value: fields),
            URLQueryItem(name: "sort", value: "date desc"),
            URLQueryItem(name: "rows", value: "\(pageSize)"),
            URLQueryItem(name: "page", value: "\(page)"),
            URLQueryItem(name: "output", value: "json"),
        ]

        guard let url = components.url else {
            throw URLError(.badURL)
        }

        let (data, response) = try await session.data(from: url)

        if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 200 {
            throw URLError(.badServerResponse)
        }

        let searchResponse = try JSONDecoder().decode(ArchiveSearchResponse.self, from: data)
        let shows = searchResponse.response.docs.map { $0.toArchiveShow() }

        return (shows: shows, totalCount: searchResponse.response.numFound)
    }
}
