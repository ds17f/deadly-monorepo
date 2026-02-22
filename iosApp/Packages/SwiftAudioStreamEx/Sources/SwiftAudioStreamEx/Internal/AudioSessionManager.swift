import AVFoundation
import Foundation
import os

/// Manages AVAudioSession configuration, interruptions, and route changes.
@MainActor
final class AudioSessionManager {
    private let logger = Logger(subsystem: "SwiftAudioStreamEx", category: "AudioSession")

    var onInterruptionBegan: (() -> Void)?
    var onInterruptionEnded: ((Bool) -> Void)?  // Bool = shouldResume
    var onRouteChangedToSpeaker: (() -> Void)?  // Headphone unplug

    #if os(iOS)
    private var isObserving = false
    #endif

    func configure() {
        #if os(iOS)
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
            logger.info("Audio session configured for playback")
        } catch {
            logger.error("Failed to configure audio session: \(error.localizedDescription)")
        }
        startObserving()
        #endif
    }

    func deactivate() {
        #if os(iOS)
        stopObserving()
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            logger.warning("Failed to deactivate audio session: \(error.localizedDescription)")
        }
        #endif
    }

    #if os(iOS)
    // MARK: - Interruption handling

    private func startObserving() {
        guard !isObserving else { return }
        isObserving = true

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance()
        )

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleRouteChange(_:)),
            name: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance()
        )
    }

    private func stopObserving() {
        guard isObserving else { return }
        isObserving = false
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleInterruption(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }

        switch type {
        case .began:
            logger.info("Audio interruption began")
            onInterruptionBegan?()

        case .ended:
            let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            let shouldResume = options.contains(.shouldResume)
            logger.info("Audio interruption ended, shouldResume: \(shouldResume)")
            onInterruptionEnded?(shouldResume)

        @unknown default:
            break
        }
    }

    @objc private func handleRouteChange(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }

        if reason == .oldDeviceUnavailable {
            logger.info("Audio route changed: old device unavailable (headphone unplug)")
            onRouteChangedToSpeaker?()
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }
    #endif
}
