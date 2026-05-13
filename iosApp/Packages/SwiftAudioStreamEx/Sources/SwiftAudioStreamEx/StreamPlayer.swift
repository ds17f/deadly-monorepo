import AVFoundation
import Foundation
import os

/// A streaming audio queue player with gapless playback, system integration, and SwiftUI observation.
///
/// `StreamPlayer` combines AudioStreaming's gapless AVAudioEngine pipeline with full
/// system integration (Now Playing, Remote Commands, audio session management) and
/// a modern `@Observable` API for SwiftUI.
@Observable
@MainActor
public final class StreamPlayer {
    private let logger = Logger(subsystem: "SwiftAudioStreamEx", category: "StreamPlayer")

    // MARK: - Observable state

    /// Current playback state.
    public private(set) var playbackState: PlaybackState = .idle

    /// The currently playing track, if any.
    public private(set) var currentTrack: TrackItem?

    /// Current playback progress (position, duration, fraction).
    public private(set) var progress: PlaybackProgress = .zero

    /// Current queue state (index, total, hasNext/hasPrevious).
    public private(set) var queueState: QueueState = .empty

    // MARK: - Configuration

    /// If playback position is past this threshold (seconds), "previous" restarts instead of going back.
    public var previousTrackThreshold: TimeInterval = 3.0

    /// Player output volume (0.0–1.0). Does not affect system volume.
    public var volume: Float {
        get { engine.volume }
        set { engine.volume = newValue }
    }

    /// Set a fallback image (UIImage) to display in Now Playing when artwork is unavailable
    /// or is detected as an auto-generated waveform spectrogram.
    public func setFallbackArtwork(_ image: Any?) {
        nowPlayingManager.setFallbackImage(image)
    }

    /// Attach an AVAudioNode to the player's audio engine processing chain.
    public func attachAudioNode(_ node: AVAudioNode) {
        engine.attachAudioNode(node)
    }

    /// Detach a previously attached AVAudioNode from the player's audio engine.
    public func detachAudioNode(_ node: AVAudioNode) {
        engine.detachAudioNode(node)
    }

    // MARK: - Internal components

    private let engine: AudioStreamEngine
    private let sessionManager: AudioSessionManager
    private let nowPlayingManager: NowPlayingManager
    private let remoteCommandManager: RemoteCommandManager

    /// The full track list.
    private var tracks: [TrackItem] = []

    // MARK: - Init

    public init() {
        self.engine = AudioStreamEngine()
        self.sessionManager = AudioSessionManager()
        self.nowPlayingManager = NowPlayingManager()
        self.remoteCommandManager = RemoteCommandManager()

        setupEngineCallbacks()
        setupSessionCallbacks()
        setupRemoteCommandCallbacks()
    }

    /// Internal init for testing with a mock engine protocol.
    init(engine: AudioStreamEngine) {
        self.engine = engine
        self.sessionManager = AudioSessionManager()
        self.nowPlayingManager = NowPlayingManager()
        self.remoteCommandManager = RemoteCommandManager()

        setupEngineCallbacks()
        setupSessionCallbacks()
        setupRemoteCommandCallbacks()
    }

    // MARK: - Queue loading

    /// Load a queue of tracks and optionally begin playback.
    public func loadQueue(_ tracks: [TrackItem], startingAt index: Int = 0, autoPlay: Bool = true) {
        guard !tracks.isEmpty else {
            logger.warning("[PB] loadQueue called with empty tracks array")
            return
        }
        let startIndex = min(max(index, 0), tracks.count - 1)
        let firstTitle = tracks[startIndex].title
        logger.notice("[PB] StreamPlayer.loadQueue count=\(tracks.count, privacy: .public) requestedIdx=\(index, privacy: .public) clampedIdx=\(startIndex, privacy: .public) autoPlay=\(autoPlay, privacy: .public) startTitle=\(firstTitle, privacy: .public)")

        // Stop current playback immediately so the UI feels responsive before redirect resolution.
        engine.stop()
        if autoPlay {
            playbackState = .loading
        }
        progress = .zero

        self.tracks = tracks
        self.currentTrack = tracks[startIndex]
        updateQueueState(index: startIndex)

        sessionManager.configure()
        remoteCommandManager.setup()

        let urls = tracks.map(\.url)
        engine.loadQueue(urls: urls, startingAt: startIndex)

        updateNowPlaying()
        nowPlayingManager.loadArtwork(from: currentTrack?.artworkURL)
        logger.notice("[PB] StreamPlayer.loadQueue submitted to engine count=\(tracks.count, privacy: .public) startIdx=\(startIndex, privacy: .public)")
    }

