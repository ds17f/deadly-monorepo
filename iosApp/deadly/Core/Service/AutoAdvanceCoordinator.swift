import Foundation
import os.log

private let logger = Logger(subsystem: "com.grateful.deadly", category: "AutoAdvance")

/// ADR-0010 Chunk 2 — chronological auto-advance (iOS).
///
/// An independent coordinator whose only input is
/// `PlaylistServiceImpl.onShowCompleted` (the Chunk 1 end-of-show signal). It
/// reads no transport/Connect state to decide whether/what to advance — that
/// interrogation was the v1 whack-a-mole. Gating is intrinsic: only the
/// audio-producing device reaches end-of-show, so only it advances.
///
/// On end of show: **park** (`sendStop`, so the server stops believing we're
/// playing and can't drag us back) → **cancelable countdown** →
/// `playShow(next chronological)`, whose output (local audio + sendLoad) is
/// identical to a user tap, so remote Connect clients follow for free.
@MainActor
@Observable
final class AutoAdvanceCoordinator {
    struct Countdown: Equatable {
        let secondsRemaining: Int
        let nextShowLabel: String?
    }

    /// Drives the cancelable "next show in Ns" overlay; nil when idle.
    private(set) var countdown: Countdown?

    private let playlistService: PlaylistServiceImpl
    private let showRepository: any ShowRepository
    private let connectService: ConnectService
    private var advanceTask: Task<Void, Never>?

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
    }

    /// User canceled the pending advance.
    func cancel() {
        logger.notice("auto-advance: canceled by user")
        advanceTask?.cancel()
        advanceTask = nil
        countdown = nil
    }

    private func onShowCompleted(_ completedShowId: String) {
        logger.notice("auto-advance: show completed = \(completedShowId, privacy: .public)")
        advanceTask?.cancel()
        advanceTask = Task { @MainActor [weak self] in
            guard let self else { return }

            // 1. Park: stop the server believing we're playing so it can't drag
            //    us back during the countdown. No-op on the wire when not connected.
            self.connectService.sendStop()

            // 2. Resolve the next chronological show.
            guard
                let completed = try? self.showRepository.getShowById(completedShowId),
                let next = try? self.showRepository.getNextShow(afterDate: completed.date)
            else {
                logger.notice("auto-advance: no next show after \(completedShowId, privacy: .public)")
                self.countdown = nil
                return
            }

            // 3. Cancelable countdown (server is parked, so no drag-back).
            var remaining = Self.countdownSeconds
            while remaining > 0 {
                if Task.isCancelled { return }
                self.countdown = Countdown(secondsRemaining: remaining, nextShowLabel: next.displayTitle)
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                remaining -= 1
            }
            if Task.isCancelled { return }
            self.countdown = nil

            // 4. Advance — identical to a user tap; remotes follow via sendLoad.
            logger.notice("auto-advance: advancing to \(next.displayTitle, privacy: .public)")
            await self.playlistService.playShow(next)
        }
    }
}
