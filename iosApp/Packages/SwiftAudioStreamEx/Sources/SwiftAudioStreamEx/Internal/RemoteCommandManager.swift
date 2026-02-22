import Foundation
import MediaPlayer
import os

/// Registers and manages MPRemoteCommandCenter handlers.
@MainActor
final class RemoteCommandManager {
    private let logger = Logger(subsystem: "SwiftAudioStreamEx", category: "RemoteCommand")

    var onPlay: (() -> Void)?
    var onPause: (() -> Void)?
    var onTogglePlayPause: (() -> Void)?
    var onNext: (() -> Void)?
    var onPrevious: (() -> Void)?
    var onSeek: ((TimeInterval) -> Void)?
    var onSkipForward: ((TimeInterval) -> Void)?
    var onSkipBackward: ((TimeInterval) -> Void)?

    private var commandTargets: [Any] = []

    func setup() {
        teardown()
        let center = MPRemoteCommandCenter.shared()

        let playTarget = center.playCommand.addTarget { [weak self] _ in
            self?.onPlay?()
            return .success
        }
        center.playCommand.isEnabled = true
        commandTargets.append(playTarget)

        let pauseTarget = center.pauseCommand.addTarget { [weak self] _ in
            self?.onPause?()
            return .success
        }
        center.pauseCommand.isEnabled = true
        commandTargets.append(pauseTarget)

        let toggleTarget = center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.onTogglePlayPause?()
            return .success
        }
        center.togglePlayPauseCommand.isEnabled = true
        commandTargets.append(toggleTarget)

        let nextTarget = center.nextTrackCommand.addTarget { [weak self] _ in
            self?.onNext?()
            return .success
        }
        center.nextTrackCommand.isEnabled = true
        commandTargets.append(nextTarget)

        let prevTarget = center.previousTrackCommand.addTarget { [weak self] _ in
            self?.onPrevious?()
            return .success
        }
        center.previousTrackCommand.isEnabled = true
        commandTargets.append(prevTarget)

        let seekTarget = center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self?.onSeek?(positionEvent.positionTime)
            return .success
        }
        center.changePlaybackPositionCommand.isEnabled = true
        commandTargets.append(seekTarget)

        center.skipForwardCommand.preferredIntervals = [15]
        let skipFwdTarget = center.skipForwardCommand.addTarget { [weak self] event in
            guard let skipEvent = event as? MPSkipIntervalCommandEvent else {
                return .commandFailed
            }
            self?.onSkipForward?(skipEvent.interval)
            return .success
        }
        center.skipForwardCommand.isEnabled = true
        commandTargets.append(skipFwdTarget)

        center.skipBackwardCommand.preferredIntervals = [15]
        let skipBkTarget = center.skipBackwardCommand.addTarget { [weak self] event in
            guard let skipEvent = event as? MPSkipIntervalCommandEvent else {
                return .commandFailed
            }
            self?.onSkipBackward?(skipEvent.interval)
            return .success
        }
        center.skipBackwardCommand.isEnabled = true
        commandTargets.append(skipBkTarget)

        logger.info("Remote commands registered")
    }

    func updateCommandState(hasNext: Bool, hasPrevious: Bool) {
        let center = MPRemoteCommandCenter.shared()
        center.nextTrackCommand.isEnabled = hasNext
        center.previousTrackCommand.isEnabled = hasPrevious
    }

    func teardown() {
        let center = MPRemoteCommandCenter.shared()
        for target in commandTargets {
            center.playCommand.removeTarget(target)
            center.pauseCommand.removeTarget(target)
            center.togglePlayPauseCommand.removeTarget(target)
            center.nextTrackCommand.removeTarget(target)
            center.previousTrackCommand.removeTarget(target)
            center.changePlaybackPositionCommand.removeTarget(target)
            center.skipForwardCommand.removeTarget(target)
            center.skipBackwardCommand.removeTarget(target)
        }
        commandTargets.removeAll()
    }

    deinit {
        // Can't call teardown() here since it's @MainActor isolated,
        // but the command targets will be released with this object
    }
}