    // MARK: - Playback controls

    public func play() {
        guard !tracks.isEmpty else { return }
        engine.play()
    }

    public func pause() {
        engine.pause()
    }

    public func togglePlayPause() {
        if playbackState.isPlaying {
            pause()
        } else {
            play()
        }
    }

    public func next() {
        let idx = queueState.currentIndex
        let total = queueState.totalTracks
        guard queueState.hasNext else {
            logger.warning("[PB] StreamPlayer.next guarded: no next (idx=\(idx, privacy: .public)/\(total, privacy: .public))")
            return
        }
        logger.notice("[PB] StreamPlayer.next requested at idx=\(idx, privacy: .public)/\(total, privacy: .public)")
        let advanced = engine.advanceToNext()
        if advanced {
            syncTrackFromEngine()
        } else {
            logger.warning("[PB] StreamPlayer.next engine refused to advance")
        }
    }

    public func previous() {
        let idx = queueState.currentIndex
        let total = queueState.totalTracks

        // If past threshold, restart current track
        if progress.currentTime > previousTrackThreshold {
            logger.notice("[PB] StreamPlayer.previous restart-current at idx=\(idx, privacy: .public)/\(total, privacy: .public) (progress=\(self.progress.currentTime, format: .fixed(precision: 1), privacy: .public)s)")
            seek(to: 0)
            return
        }

        // If at first track, also restart
        guard queueState.hasPrevious else {
            logger.notice("[PB] StreamPlayer.previous restart-current at first track (idx=\(idx, privacy: .public)/\(total, privacy: .public))")
            seek(to: 0)
            return
        }

        logger.notice("[PB] StreamPlayer.previous requested at idx=\(idx, privacy: .public)/\(total, privacy: .public)")
        let rewound = engine.rewindToPrevious()
        if rewound {
            syncTrackFromEngine()
        } else {
            logger.warning("[PB] StreamPlayer.previous engine refused to rewind")
        }
    }

    public func skipTo(index: Int) {
        guard index >= 0, index < tracks.count else {
            logger.warning("[PB] StreamPlayer.skipTo invalid index=\(index, privacy: .public) total=\(self.tracks.count, privacy: .public)")
            return
        }
        logger.notice("[PB] StreamPlayer.skipTo target=\(index, privacy: .public) from=\(self.queueState.currentIndex, privacy: .public)")
        let skipped = engine.skipTo(index: index)
        if skipped {
            syncTrackFromEngine()
        } else {
            logger.warning("[PB] StreamPlayer.skipTo engine refused")
        }
    }

    public func seek(to time: TimeInterval) {
        engine.seek(to: time)
        // Immediately update progress for responsive UI
        progress = PlaybackProgress(currentTime: time, duration: progress.duration)
        nowPlayingManager.updateElapsedTime(time, isPlaying: playbackState.isPlaying)
    }

    public func seek(by offset: TimeInterval) {
        let target = max(0, progress.currentTime + offset)
        seek(to: target)
    }

    // MARK: - Queue modification

    public func append(_ track: TrackItem) {
        tracks.append(track)
        engine.appendTrack(url: track.url)
        updateQueueState(index: engine.currentIndex)
        remoteCommandManager.updateCommandState(
            hasNext: queueState.hasNext,
            hasPrevious: queueState.hasPrevious
        )
    }

    public func insertNext(_ track: TrackItem) {
        let insertIndex = engine.currentIndex + 1
        if insertIndex <= tracks.count {
            tracks.insert(track, at: insertIndex)
        } else {
            tracks.append(track)
        }
        engine.insertNext(url: track.url)
        updateQueueState(index: engine.currentIndex)
        remoteCommandManager.updateCommandState(
            hasNext: queueState.hasNext,
            hasPrevious: queueState.hasPrevious
        )
    }

