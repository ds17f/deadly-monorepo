/// Chooses which value is published as the system Now Playing artist.
public enum NowPlayingMetadataStyle: String, CaseIterable, Sendable {
    case showDetails
    case scrobbling

    public init(rawValueOrDefault value: String?) {
        self = NowPlayingMetadataStyle(rawValue: value ?? "") ?? .scrobbling
    }
}
