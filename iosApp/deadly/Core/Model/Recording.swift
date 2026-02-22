import Foundation

enum RecordingSourceType: String, Codable, Sendable, Equatable, CaseIterable {
    case soundboard = "SOUNDBOARD"
    case audience = "AUDIENCE"
    case fm = "FM"
    case matrix = "MATRIX"
    case remaster = "REMASTER"
    case unknown = "UNKNOWN"

    var displayName: String {
        switch self {
        case .soundboard: return "SBD"
        case .audience: return "AUD"
        case .fm: return "FM"
        case .matrix: return "Matrix"
        case .remaster: return "Remaster"
        case .unknown: return "Unknown"
        }
    }

    static func fromString(_ value: String?) -> RecordingSourceType {
        switch value?.uppercased() {
        case "SBD", "SOUNDBOARD": return .soundboard
        case "AUD", "AUDIENCE": return .audience
        case "FM": return .fm
        case "MATRIX", "MTX": return .matrix
        case "REMASTER": return .remaster
        default: return .unknown
        }
    }
}

struct Recording: Codable, Sendable, Equatable, Identifiable {
    let identifier: String
    let showId: String
    let sourceType: RecordingSourceType
    let rating: Double
    let reviewCount: Int
    let taper: String?
    let source: String?
    let lineage: String?
    let sourceTypeString: String?

    var id: String { identifier }

    var hasRating: Bool { rating > 0.0 && reviewCount > 0 }
    var displayRating: String {
        hasRating ? String(format: "%.1f/5.0 (%d reviews)", rating, reviewCount) : "Not Rated"
    }
    var displayTitle: String { "\(sourceType.displayName) • \(displayRating)" }
}
