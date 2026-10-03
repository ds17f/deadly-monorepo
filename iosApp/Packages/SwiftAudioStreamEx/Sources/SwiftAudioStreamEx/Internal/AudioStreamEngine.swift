import AudioStreaming
import AVFoundation
import Foundation
import os

/// Wraps AudioStreaming's AudioPlayer, manages queue state and redirect resolution.
/// Gapless playback is achieved by passing ALL tracks to AudioStreaming's internal queue
/// upfront, letting it pre-buffer the next track while the current one plays.
final class AudioStreamEngine: NSObject, AudioEngineProtocol, @unchecked Sendable {
    private let player: AudioPlayer
    private let diagnosticSession: URLSession
    private let cdnResolver: CDNURLResolver
    private let lock = NSLock()
    private let logger = Logger(subsystem: "SwiftAudioStreamEx", category: "Engine")

    private struct QueueState {
        var tracks: [URL] = []          // original URLs
        var resolved: [URL] = []        // redirect-resolved URLs
        var fallbacks: [[URL]] = []     // per-track fallback URLs, parallel to `tracks`
        var currentIndex: Int = 0
    }

    nonisolated(unsafe) private var queue = QueueState()
    nonisolated(unsafe) private var progressTimer: Timer?

    /// URLs waiting to be queued after play() completes its internal clearQueue().
    /// Set before calling player.play(), consumed in didStartPlaying callback.
    nonisolated(unsafe) private var pendingQueueURLs: [URL] = []

    /// When true, the engine immediately pauses after the next track starts
    /// playing. Used by skipTo(index:autoplay:false) to load a track (for
    /// Connect transfer-in sync) without actually playing it.
    nonisolated(unsafe) private var pauseAfterSkip = false

    /// Monotonically-increasing token bumped on every `loadQueue` call. A stale
    /// `resolveAllRedirects` completion whose captured generation no longer matches
    /// is dropped — this prevents a previously-tapped recording from clobbering
    /// the queue after the user switches recordings mid-load.
    nonisolated(unsafe) private var loadGeneration: Int = 0

    /// Last error reported via the AudioStreaming delegate, preserved so it can be
    /// surfaced when `mapState` later transitions to `.error` (which otherwise has
    /// no context and falls back to a generic "Player error" string).
    nonisolated(unsafe) private var lastError: StreamPlayerError?

    /// True once any URL has been handed to the underlying player. Used by
    /// `startCurrent()` to distinguish "queue is loaded but never started"
    /// (call `play(url:)`) from "already playing/paused" (call `play()` to resume).
    /// Reset on every `loadQueue`. Guarded by `lock`.
    nonisolated(unsafe) private var hasStartedAnyTrack: Bool = false

    /// When `startCurrent()` is called before `resolveAllRedirects` finishes,
    /// we remember the intent and the resolve handler honors it by starting
    /// playback once URLs land. Guarded by `lock`.
    nonisolated(unsafe) private var playWhenResolved: Bool = false

    /// Number of retries already attempted for the current network failure
    /// burst. Reset on success or on a new queue load. Guarded by `lock`.
    nonisolated(unsafe) private var retryAttempts: Int = 0

    /// Hard deadline for the current retry burst. After this point further
    /// errors are surfaced to the user rather than retried. Guarded by `lock`.
    nonisolated(unsafe) private var retryDeadline: Date?

    /// Backoff schedule for network-error retries. Total ~7s, under the
    /// `maxRetryDuration` budget below.
    private let retryDelays: [TimeInterval] = [1.0, 2.0, 4.0]

    /// Maximum total time spent retrying before the error reaches the user.
    private let maxRetryDuration: TimeInterval = 10.0

    /// Position captured at the moment of network failure. Applied as a seek
    /// the next time AudioStreaming reaches `.playing` (either from an
    /// automatic retry or a manual user retry), so audio resumes from where
    /// the stream dropped instead of restarting at 0:00.
    nonisolated(unsafe) private var resumePositionForRetry: TimeInterval?

    /// Volume captured when `resumePositionForRetry` is set. Restored after
    /// the post-retry seek lands so the user doesn't hear the brief audio
    /// from 0:00 before the seek takes effect.
    nonisolated(unsafe) private var savedVolumeBeforeRetry: Float?

    /// Background work item that fires if the underlying player has been
    /// stuck in `.bufferring` for too long without firing `unexpectedError`.
    /// AudioStreaming sometimes hangs silently when the network dies (no
    /// error, no state change), leaving the UI spinning forever. The
    /// watchdog converts that into a synthetic network failure so our
    /// retry / surfaced-error path runs normally.
    nonisolated(unsafe) private var bufferingStallWatchdog: DispatchWorkItem?

    /// Seconds of continuous `.bufferring` before the stall watchdog fires.
    private let bufferingStallTimeout: TimeInterval = 15.0

    /// Most recent user-driven seek target. Set by `seek(to:)`, cleared on
    /// the next `.playing` state change (by which point the seek has landed
    /// and `player.progress` is the authoritative source again). Consulted
    /// in `audioPlayerUnexpectedError` when capturing the resume position —
    /// without this, a failed-seek error captures `player.progress` which
    /// AudioStreaming may have reset to 0 while fetching the new range,
    /// causing the manual/auto retry to play from the beginning instead of
    /// where the user seeked.
    nonisolated(unsafe) private var lastUserSeekTarget: TimeInterval?

    /// True once we've exhausted the retry budget and shown the user-facing
    /// error. Used to silently drop any subsequent `unexpectedError` callbacks
    /// AudioStreaming fires from its own internal recovery — without this the
    /// retry cycle restarts indefinitely after we've already given up.
    /// Reset when the user manually retries (`startCurrent`) or a new queue
    /// is loaded.
    nonisolated(unsafe) private var hasSurfacedFinalError: Bool = false

    /// Prevents a burst of opaque AudioStreaming `serverError` callbacks from
    /// launching the same HTTP diagnostics repeatedly. Reset after playback
    /// recovers, the user manually retries, or a new queue is loaded.
    nonisolated(unsafe) private var hasDiagnosedNetworkFailure = false

    /// Bumped whenever a scheduled retry is created or cancelled. A delayed retry
    /// closure captures the value and bails if it changed, so a retry that was
    /// scheduled before a stop, skip, or new queue cannot fire afterwards.
    /// Guarded by `lock`.
    nonisolated(unsafe) private var retryGeneration: Int = 0

    /// State of one CDN recovery (ADR-0019). Created on the first `.cdnServer`
    /// failure in a burst and cleared on success, exhaustion, or cancellation.
    private struct CDNRecovery {
        enum Phase {
            /// Validating the current (and next) track URLs. The player is stopped.
            case resolving
            /// Waiting for the next attempt. The player is stopped.
            case backoff
            /// `play(url:)` was submitted for `expectedURL`; waiting for `.playing`.
            case starting
        }

        let id: Int
        let loadGeneration: Int
        let currentIndex: Int
        let canonicalURLs: [URL]
        let resumePosition: TimeInterval
        let shouldResumePlayback: Bool
        let failedHost: String?
        let capturedVolume: Float
        let startedAt: Date
        let source: NetworkFailureSource
        /// True when the engine muted for this recovery and must restore the
        /// volume. False for a manual Retry, where `StreamPlayer` owns the mute.
        let ownsMute: Bool
        var deadline: Date

        var phase: Phase = .resolving
        /// Number of resolution rounds started.
        var attempts = 0
        /// Hosts that failed validation or playback during this recovery. They
        /// move to the end of the candidate order. RAM only.
        var failedHosts: Set<String> = []
        /// URL submitted to the player while `phase == .starting`.
        var expectedURL: URL?
    }

#if DEBUG
    /// Debug: treat `dn*.archive.org` final hosts as failed and delay each CDN
    /// resolution attempt, so the simulator exercises the fallback path and
    /// leaves time to skip during recovery. Guarded by `lock`.
    nonisolated(unsafe) private var debugForceCDNFallbackStorage = false
    private let debugCDNDelay: TimeInterval = 4.0

    var debugForceCDNFallback: Bool {
        get { lock.withLock { debugForceCDNFallbackStorage } }
        set { lock.withLock { debugForceCDNFallbackStorage = newValue } }
    }
#endif

    nonisolated(unsafe) private var nextCDNRecoveryID = 0
    nonisolated(unsafe) private var cdnRecovery: CDNRecovery?

    /// Tracks user play/pause intent independently of AudioStreaming's state,
    /// which is forced to stopped during a retry.
    nonisolated(unsafe) private var shouldBePlaying = false

    /// One-shot debug delay (seconds) injected before the NEXT `loadQueue`'s
    /// redirect-resolve completion fires. Used to deterministically force the
    /// stale-generation race when paired with a quick second `loadQueue`.
    /// Cleared after use. Guarded by `lock`.
    nonisolated(unsafe) private var debugNextResolveDelay: TimeInterval = 0

    /// Minimum duration (seconds) a track must have played to count as a real completion.
    private let minimumPlayDuration: Double = 0.5

    // MARK: - AudioEngineProtocol callbacks
    nonisolated(unsafe) var onStateChange: ((PlaybackState) -> Void)?
    nonisolated(unsafe) var onTrackComplete: (() -> Void)?
    /// Fired when the *last* track of the queue reaches its natural end of file
    /// (the positive "show completed" signal — see ADR-0010 Chunk 1). Distinct
    /// from onTrackComplete (mid-queue auto-advance) and from a user stop/pause
    /// or an error (which never reach this `.eof && isLastTrack` path).
    nonisolated(unsafe) var onQueueComplete: (() -> Void)?
    nonisolated(unsafe) var onProgressUpdate: ((PlaybackProgress) -> Void)?
    /// Fired when the engine surfaces a user-visible playback failure.
    /// The second parameter, when non-nil, is the playback position at the
    /// moment of failure — passed through so the StreamPlayer can land
    /// there on the user's manual retry. Reading `progress.currentTime`
    /// from the StreamPlayer at this point is unreliable because the
    /// underlying player has already been stopped.
    nonisolated(unsafe) var onError: ((StreamPlayerError, TimeInterval?) -> Void)?
    /// Called when the retry-with-backoff path enters or exits the active state.
    /// `true` while the engine is automatically retrying after a transient
    /// network failure; `false` once playback resumes or the retry budget is
    /// exhausted (the user-facing error fires after the latter).
    nonisolated(unsafe) var onRetryStateChange: ((Bool) -> Void)?

