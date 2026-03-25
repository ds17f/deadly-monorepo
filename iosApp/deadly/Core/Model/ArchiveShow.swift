import Foundation

/// Lightweight show model from Internet Archive search results.
struct ArchiveShow: Identifiable, Codable, Sendable, Hashable {
    let identifier: String
    let date: String?
    let title: String?
    let venue: String?
    let coverage: String?
    let avgRating: Double?
    let numReviews: Int?

    var id: String { identifier }

    var displayDate: String {
        date ?? "Unknown Date"
    }

    var displayVenue: String {
        // IA titles often include the full show info: "Artist Live at Venue on Date"
        // Try to extract venue from title if venue field is missing
        if let venue, !venue.isEmpty { return venue }
        if let title, title.contains(" at ") {
            let parts = title.components(separatedBy: " at ")
            if parts.count >= 2 {
                let afterAt = parts.dropFirst().joined(separator: " at ")
                // Strip " on YYYY-MM-DD" suffix if present
                if let onRange = afterAt.range(of: " on \\d{4}-\\d{2}-\\d{2}", options: .regularExpression) {
                    return String(afterAt[afterAt.startIndex..<onRange.lowerBound])
                }
                return afterAt
            }
        }
        return title ?? identifier
    }

    var displayLocation: String {
        coverage ?? ""
    }

    var displayRating: String? {
        guard let avgRating, avgRating > 0 else { return nil }
        return String(format: "%.1f★", avgRating)
    }

    /// Convert to a full Show model for downstream compatibility (player, detail screen).
    func toShow(band: String) -> Show {
        let parsedVenue = Venue(
            name: displayVenue,
            city: nil,
            state: nil,
            country: ""
        )
        let location = Location.fromRaw(coverage, city: nil, state: nil)

        return Show(
            id: identifier,
            date: displayDate,
            year: yearFromDate ?? 0,
            band: band,
            venue: parsedVenue,
            location: location,
            setlist: nil,
            lineup: nil,
            recordingIds: [identifier],
            bestRecordingId: identifier,
            bestSourceType: .unknown,
            recordingCount: 1,
            averageRating: avgRating.map { Float($0) },
            totalReviews: numReviews ?? 0,
            coverImageUrl: "https://archive.org/services/img/\(identifier)",
            isFavorite: false,
            favoritedAt: nil
        )
    }

    private var yearFromDate: Int? {
        guard let date, date.count >= 4 else { return nil }
        return Int(date.prefix(4))
    }
}

// MARK: - Search Response

struct ArchiveSearchResponse: Codable, Sendable {
    let responseHeader: ArchiveSearchResponseHeader?
    let response: ArchiveSearchResponseBody

    struct ArchiveSearchResponseHeader: Codable, Sendable {
        let status: Int?
    }

    struct ArchiveSearchResponseBody: Codable, Sendable {
        let numFound: Int
        let start: Int
        let docs: [ArchiveSearchDoc]
    }

    struct ArchiveSearchDoc: Codable, Sendable {
        let identifier: String
        let date: String?
        let title: String?
        let venue: String?
        let coverage: String?
        let avg_rating: Double?
        let num_reviews: Int?

        func toArchiveShow() -> ArchiveShow {
            ArchiveShow(
                identifier: identifier,
                date: normalizedDate,
                title: title,
                venue: venue,
                coverage: coverage,
                avgRating: avg_rating,
                numReviews: num_reviews
            )
        }

        /// IA dates come as "YYYY-MM-DDT00:00:00Z" — normalize to "YYYY-MM-DD".
        private var normalizedDate: String? {
            guard let date else { return nil }
            if date.count >= 10 {
                return String(date.prefix(10))
            }
            return date
        }
    }
}
