import Foundation

protocol GeniusService: Sendable {
    func getLyrics(songTitle: String, artist: String) async -> String?
}

actor URLSessionGeniusService: GeniusService {
    private let accessToken: String
    private let session: URLSession
    private let cacheDir: URL
    private let cacheExpirySeconds: TimeInterval = 30 * 24 * 60 * 60

    init(accessToken: String) {
        self.accessToken = accessToken
        self.session = URLSession.shared
        let cachesDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        self.cacheDir = cachesDir.appendingPathComponent("genius", isDirectory: true)
        try? FileManager.default.createDirectory(at: self.cacheDir, withIntermediateDirectories: true)
    }

    func getLyrics(songTitle: String, artist: String) async -> String? {
        guard !accessToken.isEmpty, !songTitle.isEmpty else { return nil }

        let cacheKey = "\(songTitle)_\(artist)".hashValue.description
        let cacheFile = cacheDir.appendingPathComponent("\(cacheKey).txt")

        if let cached = try? String(contentsOf: cacheFile, encoding: .utf8),
           !isCacheExpired(at: cacheFile) {
            return cached.isEmpty ? nil : cached
        }

        let lyrics = await fetchLyrics(songTitle: songTitle, artist: artist)
        try? (lyrics ?? "").write(to: cacheFile, atomically: true, encoding: .utf8)
        return lyrics
    }

    private func fetchLyrics(songTitle: String, artist: String) async -> String? {
        guard let songUrl = await searchGeniusURL(songTitle: songTitle, artist: artist) else { return nil }
        return await scrapeLyrics(from: songUrl)
    }

    private func searchGeniusURL(songTitle: String, artist: String) async -> String? {
        let query = "\(songTitle) \(artist)"
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://api.genius.com/search?q=\(encoded)") else { return nil }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        guard let (data, _) = try? await session.data(for: request) else { return nil }
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let response = json["response"] as? [String: Any],
              let hits = response["hits"] as? [[String: Any]],
              let firstHit = hits.first,
              let result = firstHit["result"] as? [String: Any],
              let songUrl = result["url"] as? String else { return nil }

        return songUrl
    }

    private func scrapeLyrics(from urlString: String) async -> String? {
        guard let url = URL(string: urlString) else { return nil }
        guard let (data, _) = try? await session.data(from: url) else { return nil }
        guard let html = String(data: data, encoding: .utf8) else { return nil }

        let cleaned = Self.stripExcludeBlocks(html)
        var lyricsBuilder = ""
        var searchRange = cleaned.startIndex..<cleaned.endIndex

        let containerMarker = #"data-lyrics-container="true""#

        while let markerRange = cleaned.range(of: containerMarker, range: searchRange) {
            guard let divStart = cleaned.range(of: "<div", options: .backwards, range: cleaned.startIndex..<markerRange.lowerBound),
                  let tagEnd = cleaned.range(of: ">", range: markerRange.upperBound..<cleaned.endIndex) else {
                searchRange = markerRange.upperBound..<cleaned.endIndex
                continue
            }

            let contentStart = tagEnd.upperBound
            let content = Self.extractDivContent(from: cleaned, startIndex: contentStart)

            let text = content
                .replacingOccurrences(of: #"<br\s*/?>"#, with: "\n", options: .regularExpression)
                .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)

            if !text.isEmpty {
                if !lyricsBuilder.isEmpty { lyricsBuilder += "\n\n" }
                lyricsBuilder += text
            }

            searchRange = tagEnd.upperBound..<cleaned.endIndex
        }

        let lyrics = Self.decodeHTMLEntities(lyricsBuilder)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        return lyrics.isEmpty ? nil : lyrics
    }

    private func isCacheExpired(at url: URL) -> Bool {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let modDate = attrs[.modificationDate] as? Date else { return true }
        return Date().timeIntervalSince(modDate) > cacheExpirySeconds
    }

    // MARK: - Internal parsing helpers (internal for testability)

    /// Remove all `data-exclude-from-selection="true"` div blocks, tracking nesting depth.
    static func stripExcludeBlocks(_ html: String) -> String {
        let marker = #"data-exclude-from-selection="true""#
        var result = ""
        var searchFrom = html.startIndex

        while let markerRange = html.range(of: marker, range: searchFrom..<html.endIndex) {
            guard let divStart = html.range(of: "<div", options: .backwards, range: searchFrom..<markerRange.lowerBound) else {
                result += html[searchFrom...]
                return result
            }

            result += html[searchFrom..<divStart.lowerBound]

            guard let tagEnd = html.range(of: ">", range: markerRange.upperBound..<html.endIndex) else { break }

            var depth = 1
            var pos = tagEnd.upperBound

            while pos < html.endIndex, depth > 0 {
                let nextOpen = html.range(of: "<div", range: pos..<html.endIndex)
                let nextClose = html.range(of: "</div>", range: pos..<html.endIndex)

                guard let close = nextClose else { break }

                if let open = nextOpen, open.lowerBound < close.lowerBound {
                    depth += 1
                    pos = open.upperBound
                } else {
                    depth -= 1
                    pos = close.upperBound
                }
            }

            searchFrom = pos
        }

        result += html[searchFrom...]
        return result
    }

    /// Extract content inside a div starting after its opening `>`, tracking nesting.
    static func extractDivContent(from html: String, startIndex: String.Index) -> String {
        var depth = 1
        var pos = startIndex

        while pos < html.endIndex, depth > 0 {
            let nextOpen = html.range(of: "<div", range: pos..<html.endIndex)
            let nextClose = html.range(of: "</div>", range: pos..<html.endIndex)

            guard let close = nextClose else { break }

            if let open = nextOpen, open.lowerBound < close.lowerBound {
                depth += 1
                pos = open.upperBound
            } else {
                depth -= 1
                if depth == 0 {
                    return String(html[startIndex..<close.lowerBound])
                }
                pos = close.upperBound
            }
        }

        return String(html[startIndex...])
    }

    static func decodeHTMLEntities(_ text: String) -> String {
        text
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#x27;", with: "'")
            .replacingOccurrences(of: "&#39;", with: "'")
    }
}