    override init() {
        let diagnosticConfiguration = URLSessionConfiguration.ephemeral
        diagnosticConfiguration.requestCachePolicy = .reloadIgnoringLocalCacheData
        diagnosticConfiguration.urlCache = nil
        diagnosticConfiguration.timeoutIntervalForRequest = 10
        diagnosticConfiguration.timeoutIntervalForResource = 10
        self.diagnosticSession = URLSession(configuration: diagnosticConfiguration)
        self.cdnResolver = CDNURLResolver.live()

        let config = AudioPlayerConfiguration(
            flushQueueOnSeek: false,
            bufferSizeInSeconds: 10,
            secondsRequiredToStartPlaying: 0.001,
            gracePeriodAfterSeekInSeconds: 0.5,
            secondsRequiredToStartPlayingAfterBufferUnderrun: 1,
            enableLogs: true
        )
        self.player = AudioPlayer(configuration: config)
        super.init()
        player.delegate = self
    }

    // MARK: - AudioEngineProtocol

    func load(url: URL) {
        logger.notice("load: \(url.lastPathComponent)")
        player.play(url: url)
        startProgressTimer()
    }

    func queue(url: URL) {
        logger.notice("queue: \(url.lastPathComponent)")
        player.queue(url: url)
    }

    func play() {
        logger.info("play (resume)")
        lock.withLock { shouldBePlaying = true }
        player.resume()
        startProgressTimer()
    }

    func pause() {
        logger.info("pause")
        lock.lock()
        shouldBePlaying = false
        // Pause after `play(url:)` was submitted but before `.playing`: do not
        // rely on AudioStreaming honoring pause while it buffers. Abandon the
        // start, keep the new mapping, and leave the engine prepared.
        var pausedStart: (recovery: CDNRecovery, restoreVolume: Float?)?
        if let recovery = cdnRecovery, recovery.phase == .starting {
            pausedStart = (recovery, preparePausedAfterRecoveryLocked(recovery))
        }
        lock.unlock()

        if let pausedStart {
            let recovery = pausedStart.recovery
            logger.notice("[PB] CDN recovery prepared paused kind=cdn source=\(recovery.source.rawValue, privacy: .public) loadGeneration=\(recovery.loadGeneration, privacy: .public) recoveryId=\(recovery.id, privacy: .public) reason=pausedDuringStart")
            player.stop()
            if let restore = pausedStart.restoreVolume {
                player.volume = restore
            }
            stopProgressTimer()
            onRetryStateChange?(false)
            onStateChange?(.paused)
            return
        }
        player.pause()
        stopProgressTimer()
        sendCurrentProgress()
    }

    func seek(to time: TimeInterval) {
        logger.info("seek to \(time, format: .fixed(precision: 1))s")
        lock.lock()
        lastUserSeekTarget = time
        // During a recovery the player is stopped or restarting at 0:00, so
        // this seek cannot land. Make the latest user target the position that
        // the post-`.playing` seek applies. A manual-Retry recovery leaves the
        // seek to `StreamPlayer`.
        var retargeted: CDNRecovery?
        if let recovery = cdnRecovery, recovery.ownsMute {
            resumePositionForRetry = time
            retargeted = recovery
        } else if cdnRecovery == nil, resumePositionForRetry != nil, !hasStartedAnyTrack {
            // Recovery finished while paused; next Play will apply this.
            resumePositionForRetry = time
        }
        lock.unlock()
        if let retargeted {
            logger.notice("[PB] CDN recovery resume position replaced by user seek kind=cdn source=\(retargeted.source.rawValue, privacy: .public) loadGeneration=\(retargeted.loadGeneration, privacy: .public) recoveryId=\(retargeted.id, privacy: .public) phase=\(String(describing: retargeted.phase), privacy: .public) position=\(time, format: .fixed(precision: 1), privacy: .public)s")
        }
        player.seek(to: time)
    }

    func stop() {
        logger.info("stop")
        cancelRecoveryForUserAction(reason: "stop")
        lock.withLock { shouldBePlaying = false }
        stopProgressTimer()
        player.stop()
    }

    func attachAudioNode(_ node: AVAudioNode) {
        logger.info("attaching audio node: \(type(of: node))")
        player.attach(node: node)
    }

    func detachAudioNode(_ node: AVAudioNode) {
        logger.info("detaching audio node: \(type(of: node))")
        player.detach(node: node)
    }

    var volume: Float {
        get { player.volume }
        set { player.volume = newValue }
    }

    // MARK: - Queue management

    /// Resolves all redirects upfront, then (if `autoPlay`) plays track at `index`
    /// and queues all remaining tracks with AudioStreaming's internal gapless queue.
    /// When `autoPlay` is false the queue is populated and ready, but playback is
    /// not started — call `startCurrent()` later to begin the loaded track.
    func loadQueue(urls: [URL], fallbackURLs: [[URL]] = [], startingAt index: Int, autoPlay: Bool = true) {
        lock.lock()
        loadGeneration += 1
        retryGeneration += 1
        let generation = loadGeneration
        shouldBePlaying = autoPlay
        let cancelledRecovery = cdnRecovery
        cdnRecovery = nil
        hasStartedAnyTrack = false
        playWhenResolved = false
        retryAttempts = 0
        retryDeadline = nil
        // A new queue invalidates any pending post-error resume from a prior load.
        let staleVolume = savedVolumeBeforeRetry
        resumePositionForRetry = nil
        savedVolumeBeforeRetry = nil
        lastUserSeekTarget = nil
        hasSurfacedFinalError = false
        hasDiagnosedNetworkFailure = false
        // Capture the debug delay NOW so it binds to this generation, not
        // whichever loadQueue's resolve completes first.
        let delayThisResolve = debugNextResolveDelay
        debugNextResolveDelay = 0
        lock.unlock()
        if let cancelledRecovery {
            logger.notice("[PB] CDN recovery cancelled kind=cdn source=player loadGeneration=\(cancelledRecovery.loadGeneration, privacy: .public) recoveryId=\(cancelledRecovery.id, privacy: .public) reason=newQueue")
        }
        if let restore = staleVolume {
            player.volume = restore
        }

        let firstName = urls.first?.lastPathComponent ?? "(none)"
        let lastName = urls.last?.lastPathComponent ?? "(none)"
        logger.notice("[PB] loadQueue gen=\(generation, privacy: .public) count=\(urls.count, privacy: .public) startIdx=\(index, privacy: .public) autoPlay=\(autoPlay, privacy: .public) first=\(firstName, privacy: .public) last=\(lastName, privacy: .public)")

        // Keep `fallbacks` parallel to `urls` even if the caller passed fewer.
        let fallbacks = urls.indices.map { fallbackURLs.indices.contains($0) ? fallbackURLs[$0] : [] }

        resolveAllRedirects(for: urls) { [weak self] resolved in
            guard let self else { return }

            // `delayThisResolve` was captured at the start of this specific
            // loadQueue call, so it always binds to THIS generation.
            if delayThisResolve > 0 {
                self.logger.warning("[PB] DEBUG delaying resolve gen=\(generation, privacy: .public) by \(delayThisResolve, format: .fixed(precision: 1), privacy: .public)s")
                DispatchQueue.main.asyncAfter(deadline: .now() + delayThisResolve) { [weak self] in
                    self?.processResolveCompletion(resolved: resolved, urls: urls, fallbacks: fallbacks, index: index, generation: generation, autoPlay: autoPlay)
                }
                return
            }
            self.processResolveCompletion(resolved: resolved, urls: urls, fallbacks: fallbacks, index: index, generation: generation, autoPlay: autoPlay)
        }
    }

    /// Body of the `resolveAllRedirects` completion. Extracted so the debug
    /// delay can defer it without forking the closure body.
    private func processResolveCompletion(resolved: [URL], urls: [URL], fallbacks: [[URL]], index: Int, generation: Int, autoPlay: Bool) {
            self.lock.lock()
            guard generation == self.loadGeneration else {
                self.lock.unlock()
                self.logger.warning("[PB] loadQueue stale gen=\(generation, privacy: .public) current=\(self.loadGeneration, privacy: .public) — dropping completion")
                return
            }
            self.queue = QueueState(tracks: urls, resolved: resolved, fallbacks: fallbacks, currentIndex: index)
            // Stash remaining URLs — they'll be queued in didStartPlaying
            // (play() triggers an async clearQueue() that would wipe anything queued now)
            self.pendingQueueURLs = index + 1 < resolved.count ? Array(resolved[(index + 1)...]) : []
            let snapshot = self.queueSnapshotLocked()
            self.lock.unlock()

            guard index < resolved.count else {
                self.logger.warning("[PB] loadQueue resolved count=\(resolved.count, privacy: .public) but startIdx=\(index, privacy: .public) is out of bounds")
                return
            }

            // Capture whether play was requested while we were resolving.
            self.lock.lock()
            let pendingPlay = self.playWhenResolved
            self.playWhenResolved = false
            self.lock.unlock()

            if autoPlay || pendingPlay {
                let reason = autoPlay ? "autoPlay" : "pendingPlay"
                self.logger.notice("[PB] loadQueue resolved gen=\(generation, privacy: .public) \(snapshot, privacy: .public) — play (\(reason, privacy: .public)) \(resolved[index].absoluteString, privacy: .public)")
                self.lock.lock()
                self.hasStartedAnyTrack = true
                self.shouldBePlaying = true
                self.lock.unlock()
                self.player.play(url: resolved[index])
                self.startProgressTimer()
            } else {
                self.logger.notice("[PB] loadQueue resolved gen=\(generation, privacy: .public) \(snapshot, privacy: .public) — autoPlay=false, deferring playback start")
            }
    }

