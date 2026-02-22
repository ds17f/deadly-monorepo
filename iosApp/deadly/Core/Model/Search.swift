import Foundation

struct SearchResultShow: Codable, Sendable, Equatable, Identifiable {
    let show: Show
    let relevanceScore: Float
    let matchType: SearchMatchType
    let hasDownloads: Bool
    let highlightedFields: [String]

    init(show: Show, relevanceScore: Float = 1.0,
         matchType: SearchMatchType = .general,
         hasDownloads: Bool = false,
         highlightedFields: [String] = []) {
        self.show = show
        self.relevanceScore = relevanceScore
        self.matchType = matchType
        self.hasDownloads = hasDownloads
        self.highlightedFields = highlightedFields
    }

    var id: String { show.id }
}

struct RecentSearch: Codable, Sendable, Equatable {
    let query: String
    let timestamp: Int64
}

struct SuggestedSearch: Codable, Sendable, Equatable {
    let query: String
    let resultCount: Int
    let type: SuggestionType

    init(query: String, resultCount: Int = 0, type: SuggestionType = .general) {
        self.query = query
        self.resultCount = resultCount
        self.type = type
    }
}

enum SearchMatchType: String, Codable, Sendable, Equatable, CaseIterable {
    case title = "TITLE"
    case venue = "VENUE"
    case year = "YEAR"
    case setlist = "SETLIST"
    case location = "LOCATION"
    case general = "GENERAL"

    var displayName: String {
        switch self {
        case .title: return "Title"
        case .venue: return "Venue"
        case .year: return "Year"
        case .setlist: return "Setlist"
        case .location: return "Location"
        case .general: return "General"
        }
    }
}

enum SuggestionType: String, Codable, Sendable, Equatable, CaseIterable {
    case general = "GENERAL"
    case venue = "VENUE"
    case year = "YEAR"
    case song = "SONG"
    case location = "LOCATION"
}

enum SearchSortOption: String, Codable, Sendable, Equatable, CaseIterable {
    case relevance = "RELEVANCE"
    case rating = "RATING"
    case dateOfShow = "DATE_OF_SHOW"
    case venue = "VENUE"
    case state = "STATE"

    var displayName: String {
        switch self {
        case .relevance: return "Relevance"
        case .rating: return "Rating"
        case .dateOfShow: return "Show Date"
        case .venue: return "Venue"
        case .state: return "State"
        }
    }
}

enum SearchSortDirection: String, Codable, Sendable, Equatable, CaseIterable {
    case ascending = "ASCENDING"
    case descending = "DESCENDING"

    var displayName: String {
        switch self {
        case .ascending: return "Ascending"
        case .descending: return "Descending"
        }
    }
}
