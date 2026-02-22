import Foundation

struct Venue: Codable, Sendable, Equatable {
    let name: String
    let city: String?
    let state: String?
    let country: String

    var displayLocation: String {
        var parts: [String] = []
        if let city { parts.append(city) }
        if let state { parts.append(state) }
        if country != "USA" { parts.append(country) }
        return parts.joined(separator: ", ")
    }
}

struct Location: Codable, Sendable, Equatable {
    let displayText: String
    let city: String?
    let state: String?

    static func fromRaw(_ raw: String?, city: String?, state: String?) -> Location {
        let parts = [city, state].compactMap { $0 }
        let display = raw ?? (parts.isEmpty ? "Unknown Location" : parts.joined(separator: ", "))
        return Location(displayText: display, city: city, state: state)
    }
}

struct Show: Codable, Sendable, Equatable, Identifiable {
    let id: String
    let date: String
    let year: Int
    let band: String
    let venue: Venue
    let location: Location
    let setlist: Setlist?
    let lineup: Lineup?

    // Recording references
    let recordingIds: [String]
    let bestRecordingId: String?

    // Show-level stats (precomputed from recordings)
    let recordingCount: Int
    let averageRating: Float?
    let totalReviews: Int

    // Cover art
    let coverImageUrl: String?

    // User state
    let isInLibrary: Bool
    let libraryAddedAt: Int64?

    var displayTitle: String { "\(venue.name) - \(date)" }
    var hasRating: Bool { averageRating.map { $0 > 0 } ?? false }
    var displayRating: String { averageRating.map { String(format: "%.1f★", $0) } ?? "Not Rated" }
    var hasMultipleRecordings: Bool { recordingCount > 1 }
}
