import Foundation

/// Loads show data from the Internet Archive metadata API for artists
/// not in the local data pipeline.
protocol ArchiveShowService: Sendable {
    func loadShow(identifier: String) async throws -> Show
}

struct URLSessionArchiveShowService: ArchiveShowService {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func loadShow(identifier: String) async throws -> Show {
        guard let url = URL(string: "https://archive.org/metadata/\(identifier)") else {
            throw URLError(.badURL)
        }

        let (data, _) = try await session.data(from: url)

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let metadata = json["metadata"] as? [String: Any] else {
            throw URLError(.cannotParseResponse)
        }

        let creator = stringField(metadata["creator"]) ?? "Unknown Artist"
        let title = stringField(metadata["title"]) ?? identifier
        let date = stringField(metadata["date"]).flatMap { normalizeDate($0) } ?? "Unknown Date"
        let venue = stringField(metadata["venue"]) ?? extractVenueFromTitle(title)
        let coverage = stringField(metadata["coverage"])
        let year = date.count >= 4 ? (Int(date.prefix(4)) ?? 0) : 0

        let avgRatingStr = stringField(metadata["avg_rating"])
        let avgRating = avgRatingStr.flatMap { Float($0) }
        let numReviews = (metadata["num_reviews"] as? Int)
            ?? (stringField(metadata["num_reviews"]).flatMap { Int($0) })
            ?? 0

        let parsedVenue = Venue(name: venue, city: nil, state: nil, country: "")
        let location = Location.fromRaw(coverage, city: nil, state: nil)

        return Show(
            id: identifier,
            date: date,
            year: year,
            band: creator,
            venue: parsedVenue,
            location: location,
            setlist: nil,
            lineup: nil,
            recordingIds: [identifier],
            bestRecordingId: identifier,
            bestSourceType: .unknown,
            recordingCount: 1,
            averageRating: avgRating,
            totalReviews: numReviews,
            coverImageUrl: "https://archive.org/services/img/\(identifier)",
            isFavorite: false,
            favoritedAt: nil
        )
    }

    // MARK: - Helpers

    private func stringField(_ value: Any?) -> String? {
        if let s = value as? String { return s.isEmpty ? nil : s }
        if let arr = value as? [String] { return arr.first }
        return nil
    }

    private func normalizeDate(_ raw: String) -> String {
        // IA dates may be "YYYY-MM-DDT00:00:00Z" or "YYYY-MM-DD" or "YYYY"
        if raw.count >= 10 { return String(raw.prefix(10)) }
        return raw
    }

    private func extractVenueFromTitle(_ title: String) -> String {
        // Titles are often "Artist Live at Venue on Date"
        if title.contains(" at ") {
            let parts = title.components(separatedBy: " at ")
            if parts.count >= 2 {
                let afterAt = parts.dropFirst().joined(separator: " at ")
                if let onRange = afterAt.range(of: " on \\d{4}-\\d{2}-\\d{2}", options: .regularExpression) {
                    return String(afterAt[afterAt.startIndex..<onRange.lowerBound])
                }
                return afterAt
            }
        }
        return title
    }
}