    /// Set a one-shot delay for the next `loadQueue` resolve completion. Used
    /// by the developer "Force stale-gen race" tool.
    func setDebugNextResolveDelay(_ seconds: TimeInterval) {
        lock.lock()
        debugNextResolveDelay = seconds
        lock.unlock()
    }

    /// Start playback of the queue's current index. Used when `loadQueue` was
    /// called with `autoPlay: false` and the user later presses play.
    /// Idempotent: if a track has already been started, this resumes via the
    /// underlying player's `play()` (resume) instead of restarting.
    /// If `resolveAllRedirects` hasn't completed yet, the intent is stashed
    /// (`playWhenResolved`) and the resolve handler kicks playback off.
    func startCurrent() {
        lock.lock()
        shouldBePlaying = true
        // User-driven retry clears the "we gave up" gate so future errors can
        // trigger the retry path again.
        let wasInError = hasSurfacedFinalError
        hasSurfacedFinalError = false
        if wasInError {
            hasDiagnosedNetworkFailure = false
        }
        // After a surfaced error the underlying player has been `.stop()`'d
        // and `player.resume()` is a no-op — force a fresh `play(url:)` even
        // if we'd previously started playback. Otherwise the user taps Retry
        // and nothing happens.
        if hasStartedAnyTrack && !wasInError {
            lock.unlock()
            logger.notice("[PB] startCurrent: already started, resuming")
            player.resume()
            startProgressTimer()
            return
        }
        // Manual Retry after a surfaced error on a streamed track: do not replay
        // the cached (possibly dead) URL. Start a fresh canonical recovery.
        if wasInError, !queue.resolved.isEmpty, queue.currentIndex < queue.resolved.count,
           queue.currentIndex < queue.tracks.count, !queue.resolved[queue.currentIndex].isFileURL {
            hasStartedAnyTrack = true
            playWhenResolved = false
            lock.unlock()
            logger.notice("[PB] startCurrent: manual retry — starting fresh CDN recovery from canonical URL")
            beginManualCDNRecovery()
            return
        }
        guard !queue.resolved.isEmpty, queue.currentIndex < queue.resolved.count else {
            // Redirects not yet resolved — record intent; resolve handler will start.
            playWhenResolved = true
            let snapshot = queueSnapshotLocked()
            lock.unlock()
            logger.notice("[PB] startCurrent: deferring until resolve \(snapshot, privacy: .public)")
            return
        }
        let url = queue.resolved[queue.currentIndex]
        // `play(url:)` clears AudioStreaming's forward queue. Rebuild it, in
        // case an earlier start already drained `pendingQueueURLs` (e.g. a
        // pause during CDN recovery stopped the player after didStartPlaying).
        pendingQueueURLs = queue.currentIndex + 1 < queue.resolved.count
            ? Array(queue.resolved[(queue.currentIndex + 1)...])
            : []
        hasStartedAnyTrack = true
        playWhenResolved = false
        // A CDN recovery that finished while paused kept the captured position
        // but restored the volume. Mute again so the post-play seek is silent.
        let needsResumeMute = resumePositionForRetry != nil && savedVolumeBeforeRetry == nil
        let snapshot = queueSnapshotLocked()
        lock.unlock()

        if needsResumeMute {
            let currentVolume = player.volume
            if currentVolume > 0 {
                lock.withLock { savedVolumeBeforeRetry = currentVolume }
                player.volume = 0
            }
        }
        logger.notice("[PB] startCurrent \(snapshot, privacy: .public) play=\(url.absoluteString, privacy: .public)")
        player.play(url: url)
        startProgressTimer()
    }

    func advanceToNext() -> Bool {
        cancelRecoveryForUserAction(reason: "skip")
        lock.lock()
        shouldBePlaying = true
        let before = queue.currentIndex
        guard queue.currentIndex < queue.resolved.count - 1 else {
            let snapshot = queueSnapshotLocked()
            lock.unlock()
            logger.warning("[PB] advanceToNext guarded: at end of queue \(snapshot, privacy: .public)")
            return false
        }
        queue.currentIndex += 1
        let remaining = Array(queue.resolved[(queue.currentIndex)...])
        pendingQueueURLs = remaining.count > 1 ? Array(remaining[1...]) : []
        hasStartedAnyTrack = true
        // User-driven navigation clears the "we gave up" gate so any future
        // error during this play gets a fresh retry budget.
        hasSurfacedFinalError = false
        let snapshot = queueSnapshotLocked()
        lock.unlock()

        logger.notice("[PB] advanceToNext \(before, privacy: .public) → \(self.currentIndex, privacy: .public) \(snapshot, privacy: .public) play=\(remaining[0].absoluteString, privacy: .public)")

        player.play(url: remaining[0])
        startProgressTimer()
        return true
    }

    func rewindToPrevious() -> Bool {
        cancelRecoveryForUserAction(reason: "previous")
        lock.lock()
        shouldBePlaying = true
        let before = queue.currentIndex
        guard queue.currentIndex > 0 else {
            let snapshot = queueSnapshotLocked()
            lock.unlock()
            logger.warning("[PB] rewindToPrevious guarded: at start of queue \(snapshot, privacy: .public)")
            return false
        }
        queue.currentIndex -= 1
        let remaining = Array(queue.resolved[(queue.currentIndex)...])
        pendingQueueURLs = remaining.count > 1 ? Array(remaining[1...]) : []
        hasStartedAnyTrack = true
        // User-driven navigation clears the "we gave up" gate so any future
        // error during this play gets a fresh retry budget.
        hasSurfacedFinalError = false
        let snapshot = queueSnapshotLocked()
        lock.unlock()

        logger.notice("[PB] rewindToPrevious \(before, privacy: .public) → \(self.currentIndex, privacy: .public) \(snapshot, privacy: .public) play=\(remaining[0].absoluteString, privacy: .public)")

        player.play(url: remaining[0])
        startProgressTimer()
        return true
    }

    func skipTo(index: Int, autoplay: Bool = true) -> Bool {
        cancelRecoveryForUserAction(reason: "skipTo")
        lock.lock()
        shouldBePlaying = autoplay
        let before = queue.currentIndex
        guard index >= 0, index < queue.resolved.count else {
            let snapshot = queueSnapshotLocked()
            lock.unlock()
            logger.warning("[PB] skipTo guarded: invalid index=\(index, privacy: .public) \(snapshot, privacy: .public)")
            return false
        }
        queue.currentIndex = index
        let remaining = Array(queue.resolved[index...])
        pendingQueueURLs = remaining.count > 1 ? Array(remaining[1...]) : []
        hasStartedAnyTrack = true
        // User-driven navigation clears the "we gave up" gate so any future
        // error during this play gets a fresh retry budget.
        hasSurfacedFinalError = false
        pauseAfterSkip = !autoplay
        let snapshot = queueSnapshotLocked()
        lock.unlock()

        logger.notice("[PB] skipTo \(before, privacy: .public) → \(index, privacy: .public) \(snapshot, privacy: .public) autoplay=\(autoplay, privacy: .public) play=\(remaining[0].absoluteString, privacy: .public)")

        player.play(url: remaining[0])
        if autoplay {
            startProgressTimer()
        }
        return true
    }

    var currentIndex: Int {
        lock.lock()
        defer { lock.unlock() }
        return queue.currentIndex
    }

    var totalTracks: Int {
        lock.lock()
        defer { lock.unlock() }
        return queue.tracks.count
    }

    func appendTrack(url: URL, fallbackURLs: [URL] = []) {
        resolveRedirect(for: url) { [weak self] resolved in
            guard let self else { return }
            self.lock.lock()
            self.queue.tracks.append(url)
            self.queue.resolved.append(resolved)
            self.queue.fallbacks.append(fallbackURLs)
            self.lock.unlock()
            // Add to AudioStreaming's queue too
            self.player.queue(url: resolved)
        }
    }

    func insertNext(url: URL, fallbackURLs: [URL] = []) {
        resolveRedirect(for: url) { [weak self] resolved in
            guard let self else { return }
            self.lock.lock()
            let insertIndex = self.queue.currentIndex + 1
            if insertIndex <= self.queue.tracks.count {
                self.queue.tracks.insert(url, at: insertIndex)
                self.queue.resolved.insert(resolved, at: insertIndex)
                self.queue.fallbacks.insert(fallbackURLs, at: min(insertIndex, self.queue.fallbacks.count))
            } else {
                self.queue.tracks.append(url)
                self.queue.resolved.append(resolved)
                self.queue.fallbacks.append(fallbackURLs)
            }
            // Get the current track's resolved URL to insert after
            let currentResolved = self.queue.resolved[self.queue.currentIndex]
            self.lock.unlock()
            self.player.queue(url: resolved, after: currentResolved)
        }
    }

    func removeTrack(at index: Int) -> Bool {
        lock.lock()
        guard index >= 0, index < queue.tracks.count, index != queue.currentIndex else {
            lock.unlock()
            return false
        }
        let removedResolved = queue.resolved[index]
        queue.tracks.remove(at: index)
        queue.resolved.remove(at: index)
        if index < queue.fallbacks.count {
            queue.fallbacks.remove(at: index)
        }
        if index < queue.currentIndex {
            queue.currentIndex -= 1
        }
        lock.unlock()
        player.removeFromQueue(url: removedResolved)
        return true
    }

    // MARK: - Diagnostics

    /// Compact one-line description of queue state for log messages.
    /// Caller MUST hold `lock`.
    private func queueSnapshotLocked() -> String {
        "idx=\(queue.currentIndex)/\(queue.resolved.count) pending=\(pendingQueueURLs.count)"
    }

