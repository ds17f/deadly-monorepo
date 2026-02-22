import Foundation

/// A single track in the playback queue.
public struct TrackItem: Sendable, Identifiable, Equatable {
    public let id: UUID
    public let url: URL
    public let title: String
    public let artist: String
    public let albumTitle: String?
    public let artworkURL: URL?
    public let duration: TimeInterval?
    public let metadata: [String: String]

    public init(
        url: URL,
        title: String,
        artist: String,
        albumTitle: String? = nil,
        artworkURL: URL? = nil,
        duration: TimeInterval? = nil,
        metadata: [String: String] = [:]
    ) {
        self.id = UUID()
        self.url = url
        self.title = title
        self.artist = artist
        self.albumTitle = albumTitle
        self.artworkURL = artworkURL
        self.duration = duration
        self.metadata = metadata
    }

    public static func == (lhs: TrackItem, rhs: TrackItem) -> Bool {
        lhs.id == rhs.id
    }
}
