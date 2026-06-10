import AudioStreaming
import AVFoundation
import Foundation
import os

/// Wraps AudioStreaming's AudioPlayer, manages queue state and redirect resolution.
/// Gapless playback is achieved by passing ALL tracks to AudioStreaming's internal queue
/// upfront, letting it pre-buffer the next track while the current one plays.
final class AudioStreamEngine: NSObject, AudioEngineProtocol, @unchecked Sendable {
    private let player: AudioPlayer
    private let lock = NSLock()
    private let logger = Logger(subsystem: "SwiftAudioStreamEx", category: "Engine")

    private struct QueueState {
        var tracks: [URL] = []          // original URLs
        var resolved: [URL] = []        // redirect-resolved URLs
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
        player.resume()
        startProgressTimer()
    }

    func pause() {
        logger.info("pause")
        player.pause()
        stopProgressTimer()
        sendCurrentProgress()
    }

    func seek(to time: TimeInterval) {
        logger.info("seek to \(time, format: .fixed(precision: 1))s")
        lock.lock()
        lastUserSeekTarget = time
        lock.unlock()
        player.seek(to: time)
    }

    func stop() {
        logger.info("stop")
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
    func loadQueue(urls: [URL], startingAt index: Int, autoPlay: Bool = true) {
        lock.lock()
        loadGeneration += 1
        let generation = loadGeneration
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
        // Capture the debug delay NOW so it binds to this generation, not
        // whichever loadQueue's resolve completes first.
        let delayThisResolve = debugNextResolveDelay
        debugNextResolveDelay = 0
        lock.unlock()
        if let restore = staleVolume {
            player.volume = restore
        }

        let firstName = urls.first?.lastPathComponent ?? "(none)"
        let lastName = urls.last?.lastPathComponent ?? "(none)"
        logger.notice("[PB] loadQueue gen=\(generation, privacy: .public) count=\(urls.count, privacy: .public) startIdx=\(index, privacy: .public) autoPlay=\(autoPlay, privacy: .public) first=\(firstName, privacy: .public) last=\(lastName, privacy: .public)")

        resolveAllRedirects(for: urls) { [weak self] resolved in
            guard let self else { return }

            // `delayThisResolve` was captured at the start of this specific
            // loadQueue call, so it always binds to THIS generation.
            if delayThisResolve > 0 {
                self.logger.warning("[PB] DEBUG delaying resolve gen=\(generation, privacy: .public) by \(delayThisResolve, format: .fixed(precision: 1), privacy: .public)s")
                DispatchQueue.main.asyncAfter(deadline: .now() + delayThisResolve) { [weak self] in
                    self?.processResolveCompletion(resolved: resolved, urls: urls, index: index, generation: generation, autoPlay: autoPlay)
                }
                return
            }
            self.processResolveCompletion(resolved: resolved, urls: urls, index: index, generation: generation, autoPlay: autoPlay)
        }
    }

    /// Body of the `resolveAllRedirects` completion. Extracted so the debug
    /// delay can defer it without forking the closure body.
    private func processResolveCompletion(resolved: [URL], urls: [URL], index: Int, generation: Int, autoPlay: Bool) {
            self.lock.lock()
            guard generation == self.loadGeneration else {
                self.lock.unlock()
                self.logger.warning("[PB] loadQueue stale gen=\(generation, privacy: .public) current=\(self.loadGeneration, privacy: .public) — dropping completion")
                return
            }
            self.queue = QueueState(tracks: urls, resolved: resolved, currentIndex: index)
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
        // User-driven retry clears the "we gave up" gate so future errors can
        // trigger the retry path again.
        let wasInError = hasSurfacedFinalError
        hasSurfacedFinalError = false
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
        guard !queue.resolved.isEmpty, queue.currentIndex < queue.resolved.count else {
            // Redirects not yet resolved — record intent; resolve handler will start.
            playWhenResolved = true
            let snapshot = queueSnapshotLocked()
            lock.unlock()
            logger.notice("[PB] startCurrent: deferring until resolve \(snapshot, privacy: .public)")
            return
        }
        let url = queue.resolved[queue.currentIndex]
        hasStartedAnyTrack = true
        playWhenResolved = false
        let snapshot = queueSnapshotLocked()
        lock.unlock()

        logger.notice("[PB] startCurrent \(snapshot, privacy: .public) play=\(url.absoluteString, privacy: .public)")
        player.play(url: url)
        startProgressTimer()
    }

    func advanceToNext() -> Bool {
        lock.lock()
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
        lock.lock()
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
        lock.lock()
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

    func appendTrack(url: URL) {
        resolveRedirect(for: url) { [weak self] resolved in
            guard let self else { return }
            self.lock.lock()
            self.queue.tracks.append(url)
            self.queue.resolved.append(resolved)
            self.lock.unlock()
            // Add to AudioStreaming's queue too
            self.player.queue(url: resolved)
        }
    }

    func insertNext(url: URL) {
        resolveRedirect(for: url) { [weak self] resolved in
            guard let self else { return }
            self.lock.lock()
            let insertIndex = self.queue.currentIndex + 1
            if insertIndex <= self.queue.tracks.count {
                self.queue.tracks.insert(url, at: insertIndex)
                self.queue.resolved.insert(resolved, at: insertIndex)
            } else {
                self.queue.tracks.append(url)
                self.queue.resolved.append(resolved)
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
        lock.lock()
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

        // Same prep as the real error path: capture position + mute (so the
        // post-retry `.playing` handler will re-seek and unmute).
        let actualProgress = player.progress
        lock.lock()
        let pendingSeekTarget = lastUserSeekTarget
        let currentPosition: TimeInterval = {
            if let seekTarget = pendingSeekTarget {
                return actualProgress > seekTarget + 1 ? actualProgress : seekTarget
            }
            return actualProgress
        }()
        if savedVolumeBeforeRetry == nil, player.volume > 0 {
            savedVolumeBeforeRetry = player.volume
        }
        if resumePositionForRetry == nil, currentPosition > 0 {
            resumePositionForRetry = currentPosition
            logger.notice("[PB] stall: captured resume position=\(currentPosition, format: .fixed(precision: 1), privacy: .public)s; muting")
        }
        lock.unlock()
        player.volume = 0

        if attemptRetry(isNetworkFailure: true) {
            return
        }

        // Out of budget — surface the error directly, same as the real path.
        let mapped: StreamPlayerError = .networkError("Can't reach Archive.org. Check your connection and try again.")
        lock.lock()
        lastError = mapped
        retryDeadline = nil
        retryAttempts = 0
        hasStartedAnyTrack = false
        let savedVolume = savedVolumeBeforeRetry
        savedVolumeBeforeRetry = nil
        let surfacedResume = resumePositionForRetry
        resumePositionForRetry = nil
        hasSurfacedFinalError = true
        lock.unlock()
        if let restore = savedVolume {
            player.volume = restore
        }
        player.stop()
        onRetryStateChange?(false)
        onError?(mapped, surfacedResume)
        onStateChange?(.error(mapped))
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
            retryAttempts = 0
            retryDeadline = nil
            hasSurfacedFinalError = false
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

        let isNetwork = isNetworkError(error)
        if isNetwork {
            // Capture position + mute BEFORE retry fires. The next time the
            // underlying player reaches `.playing`, the state-change handler
            // seeks to this position and restores the volume.
            // Prefer the user's most recent seek target over `player.progress`
            // — when a seek triggers the error (because the range request
            // failed), AudioStreaming may have reset its progress reading
            // to 0 while preparing for the new range, so player.progress
            // here is unreliable.
            let actualProgress = player.progress
            lock.lock()
            let pendingSeekTarget = lastUserSeekTarget
            let currentPosition: TimeInterval = {
                if let seekTarget = pendingSeekTarget {
                    // If the seek hadn't settled yet, the user's intent is the
                    // target. If actualProgress is meaningfully past it, the
                    // user has heard audio beyond the seek, so prefer that.
                    return actualProgress > seekTarget + 1 ? actualProgress : seekTarget
                }
                return actualProgress
            }()
            // Always remember the original volume before we mute — even if
            // we don't have a resume position to apply, we still need to
            // restore the volume on success or final error.
            //
            // BUT: if `player.volume` is already 0, someone (typically the
            // StreamPlayer's playWithPendingSeek dance) has muted us before
            // calling play. Saving 0 here would cascade: a later "restore"
            // sets volume back to 0, and the audio stays muted forever.
            // Skip the save in that case — the muter is responsible for
            // restoring their own mute when their flow completes.
            if savedVolumeBeforeRetry == nil, player.volume > 0 {
                savedVolumeBeforeRetry = player.volume
            }
            if resumePositionForRetry == nil, currentPosition > 0 {
                resumePositionForRetry = currentPosition
                logger.notice("[PB] captured resume position=\(currentPosition, format: .fixed(precision: 1), privacy: .public)s for retry; muting")
            } else if resumePositionForRetry == nil {
                logger.notice("[PB] no resume position (track had no progress); muting for retry")
            }
            lock.unlock()
            player.volume = 0
        }
        if attemptRetry(isNetworkFailure: isNetwork) {
            return
        }

        let mapped: StreamPlayerError = isNetwork
            ? .networkError("Can't reach Archive.org. Check your connection and try again.")
            : .engineError(error.localizedDescription)
        lock.lock()
        lastError = mapped
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
        logger.error("[PB] DEBUG injected synthetic network failure")
        if attemptRetry(isNetworkFailure: true) {
            return
        }
        let mapped: StreamPlayerError = .networkError("Can't reach Archive.org. Check your connection and try again.")
        lock.lock()
        lastError = mapped
        retryDeadline = nil
        retryAttempts = 0
        hasStartedAnyTrack = false
        let savedVolume = savedVolumeBeforeRetry
        savedVolumeBeforeRetry = nil
        let surfacedResume = resumePositionForRetry
        resumePositionForRetry = nil
        hasSurfacedFinalError = true
        lock.unlock()
        if let restore = savedVolume {
            player.volume = restore
        }
        player.stop()
        onRetryStateChange?(false)
        onError?(mapped, surfacedResume)
        onStateChange?(.error(mapped))
    }

    /// Schedule a retry of the current URL if this looks like a transient
    /// network failure and we're inside the retry budget. Returns `true` if
    /// a retry was scheduled (caller should NOT surface the error).
    private func attemptRetry(isNetworkFailure: Bool) -> Bool {
        guard isNetworkFailure else { return false }

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
        lock.unlock()

        logger.warning("[PB] retry attempt=\(attemptNumber, privacy: .public)/\(self.retryDelays.count, privacy: .public) in \(delay, format: .fixed(precision: 1), privacy: .public)s url=\(url.absoluteString, privacy: .public)")

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
            // Bail if a new queue or a successful play has reset retry state.
            guard self.retryDeadline != nil, self.queue.currentIndex < self.queue.resolved.count else {
                self.lock.unlock()
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
            self.logger.notice("[PB] retry firing url=\(retryURL.absoluteString, privacy: .public) (will re-queue \(pending.count, privacy: .public) pending)")
            self.player.play(url: retryURL)
        }
        return true
    }

    private func isNetworkError(_ error: AudioPlayerError) -> Bool {
        // AudioPlayerError doesn't expose a stable case discriminator, so
        // string-match on the well-known "networkError" / "serverError" tokens.
        let desc = String(describing: error).lowercased()
        return desc.contains("network") || desc.contains("server")
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
