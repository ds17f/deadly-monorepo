import Foundation
import Observation

@Observable
final class ShowArtworkService: @unchecked Sendable {
    static let shared = ShowArtworkService()
    @ObservationIgnored private var sourceTypes: [String: RecordingSourceType] = [:]
    @ObservationIgnored private let lock = NSLock()
    var badgeStyle: SourceBadgeStyle = .long

    func sourceType(for recordingId: String) -> RecordingSourceType? {
        lock.lock()
        defer { lock.unlock() }
        return sourceTypes[recordingId]
    }

    func populate(_ entries: [String: RecordingSourceType]) {
        lock.lock()
        defer { lock.unlock() }
        sourceTypes.merge(entries) { _, new in new }
    }
}