    // MARK: - Progress timer

    private func startProgressTimer() {
        stopProgressTimer()
        let timer = Timer(timeInterval: 0.5, repeats: true) { [weak self] _ in
            self?.sendCurrentProgress()
        }
        RunLoop.main.add(timer, forMode: .common)
        progressTimer = timer
    }

    private func stopProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = nil
    }

    // MARK: - Redirect resolution

    private func resolveAllRedirects(for urls: [URL], completion: @escaping ([URL]) -> Void) {
        guard !urls.isEmpty else {
            completion([])
            return
        }

        var resolved = urls
        let group = DispatchGroup()

        for (index, url) in urls.enumerated() {
            group.enter()
            resolveRedirect(for: url) { finalURL in
                resolved[index] = finalURL
                group.leave()
            }
        }

        group.notify(queue: .main) {
            completion(resolved)
        }
    }

    private func resolveRedirect(for url: URL, completion: @escaping (URL) -> Void) {
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"

        let task = URLSession.shared.dataTask(with: request) { [weak self] _, response, error in
            if let httpResponse = response as? HTTPURLResponse,
               let finalURL = httpResponse.url,
               finalURL != url {
                self?.logger.notice("resolved redirect: \(url.lastPathComponent) → \(finalURL.host ?? "")")
                completion(finalURL)
            } else if error != nil {
                self?.logger.warning("redirect resolution failed for \(url.lastPathComponent), using original")
                completion(url)
            } else {
                completion(url)
            }
        }
        task.resume()
    }

    private func sendCurrentProgress() {
        // The player is stopped or restarting at 0:00 during a CDN recovery.
        // Do not publish that to the UI.
        if lock.withLock({ cdnRecovery != nil }) { return }
        let currentTime = player.progress
        let duration = player.duration
        let progress = PlaybackProgress(
            currentTime: currentTime,
            duration: duration
        )
        onProgressUpdate?(progress)
    }
}

// MARK: - AudioPlayerDelegate

extension AudioStreamEngine: AudioPlayerDelegate {
    func audioPlayerDidStartPlaying(player: AudioPlayer, with entryId: AudioEntryId) {
        // During a CDN recovery only the URL we submitted may start. A callback
        // from the stopped player (or a superseded attempt) must not change the
        // index or drain the pending queue.
        lock.lock()
        if let recovery = cdnRecovery {
            let matches = recovery.phase == .starting
                && recovery.loadGeneration == loadGeneration
                && recovery.expectedURL?.absoluteString == entryId.id
            if !matches {
                lock.unlock()
                logger.notice("[PB] didStartPlaying ignored during CDN recovery kind=cdn source=\(recovery.source.rawValue, privacy: .public) loadGeneration=\(recovery.loadGeneration, privacy: .public) recoveryId=\(recovery.id, privacy: .public) phase=\(String(describing: recovery.phase), privacy: .public) entry=\(entryId.id, privacy: .public)")
                return
            }
            logger.notice("[PB] didStartPlaying matches CDN recovery kind=cdn source=\(recovery.source.rawValue, privacy: .public) loadGeneration=\(recovery.loadGeneration, privacy: .public) recoveryId=\(recovery.id, privacy: .public)")
        }
        let entrySnapshot = queueSnapshotLocked()
        let shouldPause = pauseAfterSkip
        pauseAfterSkip = false
        lock.unlock()
        logger.notice("[PB] didStartPlaying entry=\(entryId.id, privacy: .public) \(entrySnapshot, privacy: .public)")

        // skipTo(autoplay:false) loads a track for Connect transfer-in sync
        // without playing it: pause immediately and report .paused. currentIndex
        // was already set in skipTo, so there's nothing further to reconcile.
        if shouldPause {
            logger.notice("[PB] didStartPlaying: pauseAfterSkip — pausing immediately (no autoplay)")
            player.pause()
            stopProgressTimer()
            onStateChange?(.paused)
            return
        }
        // NOTE: previously fired `onStateChange?(.playing)` here, but this
        // signal is synthetic — AudioStreaming may still be buffering for
        // seconds afterward. Letting the real `audioPlayerStateChanged` →
        // `.playing` drive the state lets `playWithPendingSeek` and the UI
        // distinguish "URL accepted" from "audio actually flowing".
        startProgressTimer()

        // Sync currentIndex to the track AudioStreaming is actually playing.
        // This handles gapless auto-advance (where stopReason is .none, not .eof)
        // and keeps the index correct regardless of how the transition happened.
        lock.lock()
        if let actualIndex = queue.resolved.firstIndex(where: { $0.absoluteString == entryId.id }) {
            let previousIndex = queue.currentIndex
            let wasAutoAdvance = actualIndex != queue.currentIndex
            // Reject spurious auto-advance while a retry is pending OR after
            // we've already surfaced a final error: when the current track
            // errors, AudioStreaming pops to the next pre-queued track and
            // reports it as a gapless transition. That looks like a skip
            // to the user and silently advances `queue.currentIndex` away
            // from the failed track, breaking the manual retry path.
            if wasAutoAdvance, retryDeadline != nil || hasSurfacedFinalError {
                lock.unlock()
                let reason = retryDeadline != nil ? "retry pending" : "final error surfaced"
                logger.warning("[PB] suppressing auto-advance (\(reason, privacy: .public)): prev=\(previousIndex, privacy: .public) attempted=\(actualIndex, privacy: .public) entry=\(entryId.id, privacy: .public)")
                return
            }
            queue.currentIndex = actualIndex
            lock.unlock()
            logger.notice("[PB] didStartPlaying matched prev=\(previousIndex, privacy: .public) → actual=\(actualIndex, privacy: .public) wasAutoAdvance=\(wasAutoAdvance, privacy: .public)")
            if wasAutoAdvance {
                onTrackComplete?()
            }
        } else {
            // The URL AudioStreaming reports doesn't match anything in our queue.
            // This is the silent-desync trap — log loudly so we can see it.
            let resolvedNames = queue.resolved.map { $0.lastPathComponent }.joined(separator: ",")
            let resolvedCount = queue.resolved.count
            lock.unlock()
            logger.warning("[PB] didStartPlaying NO MATCH for entry=\(entryId.id, privacy: .public) — queue has \(resolvedCount, privacy: .public) tracks: [\(resolvedNames, privacy: .public)]")
        }

        // Now safe to queue remaining tracks — play()'s deferred clearQueue() has already fired
        lock.lock()
        let pending = pendingQueueURLs
        pendingQueueURLs = []
        lock.unlock()

        if !pending.isEmpty {
            logger.notice("[PB] queueing \(pending.count, privacy: .public) deferred tracks for gapless playback")
            player.queue(urls: pending)
        }
    }

    func audioPlayerDidFinishBuffering(player: AudioPlayer, with entryId: AudioEntryId) {
        logger.notice("[PB] didFinishBuffering entry=\(entryId.id, privacy: .public)")
    }

