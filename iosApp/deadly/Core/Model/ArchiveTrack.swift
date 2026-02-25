import Foundation

/// A single track parsed from archive.org's metadata API for a recording.
struct ArchiveTrack: Sendable, Equatable, Identifiable {
    let name: String        // filename: "gd77-05-08eaton-d1t01.mp3"
    let title: String       // cleaned song title
    let trackNumber: Int
    let duration: String?   // raw seconds string from API: "423.12"
    let format: String      // "VBR MP3", "Flac", etc.
    let size: String?

    var id: String { name }

    /// Stream URL for this track on archive.org.
    func streamURL(recordingId: String) -> URL {
        let encoded = name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name
        return URL(string: "https://archive.org/download/\(recordingId)/\(encoded)")!
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
