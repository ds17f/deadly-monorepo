import Foundation

/// Manages file system operations for downloaded media.
/// Uses Documents/Downloads/ for persistent storage (not Cache, which can be purged).
struct DownloadStorageManager: Sendable {
    private let fileManager: FileManager

    init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
    }

    /// Base directory for downloads: Documents/Downloads/
    var downloadsDirectory: URL {
        let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        return documentsURL.appendingPathComponent("Downloads", isDirectory: true)
    }

    /// Directory for a specific show: Downloads/{showId}/
    func showDirectory(showId: String) -> URL {
        downloadsDirectory.appendingPathComponent(showId, isDirectory: true)
    }

    /// Local path for a downloaded track.
    func localPath(for identifier: DownloadIdentifier) -> URL {
        showDirectory(showId: identifier.showId)
            .appendingPathComponent(identifier.trackFilename)
    }

    /// Check if a track exists locally.
    func exists(_ identifier: DownloadIdentifier) -> Bool {
        fileManager.fileExists(atPath: localPath(for: identifier).path)
    }

    /// Check if a track is downloaded by recording ID and filename.
    func isTrackDownloaded(recordingId: String, trackFilename: String) -> Bool {
        // We need to find the show directory that contains this recording's files.
        // The identifier format stores showId|recordingId|trackFilename, so we scan directories.
        guard fileManager.fileExists(atPath: downloadsDirectory.path) else { return false }
        guard let showDirs = try? fileManager.contentsOfDirectory(
            at: downloadsDirectory,
            includingPropertiesForKeys: nil
        ) else { return false }

        for showDir in showDirs {
            let trackPath = showDir.appendingPathComponent(trackFilename)
            if fileManager.fileExists(atPath: trackPath.path) {
                return true
            }
        }
        return false
    }

    /// Get the local URL for a track if it exists.
    func localURL(for recordingId: String, trackFilename: String) -> URL? {
        guard fileManager.fileExists(atPath: downloadsDirectory.path) else { return nil }
        guard let showDirs = try? fileManager.contentsOfDirectory(
            at: downloadsDirectory,
            includingPropertiesForKeys: nil
        ) else { return nil }

        for showDir in showDirs {
            let trackPath = showDir.appendingPathComponent(trackFilename)
            if fileManager.fileExists(atPath: trackPath.path) {
                return trackPath
            }
        }
        return nil
    }

    /// Delete a single track file.
    func delete(_ identifier: DownloadIdentifier) throws {
        let path = localPath(for: identifier)
        if fileManager.fileExists(atPath: path.path) {
            try fileManager.removeItem(at: path)
        }
    }

    /// Delete all downloaded files for a show.
    func deleteShow(_ showId: String) throws {
        let showDir = showDirectory(showId: showId)
        if fileManager.fileExists(atPath: showDir.path) {
            try fileManager.removeItem(at: showDir)
        }
    }

    /// Move a downloaded file from temporary location to permanent storage.
    func moveToStorage(from tempURL: URL, identifier: DownloadIdentifier) throws {
        let destination = localPath(for: identifier)
        try ensureDirectoryExists(showDirectory(showId: identifier.showId))

        // Remove existing file if present
        if fileManager.fileExists(atPath: destination.path) {
            try fileManager.removeItem(at: destination)
        }

        try fileManager.moveItem(at: tempURL, to: destination)
    }

    /// Total storage used by all downloads.
    func totalStorageUsed() -> Int64 {
        guard fileManager.fileExists(atPath: downloadsDirectory.path) else { return 0 }
        return directorySize(downloadsDirectory)
    }

    /// Storage used by a specific show.
    func showStorageUsed(_ showId: String) -> Int64 {
        let showDir = showDirectory(showId: showId)
        guard fileManager.fileExists(atPath: showDir.path) else { return 0 }
        return directorySize(showDir)
    }

    /// Get all show IDs that have downloaded content.
    func downloadedShowIds() -> [String] {
        guard fileManager.fileExists(atPath: downloadsDirectory.path) else { return [] }
        guard let contents = try? fileManager.contentsOfDirectory(
            at: downloadsDirectory,
            includingPropertiesForKeys: [.isDirectoryKey]
        ) else { return [] }

        return contents.compactMap { url in
            var isDirectory: ObjCBool = false
            if fileManager.fileExists(atPath: url.path, isDirectory: &isDirectory),
               isDirectory.boolValue {
                return url.lastPathComponent
            }
            return nil
        }
    }

    /// Delete all downloaded content.
    func deleteAll() throws {
        if fileManager.fileExists(atPath: downloadsDirectory.path) {
            try fileManager.removeItem(at: downloadsDirectory)
        }
    }

    // MARK: - Private

    private func ensureDirectoryExists(_ url: URL) throws {
        if !fileManager.fileExists(atPath: url.path) {
            try fileManager.createDirectory(at: url, withIntermediateDirectories: true)
        }
    }

    private func directorySize(_ url: URL) -> Int64 {
        guard let enumerator = fileManager.enumerator(
            at: url,
            includingPropertiesForKeys: [.fileSizeKey],
            options: [.skipsHiddenFiles]
        ) else { return 0 }

        var totalSize: Int64 = 0
        for case let fileURL as URL in enumerator {
            if let fileSize = try? fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize {
                totalSize += Int64(fileSize)
            }
        }
        return totalSize
    }
}
