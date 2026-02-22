import Foundation
import SwiftAudioStreamEx

@Observable
@MainActor
final class PlaylistServiceImpl: PlaylistService {
    private(set) var currentShow: Show?
    private(set) var currentRecording: Recording?
    private(set) var tracks: [ArchiveTrack] = []
    private(set) var isLoadingTracks = false
    private(set) var trackLoadError: String?

    private let showRepository: any ShowRepository
    private let archiveClient: any ArchiveMetadataClient
    private let recentShowDAO: RecentShowDAO
    let streamPlayer: StreamPlayer

    nonisolated init(
        showRepository: some ShowRepository,
        archiveClient: some ArchiveMetadataClient,
        recentShowDAO: RecentShowDAO,
        streamPlayer: StreamPlayer
    ) {
        self.showRepository = showRepository
        self.archiveClient = archiveClient
        self.recentShowDAO = recentShowDAO
        self.streamPlayer = streamPlayer
    }

    // MARK: - PlaylistService

    func loadShow(_ showId: String) async {
        do {
            let show = try showRepository.getShowById(showId)
            currentShow = show
            // Use the precomputed bestRecordingId from the data pipeline first.
            // Fall back to highest-rating DB query if it's missing or the recording isn't found.
            if let bestId = show?.bestRecordingId {
                currentRecording = try showRepository.getRecordingById(bestId)
            }
            if currentRecording == nil {
                currentRecording = try showRepository.getBestRecordingForShow(showId)
            }
            if let recording = currentRecording {
                await fetchTracks(recordingId: recording.identifier)
            }
        } catch {
            trackLoadError = error.localizedDescription
        }
    }

    func selectRecording(_ recording: Recording) async {
        currentRecording = recording
        await fetchTracks(recordingId: recording.identifier)
    }

    func playTrack(at index: Int) {
        guard index >= 0, index < tracks.count,
              let recording = currentRecording else { return }
        // Propagate the show's ticket art so mini player and full player can display it.
        let artworkURL = currentShow?.coverImageUrl.flatMap { URL(string: $0) }
        let albumTitle = currentShow.map { "\($0.venue.name) — \($0.date)" }
        let trackItems = tracks.map { track in
            TrackItem(
                url: track.streamURL(recordingId: recording.identifier),
                title: track.title,
                artist: "Grateful Dead",
                albumTitle: albumTitle,
                artworkURL: artworkURL,
                duration: track.durationInterval
            )
        }
        streamPlayer.loadQueue(trackItems, startingAt: index)
    }

    func recordRecentPlay() {
        guard let show = currentShow else { return }
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        try? recentShowDAO.upsert(showId: show.id, timestamp: timestamp)
    }

    // MARK: - Private

    private func fetchTracks(recordingId: String) async {
        isLoadingTracks = true
        trackLoadError = nil
        defer { isLoadingTracks = false }
        do {
            tracks = try await archiveClient.fetchTracks(recordingId: recordingId)
        } catch {
            trackLoadError = error.localizedDescription
            tracks = []
        }
    }
}
