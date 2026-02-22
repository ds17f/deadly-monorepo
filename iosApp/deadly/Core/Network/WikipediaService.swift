import Foundation

protocol WikipediaService: Sendable {
    func getVenueSummary(venueName: String, city: String?) async -> String?
}

actor URLSessionWikipediaService: WikipediaService {
    private let session: URLSession
    private let cacheDir: URL
    private let cacheExpirySeconds: TimeInterval = 7 * 24 * 60 * 60

    init() {
        self.session = URLSession.shared
        let cachesDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        self.cacheDir = cachesDir.appendingPathComponent("wikipedia", isDirectory: true)
        try? FileManager.default.createDirectory(at: self.cacheDir, withIntermediateDirectories: true)
    }

    func getVenueSummary(venueName: String, city: String?) async -> String? {
        guard !venueName.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }

        let cacheKey = "\(venueName)_\(city ?? "")".hashValue.description
        let cacheFile = cacheDir.appendingPathComponent("\(cacheKey).txt")

        if let cached = try? String(contentsOf: cacheFile, encoding: .utf8),
           !isCacheExpired(at: cacheFile) {
            return cached.isEmpty ? nil : cached
        }

        // Can't use ?? with await on RHS — it evaluates in an autoclosure.
        let extract: String?
        if let direct = await tryDirectLookup(venueName: venueName) {
            extract = direct
        } else {
            extract = await trySearchLookup(venueName: venueName, city: city)
        }

        try? (extract ?? "").write(to: cacheFile, atomically: true, encoding: .utf8)
        return extract
    }

    private func tryDirectLookup(venueName: String) async -> String? {
        let title = venueName.replacingOccurrences(of: " ", with: "_")
        guard let encoded = title.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
              let url = URL(string: "https://en.wikipedia.org/api/rest_v1/page/summary/\(encoded)") else { return nil }

        guard let (data, response) = try? await session.data(from: url) else { return nil }
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let extract = json["extract"] as? String,
              !extract.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }

        return extract
    }

    private func trySearchLookup(venueName: String, city: String?) async -> String? {
        let query = [venueName, city].compactMap { $0 }.joined(separator: " ")
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let searchURL = URL(string: "https://en.wikipedia.org/w/api.php?action=opensearch&search=\(encoded)&limit=1&format=json") else { return nil }

        guard let (data, _) = try? await session.data(from: searchURL) else { return nil }
        guard let array = try? JSONSerialization.jsonObject(with: data) as? [Any],
              array.count >= 2,
              let titles = array[1] as? [String],
              let firstTitle = titles.first else { return nil }

        return await tryDirectLookup(venueName: firstTitle)
    }

    private func isCacheExpired(at url: URL) -> Bool {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let modDate = attrs[.modificationDate] as? Date else { return true }
        return Date().timeIntervalSince(modDate) > cacheExpirySeconds
    }

    // MARK: - Internal helpers for testing

    static func parseSummaryJSON(_ data: Data) -> String? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let extract = json["extract"] as? String,
              !extract.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return extract
    }

    static func parseOpenSearchTitles(_ data: Data) -> String? {
        guard let array = try? JSONSerialization.jsonObject(with: data) as? [Any],
              array.count >= 2,
              let titles = array[1] as? [String],
              let first = titles.first else { return nil }
        return first
    }
}
