import Foundation

/// A single track parsed from archive.org's metadata API for a recording.
struct ArchiveTrack: Sendable, Equatable, Identifiable, Codable {
    let name: String        // filename: "gd77-05-08eaton-d1t01.mp3"
    let title: String       // cleaned song title
    let trackNumber: Int
    let duration: String?   // raw seconds string from API: "423.12"
    let format: String      // "VBR MP3", "Flac", etc.
    let size: String?
    /// Storage servers for the item (`workable_servers`, else `d1`/`d2`/`server`).
    /// Optional so cache entries written before this field existed still decode.
    var fallbackServers: [String]? = nil
    /// Item directory on the storage servers, e.g. "/33/items/<identifier>".
    var itemDir: String? = nil

    var id: String { name }

    /// Stream URL for this track on archive.org.
    func streamURL(recordingId: String) -> URL {
        let encoded = name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name
        return URL(string: "https://archive.org/download/\(recordingId)/\(encoded)")!
    }

    /// URLs of this file on the item's storage servers, in the order Archive lists
    /// them. Used by the player as a fallback when the canonical redirect (and the
    /// `dn*` CDN layer in front of the servers) fails. Empty when the metadata
    /// was cached before these fields were stored.
    func fallbackStreamURLs() -> [URL] {
        guard let servers = fallbackServers, let dir = itemDir, !dir.isEmpty else { return [] }
        let encodedName = name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name
        let encodedDir = dir.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? dir
        let normalizedDir = encodedDir.hasPrefix("/") ? encodedDir : "/" + encodedDir
        return servers.compactMap { server in
            URL(string: "https://\(server)\(normalizedDir)/\(encodedName)")
        }
    }

    /// Human-readable duration string, e.g. "7:03". Nil if duration is missing or unparseable.
    var displayDuration: String? {
        guard let duration else { return nil }

        // archive.org returns duration in multiple formats:
        // 1. "MM:SS" format (e.g., "06:21")
        // 2. Raw seconds as string (e.g., "381.5")
        if duration.contains(":") {
            // Already in MM:SS format, return as-is
            return duration
        } else if let seconds = Double(duration), seconds >= 0 {
            // Convert raw seconds to MM:SS
            let total = Int(seconds)
            let mins = total / 60
            let secs = total % 60
            return String(format: "%d:%02d", mins, secs)
        }
        return nil
    }

    /// Duration as a TimeInterval for use with AVPlayer. Nil if duration is missing.
    var durationInterval: TimeInterval? {
        guard let duration else { return nil }

        // Handle both "MM:SS" and raw seconds formats
        if duration.contains(":") {
            let parts = duration.split(separator: ":")
            if parts.count == 2,
               let mins = Double(parts[0]),
               let secs = Double(parts[1]) {
                return mins * 60 + secs
            }
            return nil
        }
        return Double(duration)
    }
}