    public func remove(at index: Int) {
        guard index >= 0, index < tracks.count, index != engine.currentIndex else { return }
        let removed = engine.removeTrack(at: index)
        if removed {
            tracks.remove(at: index)
            updateQueueState(index: engine.currentIndex)
            remoteCommandManager.updateCommandState(
                hasNext: queueState.hasNext,
                hasPrevious: queueState.hasPrevious
            )
        }
    }

    // MARK: - Lifecycle

    public func stop() {
        engine.stop()
        playbackState = .idle
        currentTrack = nil
        progress = .zero
        queueState = .empty
        tracks = []
        nowPlayingManager.clear()
        remoteCommandManager.teardown()
        sessionManager.deactivate()
        logger.info("Player stopped")
    }

    // MARK: - Private: engine callbacks

    private func setupEngineCallbacks() {
        engine.onStateChange = { [weak self] state in
            Task { @MainActor in
                guard let self else { return }
                self.playbackState = state
                self.updateNowPlaying()
            }
        }

        engine.onTrackComplete = { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                let previousTitle = self.currentTrack?.title ?? "(none)"
                self.syncTrackFromEngine()
                let newTitle = self.currentTrack?.title ?? "(none)"
                self.logger.notice("[PB] onTrackComplete prev=\(previousTitle, privacy: .public) → newIdx=\(self.engine.currentIndex, privacy: .public) new=\(newTitle, privacy: .public)")
            }
        }

        engine.onProgressUpdate = { [weak self] newProgress in
            Task { @MainActor in
                guard let self else { return }
                self.progress = newProgress
            }
        }

        engine.onError = { [weak self] error in
            Task { @MainActor in
                guard let self else { return }
                self.playbackState = .error(error)
                self.logger.error("[PB] onError case=\(String(describing: error), privacy: .public) desc=\(error.localizedDescription, privacy: .public)")
            }
        }
    }

    // MARK: - Private: session callbacks

    private func setupSessionCallbacks() {
        sessionManager.onInterruptionBegan = { [weak self] in
            self?.pause()
        }

        sessionManager.onInterruptionEnded = { [weak self] shouldResume in
            guard let self else { return }
            // Re-activate audio session before resuming playback
            self.sessionManager.reactivate()
            if shouldResume {
                self.play()
            }
        }

        sessionManager.onRouteChangedToSpeaker = { [weak self] in
            self?.pause()
        }
    }

    // MARK: - Private: remote command callbacks

    private func setupRemoteCommandCallbacks() {
        remoteCommandManager.onPlay = { [weak self] in self?.play() }
        remoteCommandManager.onPause = { [weak self] in self?.pause() }
        remoteCommandManager.onTogglePlayPause = { [weak self] in self?.togglePlayPause() }
        remoteCommandManager.onNext = { [weak self] in self?.next() }
        remoteCommandManager.onPrevious = { [weak self] in self?.previous() }
        remoteCommandManager.onSeek = { [weak self] time in self?.seek(to: time) }
        remoteCommandManager.onSkipForward = { [weak self] interval in self?.seek(by: interval) }
        remoteCommandManager.onSkipBackward = { [weak self] interval in self?.seek(by: -interval) }
    }

    // MARK: - Private: state sync

    private func syncTrackFromEngine() {
        let index = engine.currentIndex
        guard index >= 0, index < tracks.count else {
            logger.warning("[PB] syncTrackFromEngine out-of-bounds idx=\(index, privacy: .public) total=\(self.tracks.count, privacy: .public)")
            return
        }
        logger.notice("[PB] syncTrackFromEngine idx=\(index, privacy: .public) title=\(self.tracks[index].title, privacy: .public)")
        currentTrack = tracks[index]
        progress = .zero
        updateQueueState(index: index)
        updateNowPlaying()
        nowPlayingManager.loadArtwork(from: currentTrack?.artworkURL)
        remoteCommandManager.updateCommandState(
            hasNext: queueState.hasNext,
            hasPrevious: queueState.hasPrevious
        )
    }

    private func updateQueueState(index: Int) {
        queueState = QueueState(currentIndex: index, totalTracks: tracks.count)
    }

    private func updateNowPlaying() {
        nowPlayingManager.update(
            track: currentTrack,
            progress: progress,
            isPlaying: playbackState.isPlaying,
            queueIndex: queueState.currentIndex,
            queueCount: queueState.totalTracks
        )
    }
}
