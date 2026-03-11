import Foundation
import SwiftAudioStreamEx

/// Resolves a show into playable TrackItems for CarPlay (and Siri) playback.
/// Extracted from PlaylistServiceImpl to be reusable without UI dependencies.
@MainActor
final class CarPlayTrackResolver {
    private let showRepository: any ShowRepository
    private let archiveClient: any ArchiveMetadataClient
    private let recordingPreferenceDAO: RecordingPreferenceDAO
    private let downloadService: DownloadService?

    private static let formatPriority = ["VBR MP3", "MP3", "Ogg Vorbis"]

    nonisolated init(
        showRepository: some ShowRepository,
        archiveClient: some ArchiveMetadataClient,
        recordingPreferenceDAO: RecordingPreferenceDAO,
        downloadService: DownloadService? = nil
    ) {
        self.showRepository = showRepository
        self.archiveClient = archiveClient
        self.recordingPreferenceDAO = recordingPreferenceDAO
        self.downloadService = downloadService
    }

    struct ResolvedShow {
        let show: Show
        let recording: Recording
        let trackItems: [TrackItem]
    }

    /// Resolve a show to its playable tracks using preferred or best recording.
    func resolve(showId: String) async throws -> ResolvedShow? {
        guard let show = try showRepository.getShowById(showId) else { return nil }

        // Respect user's preferred recording, then fall back to best
        let recording: Recording? = {
            if let preferredId = try? recordingPreferenceDAO.fetchRecordingId(showId),
               let preferred = try? showRepository.getRecordingById(preferredId) {
                return preferred
            }
            if let bestId = show.bestRecordingId,
               let best = try? showRepository.getRecordingById(bestId) {
                return best
            }
            return try? showRepository.getBestRecordingForShow(showId)
        }()

        guard let recording else { return nil }
        let tracks = try await archiveClient.fetchTracks(recordingId: recording.identifier)
        let trackItems = buildTrackItems(tracks: tracks, show: show, recordingId: recording.identifier)
        guard !trackItems.isEmpty else { return nil }
        return ResolvedShow(show: show, recording: recording, trackItems: trackItems)
    }

    /// Resolve a specific recording to playable tracks.
    func resolve(showId: String, recordingId: String) async throws -> ResolvedShow? {
        guard let show = try showRepository.getShowById(showId),
              let recording = try showRepository.getRecordingById(recordingId) else { return nil }

        let tracks = try await archiveClient.fetchTracks(recordingId: recordingId)
        let trackItems = buildTrackItems(tracks: tracks, show: show, recordingId: recordingId)
        guard !trackItems.isEmpty else { return nil }
        return ResolvedShow(show: show, recording: recording, trackItems: trackItems)
    }

    // MARK: - Private

    private func buildTrackItems(tracks: [ArchiveTrack], show: Show, recordingId: String) -> [TrackItem] {
        // Select best available format
        let availableFormats = Set(tracks.map { $0.format })
        guard let format = Self.formatPriority.first(where: { fmt in
            availableFormats.contains(where: { $0.caseInsensitiveCompare(fmt) == .orderedSame })
        }) else { return [] }

        let filtered = tracks.filter { $0.format.caseInsensitiveCompare(format) == .orderedSame }

        let artworkURL: URL? = {
            if let coverUrl = show.coverImageUrl, let url = URL(string: coverUrl) {
                return url
            }
            return URL(string: "https://archive.org/services/img/\(recordingId)")
        }()

        let albumTitle = "\(show.venue.name) — \(show.date)"

        return filtered.enumerated().map { idx, track in
            let url: URL
            if let localURL = downloadService?.localURL(for: recordingId, trackFilename: track.name) {
                url = localURL
            } else {
                url = track.streamURL(recordingId: recordingId)
            }
            return TrackItem(
                url: url,
                title: track.title,
                artist: "Grateful Dead",
                albumTitle: albumTitle,
                artworkURL: artworkURL,
                duration: track.durationInterval,
                metadata: [
                    "showId": show.id,
                    "recordingId": recordingId,
                    "trackNumber": "\(idx + 1)",
                    "showDate": show.date,
                    "venue": show.venue.name,
                    "location": show.location.displayText
                ]
            )
        }
    }
}
