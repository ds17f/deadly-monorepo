import Foundation
import GRDB

/// Persists download task state for app restart recovery.
/// Each row represents a single track download.
struct DownloadTaskRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "download_tasks"

    /// Unique identifier: `{showId}|{recordingId}|{trackFilename}`
    var identifier: String
    var showId: String
    var recordingId: String
    var trackFilename: String
    var remoteURL: String
    var state: String  // TrackDownloadState raw value
    var bytesDownloaded: Int64
    var totalBytes: Int64
    var resumeData: Data?
    var errorMessage: String?
    var createdAt: Int64
    var updatedAt: Int64

    /// Create from a DownloadIdentifier.
    init(
        identifier: DownloadIdentifier,
        remoteURL: URL,
        state: TrackDownloadState = .pending,
        bytesDownloaded: Int64 = 0,
        totalBytes: Int64 = 0,
        resumeData: Data? = nil,
        errorMessage: String? = nil
    ) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        self.identifier = identifier.formatted
        self.showId = identifier.showId
        self.recordingId = identifier.recordingId
        self.trackFilename = identifier.trackFilename
        self.remoteURL = remoteURL.absoluteString
        self.state = state.rawValue
        self.bytesDownloaded = bytesDownloaded
        self.totalBytes = totalBytes
        self.resumeData = resumeData
        self.errorMessage = errorMessage
        self.createdAt = now
        self.updatedAt = now
    }

    /// Parse the download identifier.
    var downloadIdentifier: DownloadIdentifier? {
        DownloadIdentifier(from: identifier)
    }

    /// Parse the state enum.
    var downloadState: TrackDownloadState {
        TrackDownloadState(rawValue: state) ?? .pending
    }

    /// Convert to TrackDownload model.
    func toTrackDownload() -> TrackDownload? {
        guard let id = downloadIdentifier else { return nil }
        return TrackDownload(
            identifier: id,
            state: downloadState,
            bytesDownloaded: bytesDownloaded,
            totalBytes: totalBytes,
            resumeData: resumeData,
            errorMessage: errorMessage
        )
    }
}

/// DAO for download task persistence.
struct DownloadTaskDAO: Sendable {
    let database: AppDatabase

    // MARK: - CRUD

    func insert(_ record: DownloadTaskRecord) throws {
        try database.write { db in
            var mutableRecord = record
            try mutableRecord.insert(db)
        }
    }

    func insertAll(_ records: [DownloadTaskRecord]) throws {
        try database.write { db in
            for record in records {
                var mutableRecord = record
                try mutableRecord.insert(db)
            }
        }
    }

    func update(_ record: DownloadTaskRecord) throws {
        try database.write { db in
            var mutableRecord = record
            mutableRecord.updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
            try mutableRecord.update(db)
        }
    }

    func updateState(_ identifier: String, state: TrackDownloadState, bytesDownloaded: Int64? = nil, totalBytes: Int64? = nil, resumeData: Data? = nil, errorMessage: String? = nil) throws {
        try database.write { db in
            var columns: [ColumnAssignment] = [
                Column("state").set(to: state.rawValue),
                Column("updatedAt").set(to: Int64(Date().timeIntervalSince1970 * 1000))
            ]
            if let bytes = bytesDownloaded {
                columns.append(Column("bytesDownloaded").set(to: bytes))
            }
            if let total = totalBytes {
                columns.append(Column("totalBytes").set(to: total))
            }
            if let data = resumeData {
                columns.append(Column("resumeData").set(to: data))
            } else if state != .paused {
                // Clear resume data when not paused
                columns.append(Column("resumeData").set(to: Data?.none))
            }
            if let error = errorMessage {
                columns.append(Column("errorMessage").set(to: error))
            } else {
                columns.append(Column("errorMessage").set(to: String?.none))
            }
            try DownloadTaskRecord
                .filter(Column("identifier") == identifier)
                .updateAll(db, columns)
        }
    }

    func delete(_ identifier: String) throws {
        try database.write { db in
            try DownloadTaskRecord.deleteOne(db, key: identifier)
        }
    }

    func deleteForShow(_ showId: String) throws {
        try database.write { db in
            try DownloadTaskRecord
                .filter(Column("showId") == showId)
                .deleteAll(db)
        }
    }

    func deleteAll() throws {
        try database.write { db in
            try DownloadTaskRecord.deleteAll(db)
        }
    }

    // MARK: - Fetch

    func fetchByIdentifier(_ identifier: String) throws -> DownloadTaskRecord? {
        try database.read { db in
            try DownloadTaskRecord.fetchOne(db, key: identifier)
        }
    }

    func fetchForShow(_ showId: String) throws -> [DownloadTaskRecord] {
        try database.read { db in
            try DownloadTaskRecord
                .filter(Column("showId") == showId)
                .order(Column("createdAt").asc)
                .fetchAll(db)
        }
    }

    func fetchAll() throws -> [DownloadTaskRecord] {
        try database.read { db in
            try DownloadTaskRecord.fetchAll(db)
        }
    }

    func fetchByState(_ state: TrackDownloadState) throws -> [DownloadTaskRecord] {
        try database.read { db in
            try DownloadTaskRecord
                .filter(Column("state") == state.rawValue)
                .fetchAll(db)
        }
    }

    func fetchShowIds() throws -> [String] {
        try database.read { db in
            try String.fetchAll(db, sql: "SELECT DISTINCT showId FROM download_tasks")
        }
    }

    func fetchShowIdsWithState(_ states: [TrackDownloadState]) throws -> [String] {
        let stateStrings = states.map { $0.rawValue }
        return try database.read { db in
            try String.fetchAll(
                db,
                sql: """
                    SELECT DISTINCT showId FROM download_tasks
                    WHERE state IN (\(stateStrings.map { "'\($0)'" }.joined(separator: ",")))
                    """
            )
        }
    }
}
