import Foundation

/// Which transport controls are exposed on the lock screen / CarPlay / Control Center.
///
/// iOS surfaces a fixed transport layout that shows *either* next/prev *or* ±15s skip,
/// never both — `MPRemoteCommandCenter` doesn't expose a way to force both pairs on
/// the lock screen or CarPlay. So this enum is intentionally two-valued on iOS.
public enum PlayerControlsStyle: String, CaseIterable, Sendable {
    /// Previous / play-pause / next. Default.
    case skipTrack
    /// −15s / play-pause / +15s.
    case skipSeconds

    public init(rawValueOrDefault: String?) {
        self = PlayerControlsStyle(rawValue: rawValueOrDefault ?? "") ?? .skipTrack
    }
}
