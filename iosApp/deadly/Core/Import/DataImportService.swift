import Foundation

/// Orchestrates the full data import pipeline:
/// download → extract → parse → clear → import → finalize.
///
/// Progress is reported through an `AsyncStream<ImportProgress>`.
/// Call `run()` to start. If data already exists, the import is skipped unless `force: true`.
struct DataImportService: Sendable {
    let gitHubClient: any GitHubReleasesClient
    let zipExtractor: any ZipExtracting
    let showDAO: ShowDAO
    let recordingDAO: RecordingDAO
    let collectionsDAO: CollectionsDAO
    let showSearchDAO: ShowSearchDAO
    let dataVersionDAO: DataVersionDAO
    let libraryDAO: LibraryDAO

    private static let batchSize = 500

    // MARK: - Public API

    func run(force: Bool = false) -> AsyncStream<ImportProgress> {
        AsyncStream { continuation in
            Task {
                do {
                    try await performImport(force: force) { continuation.yield($0) }
                } catch {
                    continuation.yield(ImportProgress(
                        phase: .failed,
                        processed: 0,
                        total: 0,
                        message: error.localizedDescription
                    ))
                }
                continuation.finish()
            }
        }
    }

    // MARK: - Pipeline

    private func performImport(force: Bool, progress emit: @Sendable (ImportProgress) -> Void) async throws {

        // 1. CHECK
        emit(ImportProgress(phase: .checking, processed: 0, total: 0, message: "Checking for existing data…"))
        if !force, (try? dataVersionDAO.hasData()) == true {
            emit(ImportProgress(phase: .completed, processed: 0, total: 0, message: "Data already present; skipping import"))
            return
        }

        // 2. DOWNLOAD
        emit(ImportProgress(phase: .downloading, processed: 0, total: 1, message: "Fetching latest release…"))
        let release = try await gitHubClient.fetchLatestRelease()
        guard let asset = release.dataZipAsset else {
            throw ImportError.noDataAsset
        }
        guard let assetURL = URL(string: asset.browserDownloadUrl) else {
            throw ImportError.noDataAsset
        }
        emit(ImportProgress(phase: .downloading, processed: 0, total: 1, message: "Downloading \(asset.name)…"))
        let zipURL = try await gitHubClient.downloadZIP(from: assetURL)
        defer { try? FileManager.default.removeItem(at: zipURL) }

        // 3. EXTRACT
        emit(ImportProgress(phase: .extracting, processed: 0, total: 1, message: "Extracting archive…"))
        let extractDir = try zipExtractor.extract(from: zipURL)
        defer { try? FileManager.default.removeItem(at: extractDir) }

        let rootDir = findRootDir(in: extractDir) ?? extractDir
        let showsDir = rootDir.appendingPathComponent("shows")
        let recordingsDir = rootDir.appendingPathComponent("recordings")

        // 4. PARSE shows
        let showFiles = jsonFiles(in: showsDir)
        emit(ImportProgress(phase: .readingShows, processed: 0, total: showFiles.count, message: "Parsing \(showFiles.count) show files…"))

        var showsMap: [String: ShowImportData] = [:]
        for (i, file) in showFiles.enumerated() {
            if let data = parseJSON(ShowImportData.self, at: file) {
                showsMap[data.showId] = data
            }
            if i % 100 == 0 {
                emit(ImportProgress(phase: .readingShows, processed: i + 1, total: showFiles.count, message: "Parsing shows…"))
            }
        }
        emit(ImportProgress(phase: .readingShows, processed: showFiles.count, total: showFiles.count, message: "Parsed \(showsMap.count) shows"))

        // 5. PRESERVE library (CASCADE on shows FK will delete library_shows on clear)
        let savedLibrary = (try? libraryDAO.fetchAll()) ?? []

        // 6. CLEAR existing data
        try showSearchDAO.clearAll()
        try recordingDAO.deleteAll()
        try showDAO.deleteAll()

        // Build reverse index: recordingId → [showId]  O(shows) to build, O(1) per lookup
        // Only holds IDs (strings), not full ShowImportData objects
        var recordingToShows: [String: [String]] = [:]
        for show in showsMap.values {
            for recId in show.recordings {
                recordingToShows[recId, default: []].append(show.showId)
            }
        }

        let now = ShowImporter.currentMillis()

        // 7. IMPORT shows + FTS (batched)
        let allShows = Array(showsMap.values)
        let totalShows = allShows.count
        var importedShows = 0

        for batch in allShows.chunks(of: Self.batchSize) {
            let showRecords = batch.map { ShowImporter.makeRecord(from: $0, now: now) }
            let searchRecords = batch.map { ShowImporter.makeSearchRecord(from: $0) }
            try showDAO.insertAll(showRecords)
            try showSearchDAO.insertAll(searchRecords)
            importedShows += batch.count
            emit(ImportProgress(phase: .importingShows, processed: importedShows, total: totalShows, message: "Importing shows…"))
        }

        // Free shows from memory before processing recordings
        showsMap.removeAll()

        // 8. IMPORT recordings — stream file-by-file, batch insert, never hold all in RAM
        let recordingFiles = jsonFiles(in: recordingsDir)
        let totalRecordingFiles = recordingFiles.count
        emit(ImportProgress(phase: .importingRecordings, processed: 0, total: totalRecordingFiles, message: "Importing \(totalRecordingFiles) recordings…"))

        var batch: [RecordingRecord] = []
        batch.reserveCapacity(Self.batchSize)
        var importedRecordings = 0

        for (i, file) in recordingFiles.enumerated() {
            let recId = file.deletingPathExtension().lastPathComponent
            guard let showIds = recordingToShows[recId],
                  let recData = parseJSON(RecordingImportData.self, at: file) else { continue }
            for showId in showIds {
                batch.append(RecordingImporter.makeRecord(recordingId: recId, from: recData, showId: showId, now: now))
            }
            if batch.count >= Self.batchSize {
                try recordingDAO.insertAll(batch)
                importedRecordings += batch.count
                batch.removeAll(keepingCapacity: true)
                emit(ImportProgress(phase: .importingRecordings, processed: i + 1, total: totalRecordingFiles, message: "Importing recordings…"))
            } else if i % 500 == 0 {
                emit(ImportProgress(phase: .importingRecordings, processed: i + 1, total: totalRecordingFiles, message: "Importing recordings…"))
            }
        }
        // Flush remaining
        if !batch.isEmpty {
            try recordingDAO.insertAll(batch)
            importedRecordings += batch.count
        }
        emit(ImportProgress(phase: .importingRecordings, processed: totalRecordingFiles, total: totalRecordingFiles, message: "Imported \(importedRecordings) recordings"))

        // 9. IMPORT collections
        emit(ImportProgress(phase: .importingCollections, processed: 0, total: 1, message: "Resolving collections…"))
        let collectionsImporter = CollectionsImporter(showDAO: showDAO)
        let collectionRecords = collectionsImporter.importCollections(from: rootDir, now: now)
        try? collectionsDAO.deleteAll()
        try? collectionsDAO.insertAll(collectionRecords)
        emit(ImportProgress(phase: .importingCollections, processed: 1, total: 1, message: "Imported \(collectionRecords.count) collections"))

        // 10. FINALIZE
        emit(ImportProgress(phase: .finalizing, processed: 0, total: 1, message: "Finalizing…"))
        let manifest = parseManifest(in: rootDir)
        let versionRecord = DataVersionRecord(
            id: 1,
            dataVersion: manifest?.packageInfo?.version ?? release.tagName,
            packageName: "dead-metadata",
            versionType: "release",
            description: "Imported from GitHub release \(release.tagName)",
            importedAt: now,
            gitCommit: manifest?.buildInfo?.gitCommit,
            gitTag: release.tagName,
            buildTimestamp: manifest?.buildInfo?.buildTimestamp,
            totalShows: importedShows,
            totalVenues: 0,
            totalFiles: importedRecordings,
            totalSizeBytes: 0
        )
        try dataVersionDAO.upsert(versionRecord)

        // Restore library entries (filter to shows that still exist after re-import)
        if !savedLibrary.isEmpty {
            let existingIds = Set((try? showDAO.fetchAll().map(\.showId)) ?? [])
            let toRestore = savedLibrary.filter { existingIds.contains($0.showId) }
            try? libraryDAO.addAll(toRestore)
        }

        emit(ImportProgress(
            phase: .completed,
            processed: importedShows + importedRecordings,
            total: importedShows + importedRecordings,
            message: "Imported \(importedShows) shows, \(importedRecordings) recordings, \(collectionRecords.count) collections"
        ))
    }

