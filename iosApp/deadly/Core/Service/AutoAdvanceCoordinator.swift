import Foundation
import os.log

private let logger = Logger(subsystem: "com.grateful.deadly", category: "AutoAdvance")

/// ADR-0010 Chunk 2 + §7 — chronological auto-advance, cross-device (iOS).
///
/// Driven by `PlaylistServiceImpl.onShowCompleted` (the Chunk 1 end-of-show
/// signal). Reads no transport state to *decide* whether to advance.
///
/// In a Connect session it `announce`s the next show + a server-time deadline;
/// the shared `pendingAdvance` note then drives the countdown UI **and** the
/// advance on every device (the note-poll below). Offline it runs a purely local
/// countdown. The active device advances when the note is present and the
/// deadline passes; its `playShow` load clears the note for all. Cancel / Play
/// now work from any device (commands when remote; act directly when
/// active/offline).
@MainActor
@Observable
final class AutoAdvanceCoordinator {
    struct Countdown: Equatable {
        let secondsRemaining: Int
        let nextShow: Show
    }

    /// Drives the "Next up in Ns" UI; nil when idle.
    private(set) var countdown: Countdown?

    private let playlistService: PlaylistServiceImpl
    private let showRepository: any ShowRepository
    private let connectService: ConnectService

    private var localTask: Task<Void, Never>?
    private var noteWatcher: Task<Void, Never>?

    // Cache the resolved show for the current note, and guard one-shot advance.
    private var resolvedShowId: String?
    private var resolvedShow: Show?
    private var advanceTriggered = false

    private static let countdownSeconds = 15

    init(
        playlistService: PlaylistServiceImpl,
        showRepository: any ShowRepository,
        connectService: ConnectService
    ) {
        self.playlistService = playlistService
        self.showRepository = showRepository
        self.connectService = connectService
        playlistService.onShowCompleted = { [weak self] completedShowId in
            self?.onShowCompleted(completedShowId)
        }
        startNoteWatcher()
    }

    /// Cancel the pending advance.
    func cancel() {
        logger.notice("auto-advance: cancel")
        localTask?.cancel()
        localTask = nil
        countdown = nil
        if connectService.isConnected { connectService.sendCancelAdvance() }
    }

    /// Skip the countdown ("Play now").
    func playNow() {
        guard let show = countdown?.nextShow else { return }
        if connectService.isConnected && !connectService.isActiveDevice {
            connectService.sendAdvanceNow()
            return
        }
        localTask?.cancel()
        localTask = nil
        countdown = nil
        Task { await playlistService.playShow(show) }
    }

    // ADR-0010 §7: poll the shared note (1s) — drives the countdown + advance on
    // every device when connected. Cheap; mirrors the always-on interpolation
    // ticker elsewhere. Offline, the local countdown task owns `countdown`.
    private func startNoteWatcher() {
        noteWatcher = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                self?.tickNote()
                try? await Task.sleep(nanoseconds: 1_000_000_000)
            }
        }
    }

    private func tickNote() {
        guard connectService.isConnected else { return } // offline: local task owns it
        guard let note = connectService.connectState?.pendingAdvance else {
            countdown = nil
            resolvedShowId = nil
            resolvedShow = nil
            advanceTriggered = false
            return
        }
        if resolvedShowId != note.showId {
            resolvedShowId = note.showId
            resolvedShow = try? showRepository.getShowById(note.showId)
            advanceTriggered = false
        }
        guard let show = resolvedShow else { return }

        let serverNow = Date().timeIntervalSince1970 * 1000 + connectService.serverTimeOffsetMs
        let remaining = Int(ceil((note.deadline - serverNow) / 1000.0))
        if remaining <= 0 {
            countdown = nil
            if connectService.isActiveDevice && !advanceTriggered {
                advanceTriggered = true
                logger.notice("auto-advance: advancing to \(show.displayTitle, privacy: .public)")
                Task { await playlistService.playShow(show) } // load clears the note
            }
            return
        }
        countdown = Countdown(secondsRemaining: remaining, nextShow: show)
    }

    private func onShowCompleted(_ completedShowId: String) {
        logger.notice("auto-advance: show completed = \(completedShowId, privacy: .public)")
        localTask?.cancel()
        localTask = Task { @MainActor [weak self] in
            guard let self else { return }
            guard
                let completed = try? self.showRepository.getShowById(completedShowId),
                let next = try? self.showRepository.getNextShow(afterDate: completed.date)
            else {
                logger.notice("auto-advance: no next show after \(completedShowId, privacy: .public)")
                return
            }

            if self.connectService.isConnected {
                // In a session: announce; the note-poll drives the rest. Deadline
                // in server time so every device ticks to the same instant.
                let deadline = Date().timeIntervalSince1970 * 1000
                    + self.connectService.serverTimeOffsetMs
                    + Double(Self.countdownSeconds) * 1000
                self.connectService.sendAnnounceNext(showId: next.id, deadline: deadline)
                return
            }

            // Offline: local-only countdown, then advance.
            var remaining = Self.countdownSeconds
            while remaining > 0 {
                if Task.isCancelled { return }
                self.countdown = Countdown(secondsRemaining: remaining, nextShow: next)
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                remaining -= 1
            }
            if Task.isCancelled { return }
            self.countdown = nil
            logger.notice("auto-advance: advancing to \(next.displayTitle, privacy: .public)")
            await self.playlistService.playShow(next)
        }
    }
}
