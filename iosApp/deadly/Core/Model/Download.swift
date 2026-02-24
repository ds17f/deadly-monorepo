import Foundation

/// Identifier scheme for downloads: `{showId}|{recordingId}|{trackFilename}`
struct DownloadIdentifier: Codable, Sendable, Equatable, Hashable {
    let showId: String
    let recordingId: String
    let trackFilename: String

    /// Create from a formatted download ID string.
    init?(from string: String) {
        let parts = string.split(separator: "|", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }
        showId = String(parts[0])
        recordingId = String(parts[1])
        trackFilename = String(parts[2])
    }

    init(showId: String, recordingId: String, trackFilename: String) {
        self.showId = showId
        self.recordingId = recordingId
        self.trackFilename = trackFilename
    }

    /// Format as `{showId}|{recordingId}|{trackFilename}`.
    var formatted: String {
        "\(showId)|\(recordingId)|\(trackFilename)"
    }

    /// Group key for the show: `{showId}|{recordingId}`.
    var groupKey: String {
        "\(showId)|\(recordingId)"
    }

    /// Remote archive.org URL for this track.
    var remoteURL: URL {
        let encoded = trackFilename.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? trackFilename
        return URL(string: "https://archive.org/download/\(recordingId)/\(encoded)")!
    }
}

/// State of an individual track download.
enum TrackDownloadState: String, Codable, Sendable, Equatable {
    case pending
    case downloading
    case paused
    case completed
    case failed
}

/// Per-track download state.
struct TrackDownload: Codable, Sendable, Equatable, Identifiable {
    let identifier: DownloadIdentifier
    var state: TrackDownloadState
    var bytesDownloaded: Int64
    var totalBytes: Int64
    var resumeData: Data?
    var errorMessage: String?

    var id: String { identifier.formatted }

    var progress: Float {
        guard totalBytes > 0 else { return 0 }
        return Float(bytesDownloaded) / Float(totalBytes)
    }

    init(
        identifier: DownloadIdentifier,
        state: TrackDownloadState = .pending,
        bytesDownloaded: Int64 = 0,
        totalBytes: Int64 = 0,
        resumeData: Data? = nil,
        errorMessage: String? = nil
    ) {
        self.identifier = identifier
        self.state = state
        self.bytesDownloaded = bytesDownloaded
        self.totalBytes = totalBytes
        self.resumeData = resumeData
        self.errorMessage = errorMessage
    }
}

/// Format selection configuration.
enum DownloadFormatConfig {
    /// Priority order for selecting audio format. First match wins.
    static let priority = ["VBR MP3", "MP3", "Ogg Vorbis"]

    /// Select the best available format from a list of track formats.
    static func selectFormat(from availableFormats: Set<String>) -> String? {
        priority.first { format in
            availableFormats.contains { $0.localizedCaseInsensitiveCompare(format) == .orderedSame }
        }
    }

    /// Filter tracks by format.
    static func filterTracks(_ tracks: [ArchiveTrack], by format: String) -> [ArchiveTrack] {
        tracks.filter { $0.format.localizedCaseInsensitiveCompare(format) == .orderedSame }
    }
}
