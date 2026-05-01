import Foundation
import SwiftAudioStreamEx
#if canImport(UIKit)
import UIKit
#endif

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
    let streamPlayer: StreamPlayer

    /// Tracks the currently playing item for playback_end analytics.
    private var playbackStartInfo: (showId: String, recordingId: String, trackNumber: Int)?
    private var trackObservationTask: Task<Void, Never>?

    /// Pending track-change emit, gated by a 1s dwell timer so transient
    /// track flips during queue load don't fire phantom playback_start events.
    private var pendingPlaybackInfo: (showId: String, recordingId: String, trackNumber: Int)?
    private var pendingPlaybackTask: Task<Void, Never>?

    /// Source for the next playback_start emit. Set by `playTrack(at:source:)`,
    /// consumed (and reset to "auto_advance") on each commit so subsequent
    /// queue advances are correctly attributed to auto-advance.
    private var nextPlaybackSource: String = "auto_advance"

    /// Rolling snapshot of the active track's progress, refreshed on every
    /// progress tick. Read at track-change time so `listened_ms` reflects the
    /// *ending* track, not the new one (which has already advanced by the
    /// time the change observer fires).
    private var lastKnownProgress: (currentTime: TimeInterval, duration: TimeInterval) = (0, 0)

    /// Explicit reason for the next `playback_end` emit (skipped_next, etc).
    /// When nil, the commit path falls back to a heuristic based on snapshot.
    private var nextPlaybackEndReason: String?

    private var progressObservationTask: Task<Void, Never>?
    private var playbackStateObservationTask: Task<Void, Never>?
    private var willResignActiveObserver: NSObjectProtocol?

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

        MainActor.assumeIsolated {
            self.startProgressObservation()
            self.startPlaybackStateObservation()
            self.willResignActiveObserver = NotificationCenter.default.addObserver(
                forName: UIApplication.willResignActiveNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                MainActor.assumeIsolated {
                    self?.endActivePlayback(reason: "app_backgrounded")
                }
            }
        }
    }

    // PlaylistServiceImpl lives for the full app lifetime, so no deinit cleanup
    // is needed. Observation tasks self-terminate via `[weak self]` capture.

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

    func playTrack(at index: Int, source: String) {
        guard index >= 0, index < tracks.count,
              let recording = currentRecording else { return }

        nextPlaybackSource = source

        // If the player already has this recording's queue loaded, skip directly to the index
        // instead of rebuilding the entire queue (avoids redundant network redirect resolution).
        // The observer will emit playback_start/_end via the debounced commit path.
        if streamPlayer.currentTrack?.metadata["recordingId"] == recording.identifier {
            streamPlayer.skipTo(index: index)
            return
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
        startTrackObservation()
        // The observer will pick up the eventual settled track and fire
        // playback_start once it's been current for the dwell window.
    }

    private func startTrackObservation() {
        trackObservationTask?.cancel()
        trackObservationTask = Task { [weak self] in
            guard let self else { return }
            // Stage the *current* track immediately so the first track of a
            // freshly-loaded queue gets a playback_start. Without this, the
            // observer only ever fires on transitions and the initial track
            // is silently dropped.
            if let initialTrack = self.streamPlayer.currentTrack,
               let showId = initialTrack.metadata["showId"],
               let recordingId = initialTrack.metadata["recordingId"],
               let trackNumStr = initialTrack.metadata["trackNumber"],
               let trackNum = Int(trackNumStr) {
                self.schedulePlaybackChange(
                    to: (showId: showId, recordingId: recordingId, trackNumber: trackNum)
                )
            }
            var lastTrackId: UUID? = self.streamPlayer.currentTrack?.id
            while !Task.isCancelled {
                await withCheckedContinuation { continuation in
                    withObservationTracking {
                        _ = self.streamPlayer.currentTrack
                    } onChange: {
                        continuation.resume()
                    }
                }
                guard !Task.isCancelled else { break }
                let newTrack = self.streamPlayer.currentTrack
                if let newTrack, newTrack.id != lastTrackId {
                    lastTrackId = newTrack.id
                    if let showId = newTrack.metadata["showId"],
                       let recordingId = newTrack.metadata["recordingId"],
                       let trackNumStr = newTrack.metadata["trackNumber"],
                       let trackNum = Int(trackNumStr) {
                        self.schedulePlaybackChange(
                            to: (showId: showId, recordingId: recordingId, trackNumber: trackNum)
                        )
                    }
                }
            }
        }
    }

    /// Stage a track change behind a 1s dwell window. If another change comes in
    /// before the timer fires (queue-load churn, rapid skips), the prior pending
    /// change is dropped — only the settled track produces analytics events.
    private func schedulePlaybackChange(to info: (showId: String, recordingId: String, trackNumber: Int)) {
        pendingPlaybackTask?.cancel()
        pendingPlaybackInfo = info
        pendingPlaybackTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                self?.commitPendingPlayback()
            }
        }
    }

    private func commitPendingPlayback() {
        guard let pending = pendingPlaybackInfo else { return }
        pendingPlaybackInfo = nil
        let source = nextPlaybackSource
        nextPlaybackSource = "auto_advance"
        // Derive reason from explicit signal, else heuristic on the snapshot.
        let reason = nextPlaybackEndReason ?? defaultEndReasonFromSnapshot()
        nextPlaybackEndReason = nil
        // End the previously committed track (if any), then start the new one.
        trackPlaybackEnd(reason: reason)
        playbackStartInfo = pending
        analyticsService?.track("playback_start", props: [
            "show_id": pending.showId,
            "recording_id": pending.recordingId,
            "track_index": pending.trackNumber,
            "source": source,
        ])
    }

    /// Heuristic: if the snapshot was within ~2s of the track's end, the
    /// track finished naturally. Otherwise leave reason unset.
    private func defaultEndReasonFromSnapshot() -> String? {
        let (current, total) = lastKnownProgress
        guard total > 0 else { return nil }
        return current >= total - 2 ? "completed" : nil
    }

    /// Fires a `playback_end` event for the currently tracked playback, if any.
    private func trackPlaybackEnd(reason: String?) {
        guard let info = playbackStartInfo else { return }
        let snap = lastKnownProgress
        var props: [String: Any] = [
            "show_id": info.showId,
            "recording_id": info.recordingId,
            "track_index": info.trackNumber,
            "listened_ms": Int(snap.currentTime * 1000),
            "duration_ms": Int(snap.duration * 1000),
        ]
        if let reason {
            props["reason"] = reason
        }
        analyticsService?.track("playback_end", props: props)
        playbackStartInfo = nil
        lastKnownProgress = (0, 0)
    }

    /// Public entry point for callers (skip handlers, lifecycle observer)
    /// to attribute the next emitted `playback_end` to a specific cause.
    func noteUserSkip(forward: Bool) {
        nextPlaybackEndReason = forward ? "skipped_next" : "skipped_prev"
    }

    /// End the active track immediately with an explicit reason. Used for
    /// `app_backgrounded` and `network_error` where there's no track-change
    /// observer event to drive the commit path.
    private func endActivePlayback(reason: String) {
        guard playbackStartInfo != nil else { return }
        trackPlaybackEnd(reason: reason)
    }

    private func startProgressObservation() {
        progressObservationTask?.cancel()
        progressObservationTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                await withCheckedContinuation { continuation in
                    withObservationTracking {
                        _ = self.streamPlayer.progress
                    } onChange: {
                        continuation.resume()
                    }
                }
                guard !Task.isCancelled else { break }
                let p = self.streamPlayer.progress
                // Refresh only when the player is on the same track currently
                // attributed to playbackStartInfo. During the dwell window
                // after a transition, currentTrack has already advanced but
                // playbackStartInfo still points to the prior track — writing
                // here would overwrite the prior track's snapshot with the
                // new track's early progress (~1s) and corrupt listened_ms.
                if let info = self.playbackStartInfo,
                   let track = self.streamPlayer.currentTrack,
                   track.metadata["recordingId"] == info.recordingId,
                   let trackNumStr = track.metadata["trackNumber"],
                   Int(trackNumStr) == info.trackNumber,
                   p.duration > 0 {
                    self.lastKnownProgress = (p.currentTime, p.duration)
                }
            }
        }
    }

    private func startPlaybackStateObservation() {
        playbackStateObservationTask?.cancel()
        playbackStateObservationTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                await withCheckedContinuation { continuation in
                    withObservationTracking {
                        _ = self.streamPlayer.playbackState
                    } onChange: {
                        continuation.resume()
                    }
                }
                guard !Task.isCancelled else { break }
                if case .error = self.streamPlayer.playbackState {
                    self.endActivePlayback(reason: "network_error")
                }
            }
        }
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
