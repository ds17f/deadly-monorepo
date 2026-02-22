import Foundation

@Observable
final class SearchServiceImpl: SearchService {
    private let showSearchDAO: ShowSearchDAO
    private let showDAO: ShowDAO
    private let showRepository: any ShowRepository
    private let appPreferences: AppPreferences

    private(set) var results: [SearchResultShow] = []
    private(set) var isLoading = false
    private(set) var query = ""

    nonisolated init(showSearchDAO: ShowSearchDAO, showDAO: ShowDAO, showRepository: any ShowRepository, appPreferences: AppPreferences) {
        self.showSearchDAO = showSearchDAO
        self.showDAO = showDAO
        self.showRepository = showRepository
        self.appPreferences = appPreferences
    }

    // MARK: - SearchService

    func search(query: String) async {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        self.query = trimmed
        guard !trimmed.isEmpty else {
            results = []
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            var shows: [Show]
            if trimmed.count >= 3 {
                let showIds = try showSearchDAO.search(trimmed + "*")
                let fetched = try showRepository.getShowsByIds(showIds)
                // Preserve FTS ranking order
                let indexed = Dictionary(fetched.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
                shows = showIds.compactMap { indexed[$0] }
            } else {
                let records = try showDAO.searchLike(trimmed)
                let ids = records.map(\.showId)
                let fetched = try showRepository.getShowsByIds(ids)
                // Preserve date-ordered results from searchLike
                let indexed = Dictionary(fetched.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
                shows = ids.compactMap { indexed[$0] }
            }
            if appPreferences.showOnlyRecordedShows {
                shows = shows.filter { $0.recordingCount > 0 }
            }
            let total = shows.count
            results = shows.enumerated().map { index, show in
                let score = 1.0 - (Float(index) / Float(max(total, 1)))
                let matchType = determineMatchType(show, query: trimmed)
                return SearchResultShow(show: show, relevanceScore: score, matchType: matchType)
            }
        } catch {
            results = []
        }
    }

    func clearResults() {
        results = []
        query = ""
    }

    func searchByEra(_ decade: String) throws -> [Show] {
        let range: ClosedRange<Int>
        switch decade {
        case "60s": range = 1965...1969
        case "70s": range = 1970...1979
        case "80s": range = 1980...1989
        case "90s": range = 1990...1995
        default: return []
        }
        return try range.flatMap { year in
            try showRepository.getShowsByYear(year)
        }
    }

    func getRandomShow() throws -> Show? {
        let all = try showRepository.getAllShows()
        return all.randomElement()
    }

    func getTopRatedShows(limit: Int) throws -> [Show] {
        try showRepository.getTopRatedShows(limit: limit)
    }

    // MARK: - Private helpers

    private func determineMatchType(_ show: Show, query: String) -> SearchMatchType {
        let q = query.lowercased()
        if show.date.contains(q) || String(show.year).contains(q) { return .year }
        if show.venue.name.lowercased().contains(q) { return .venue }
        if show.venue.city?.lowercased().contains(q) == true ||
           show.location.state?.lowercased().contains(q) == true { return .location }
        return .general
    }
}
