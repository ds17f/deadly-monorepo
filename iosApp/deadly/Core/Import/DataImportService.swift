import Foundation

/// Orchestrates the full data import pipeline:
/// download → extract → parse → clear → import → finalize.
///
/// Progress is reported through an `AsyncStream<ImportProgress>`.
/// Call `run()` to start. If data already exists, the import is skipped unless `force: true`.
struct DataImportService: Sendable {
    let gitHubClient: any GitHubReleasesClient
    let zipExtractor: any ZipExtracting
    let database: AppDatabase
    let showDAO: ShowDAO
    let recordingDAO: RecordingDAO
    let collectionsDAO: CollectionsDAO
    let showSearchDAO: ShowSearchDAO
    let dataVersionDAO: DataVersionDAO
    let analyticsService: AnalyticsService?

    /// The data version this build requires; the single source of truth is the
    /// pinned release client (`URLSessionGitHubReleasesClient.dataVersion`). A
    /// stored version below this triggers a re-import — non-destructive per
    /// ADR-0009, so user data is preserved across the refresh. Injectable so
    /// tests can pin it; defaults to the build's pinned constant.
    var requiredDataVersion: String = URLSessionGitHubReleasesClient.dataVersion

    private static let batchSize = 500

    // MARK: - Public API

    func run(force: Bool = false) -> AsyncStream<ImportProgress> {
        AsyncStream { continuation in
            Task {
                do {
                    try await performImport(force: force) { continuation.yield($0) }
                } catch {
                    self.analyticsService?.track("error", props: [
                        "source": "data_import",
                        "message": error.localizedDescription,
                    ])
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

        // 1. CHECK — import when there's no data OR the stored version is behind
        //    the build's required version (a data-version bump). Mirrors Android's
        //    DatabaseManager.isDataVersionStale. ADR-0009 makes the import
        //    non-destructive (upsert + reconcile), so a refresh preserves user data.
        emit(ImportProgress(phase: .checking, processed: 0, total: 0, message: "Checking for existing data…"))
        if !force {
            let current = (try? dataVersionDAO.currentVersion()) ?? nil
            if !Self.isDataVersionStale(current, required: requiredDataVersion) {
                emit(ImportProgress(phase: .completed, processed: 0, total: 0, message: "Data up to date (\(current ?? "?")); skipping import"))
                return
            }
        }

        // 2. PREFER the prebuilt catalog seed (sub-second attach-copy). Falls through
        //    to the JSON import below if no seed is published yet or the seed fails —
        //    zero-risk migration (ADR-0007, Phase 4).
        if await importFromSeedIfAvailable(progress: emit) {
            return
        }

        // 3. DOWNLOAD (JSON fallback)
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

        // 5. CLEAR the childless catalog/derived tables only. `shows` is NOT deleted
        //    — it has CASCADE children holding user data (favorites, reviews, prefs,
        //    …); it is upserted below so those survive, and the denormalized favorite
        //    flags are reconciled at the end. See
        //    docs/adr/0009-non-destructive-catalog-refresh.md.
        try showSearchDAO.clearAll()
        try recordingDAO.deleteAll()

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
            // Upsert (not insert) so an existing row is updated in place rather than
            // deleted+recreated — keeps CASCADE children intact. The favorite flags
            // this writes are reconciled after the import.
            try showDAO.upsertAll(showRecords)
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
            packageName: "deadly-monorepo-data",
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

        // Favorites (and other CASCADE children) were never deleted; re-derive the
        // denormalized favorite flags on the freshly-upserted shows.
        try showDAO.reconcileFavoriteFlags()

        emit(ImportProgress(
            phase: .completed,
            processed: importedShows + importedRecordings,
            total: importedShows + importedRecordings,
            message: "Imported \(importedShows) shows, \(importedRecordings) recordings, \(collectionRecords.count) collections"
        ))
    }

    // MARK: - Prebuilt seed (fast path)

    /// Attempts the fast path: download the prebuilt catalog seed and attach-copy it
    /// into the migrated DB. Returns `true` if the catalog was fully imported; `false`
    /// to signal the caller to fall back to the JSON import (no seed published yet, or
    /// a recoverable failure). Never throws — failures degrade to the JSON path.
    private func importFromSeedIfAvailable(progress emit: @Sendable (ImportProgress) -> Void) async -> Bool {
        emit(ImportProgress(phase: .downloading, processed: 0, total: 1, message: "Looking for prebuilt catalog…"))

        guard let release = try? await gitHubClient.fetchLatestRelease() else {
            return false  // network issue — let the JSON path surface the error
        }
        guard let asset = release.catalogDbAsset,
              let assetURL = URL(string: asset.browserDownloadUrl) else {
            return false  // no seed published yet → JSON import
        }

        do {
            emit(ImportProgress(phase: .downloading, processed: 0, total: 1, message: "Downloading \(asset.name)…"))
            let zipURL = try await gitHubClient.downloadZIP(from: assetURL)
            defer { try? FileManager.default.removeItem(at: zipURL) }

            emit(ImportProgress(phase: .extracting, processed: 0, total: 1, message: "Extracting catalog…"))
            let extractDir = try zipExtractor.extract(from: zipURL)
            defer { try? FileManager.default.removeItem(at: extractDir) }

            guard let seedFile = findSeedDatabase(in: extractDir) else {
                return false
            }

            emit(ImportProgress(phase: .importingShows, processed: 0, total: 1, message: "Loading catalog…"))
            let seedImporter = SeedImportService(database: database, showDAO: showDAO)
            let result = try seedImporter.importSeed(at: seedFile)

            emit(ImportProgress(
                phase: .completed,
                processed: result.shows + result.recordings,
                total: result.shows + result.recordings,
                message: "Imported \(result.shows) shows, \(result.recordings) recordings"
            ))
            return true
        } catch {
            analyticsService?.track("error", props: [
                "source": "seed_import",
                "message": error.localizedDescription,
            ])
            return false  // recoverable — fall back to JSON import
        }
    }

    /// Locate the extracted `catalog.db` (the zip ships a bare `.db`; tolerate one
    /// level of nesting in case it's wrapped in a folder).
    private func findSeedDatabase(in dir: URL) -> URL? {
        let fm = FileManager.default
        guard let files = try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.isDirectoryKey]) else {
            return nil
        }
        if let db = files.first(where: { $0.pathExtension == "db" }) { return db }
        for sub in files where (try? sub.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true {
            if let nested = try? fm.contentsOfDirectory(at: sub, includingPropertiesForKeys: nil),
               let db = nested.first(where: { $0.pathExtension == "db" }) {
                return db
            }
        }
        return nil
    }

    // MARK: - Version staleness (mirrors Android DatabaseManager)

    /// True when the catalog should be (re-)imported: no stored version, or the
    /// stored version is numerically below `required`. A higher/equal stored
    /// version is left untouched.
    static func isDataVersionStale(_ current: String?, required: String) -> Bool {
        guard let current, !current.isEmpty else { return true }
        return compareVersions(current, required) < 0
    }

    /// Numeric, part-by-part semver compare. Non-numeric parts count as 0 (so a
    /// legacy/garbage version sorts below any real `x.y.z`). Returns <0, 0, or >0.
    static func compareVersions(_ a: String, _ b: String) -> Int {
        let aParts = a.split(separator: ".", omittingEmptySubsequences: false).map { Int($0) ?? 0 }
        let bParts = b.split(separator: ".", omittingEmptySubsequences: false).map { Int($0) ?? 0 }
        for i in 0..<max(aParts.count, bParts.count) {
            let av = i < aParts.count ? aParts[i] : 0
            let bv = i < bParts.count ? bParts[i] : 0
            if av != bv { return av < bv ? -1 : 1 }
        }
        return 0
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