    private func armBufferingStallWatchdog() {
        cancelBufferingStallWatchdog()
        let timeout = bufferingStallTimeout
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.logger.warning("[PB] buffering stalled for \(timeout, format: .fixed(precision: 1), privacy: .public)s — synthesizing network failure")
            self.synthesizeNetworkStall()
        }
        lock.lock()
        bufferingStallWatchdog = work
        lock.unlock()
        DispatchQueue.main.asyncAfter(deadline: .now() + timeout, execute: work)
    }

    private func cancelBufferingStallWatchdog() {
        lock.lock()
        let work = bufferingStallWatchdog
        bufferingStallWatchdog = nil
        lock.unlock()
        work?.cancel()
    }

    /// Drive the retry/error path as if AudioStreaming had fired
    /// `unexpectedError` with a network failure. Used by the buffering stall
    /// watchdog when the underlying player hangs silently in `.bufferring`.
    private func synthesizeNetworkStall() {
        // Don't double-fire after we've already surrendered.
        // Note: don't bail when retryDeadline != nil — a stall that fires
        // mid-retry means the *previous* retry attempt also stalled, and
        // attemptRetry should be allowed to either schedule the next attempt
        // or fall through to the surfaced-error path when the budget is up.
        lock.lock()
        let alreadySurfaced = hasSurfacedFinalError
        lock.unlock()
        if alreadySurfaced {
            return
        }

        handleNetworkFailure(kind: .connectivity, source: .watchdog)
    }

    func audioPlayerStateChanged(player: AudioPlayer, with newState: AudioPlayerState, previous: AudioPlayerState) {
        logger.notice("[PB] stateChanged \(String(describing: previous), privacy: .public) → \(String(describing: newState), privacy: .public)")

        // After we've surfaced a final error, AudioStreaming's internal recovery
        // keeps thrashing through bufferring/error/stopped/etc. Propagating each
        // change makes `playbackState` oscillate, which flips the UI between
        // the error card and a spinner. Latch the error state until the user
        // acts (which clears `hasSurfacedFinalError` via `startCurrent` etc).
        lock.lock()
        let suppressed = hasSurfacedFinalError
        lock.unlock()
        if suppressed {
            logger.notice("[PB] stateChanged ignored (final error surfaced)")
            // Still stop the progress timer so we don't tick during the noise.
            if newState == .paused || newState == .stopped || newState == .ready || newState == .disposed {
                stopProgressTimer()
            }
            return
        }

        // While a CDN recovery is resolving or backing off the player is
        // stopped on purpose. Its stopped/idle noise must not reach the UI
        // (which shows buffering) or re-arm the stall watchdog.
        lock.lock()
        var recoveryGap: CDNRecovery?
        if let active = cdnRecovery, active.phase != .starting {
            recoveryGap = active
        }
        lock.unlock()
        if let recoveryGap {
            logger.notice("[PB] stateChanged ignored (CDN recovery \(String(describing: recoveryGap.phase), privacy: .public)) kind=cdn source=\(recoveryGap.source.rawValue, privacy: .public) loadGeneration=\(recoveryGap.loadGeneration, privacy: .public) recoveryId=\(recoveryGap.id, privacy: .public)")
            return
        }

        let mapped = mapState(newState)
        onStateChange?(mapped)

        // Stall watchdog: start when we enter `.bufferring`, cancel on any
        // other state. AudioStreaming sometimes hangs there silently when
        // the network dies (no `unexpectedError`), leaving the UI spinning
        // forever.
        if newState == .bufferring {
            armBufferingStallWatchdog()
        } else {
            cancelBufferingStallWatchdog()
        }

        switch newState {
        case .playing:
            startProgressTimer()
            // Real playback achieved — clear retry budget so the next failure
            // burst gets a fresh 10s window. Also clear the "we gave up"
            // gate; recovery happened.
            lock.lock()
            let wasRetrying = retryDeadline != nil
            let completedRecovery = cdnRecovery
            retryAttempts = 0
            retryDeadline = nil
            cdnRecovery = nil
            hasSurfacedFinalError = false
            hasDiagnosedNetworkFailure = false
            // Seek has settled — clear the saved target so future
            // unrelated errors fall back to `player.progress`.
            lastUserSeekTarget = nil
            // If a network failure captured a resume position, apply it now
            // that audio is flowing. Seek before unmuting so the user never
            // hears the brief audio from 0:00 before the seek lands.
            let pendingResume = resumePositionForRetry
            let savedVolume = savedVolumeBeforeRetry
            resumePositionForRetry = nil
            savedVolumeBeforeRetry = nil
            lock.unlock()
            if let completedRecovery {
                let elapsed = Date.now.timeIntervalSince(completedRecovery.startedAt)
                logger.notice("[PB] CDN recovery succeeded kind=cdn source=\(completedRecovery.source.rawValue, privacy: .public) loadGeneration=\(completedRecovery.loadGeneration, privacy: .public) recoveryId=\(completedRecovery.id, privacy: .public) elapsedMs=\(Int(elapsed * 1000), privacy: .public) behavior=canonicalRefresh")
            }
            if let resume = pendingResume {
                logger.notice("[PB] post-retry seek to \(resume, format: .fixed(precision: 1), privacy: .public)s")
                player.seek(to: resume)
                // Give the seek a moment to land, then restore volume.
                let unmuteVolume = savedVolume ?? 1.0
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
                    self?.player.volume = unmuteVolume
                }
            } else if let restore = savedVolume {
                // We muted for the retry but there was no resume position to seek to
                // (the error hit during the cold initial load, before any progress).
                // Without this the mute is never undone and ALL playback stays silent.
                logger.notice("[PB] post-retry restore volume \(restore, format: .fixed(precision: 1), privacy: .public) (no resume position)")
                player.volume = restore
            }
            if wasRetrying {
                onRetryStateChange?(false)
            }
        case .paused, .stopped, .ready, .disposed:
            stopProgressTimer()
        default:
            break
        }
    }

    func audioPlayerDidFinishPlaying(player: AudioPlayer, entryId: AudioEntryId, stopReason: AudioPlayerStopReason, progress: Double, duration: Double) {
        // Check if the last track in the queue just finished (end of queue)
        lock.lock()
        let isLastTrack = queue.currentIndex >= queue.tracks.count - 1
        let snapshot = queueSnapshotLocked()
        lock.unlock()

        logger.notice("[PB] didFinishPlaying entry=\(entryId.id, privacy: .public) reason=\(String(describing: stopReason), privacy: .public) progress=\(progress, format: .fixed(precision: 1), privacy: .public) duration=\(duration, format: .fixed(precision: 1), privacy: .public) \(snapshot, privacy: .public) isLastTrack=\(isLastTrack, privacy: .public)")

        if stopReason == .eof && isLastTrack {
            logger.notice("[PB] final track finished, queue ended")
            lock.withLock { shouldBePlaying = false }
            onStateChange?(.ended)
            onQueueComplete?()
        }
    }

    func audioPlayerUnexpectedError(player: AudioPlayer, error: AudioPlayerError) {
        logger.error("[PB] unexpectedError type=\(String(describing: error), privacy: .public) desc=\(error.localizedDescription, privacy: .public)")

        // Drop late callbacks: after we've surrendered, AudioStreaming may keep
        // emitting unexpectedError as its own internal recovery thrashes. We've
        // already shown the user the error; ignore until they retry manually.
        lock.lock()
        let alreadySurfaced = hasSurfacedFinalError
        lock.unlock()
        if alreadySurfaced {
            logger.warning("[PB] unexpectedError suppressed (final error already surfaced)")
            return
        }

        let description = String(describing: error)
        if let kind = PlaybackNetworkFailureClassifier.classify(errorDescription: description) {
            handleNetworkFailure(kind: kind, source: .player)
            return
        }

        let mapped: StreamPlayerError = .engineError(error.localizedDescription)
        lock.lock()
        lastError = mapped
        shouldBePlaying = false
        let abortedRecovery = cdnRecovery
        cdnRecovery = nil
        if abortedRecovery != nil {
            pendingQueueURLs = []
        }
        retryGeneration += 1
        retryDeadline = nil
        retryAttempts = 0
        // Reset hasStartedAnyTrack so the next play() reaches `player.play(url:)`
        // (fresh start) instead of `player.resume()`, which is a no-op once the
        // underlying player has hit `.stopped`/`.error`.
        hasStartedAnyTrack = false
        // Auto-retries failed. Restore the volume we muted at error capture so
        // StreamPlayer's manual-retry dance (which snapshots `volume`) sees a
        // sane value and doesn't end up muted forever after restoring.
        // Also clear `resumePositionForRetry` so the post-`.playing` handler
        // doesn't double-seek alongside StreamPlayer's manual-retry dance,
        // which captures the same position into `pendingSeekOnFirstPlay`.
        let savedVolume = savedVolumeBeforeRetry
        savedVolumeBeforeRetry = nil
        let surfacedResume = resumePositionForRetry
        resumePositionForRetry = nil
        // Latch the "we gave up" gate. Subsequent unexpectedError callbacks
        // from AudioStreaming's own internal thrashing are silently dropped.
        hasSurfacedFinalError = true
        lock.unlock()
        if let abortedRecovery {
            logger.notice("[PB] CDN recovery cancelled kind=cdn source=\(abortedRecovery.source.rawValue, privacy: .public) loadGeneration=\(abortedRecovery.loadGeneration, privacy: .public) recoveryId=\(abortedRecovery.id, privacy: .public) reason=engineError")
        }
        if let restore = savedVolume {
            player.volume = restore
        }
        // Halt AudioStreaming so it stops generating further error callbacks.
        player.stop()
        onRetryStateChange?(false)
        onError?(mapped, surfacedResume)
        onStateChange?(.error(mapped))
    }

    /// Inject a synthetic network failure for debugging the retry/backoff path
    /// without depending on real network conditions. Wires through the same
    /// `attemptRetry` logic as a real failure.
    func debugInjectNetworkFailure() {
        handleNetworkFailure(kind: .connectivity, source: .developer)
    }

#if DEBUG
    /// Injects at the same dispatcher seam as a real AudioStreaming
    /// `serverError`, so it runs the production CDN recovery.
    func debugSimulateCDNFailure() -> Int? {
        lock.lock()
        let hasCurrentTrack = queue.currentIndex >= 0
            && queue.currentIndex < queue.tracks.count
            && queue.currentIndex < queue.resolved.count
        let alreadyRecovering = cdnRecovery != nil
        lock.unlock()
        guard hasCurrentTrack, !alreadyRecovering else {
            logger.notice("[PB] CDN developer injection ignored kind=cdn source=developer hasCurrent=\(hasCurrentTrack, privacy: .public) recoveryActive=\(alreadyRecovering, privacy: .public)")
            return nil
        }
        return handleNetworkFailure(kind: .cdnServer, source: .developer)
    }
