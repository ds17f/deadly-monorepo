import Foundation
import os.log
import SwiftAudioStreamEx

@Observable
@MainActor
final class PlaylistServiceImpl: PlaylistService {
    private(set) var currentShow: Show?
    private(set) var currentRecording: Recording?
    private(set) var tracks: [ArchiveTrack] = []
    private(set) var isLoadingTracks = false
    private(set) var trackLoadError: String?

    private(set) var reviews: [Review] = []
    private(set) var isLoadingReviews = false
    private(set) var reviewsError: String?

    private let showRepository: any ShowRepository
    private let archiveClient: any ArchiveMetadataClient
    private let recentShowsService: RecentShowsService
    private let recordingPreferenceDAO: RecordingPreferenceDAO
    private let downloadService: DownloadService?
    private let analyticsService: AnalyticsService?
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "Playlist")
    let streamPlayer: StreamPlayer

    /// Tracks the currently playing item for playback_end analytics.
    private var playbackStartInfo: (showId: String, recordingId: String, trackNumber: Int)?

    nonisolated init(
        showRepository: some ShowRepository,
        archiveClient: some ArchiveMetadataClient,
        recentShowsService: RecentShowsService,
        recordingPreferenceDAO: RecordingPreferenceDAO,
        streamPlayer: StreamPlayer,
        downloadService: DownloadService? = nil,
        analyticsService: AnalyticsService? = nil
    ) {
        self.showRepository = showRepository
        self.archiveClient = archiveClient
        self.recentShowsService = recentShowsService
        self.recordingPreferenceDAO = recordingPreferenceDAO
        self.streamPlayer = streamPlayer
        self.downloadService = downloadService
        self.analyticsService = analyticsService
    }

    // MARK: - Show Navigation

    var hasNextShow: Bool {
        guard let current = currentShow else { return false }
        return (try? showRepository.getNextShow(afterDate: current.date)) != nil
    }

    var hasPreviousShow: Bool {
        guard let current = currentShow else { return false }
        return (try? showRepository.getPreviousShow(beforeDate: current.date)) != nil
    }

    func navigateToNextShow() async -> Bool {
        guard let current = currentShow else { return false }
        guard let nextShow = try? showRepository.getNextShow(afterDate: current.date) else { return false }

        currentShow = nextShow
        reviews = []
        reviewsError = nil
        // Check for user's preferred recording before falling back to best
        if let preferredId = try? recordingPreferenceDAO.fetchRecordingId(nextShow.id),
           let preferred = try? showRepository.getRecordingById(preferredId) {
            currentRecording = preferred
        } else {
            currentRecording = try? showRepository.getBestRecordingForShow(nextShow.id)
        }

        if let recording = currentRecording {
            await fetchTracks(recordingId: recording.identifier)
        }
        prefetchAdjacentShowImages()
        return true
    }

    func navigateToPreviousShow() async -> Bool {
        guard let current = currentShow else { return false }
        guard let previousShow = try? showRepository.getPreviousShow(beforeDate: current.date) else { return false }

        currentShow = previousShow
        reviews = []
        reviewsError = nil
        // Check for user's preferred recording before falling back to best
        if let preferredId = try? recordingPreferenceDAO.fetchRecordingId(previousShow.id),
           let preferred = try? showRepository.getRecordingById(preferredId) {
            currentRecording = preferred
        } else {
            currentRecording = try? showRepository.getBestRecordingForShow(previousShow.id)
        }

        if let recording = currentRecording {
            await fetchTracks(recordingId: recording.identifier)
        }
        prefetchAdjacentShowImages()
        return true
    }

    // MARK: - PlaylistService

    func loadShow(_ showId: String) async {
        do {
            let show = try showRepository.getShowById(showId)
            currentShow = show
            // Check for user's preferred recording before falling back to best.
            if let preferredId = try? recordingPreferenceDAO.fetchRecordingId(showId),
               let preferred = try? showRepository.getRecordingById(preferredId) {
                currentRecording = preferred
            } else if let bestId = show?.bestRecordingId {
                currentRecording = try showRepository.getRecordingById(bestId)
            }
            if currentRecording == nil {
                currentRecording = try showRepository.getBestRecordingForShow(showId)
            }
            if let recording = currentRecording {
                await fetchTracks(recordingId: recording.identifier)
            }
            prefetchAdjacentShowImages()
        } catch {
            trackLoadError = error.localizedDescription
        }
    }

    func selectRecording(_ recording: Recording) async {
        currentRecording = recording
        reviews = []
        reviewsError = nil
        await fetchTracks(recordingId: recording.identifier)
    }

    func setRecordingAsDefault(_ recording: Recording) async {
        guard let showId = currentShow?.id else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try? recordingPreferenceDAO.upsert(RecordingPreferenceRecord(
            showId: showId, recordingId: recording.identifier, updatedAt: now
        ))
        await selectRecording(recording)
    }

    @discardableResult
    func playTrack(at index: Int) -> Bool {
        guard index >= 0, index < tracks.count,
              let recording = currentRecording else {
            logger.warning("playTrack(\(index)) failed: tracks=\(self.tracks.count), recording=\(self.currentRecording?.identifier ?? "nil")")
            return false
        }

        // Fire playback_end for the previous track, if any
        trackPlaybackEnd()

        // If the player already has this recording's queue loaded, skip directly to the index
        // instead of rebuilding the entire queue (avoids redundant network redirect resolution).
        if streamPlayer.currentTrack?.metadata["recordingId"] == recording.identifier {
            streamPlayer.skipTo(index: index)
            return true
        }
        // Propagate the show's ticket art so mini player and full player can display it.
        // Fall back to archive.org's auto-generated image for the recording.
        let artworkURL: URL? = {
            if let coverUrl = currentShow?.coverImageUrl, let url = URL(string: coverUrl) {
                return url
            }
            // Fallback: archive.org provides an image service for any item
            return URL(string: "https://archive.org/services/img/\(recording.identifier)")
        }()
        let albumTitle = currentShow.map { "\($0.venue.name) — \($0.date)" }
        let showId = currentShow?.id ?? ""
        let recordingId = recording.identifier
        let trackItems = tracks.enumerated().map { idx, track in
            // Check for local downloaded file first
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
                    "showId": showId,
                    "recordingId": recordingId,
                    "trackNumber": "\(idx + 1)",
                    "showDate": currentShow?.date ?? "",
                    "venue": currentShow?.venue.name ?? "",
                    "location": currentShow?.location.displayText ?? ""
                ]
            )
        }
        streamPlayer.loadQueue(trackItems, startingAt: index)

        playbackStartInfo = (showId: showId, recordingId: recordingId, trackNumber: index + 1)
        analyticsService?.track("playback_start", props: [
            "show_id": showId,
            "recording_id": recordingId,
            "track_number": index + 1,
        ])
        return true
    }

    /// Fires a `playback_end` event for the currently tracked playback, if any.
    private func trackPlaybackEnd() {
        guard let info = playbackStartInfo else { return }
        let progress = streamPlayer.progress
        let completionRate = progress.duration > 0 ? progress.currentTime / progress.duration : 0
        analyticsService?.track("playback_end", props: [
            "show_id": info.showId,
            "recording_id": info.recordingId,
            "track_number": info.trackNumber,
            "duration_ms": Int(progress.currentTime * 1000),
            "completion_rate": round(completionRate * 100) / 100,
        ])
        playbackStartInfo = nil
    }

    func recordRecentPlay() {
        guard let show = currentShow else { return }
        recentShowsService.recordShowPlay(showId: show.id)
    }

    func loadReviews() async {
        guard let recording = currentRecording else { return }
        isLoadingReviews = true
        reviewsError = nil
        defer { isLoadingReviews = false }
        do {
            reviews = try await archiveClient.fetchReviews(recordingId: recording.identifier)
        } catch {
            reviewsError = error.localizedDescription
            reviews = []
        }
    }

    // MARK: - Private

    private func fetchTracks(recordingId: String) async {
        isLoadingTracks = true
        trackLoadError = nil
        defer { isLoadingTracks = false }
        do {
            tracks = try await archiveClient.fetchTracks(recordingId: recordingId)
        } catch {
            logger.error("fetchTracks(\(recordingId)) failed: \(error.localizedDescription)")
            trackLoadError = error.localizedDescription
            tracks = []
            analyticsService?.track("error", props: [
                "source": "fetch_tracks",
                "message": error.localizedDescription,
            ])
        }
    }

    // MARK: - Image Prefetching

    private func prefetchAdjacentShowImages() {
        guard let current = currentShow else { return }

        var urlsToPrefetch: [URL] = []

        // Get next 2 shows
        if let next1 = try? showRepository.getNextShow(afterDate: current.date) {
            urlsToPrefetch.append(contentsOf: imageURLs(for: next1))
            if let next2 = try? showRepository.getNextShow(afterDate: next1.date) {
                urlsToPrefetch.append(contentsOf: imageURLs(for: next2))
            }
        }

        // Get previous 2 shows
        if let prev1 = try? showRepository.getPreviousShow(beforeDate: current.date) {
            urlsToPrefetch.append(contentsOf: imageURLs(for: prev1))
            if let prev2 = try? showRepository.getPreviousShow(beforeDate: prev1.date) {
                urlsToPrefetch.append(contentsOf: imageURLs(for: prev2))
            }
        }

        Task {
            await ImageCache.shared.prefetch(urls: urlsToPrefetch)
        }
    }

    private func imageURLs(for show: Show) -> [URL] {
        if let coverUrl = show.coverImageUrl, let url = URL(string: coverUrl) {
            return [url]
        }
        if let recordingId = show.bestRecordingId,
           let url = URL(string: "https://archive.org/services/img/\(recordingId)") {
            return [url]
        }
        return []
    }
}
