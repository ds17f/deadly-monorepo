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

    /// Seek target to apply the first time the user starts playback after
    /// `loadQueue`. Used by playback restoration so we can load a queue without
    /// auto-playing, then land the saved position once the user actually presses
    /// play. `seek(to:)` is only honored while the engine is actively playing —
    /// so on first play we briefly mute, start, wait for `.playing`, seek, and
    /// unmute. Cleared at the start of every `loadQueue` and after first play.
    public var pendingSeekOnFirstPlay: TimeInterval?

    /// True while the first-play seek dance is running: from the moment `play()`
    /// is called with a pending seek until the engine has reached `.playing` and
    /// the seek has landed. Surface to the UI as a spinner so users see something
    /// happen during the ~600–900ms of mute + buffer + seek.
    public private(set) var isPreparing: Bool = false

    /// True while the engine is automatically retrying a transient network
    /// failure. The UI can surface this as a "Network trouble — retrying"
    /// banner so the user understands what's happening between the silent
    /// mute and the eventual recovery (or user-visible error).
    public private(set) var isRetrying: Bool = false

    // MARK: - Configuration

    /// If playback position is past this threshold (seconds), "previous" restarts instead of going back.
    public var previousTrackThreshold: TimeInterval = 3.0

    /// Called when playback auto-advances to the next track (track ended naturally).
    /// Not called for explicit `next()`, `previous()`, or `skipTo(index:)`. Connect
    /// uses this so the active device broadcasts the new track index to the session.
    public var onTrackComplete: (() -> Void)?

    /// Called when the *last* track of the queue reaches its natural end of file —
    /// the positive "show completed" signal (ADR-0010 Chunk 1). Fires only on
    /// `.eof && isLastTrack`, so a user stop/pause, an error, or a mid-queue
    /// transition never trigger it. The package stays show-agnostic; the app
    /// layer translates this into `onShowCompleted(showId)`.
    public var onQueueComplete: (() -> Void)?

    /// Called when play INTENT changes — true when the player wants to be playing
    /// (`.playing`/`.buffering`), false when it has stopped (`.paused`/`.ended`/
    /// `.idle`). Distinct from `playbackState.isPlaying`, which is false during
    /// buffering stalls; intent stays true through a rebuffer and flips only on a
    /// real pause/resume. Fires for EVERY pause source — including the lock screen,
    /// Bluetooth/headset keys, and audio-session interruptions that drive the
    /// engine directly — so Connect can forward those to the session. Transitional
    /// states (`.loading`/`.error`) leave intent unchanged.
    public var onPlayIntentChange: ((Bool) -> Void)?

    /// Backing value for `onPlayIntentChange`; nil until the first definite state.
    private var playIntent: Bool?

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

    /// The full loaded queue, read-only. Lets Connect resolve display metadata
    /// (title/duration/show info, via `TrackItem.metadata`) for any index — e.g.
    /// a remote-controlled session's current track — from the queue the player
    /// actually loaded, rather than trusting server-supplied state.
    public var loadedTracks: [TrackItem] { tracks }

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
        // A new queue invalidates any pending first-play seek from a prior load.
        if pendingSeekOnFirstPlay != nil {
            logger.notice("[PB] loadQueue clearing pendingSeekOnFirstPlay")
            pendingSeekOnFirstPlay = nil
        }

        self.tracks = tracks
        self.currentTrack = tracks[startIndex]
        updateQueueState(index: startIndex)

        sessionManager.configure()
        remoteCommandManager.setup()

        let urls = tracks.map(\.url)
        engine.loadQueue(urls: urls, startingAt: startIndex, autoPlay: autoPlay)

        updateNowPlaying()
        nowPlayingManager.loadArtwork(from: currentTrack?.artworkURL)
        logger.notice("[PB] StreamPlayer.loadQueue submitted to engine count=\(tracks.count, privacy: .public) startIdx=\(startIndex, privacy: .public)")
    }

    // MARK: - Playback controls

    public func play() {
        guard !tracks.isEmpty else { return }
        // Ignore taps while the first-play seek dance is running; it will land
        // on its own and the UI shows a spinner in the meantime.
        if isPreparing {
            logger.notice("[PB] play ignored: isPreparing")
            return
        }
        if let seekTarget = pendingSeekOnFirstPlay {
            pendingSeekOnFirstPlay = nil
            playWithPendingSeek(seekTarget)
        } else {
            engine.startCurrent()
        }
    }

    /// Start playback and apply a pending seek as soon as the engine reaches
    /// `.playing`. Mutes output during the brief pre-seek window so the user
    /// only hears audio from the saved position.
    private func playWithPendingSeek(_ target: TimeInterval) {
        logger.notice("[PB] play with pendingSeek=\(target, format: .fixed(precision: 1), privacy: .public)s")
        let savedVolume = volume
        isPreparing = true
        volume = 0
        // Clear stale `.error` so the wait Task doesn't see the previous
        // failure's state and bail out immediately. The engine is about to
        // emit fresh state changes; treat them as authoritative.
        if case .error = playbackState {
            playbackState = .loading
        }
        engine.startCurrent()
        Task { @MainActor [weak self] in
            guard let self else { return }
            let overallDeadline = Date.now.addingTimeInterval(30)

            // Phase 1: wait for the engine to first reach `.playing` so seek
            // can fire its HTTP range request on an active connection.
            // Abort early on `.error` — engine surrendered, no point waiting
            // out the deadline while the user hears nothing.
            while self.playbackState != .playing && Date.now < overallDeadline {
                if case .error = self.playbackState { break }
                try? await Task.sleep(for: .milliseconds(50))
            }
            guard self.playbackState == .playing else {
                // Never reached the initial `.playing` (engine errored or stalled).
                // Don't abandon the intended start — re-arm it so the eventual
                // recovery play() (Connect re-broadcast, user tap, interruption
                // end) lands on the right spot instead of restarting from 0. This
                // is the network-error-on-transfer / -restore case: the position
                // lived only here, and the engine's retry snapshot is 0 because
                // the track never played, so without this it's lost.
                if case .error = self.playbackState {
                    self.logger.warning("[PB] play+seek: engine errored before .playing — re-arming pendingSeekOnFirstPlay=\(target, format: .fixed(precision: 1), privacy: .public)s")
                } else {
                    self.logger.warning("[PB] play+seek: timed out before .playing — re-arming pendingSeekOnFirstPlay=\(target, format: .fixed(precision: 1), privacy: .public)s")
                }
                self.pendingSeekOnFirstPlay = target
                self.volume = savedVolume
                self.isPreparing = false
                return
            }

            // Phase 2: issue the seek. AudioStreaming typically transitions
            // `.playing → .bufferring` while fetching the requested byte range.
            self.seek(to: target)

            // Phase 3: wait for the post-seek rebuffer to complete. We watch
            // for the next `.playing` after the state leaves `.playing`. If
            // the engine never left `.playing` (cached/no rebuffer), the
            // initial check still passes and we proceed.
            try? await Task.sleep(for: .milliseconds(100))
            while self.playbackState != .playing && Date.now < overallDeadline {
                try? await Task.sleep(for: .milliseconds(50))
            }
            if self.playbackState != .playing {
                self.logger.warning("[PB] play+seek: timed out waiting for post-seek .playing")
            } else {
                self.logger.notice("[PB] play+seek: settled at target — unmuting")
            }

            self.volume = savedVolume
            self.isPreparing = false
        }
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

    public func skipTo(index: Int, autoplay: Bool = true) {
        guard index >= 0, index < tracks.count else {
            logger.warning("[PB] StreamPlayer.skipTo invalid index=\(index, privacy: .public) total=\(self.tracks.count, privacy: .public)")
            return
        }
        logger.notice("[PB] StreamPlayer.skipTo target=\(index, privacy: .public) from=\(self.queueState.currentIndex, privacy: .public) autoplay=\(autoplay, privacy: .public)")
        let skipped = engine.skipTo(index: index, autoplay: autoplay)
        if skipped {
            syncTrackFromEngine()
        } else {
            logger.warning("[PB] StreamPlayer.skipTo engine refused")
        }
    }

    public func seek(to time: TimeInterval) {
        // If the user manually seeks before first play, redirect the pending
        // first-play seek to their chosen position rather than the restored one.
        if pendingSeekOnFirstPlay != nil {
            logger.notice("[PB] seek before first play — updating pendingSeekOnFirstPlay=\(time, format: .fixed(precision: 1), privacy: .public)s")
            pendingSeekOnFirstPlay = time
        }
        engine.seek(to: time)
        // Immediately update progress for responsive UI
        progress = PlaybackProgress(currentTime: time, duration: progress.duration)
        nowPlayingManager.updateElapsedTime(time, isPlaying: playbackState.isPlaying)
    }

    /// Fire a synthetic network failure into the engine to exercise the retry
    /// and auto-advance-suppression paths without depending on real network
    /// conditions. Intended for in-app debugging only.
    public func debugInjectNetworkError() {
        engine.debugInjectNetworkFailure()
    }

    /// Force the stale-generation race so the bug is user-visible.
    ///
    /// Loads the queue TWICE in quick succession with a 3s delay injected on
    /// the FIRST resolve. The first load uses the current tracks REVERSED;
    /// the second load uses the originals. The fast (newer) completion wins
    /// the race and starts audio on the original first track. The delayed
    /// (older) completion then arrives — with the stale-gen guard it is
    /// dropped (queue stays correct); without the guard it clobbers
    /// `engine.queue.resolved` with the reversed URLs.
    ///
    /// After the race, audio + UI show the original track 1, but the next
    /// time anything reads from the engine's queue (skip-next, skipTo, or a
    /// natural track-end auto-advance), it picks from the reversed URLs and
    /// audibly jumps to the wrong track. Intended for in-app debugging only.
    public func debugForceRaceCondition() {
        guard !tracks.isEmpty, tracks.count >= 2 else {
            logger.warning("[PB] DEBUG race: need at least 2 tracks loaded")
            return
        }
        let original = tracks
        let reversed = Array(tracks.reversed())
        let startIndex = queueState.currentIndex
        logger.error("[PB] DEBUG forcing race: first load=REVERSED tracks (delayed 3s), second load=ORIGINAL")
        engine.setDebugNextResolveDelay(3.0)
        // First load — REVERSED tracks, resolve delayed. This is the older
        // generation; if the guard is enabled its completion will be dropped.
        loadQueue(reversed, startingAt: startIndex, autoPlay: false)
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .milliseconds(100))
            guard let self else { return }
            self.logger.error("[PB] DEBUG forcing race: second loadQueue firing (ORIGINAL tracks, autoPlay)")
            // Second load — ORIGINAL tracks, fast resolve, auto-play so the
            // user hears the correct track 1 starting before the older
            // completion arrives.
            self.loadQueue(original, startingAt: startIndex, autoPlay: true)
        }
    }

    /// Publish an optimistic `currentTime` without touching the engine.
    /// Used by playback restoration so the slider and time label reflect the
    /// saved position before the user presses play. Duration falls back to the
    /// current track's known duration when the engine hasn't reported one yet.
    public func applyOptimisticProgress(currentTime: TimeInterval) {
        let duration = progress.duration > 0
            ? progress.duration
            : (currentTrack?.duration ?? 0)
        progress = PlaybackProgress(currentTime: currentTime, duration: duration)
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
    }

    public func remove(at index: Int) {
        guard index >= 0, index < tracks.count, index != engine.currentIndex else { return }
        let removed = engine.removeTrack(at: index)
        if removed {
            tracks.remove(at: index)
            updateQueueState(index: engine.currentIndex)
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
                self.updatePlayIntent(for: state)
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
                self.onTrackComplete?()
            }
        }

        engine.onQueueComplete = { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                self.logger.notice("[PB] onQueueComplete — final track finished naturally")
                self.onQueueComplete?()
            }
        }

        engine.onProgressUpdate = { [weak self] newProgress in
            Task { @MainActor in
                guard let self else { return }
                // Don't overwrite progress while the first-play seek dance is
                // running: the engine ticks `currentTime=0` between play-start
                // and seek-landing, which would flash the slider to 0. The
                // dance's `seek(to:)` writes the correct position directly.
                if self.isPreparing { return }
                // Same reasoning during auto-retries: `player.play(url:)` for
                // each retry resets the underlying player's progress to 0,
                // which the timer reports. Freezing keeps the slider where it
                // was; the post-retry seek lands the correct position once
                // we reach `.playing`.
                if self.isRetrying { return }
                // If the engine hasn't yet reported a duration (e.g. paused during
                // initial buffering after restore), fall back to the playlist's
                // known duration so the slider denominator is non-zero.
                let effectiveDuration = newProgress.duration > 0
                    ? newProgress.duration
                    : (self.currentTrack?.duration ?? 0)
                self.progress = PlaybackProgress(
                    currentTime: newProgress.currentTime,
                    duration: effectiveDuration
                )
            }
        }

        engine.onRetryStateChange = { [weak self] retrying in
            Task { @MainActor in
                guard let self else { return }
                self.logger.notice("[PB] StreamPlayer.isRetrying → \(retrying, privacy: .public)")
                self.isRetrying = retrying
            }
        }

        engine.onError = { [weak self] error, resumePosition in
            Task { @MainActor in
                guard let self else { return }
                // Engine snapshotted the position at the moment of failure
                // (before `player.stop()` wiped it). Use that directly — reading
                // `self.progress.currentTime` here is unreliable because the
                // last progress tick may have reported 0 once the underlying
                // player went idle.
                if let resume = resumePosition, resume > 0 {
                    self.pendingSeekOnFirstPlay = resume
                    self.logger.notice("[PB] onError will resume at \(resume, format: .fixed(precision: 1), privacy: .public)s on next play")
                }
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

    // MARK: - Control style

    /// Update which transport controls are exposed on lock screen / CarPlay.
    /// Safe to call at any time; applies immediately.
    public func setControlStyle(_ style: PlayerControlsStyle) {
        remoteCommandManager.setControlStyle(style)
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

    /// Map an engine state to play intent and fire `onPlayIntentChange` on change.
    /// `.buffering` counts as intending-to-play (a stall, not a pause), so a
    /// rebuffer never reads as a pause. `.loading`/`.error` are transitional and
    /// leave the last intent untouched.
    private func updatePlayIntent(for state: PlaybackState) {
        let intent: Bool
        switch state {
        case .playing, .buffering: intent = true
        case .paused, .ended, .idle: intent = false
        case .loading, .error: return
        }
        guard intent != playIntent else { return }
        playIntent = intent
        onPlayIntentChange?(intent)
    }

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
    }

    private func updateQueueState(index: Int) {
        queueState = QueueState(currentIndex: index, totalTracks: tracks.count)
        // Always mirror queue position to the remote command manager so the lock
        // screen's prev/next buttons reflect reality from the first frame —
        // including session restore, where loadQueue() runs before any track-
        // change event would otherwise sync the state.
        remoteCommandManager.updateCommandState(
            hasNext: queueState.hasNext,
            hasPrevious: queueState.hasPrevious
        )
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
