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
    private var currentStyle: PlayerControlsStyle = .skipTrack
    private var lastHasNext: Bool = false
    private var lastHasPrevious: Bool = false

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
        commandTargets.append(nextTarget)

        let prevTarget = center.previousTrackCommand.addTarget { [weak self] _ in
            self?.onPrevious?()
            return .success
        }
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
        commandTargets.append(skipFwdTarget)

        center.skipBackwardCommand.preferredIntervals = [15]
        let skipBkTarget = center.skipBackwardCommand.addTarget { [weak self] event in
            guard let skipEvent = event as? MPSkipIntervalCommandEvent else {
                return .commandFailed
            }
            self?.onSkipBackward?(skipEvent.interval)
            return .success
        }
        commandTargets.append(skipBkTarget)

        applyEnabledState()
        logger.info("Remote commands registered (style=\(self.currentStyle.rawValue))")
    }

    func updateCommandState(hasNext: Bool, hasPrevious: Bool) {
        lastHasNext = hasNext
        lastHasPrevious = hasPrevious
        applyEnabledState()
    }

    func setControlStyle(_ style: PlayerControlsStyle) {
        guard style != currentStyle else { return }
        currentStyle = style
        applyEnabledState()
        logger.info("Remote command style updated to \(style.rawValue)")
    }

    /// Toggle which command groups are exposed based on the current style and queue state.
    /// iOS lock screen / CarPlay choose buttons from whichever commands are enabled, so
    /// disabling skip commands is what makes next/prev appear prominently (and vice versa).
    private func applyEnabledState() {
        let center = MPRemoteCommandCenter.shared()
        let trackEnabled = currentStyle != .skipSeconds
        let skipEnabled = currentStyle != .skipTrack
        center.nextTrackCommand.isEnabled = trackEnabled && lastHasNext
        center.previousTrackCommand.isEnabled = trackEnabled && lastHasPrevious
        center.skipForwardCommand.isEnabled = skipEnabled
        center.skipBackwardCommand.isEnabled = skipEnabled
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
