import Foundation
import os

/// UI-facing state for end-of-show auto-advance (ADR-0010): the cancelable
/// countdown banner and the "Queue A" interrupt snackbar. Owned by
/// `PlaylistServiceImpl` (which drives it) and read by a root overlay.
@Observable
@MainActor
final class PlaybackAdvanceCoordinator {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "PlayQueueAdvance")

    // MARK: - Countdown (auto-advance)

    /// Seconds remaining before the next show plays. `nil` when no countdown is
    /// active. Drives the "Next show in N… [Cancel]" banner.
    private(set) var countdownRemaining: Int?

    /// Title of the show the countdown will advance to (for the banner label).
    private(set) var countdownNextTitle: String?

    private var countdownTask: Task<Void, Never>?
    private var onCountdownFire: (() -> Void)?

    /// Start a cancelable countdown. `onFire` runs when it reaches zero (unless
    /// canceled). Any prior countdown is replaced.
    func startCountdown(seconds: Int, nextTitle: String?, onFire: @escaping () -> Void) {
        cancelCountdown()
        countdownRemaining = seconds
        countdownNextTitle = nextTitle
        onCountdownFire = onFire
        countdownTask = Task { [weak self] in
            guard let self else { return }
            while let remaining = self.countdownRemaining, remaining > 0 {
                try? await Task.sleep(for: .seconds(1))
                guard !Task.isCancelled else { return }
                self.countdownRemaining = (self.countdownRemaining ?? 1) - 1
            }
            guard !Task.isCancelled else { return }
            let fire = self.onCountdownFire
            self.resetCountdown()
            fire?()
        }
    }

    /// Cancel the countdown without advancing (leaves the queue intact).
    func cancelCountdown() {
        countdownTask?.cancel()
        countdownTask = nil
        resetCountdown()
    }

    private func resetCountdown() {
        countdownRemaining = nil
        countdownNextTitle = nil
        onCountdownFire = nil
    }

    // MARK: - Interrupt "Queue A" snackbar

    struct RequeuePayload: Identifiable, Equatable {
        let id = UUID()
        let showId: String
        let showTitle: String
        let recordingId: String?
        let resumeTrackIndex: Int?
        let resumePositionMs: Int64?
    }

    /// Pending "Now playing B · [Queue A]" snackbar. `nil` when none is shown.
    private(set) var pendingRequeue: RequeuePayload?

    private var requeueDismissTask: Task<Void, Never>?

    /// Show the interrupt snackbar offering to re-queue the interrupted show.
    /// Auto-dismisses after a few seconds if ignored.
    func presentRequeue(_ payload: RequeuePayload) {
        requeueDismissTask?.cancel()
        pendingRequeue = payload
        requeueDismissTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(6))
            guard !Task.isCancelled else { return }
            self?.pendingRequeue = nil
        }
    }

    func dismissRequeue() {
        requeueDismissTask?.cancel()
        requeueDismissTask = nil
        pendingRequeue = nil
    }
}