#endif

    // MARK: - Cancellation of retries and recovery

    /// A user transport action (stop, skip, previous, skipTo) supersedes any
    /// retry or CDN recovery in progress. Invalidates delayed retries, clears
    /// the resume state, restores a recovery-owned mute, and tells the UI.
    private func cancelRecoveryForUserAction(reason: String) {
        lock.lock()
        let cancelled = cdnRecovery
        let wasRetrying = retryDeadline != nil
        let savedVolume = savedVolumeBeforeRetry
        cdnRecovery = nil
        retryGeneration += 1
        retryDeadline = nil
        retryAttempts = 0
        resumePositionForRetry = nil
        savedVolumeBeforeRetry = nil
        hasDiagnosedNetworkFailure = false
        if cancelled != nil {
            pendingQueueURLs = []
        }
        lock.unlock()

        if let cancelled {
            logger.notice("[PB] CDN recovery cancelled kind=cdn source=\(cancelled.source.rawValue, privacy: .public) loadGeneration=\(cancelled.loadGeneration, privacy: .public) recoveryId=\(cancelled.id, privacy: .public) reason=\(reason, privacy: .public)")
        } else if wasRetrying {
            logger.notice("[PB] retry cancelled kind=connectivity reason=\(reason, privacy: .public)")
        }
        if let restore = savedVolume {
            player.volume = restore
        }
        if wasRetrying {
            onRetryStateChange?(false)
        }
    }

    // MARK: - Network failure handling

    /// Shared dispatcher for real, watchdog, and developer-triggered network
    /// incidents. `.connectivity` retries the current resolved URL with backoff.
    /// `.cdnServer` runs the canonical-refresh recovery (ADR-0019), except for a
    /// local file, which has no CDN and uses the same-URL retry.
    @discardableResult
    private func handleNetworkFailure(
        kind requestedKind: PlaybackNetworkFailureKind,
        source: NetworkFailureSource
    ) -> Int? {
        var kind = requestedKind
        if let handledID = handleFailureDuringCDNRecovery(kind: kind, source: source) {
            return handledID
        }

        let actualProgress = player.progress
        let currentVolume = player.volume
        lock.lock()
        let pendingSeekTarget = lastUserSeekTarget
        let currentPosition: TimeInterval = {
            if let seekTarget = pendingSeekTarget {
                return actualProgress > seekTarget + 1 ? actualProgress : seekTarget
            }
            return actualProgress
        }()
        let index = queue.currentIndex
        let count = queue.tracks.count
        let generation = loadGeneration
        let failedHost = index >= 0 && index < queue.resolved.count
            ? queue.resolved[index].host
            : nil

        var recoveryID: Int? = cdnRecovery?.id
        var startedRecovery: CDNRecovery?
        var skippedLocalFile = false
        if kind == .cdnServer, cdnRecovery == nil,
           index >= 0, index < queue.tracks.count, index < queue.resolved.count {
            if queue.resolved[index].isFileURL {
                // Downloaded track: no CDN to refresh.
                skippedLocalFile = true
                kind = .connectivity
            } else {
                let recovery = makeCDNRecoveryLocked(
                    source: source,
                    position: currentPosition,
                    volume: currentVolume,
                    shouldResume: shouldBePlaying,
                    ownsMute: true
                )
                recoveryID = recovery.id
                startedRecovery = recovery
            }
        }

        if savedVolumeBeforeRetry == nil, currentVolume > 0 {
            savedVolumeBeforeRetry = currentVolume
        }
        if resumePositionForRetry == nil, currentPosition > 0 {
            resumePositionForRetry = currentPosition
        }
        let hasResumePosition = resumePositionForRetry != nil
        lock.unlock()

        let recoveryLog = recoveryID.map(String.init) ?? "none"
        logger.notice("[PB] network failure dispatched kind=\(kind.rawValue, privacy: .public) source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryLog, privacy: .public) idx=\(index, privacy: .public)/\(count, privacy: .public) position=\(currentPosition, format: .fixed(precision: 1), privacy: .public)s failedHost=\(failedHost ?? "nil", privacy: .public)")

        if skippedLocalFile {
            logger.notice("[PB] CDN recovery skipped kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) reason=localFile behavior=sameURLRetry")
        }

        if hasResumePosition {
            logger.notice("[PB] retry capture kind=\(kind.rawValue, privacy: .public) source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryLog, privacy: .public) position=\(currentPosition, format: .fixed(precision: 1), privacy: .public)s; muting")
        } else {
            logger.notice("[PB] retry capture kind=\(kind.rawValue, privacy: .public) source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryLog, privacy: .public) position=none; muting")
        }
        player.volume = 0

        if let recovery = startedRecovery {
            logger.notice("[PB] CDN recovery started kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(recovery.loadGeneration, privacy: .public) recoveryId=\(recovery.id, privacy: .public) idx=\(recovery.currentIndex, privacy: .public)/\(recovery.canonicalURLs.count, privacy: .public) position=\(recovery.resumePosition, format: .fixed(precision: 1), privacy: .public)s shouldResume=\(recovery.shouldResumePlayback, privacy: .public) volume=\(recovery.capturedVolume, format: .fixed(precision: 2), privacy: .public) failedHost=\(recovery.failedHost ?? "nil", privacy: .public) behavior=canonicalRefresh")
            diagnoseNetworkFailureIfNeeded(recovery: recovery, source: source)
            startCDNRecovery(recovery)
            return recovery.id
        }

        if attemptRetry(kind: kind, source: source, recoveryID: recoveryID) {
            return recoveryID
        }
        surfaceNetworkFailure(kind: kind, source: source, recoveryID: recoveryID)
        return recoveryID
    }

    private func surfaceNetworkFailure(
        kind: PlaybackNetworkFailureKind,
        source: NetworkFailureSource,
        recoveryID: Int?
    ) {
        let mapped: StreamPlayerError = .networkError("Can't reach Archive.org. Check your connection and try again.")
        lock.lock()
        lastError = mapped
        let attempts = retryAttempts
        retryGeneration += 1
        retryDeadline = nil
        retryAttempts = 0
        hasStartedAnyTrack = false
        shouldBePlaying = false
        let savedVolume = savedVolumeBeforeRetry
        savedVolumeBeforeRetry = nil
        let surfacedResume = resumePositionForRetry
        resumePositionForRetry = nil
        let recovery = cdnRecovery
        cdnRecovery = nil
        pendingQueueURLs = []
        hasSurfacedFinalError = true
        lock.unlock()
        if let restore = savedVolume {
            player.volume = restore
        }
        let recoveryLog = recoveryID.map(String.init) ?? "none"
        logger.error("[PB] network failure exhausted kind=\(kind.rawValue, privacy: .public) source=\(source.rawValue, privacy: .public) recoveryId=\(recoveryLog, privacy: .public) attempts=\(attempts, privacy: .public)")
        if let recovery {
            let elapsed = Date.now.timeIntervalSince(recovery.startedAt)
            logger.error("[PB] CDN recovery exhausted kind=cdn source=\(recovery.source.rawValue, privacy: .public) loadGeneration=\(recovery.loadGeneration, privacy: .public) recoveryId=\(recovery.id, privacy: .public) attempts=\(attempts, privacy: .public) elapsedMs=\(Int(elapsed * 1000), privacy: .public) behavior=canonicalRefresh")
        }
        player.stop()
        onRetryStateChange?(false)
        onError?(mapped, surfacedResume)
        onStateChange?(.error(mapped))
    }

    /// Schedule a retry of the current URL if this looks like a transient
    /// network failure and we're inside the retry budget. Returns `true` if
    /// a retry was scheduled (caller should NOT surface the error).
    private func attemptRetry(
        kind: PlaybackNetworkFailureKind,
        source: NetworkFailureSource,
        recoveryID: Int?
    ) -> Bool {
        lock.lock()
        if retryDeadline == nil {
            retryDeadline = Date.now.addingTimeInterval(maxRetryDuration)
            retryAttempts = 0
        }
        let attempts = retryAttempts
        let deadline = retryDeadline ?? Date.now
        let withinBudget = attempts < retryDelays.count && Date.now < deadline
        guard withinBudget, queue.currentIndex < queue.resolved.count else {
            lock.unlock()
            return false
        }
        let delay = retryDelays[attempts]
        let url = queue.resolved[queue.currentIndex]
        retryAttempts += 1
        let attemptNumber = retryAttempts
        // Only the newest scheduled retry may fire.
        retryGeneration += 1
        let retryToken = retryGeneration
        let generation = loadGeneration
        lock.unlock()

        let recoveryLog = recoveryID.map(String.init) ?? "none"
        logger.warning("[PB] retry scheduled kind=\(kind.rawValue, privacy: .public) source=\(source.rawValue, privacy: .public) recoveryId=\(recoveryLog, privacy: .public) attempt=\(attemptNumber, privacy: .public)/\(self.retryDelays.count, privacy: .public) in=\(delay, format: .fixed(precision: 1), privacy: .public)s behavior=sameURLRetry url=\(url.absoluteString, privacy: .public)")

        // Notify on first retry so the UI can show "Network trouble — retrying".
        if attemptNumber == 1 {
            let callbackSet = self.onRetryStateChange != nil
            logger.notice("[PB] firing onRetryStateChange(true) — callback set=\(callbackSet, privacy: .public)")
            onRetryStateChange?(true)
        }

        // CRITICAL: stop the player immediately so AudioStreaming doesn't
        // auto-advance to the next pre-queued track while we're waiting for
        // the retry to fire. Without this, an error on the current track
        // causes the library's internal queue to pop forward and report
        // `wasAutoAdvance=true` on the next URL — exactly the "skips to next
        // song after a glitch" symptom from the original bug report.
        player.stop()

        // Surface buffering so the UI keeps showing a spinner while we wait.
        onStateChange?(.buffering)

        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self else { return }
            self.lock.lock()
            // Bail if a stop, skip, or new queue cancelled this retry, a newer
            // retry replaced it, or a successful play reset retry state.
            guard self.retryGeneration == retryToken,
                  self.loadGeneration == generation,
                  self.retryDeadline != nil,
                  self.queue.currentIndex < self.queue.resolved.count else {
                self.lock.unlock()
                self.logger.notice("[PB] retry dropped (stale) kind=\(kind.rawValue, privacy: .public) recoveryId=\(recoveryLog, privacy: .public) attempt=\(attemptNumber, privacy: .public)")
                return
            }
            let retryURL = self.queue.resolved[self.queue.currentIndex]
            let pending = self.queue.currentIndex + 1 < self.queue.resolved.count
                ? Array(self.queue.resolved[(self.queue.currentIndex + 1)...])
                : []
            // We just stopped the player; the gapless queue is gone. Re-stash
            // pending URLs so `didStartPlaying` re-queues them.
            self.pendingQueueURLs = pending
            self.lock.unlock()
            self.logger.notice("[PB] retry firing kind=\(kind.rawValue, privacy: .public) source=\(source.rawValue, privacy: .public) recoveryId=\(recoveryLog, privacy: .public) url=\(retryURL.absoluteString, privacy: .public) pending=\(pending.count, privacy: .public)")
            self.player.play(url: retryURL)
        }
        return true
    }

    // MARK: - CDN recovery (ADR-0019)

    /// Build the recovery record and register it. Caller MUST hold `lock`.
    private func makeCDNRecoveryLocked(
        source: NetworkFailureSource,
        position: TimeInterval,
        volume: Float,
        shouldResume: Bool,
        ownsMute: Bool
    ) -> CDNRecovery {
        nextCDNRecoveryID += 1
        let index = queue.currentIndex
        let deadline = Date.now.addingTimeInterval(maxRetryDuration)
        let recovery = CDNRecovery(
            id: nextCDNRecoveryID,
            loadGeneration: loadGeneration,
            currentIndex: index,
            canonicalURLs: queue.tracks,
            resumePosition: position,
            shouldResumePlayback: shouldResume,
            failedHost: queue.resolved[index].host,
            capturedVolume: volume,
            startedAt: .now,
            source: source,
            ownsMute: ownsMute,
            deadline: deadline
        )
        cdnRecovery = recovery
        retryGeneration += 1
        // Shares the retry bookkeeping so `.playing` and the auto-advance
        // suppression in `didStartPlaying` treat this as a retry in progress.
        retryDeadline = deadline
        retryAttempts = 0
        pendingQueueURLs = []
        return recovery
    }

    /// Stop the player, show buffering, and run the first resolution attempt.
    /// The recovery is already registered and the volume already muted.
    private func startCDNRecovery(_ recovery: CDNRecovery) {
        cancelBufferingStallWatchdog()
        player.stop()
        onRetryStateChange?(true)
        onStateChange?(.buffering)
        runCDNRecoveryAttempt(recoveryID: recovery.id)
    }

    /// Manual Retry after a surfaced error. `StreamPlayer` owns any mute and
    /// the resume seek, so the engine neither mutes nor seeks here.
    private func beginManualCDNRecovery() {
        let currentVolume = player.volume
        lock.lock()
        guard cdnRecovery == nil,
              queue.currentIndex >= 0,
              queue.currentIndex < queue.tracks.count,
              queue.currentIndex < queue.resolved.count else {
            lock.unlock()
            return
        }
        let recovery = makeCDNRecoveryLocked(
            source: .manualRetry,
            position: 0,
            volume: currentVolume,
            shouldResume: true,
            ownsMute: false
        )
        let index = queue.currentIndex
        let count = queue.tracks.count
        lock.unlock()

        logger.notice("[PB] CDN recovery started kind=cdn source=\(recovery.source.rawValue, privacy: .public) loadGeneration=\(recovery.loadGeneration, privacy: .public) recoveryId=\(recovery.id, privacy: .public) idx=\(index, privacy: .public)/\(count, privacy: .public) position=none shouldResume=true failedHost=\(recovery.failedHost ?? "nil", privacy: .public) behavior=canonicalRefresh")
        startCDNRecovery(recovery)
    }

    /// A failure reported while a CDN recovery is active. Returns the recovery
    /// ID if the failure was absorbed, or nil if the normal path should run
    /// (a connectivity error after the new URL started playing).
    private func handleFailureDuringCDNRecovery(
        kind: PlaybackNetworkFailureKind,
        source: NetworkFailureSource
    ) -> Int? {
        lock.lock()
        guard var recovery = cdnRecovery else {
            lock.unlock()
            return nil
        }
        // A watchdog stall during a CDN recovery is part of that recovery.
        let promoted = kind == .connectivity && source == .watchdog
        let effectiveKind: PlaybackNetworkFailureKind = promoted ? .cdnServer : kind
        let phase = recovery.phase
        let generation = loadGeneration
        let promotedLog = promoted ? " promotedFrom=connectivity" : ""

        guard effectiveKind == .cdnServer else {
            lock.unlock()
            if phase == .starting {
                return nil
            }
            logger.notice("[PB] network failure coalesced kind=\(kind.rawValue, privacy: .public) source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recovery.id, privacy: .public) phase=\(String(describing: phase), privacy: .public)")
            return recovery.id
        }
        guard phase == .starting else {
            // Burst of callbacks from the player we already stopped.
            lock.unlock()
            logger.notice("[PB] network failure coalesced kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recovery.id, privacy: .public) phase=\(String(describing: phase), privacy: .public)\(promotedLog, privacy: .public)")
            return recovery.id
        }

        // The URL we validated and started has failed. Demote its host and retry.
        let failedHost = recovery.expectedURL?.host
        if let failedHost {
            recovery.failedHosts.insert(failedHost)
        }
        recovery.phase = .backoff
        recovery.expectedURL = nil
        cdnRecovery = recovery
        pendingQueueURLs = []
        lock.unlock()

        logger.notice("[PB] CDN recovery attempt failed after start kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recovery.id, privacy: .public) attempt=\(recovery.attempts, privacy: .public) failedHost=\(failedHost ?? "nil", privacy: .public)\(promotedLog, privacy: .public)")
        cancelBufferingStallWatchdog()
        player.stop()
        onStateChange?(.buffering)
        scheduleNextCDNAttempt(recoveryID: recovery.id)
        return recovery.id
    }

    /// Resolve the current track and its successor in parallel, then hand the
    /// result to `finishCDNRecoveryAttempt`. No lock is held across the network.
    private func runCDNRecoveryAttempt(recoveryID: Int) {
        lock.lock()
        guard var recovery = cdnRecovery,
              recovery.id == recoveryID,
              recovery.loadGeneration == loadGeneration,
              recovery.currentIndex < queue.tracks.count,
              recovery.currentIndex < queue.resolved.count else {
            lock.unlock()
            return
        }
        recovery.attempts += 1
        recovery.phase = .resolving
        recovery.expectedURL = nil
        var debugDelay: TimeInterval = 0
#if DEBUG
        if debugForceCDNFallbackStorage {
            // The delay would otherwise eat the 10s budget, so extend it.
            debugDelay = debugCDNDelay
            recovery.deadline = recovery.deadline.addingTimeInterval(debugDelay)
            retryDeadline = recovery.deadline
        }
#endif
        cdnRecovery = recovery
        retryAttempts = recovery.attempts

        let generation = recovery.loadGeneration
        let index = recovery.currentIndex
        let attemptNumber = recovery.attempts
        let failedHosts = recovery.failedHosts
        let deadline = recovery.deadline
        let source = recovery.source
        // A local file in the next slot needs no validation.
        let nextIndex = index + 1 < queue.tracks.count
            ? CDNRecoveryPlanner.nextIndexToValidate(resolved: queue.resolved, currentIndex: index)
            : nil
        let currentCandidates = candidatesLocked(at: index, failedHosts: failedHosts)
        let nextCandidates = nextIndex.map { candidatesLocked(at: $0, failedHosts: failedHosts) } ?? []
        lock.unlock()

        logger.notice("[PB] CDN recovery resolving kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) attempt=\(attemptNumber, privacy: .public) idx=\(index, privacy: .public) next=\(nextIndex.map(String.init) ?? "none", privacy: .public) candidates=\(currentCandidates.count, privacy: .public)/\(nextCandidates.count, privacy: .public) demotedHosts=\(failedHosts.count, privacy: .public)")

        var attemptResolver = cdnResolver
#if DEBUG
        if debugDelay > 0 {
            attemptResolver.rejectFinalHost = { host in host.hasPrefix("dn") && host.hasSuffix(".archive.org") }
            logger.notice("[PB] CDN recovery debug delay \(debugDelay, format: .fixed(precision: 1), privacy: .public)s kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) attempt=\(attemptNumber, privacy: .public)")
        }
#endif
        let resolver = attemptResolver
        let startDelay = debugDelay
        Task { [weak self] in
            if startDelay > 0 {
                try? await Task.sleep(for: .seconds(startDelay))
            }
            async let currentResult = resolver.resolve(candidates: currentCandidates, deadline: deadline)
            async let nextResult = resolver.resolve(candidates: nextCandidates, deadline: deadline)
            let (current, next) = await (currentResult, nextResult)
            // Bind to a constant: a weak capture is a mutable box, which Swift 6
            // will not send into the main-queue closure. The engine is
            // `@unchecked Sendable`, so a strong reference is safe to send.
            guard let engine = self else { return }
            // Engine state, the player, and callbacks are main-thread territory.
            DispatchQueue.main.async {
                engine.finishCDNRecoveryAttempt(
                    recoveryID: recoveryID,
                    generation: generation,
                    attempt: attemptNumber,
                    currentIndex: index,
                    nextIndex: nextIndex,
                    current: current,
                    next: next
                )
            }
        }
    }

    /// Ordered candidate URLs for one track. Caller MUST hold `lock`.
    private func candidatesLocked(at index: Int, failedHosts: Set<String>) -> [URL] {
        let fallbacks = queue.fallbacks.indices.contains(index) ? queue.fallbacks[index] : []
        return CDNURLResolver.orderedCandidates(
            canonical: queue.tracks[index],
            fallbacks: fallbacks,
            failedHosts: failedHosts
        )
    }

    private func finishCDNRecoveryAttempt(
        recoveryID: Int,
        generation: Int,
        attempt: Int,
        currentIndex: Int,
        nextIndex: Int?,
        current: CDNResolution,
        next: CDNResolution
    ) {
        lock.lock()
        guard var recovery = cdnRecovery,
              recovery.id == recoveryID,
              recovery.loadGeneration == generation,
              loadGeneration == generation,
              recovery.phase == .resolving,
              recovery.attempts == attempt else {
            lock.unlock()
            logger.notice("[PB] CDN resolve result discarded kind=cdn loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) attempt=\(attempt, privacy: .public) reason=stale")
            return
        }
        let source = recovery.source

        // Remember failing hosts for the rest of this recovery.
        for resolveAttempt in current.attempts + next.attempts {
            if let host = resolveAttempt.failedHost {
                recovery.failedHosts.insert(host)
            }
        }

        let currentReady = current.resolvedURL != nil
        let nextReady = nextIndex == nil || next.resolvedURL != nil
        guard currentReady, nextReady, let currentURL = current.resolvedURL,
              currentIndex < queue.resolved.count else {
            cdnRecovery = recovery
            lock.unlock()
            logResolveAttempts(current, track: currentIndex, source: source, generation: generation, recoveryID: recoveryID, attempt: attempt)
            if let nextIndex {
                logResolveAttempts(next, track: nextIndex, source: source, generation: generation, recoveryID: recoveryID, attempt: attempt)
            }
            logger.notice("[PB] CDN recovery foreground not ready kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) attempt=\(attempt, privacy: .public) currentReady=\(currentReady, privacy: .public) nextReady=\(nextReady, privacy: .public)")
            scheduleNextCDNAttempt(recoveryID: recoveryID)
            return
        }

        // Install the validated URLs. Everything after the next track keeps its
        // existing mapping until Phase 3 refreshes the rest of the queue.
        let installed = CDNRecoveryPlanner.install(
            resolved: queue.resolved,
            currentIndex: currentIndex,
            currentURL: currentURL,
            nextIndex: nextIndex,
            nextURL: next.resolvedURL
        )
        queue.resolved = installed.resolved
        pendingQueueURLs = installed.pending
        let pendingCount = pendingQueueURLs.count
        // Read live intent: the user may have paused or pressed play meanwhile.
        let shouldResume = shouldBePlaying
        let currentHost = currentURL.host
        let startedAt = recovery.startedAt

        if shouldResume {
            recovery.phase = .starting
            recovery.expectedURL = currentURL
            cdnRecovery = recovery
            hasStartedAnyTrack = true
            lock.unlock()

            logResolveAttempts(current, track: currentIndex, source: source, generation: generation, recoveryID: recoveryID, attempt: attempt)
            if let nextIndex {
                logResolveAttempts(next, track: nextIndex, source: source, generation: generation, recoveryID: recoveryID, attempt: attempt)
            }
            logger.notice("[PB] CDN recovery foreground ready kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) attempt=\(attempt, privacy: .public) idx=\(currentIndex, privacy: .public) host=\(currentHost ?? "nil", privacy: .public) pending=\(pendingCount, privacy: .public)")
            logger.notice("[PB] CDN recovery play submitted kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) url=\(currentURL.absoluteString, privacy: .public)")
            player.play(url: currentURL)
            startProgressTimer()
            return
        }

        // Paused intent: leave the engine prepared but silent. The next user
        // Play does a fresh `play(url:)` of the new current URL.
        let restoreVolume = preparePausedAfterRecoveryLocked(recovery)
        lock.unlock()

        logResolveAttempts(current, track: currentIndex, source: source, generation: generation, recoveryID: recoveryID, attempt: attempt)
        if let nextIndex {
            logResolveAttempts(next, track: nextIndex, source: source, generation: generation, recoveryID: recoveryID, attempt: attempt)
        }
        let elapsed = Date.now.timeIntervalSince(startedAt)
        logger.notice("[PB] CDN recovery prepared paused kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) attempt=\(attempt, privacy: .public) idx=\(currentIndex, privacy: .public) host=\(currentHost ?? "nil", privacy: .public) elapsedMs=\(Int(elapsed * 1000), privacy: .public) behavior=canonicalRefresh")
        if let restoreVolume {
            player.volume = restoreVolume
        }
        onRetryStateChange?(false)
        onStateChange?(.paused)
    }

    /// End a recovery without playing: the engine stays prepared so the next
    /// user Play does a fresh `play(url:)` of the new current URL. Keeps the
    /// captured resume position. Returns the volume to restore, if the
    /// recovery owned the mute. Caller MUST hold `lock`.
    private func preparePausedAfterRecoveryLocked(_ recovery: CDNRecovery) -> Float? {
        cdnRecovery = nil
        retryGeneration += 1
        retryDeadline = nil
        retryAttempts = 0
        hasStartedAnyTrack = false
        hasDiagnosedNetworkFailure = false
        guard recovery.ownsMute else { return nil }
        let volume = savedVolumeBeforeRetry
        savedVolumeBeforeRetry = nil
        return volume
    }

    private func logResolveAttempts(
        _ resolution: CDNResolution,
        track: Int,
        source: NetworkFailureSource,
        generation: Int,
        recoveryID: Int,
        attempt: Int
    ) {
        for resolveAttempt in resolution.attempts {
            logger.notice("[PB] CDN resolve attempt kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) attempt=\(attempt, privacy: .public) track=\(track, privacy: .public) \(resolveAttempt.logDescription, privacy: .public)")
        }
        if resolution.resolvedURL == nil {
            logger.warning("[PB] CDN resolve failed kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) attempt=\(attempt, privacy: .public) track=\(track, privacy: .public) tried=\(resolution.attempts.count, privacy: .public)")
        }
    }

    /// After a failed attempt: schedule the next one on the existing backoff
    /// schedule, or surface the final error when the budget is spent.
    private func scheduleNextCDNAttempt(recoveryID: Int) {
        lock.lock()
        guard var recovery = cdnRecovery,
              recovery.id == recoveryID,
              recovery.loadGeneration == loadGeneration else {
            lock.unlock()
            return
        }
        let done = max(recovery.attempts, 1)
        let hasDelay = done <= retryDelays.count
        let delay = hasDelay ? retryDelays[done - 1] : 0
        let withinBudget = hasDelay && Date.now.addingTimeInterval(delay) < recovery.deadline
        let source = recovery.source
        guard withinBudget else {
            lock.unlock()
            surfaceNetworkFailure(kind: .cdnServer, source: source, recoveryID: recoveryID)
            return
        }
        recovery.phase = .backoff
        cdnRecovery = recovery
        retryGeneration += 1
        let retryToken = retryGeneration
        let generation = recovery.loadGeneration
        lock.unlock()

        logger.warning("[PB] CDN recovery retry scheduled kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) afterAttempt=\(done, privacy: .public) in=\(delay, format: .fixed(precision: 1), privacy: .public)s behavior=canonicalRefresh")
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self else { return }
            let isCurrent = self.lock.withLock {
                self.retryGeneration == retryToken
                    && self.loadGeneration == generation
                    && self.cdnRecovery?.id == recoveryID
            }
            guard isCurrent else {
                self.logger.notice("[PB] CDN recovery retry dropped kind=cdn loadGeneration=\(generation, privacy: .public) recoveryId=\(recoveryID, privacy: .public) reason=stale")
                return
            }
            self.runCDNRecoveryAttempt(recoveryID: recoveryID)
        }
    }

    // MARK: - Network failure diagnostics

    /// AudioStreaming 1.4.4 collapses every HTTP response >= 300 into the
    /// context-free `NetworkError.serverError`. Probe the exact failed CDN URL
    /// so bug reports retain the status code and redirect host that the
    /// dependency discards. The recovery resolver logs every canonical and
    /// fallback candidate itself, so the canonical URL is not probed here.
    private func diagnoseNetworkFailureIfNeeded(
        recovery: CDNRecovery,
        source: NetworkFailureSource
    ) {
        lock.lock()
        guard !hasDiagnosedNetworkFailure,
              queue.currentIndex >= 0,
              queue.currentIndex < queue.tracks.count,
              queue.currentIndex < queue.resolved.count else {
            lock.unlock()
            return
        }
        hasDiagnosedNetworkFailure = true
        let generation = loadGeneration
        let index = queue.currentIndex
        let failedURL = queue.resolved[index]
        lock.unlock()

        Task { [weak self] in
            guard let self else { return }
            let failedProbe = await self.probePlaybackURL(failedURL)

            let isCurrent = self.lock.withLock {
                self.loadGeneration == generation && self.queue.currentIndex == index
            }
            guard isCurrent else {
                self.logger.notice("[PB] networkDiagnostic discarded kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recovery.id, privacy: .public) idx=\(index, privacy: .public) reason=queueChanged")
                return
            }

            self.logger.error("[PB] networkDiagnostic kind=cdn source=\(source.rawValue, privacy: .public) loadGeneration=\(generation, privacy: .public) recoveryId=\(recovery.id, privacy: .public) target=failed \(failedProbe.logDescription, privacy: .public)")
        }
    }

    /// A two-byte ranged GET mirrors AudioStreaming's playback requests closely
    /// while avoiding a full media download. It returns at the response headers
    /// and never reads the body, so a server that ignores `Range` cannot make us
    /// download the track. The ephemeral session and explicit cache directives
    /// ensure the diagnostic observes current Archive routing.
    private func probePlaybackURL(_ url: URL) async -> HTTPProbeResult {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 10
        request.setValue("bytes=0-1", forHTTPHeaderField: "Range")
        request.setValue("*/*", forHTTPHeaderField: "Accept")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

        do {
            let (bytes, response) = try await diagnosticSession.bytes(for: request)
            bytes.task.cancel()
            guard let http = response as? HTTPURLResponse else {
                return HTTPProbeResult(
                    requestedHost: url.host,
                    finalHost: response.url?.host,
                    statusCode: nil,
                    contentRange: nil,
                    server: nil,
                    retryAfter: nil,
                    errorCode: "non-http-response"
                )
            }
            return HTTPProbeResult(
                requestedHost: url.host,
                finalHost: http.url?.host,
                statusCode: http.statusCode,
                contentRange: http.value(forHTTPHeaderField: "Content-Range"),
                server: http.value(forHTTPHeaderField: "Server"),
                retryAfter: http.value(forHTTPHeaderField: "Retry-After"),
                errorCode: nil
            )
        } catch {
            let nsError = error as NSError
            return HTTPProbeResult(
                requestedHost: url.host,
                finalHost: nil,
                statusCode: nil,
                contentRange: nil,
                server: nil,
                retryAfter: nil,
                errorCode: "\(nsError.domain):\(nsError.code)"
            )
        }
    }

    func audioPlayerDidCancel(player: AudioPlayer, queuedItems: [AudioEntryId]) {
        logger.notice("delegate: didCancel \(queuedItems.count) queued items")
    }

    func audioPlayerDidReadMetadata(player: AudioPlayer, metadata: [String: String]) {
        logger.notice("delegate: didReadMetadata \(metadata)")
    }

    private func mapState(_ state: AudioPlayerState) -> PlaybackState {
        switch state {
        case .ready:
            return .idle
        case .running:
            return .loading
        case .playing:
            return .playing
        case .bufferring:
            return .buffering
        case .paused:
            return .paused
        case .stopped:
            return .idle
        case .error:
            lock.lock()
            let preserved = lastError
            lock.unlock()
            return .error(preserved ?? .unknown("Player error"))
        case .disposed:
            return .idle
        }
    }
}
