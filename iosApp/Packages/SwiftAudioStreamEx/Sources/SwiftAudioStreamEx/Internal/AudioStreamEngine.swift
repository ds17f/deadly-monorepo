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

    /// Monotonically-increasing token bumped on every `loadQueue` call. A stale
    /// `resolveAllRedirects` completion whose captured generation no longer matches
    /// is dropped — this prevents a previously-tapped recording from clobbering
    /// the queue after the user switches recordings mid-load.
    nonisolated(unsafe) private var loadGeneration: Int = 0

    /// Last error reported via the AudioStreaming delegate, preserved so it can be
    /// surfaced when `mapState` later transitions to `.error` (which otherwise has
    /// no context and falls back to a generic "Player error" string).
    nonisolated(unsafe) private var lastError: StreamPlayerError?

    /// Minimum duration (seconds) a track must have played to count as a real completion.
    private let minimumPlayDuration: Double = 0.5

    // MARK: - AudioEngineProtocol callbacks
    nonisolated(unsafe) var onStateChange: ((PlaybackState) -> Void)?
    nonisolated(unsafe) var onTrackComplete: (() -> Void)?
    nonisolated(unsafe) var onProgressUpdate: ((PlaybackProgress) -> Void)?
    nonisolated(unsafe) var onError: ((StreamPlayerError) -> Void)?

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

    /// Resolves all redirects upfront, then plays track at `index` and queues all remaining
    /// tracks with AudioStreaming's internal gapless queue.
    func loadQueue(urls: [URL], startingAt index: Int) {
        lock.lock()
        loadGeneration += 1
        let generation = loadGeneration
        lock.unlock()

        let firstName = urls.first?.lastPathComponent ?? "(none)"
        let lastName = urls.last?.lastPathComponent ?? "(none)"
        logger.notice("[PB] loadQueue gen=\(generation, privacy: .public) count=\(urls.count, privacy: .public) startIdx=\(index, privacy: .public) first=\(firstName, privacy: .public) last=\(lastName, privacy: .public)")

        resolveAllRedirects(for: urls) { [weak self] resolved in
            guard let self else { return }

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
            self.logger.notice("[PB] loadQueue resolved gen=\(generation, privacy: .public) \(snapshot, privacy: .public) — play \(resolved[index].absoluteString, privacy: .public)")

            // Play the first track — remaining tracks queued after didStartPlaying fires
            self.player.play(url: resolved[index])
            self.startProgressTimer()
        }
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
        let snapshot = queueSnapshotLocked()
        lock.unlock()

        logger.notice("[PB] rewindToPrevious \(before, privacy: .public) → \(self.currentIndex, privacy: .public) \(snapshot, privacy: .public) play=\(remaining[0].absoluteString, privacy: .public)")

        player.play(url: remaining[0])
        startProgressTimer()
        return true
    }

    func skipTo(index: Int) -> Bool {
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
        let snapshot = queueSnapshotLocked()
        lock.unlock()

        logger.notice("[PB] skipTo \(before, privacy: .public) → \(index, privacy: .public) \(snapshot, privacy: .public) play=\(remaining[0].absoluteString, privacy: .public)")

        player.play(url: remaining[0])
        startProgressTimer()
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
        lock.unlock()
        logger.notice("[PB] didStartPlaying entry=\(entryId.id, privacy: .public) \(entrySnapshot, privacy: .public)")
        onStateChange?(.playing)
        startProgressTimer()

        // Sync currentIndex to the track AudioStreaming is actually playing.
        // This handles gapless auto-advance (where stopReason is .none, not .eof)
        // and keeps the index correct regardless of how the transition happened.
        lock.lock()
        if let actualIndex = queue.resolved.firstIndex(where: { $0.absoluteString == entryId.id }) {
            let previousIndex = queue.currentIndex
            let wasAutoAdvance = actualIndex != queue.currentIndex
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

    func audioPlayerStateChanged(player: AudioPlayer, with newState: AudioPlayerState, previous: AudioPlayerState) {
        logger.notice("[PB] stateChanged \(String(describing: previous), privacy: .public) → \(String(describing: newState), privacy: .public)")
        let mapped = mapState(newState)
        onStateChange?(mapped)

        switch newState {
        case .playing:
            startProgressTimer()
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
        }
    }

    func audioPlayerUnexpectedError(player: AudioPlayer, error: AudioPlayerError) {
        let mapped = StreamPlayerError.engineError(error.localizedDescription)
        lock.lock()
        lastError = mapped
        lock.unlock()
        logger.error("[PB] unexpectedError type=\(String(describing: error), privacy: .public) desc=\(error.localizedDescription, privacy: .public)")
        onError?(mapped)
        onStateChange?(.error(mapped))
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
