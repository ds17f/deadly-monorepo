import GRDB

struct RecordingPreferenceRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "recording_preferences"

    var showId: String
    var recordingId: String
    var updatedAt: Int64
}
