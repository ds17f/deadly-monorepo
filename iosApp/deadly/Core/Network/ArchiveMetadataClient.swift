import Foundation

protocol ArchiveMetadataClient: Sendable {
    func fetchTracks(recordingId: String) async throws -> [ArchiveTrack]
    func clearCache(recordingId: String)
}

// MARK: - URLSession implementation with disk caching

struct URLSessionArchiveMetadataClient: ArchiveMetadataClient {
    private let session: URLSession
    private let cacheExpiryHours: Int = 24

    init(session: URLSession = .shared) {
        self.session = session
    }

    private var cacheDirectory: URL {
        let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let archiveDir = cacheDir.appendingPathComponent("archive", isDirectory: true)
        try? FileManager.default.createDirectory(at: archiveDir, withIntermediateDirectories: true)
        return archiveDir
    }

    private func cacheFile(for recordingId: String) -> URL {
        cacheDirectory.appendingPathComponent("\(recordingId).tracks.json")
    }

    private func isCacheExpired(_ modificationDate: Date) -> Bool {
        let expiryInterval = TimeInterval(cacheExpiryHours * 60 * 60)
        return Date().timeIntervalSince(modificationDate) > expiryInterval
    }

    func fetchTracks(recordingId: String) async throws -> [ArchiveTrack] {
        let cache = cacheFile(for: recordingId)

        // Check fresh cache first
        if let attrs = try? FileManager.default.attributesOfItem(atPath: cache.path),
           let modDate = attrs[.modificationDate] as? Date,
           !isCacheExpired(modDate),
           let data = try? Data(contentsOf: cache),
           let tracks = try? JSONDecoder().decode([ArchiveTrack].self, from: data) {
            return tracks
        }

        // Cache miss or expired - try network
        do {
            guard let url = URL(string: "https://archive.org/metadata/\(recordingId)") else {
                throw URLError(.badURL)
            }
            let (data, _) = try await session.data(from: url)
            let tracks = Self.parseTracks(from: data)

            // Cache the result
            if let encoded = try? JSONEncoder().encode(tracks) {
                try? encoded.write(to: cache)
            }

            return tracks
        } catch {
            // Fallback: serve expired cache if available
            if let data = try? Data(contentsOf: cache),
               let tracks = try? JSONDecoder().decode([ArchiveTrack].self, from: data) {
                return tracks
            }
            throw error
        }
    }

    func clearCache(recordingId: String) {
        try? FileManager.default.removeItem(at: cacheFile(for: recordingId))
    }

    // MARK: - Parsing (internal for testing)

    static func parseTracks(from data: Data) -> [ArchiveTrack] {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let files = json["files"] as? [[String: Any]] else {
            return []
        }

        let audioExtensions: Set<String> = ["mp3"]

        let tracks = files.compactMap { file -> ArchiveTrack? in
            guard let name = file["name"] as? String else { return nil }
            let ext = (name as NSString).pathExtension.lowercased()
            guard audioExtensions.contains(ext) else { return nil }

            let format = stringField(file["format"]) ?? ext.uppercased()
            let duration = stringField(file["length"])
            let size = stringField(file["size"])
            let trackStr = stringField(file["track"])
            let trackNumber = trackStr.flatMap { Int($0) } ?? 0
            let rawTitle = stringField(file["title"])
            let title = (rawTitle?.isEmpty == false ? rawTitle! : nil)
                ?? Self.extractTitleFromFilename(name)

            return ArchiveTrack(
                name: name,
                title: title,
                trackNumber: trackNumber,
                duration: duration,
                format: format,
                size: size
            )
        }

        return tracks.sorted { a, b in
            if a.trackNumber != b.trackNumber { return a.trackNumber < b.trackNumber }
            return a.name < b.name
        }
    }

    /// archive.org JSON is polymorphic — fields can be a string or an array of strings.
    private static func stringField(_ value: Any?) -> String? {
        if let s = value as? String { return s.isEmpty ? nil : s }
        if let arr = value as? [String] { return arr.first }
        return nil
    }

    // MARK: - Title extraction

    /// Derive a human-readable title from an archive.org filename when the metadata
    /// title field is absent. Ported from Android's ArchiveMapper logic.
    static func extractTitleFromFilename(_ filename: String) -> String {
        var stem = (filename as NSString).deletingPathExtension

        // Strip "grateful_dead" or "gd" prefix
        if stem.lowercased().hasPrefix("grateful_dead") {
            stem = String(stem.dropFirst("grateful_dead".count))
        } else if stem.lowercased().hasPrefix("gd") {
            stem = String(stem.dropFirst(2))
        }

        // Strip leading separator
        if stem.hasPrefix("-") || stem.hasPrefix("_") { stem = String(stem.dropFirst()) }

        // Strip date prefix: "1977-05-08" or "77-05-08"
        let datePattern = #"^\d{2,4}[-_]\d{2}[-_]\d{2}"#
        if let range = stem.range(of: datePattern, options: .regularExpression) {
            stem = String(stem[range.upperBound])
            if stem.hasPrefix("-") || stem.hasPrefix("_") { stem = String(stem.dropFirst()) }
        }

        // Strip source identifier + disc/track pattern: e.g. "eaton-d1t01", "sbd.d2t03", "d1t01"
        let discTrackPattern = #"^[a-z0-9]*[-_.]?d\d+t\d+[-_]?"#
        if let range = stem.range(of: discTrackPattern, options: [.regularExpression, .caseInsensitive]) {
            let candidate = String(stem[range.upperBound])
            // Only strip if there's something left, or use the original if nothing remains
            if !candidate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                stem = candidate
            }
        }

        // Replace underscores and hyphens with spaces, clean up extra whitespace
        stem = stem.replacingOccurrences(of: "_", with: " ")
                   .replacingOccurrences(of: "-", with: " ")
                   .trimmingCharacters(in: .whitespacesAndNewlines)

        // Collapse multiple spaces
        while stem.contains("  ") {
            stem = stem.replacingOccurrences(of: "  ", with: " ")
        }

        return stem.isEmpty ? filename : stem.capitalized
    }
}