    // MARK: - Helpers

    private func jsonFiles(in dir: URL) -> [URL] {
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: dir, includingPropertiesForKeys: [.isRegularFileKey]
        ) else { return [] }
        return files.filter { $0.pathExtension == "json" }.sorted { $0.path < $1.path }
    }

    private func parseJSON<T: Decodable>(_ type: T.Type, at url: URL) -> T? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    private func parseManifest(in dir: URL) -> ManifestData? {
        parseJSON(ManifestData.self, at: dir.appendingPathComponent("manifest.json"))
    }

    /// If the ZIP extracted into a single root folder, return that folder; otherwise return nil.
    private func findRootDir(in extractDir: URL) -> URL? {
        let fm = FileManager.default
        if fm.fileExists(atPath: extractDir.appendingPathComponent("shows").path) {
            return extractDir
        }
        guard let contents = try? fm.contentsOfDirectory(
            at: extractDir, includingPropertiesForKeys: [.isDirectoryKey]
        ) else { return nil }
        let dirs = contents.filter { url in
            (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true
        }
        if dirs.count == 1, fm.fileExists(atPath: dirs[0].appendingPathComponent("shows").path) {
            return dirs[0]
        }
        return nil
    }
}

// MARK: - Array chunking

private extension Array {
    func chunks(of size: Int) -> [[Element]] {
        guard size > 0 else { return [self] }
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0..<Swift.min($0 + size, count)])
        }
    }
}
