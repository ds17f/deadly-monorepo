import Foundation
import os

/// Background download session identifier.
private let downloadSessionIdentifier = "com.grateful.deadly.downloads"

/// Implementation of DownloadService using URLSession background downloads.
@Observable
@MainActor
final class DownloadServiceImpl: DownloadService {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "DownloadService")

    private(set) var allProgress: [String: ShowDownloadProgress] = [:]

    private let archiveClient: any ArchiveMetadataClient
    private let showRepository: any ShowRepository
    private let libraryDAO: LibraryDAO
    private let downloadTaskDAO: DownloadTaskDAO
    private let storageManager: DownloadStorageManager

    private let sessionDelegate: DownloadSessionDelegate
    private let urlSession: URLSession

    /// Maps task identifier to download identifier.
    private var taskToIdentifier: [Int: String] = [:]

    /// Maximum number of concurrent downloads per show.
    private let maxConcurrentDownloads = 2

    /// Per-show queue of records waiting to start, in track order.
    private var pendingDownloads: [String: [DownloadTaskRecord]] = [:]

    nonisolated init(
        archiveClient: some ArchiveMetadataClient,
        showRepository: some ShowRepository,
        libraryDAO: LibraryDAO,
        downloadTaskDAO: DownloadTaskDAO,
        storageManager: DownloadStorageManager
    ) {
        self.archiveClient = archiveClient
        self.showRepository = showRepository
        self.libraryDAO = libraryDAO
        self.downloadTaskDAO = downloadTaskDAO
        self.storageManager = storageManager

        let delegate = DownloadSessionDelegate()
        self.sessionDelegate = delegate

        let config = URLSessionConfiguration.background(withIdentifier: downloadSessionIdentifier)
        config.httpMaximumConnectionsPerHost = 2
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        self.urlSession = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)

        // Set up callbacks on main actor
        Task { @MainActor in
            self.setupCallbacks()
            self.restoreDownloadState()
        }
    }

    private func setupCallbacks() {
        sessionDelegate.onDownloadComplete = { [weak self] task, location in
            Task { @MainActor in
                self?.handleDownloadComplete(task: task, location: location)
            }
        }

        sessionDelegate.onProgress = { [weak self] task, _, bytesWritten, totalBytes in
            Task { @MainActor in
                self?.handleProgress(task: task, bytesWritten: bytesWritten, totalBytes: totalBytes)
            }
        }

        sessionDelegate.onError = { [weak self] task, error in
            Task { @MainActor in
                self?.handleError(task: task, error: error)
            }
        }
    }

    private func restoreDownloadState() {
        logger.debug("Restoring download state")
        do {
            let tasks = try downloadTaskDAO.fetchAll()
            var progressByShow: [String: [DownloadTaskRecord]] = [:]

            for task in tasks {
                progressByShow[task.showId, default: []].append(task)
            }

            for (showId, showTasks) in progressByShow {
                allProgress[showId] = computeProgress(showId: showId, tasks: showTasks)
            }

            // Resume any downloads that were in progress
            let activeShowIds = try downloadTaskDAO.fetchShowIdsWithState([.downloading, .pending])
            for showId in activeShowIds {
                resumeShowDownloads(showId)
            }
        } catch {
            logger.error("Failed to restore download state: \(error.localizedDescription)")
        }
    }

    // MARK: - DownloadService

    func downloadStatus(for showId: String) -> LibraryDownloadStatus {
        allProgress[showId]?.status ?? .notDownloaded
    }

    func isTrackDownloaded(recordingId: String, trackFilename: String) -> Bool {
        storageManager.isTrackDownloaded(recordingId: recordingId, trackFilename: trackFilename)
    }

    func localURL(for recordingId: String, trackFilename: String) -> URL? {
        storageManager.localURL(for: recordingId, trackFilename: trackFilename)
    }

    func trackDownloadStates(for showId: String) -> [String: TrackDownloadState] {
        do {
            let tasks = try downloadTaskDAO.fetchForShow(showId)
            var states: [String: TrackDownloadState] = [:]
            for task in tasks {
                states[task.trackFilename] = task.downloadState
            }
            return states
        } catch {
            logger.error("Failed to fetch track download states: \(error.localizedDescription)")
            return [:]
        }
    }

    func downloadShow(_ showId: String, recordingId: String?) async throws {
        logger.debug("Starting download for show: \(showId)")

        // Resolve recording
        let resolvedRecordingId: String
        if let rid = recordingId {
            resolvedRecordingId = rid
        } else if let best = try? showRepository.getBestRecordingForShow(showId) {
            resolvedRecordingId = best.identifier
        } else {
            throw DownloadError.noRecordingFound
        }

        // Fetch tracks
        let allTracks = try await archiveClient.fetchTracks(recordingId: resolvedRecordingId)
        guard !allTracks.isEmpty else {
            throw DownloadError.noTracksFound
        }

        // Select format
        let availableFormats = Set(allTracks.map { $0.format })
        guard let format = DownloadFormatConfig.selectFormat(from: availableFormats) else {
            throw DownloadError.noSupportedFormat
        }

        let audioTracks = DownloadFormatConfig.filterTracks(allTracks, by: format)
        logger.debug("Downloading \(audioTracks.count) tracks in '\(format)' format")

        // Create download tasks
        var records: [DownloadTaskRecord] = []
        for track in audioTracks {
            let identifier = DownloadIdentifier(
                showId: showId,
                recordingId: resolvedRecordingId,
                trackFilename: track.name
            )
            let record = DownloadTaskRecord(
                identifier: identifier,
                remoteURL: identifier.remoteURL,
                state: .pending
            )
            records.append(record)
        }

        // Persist to database
        try downloadTaskDAO.insertAll(records)

        // Update library record
        do {
            try libraryDAO.updateDownloadedRecording(showId, recordingId: resolvedRecordingId, format: format)
        } catch {
            logger.error("Failed to update library record: \(error.localizedDescription)")
        }

        // Enqueue downloads and start the first batch
        pendingDownloads[showId] = records
        startNextDownloads(for: showId)

        // Update progress
        updateProgress(for: showId)
    }

    func pauseShow(_ showId: String) {
        logger.debug("Pausing downloads for show: \(showId)")

        // Clear pending queue so queued tracks don't start after active ones are paused
        pendingDownloads.removeValue(forKey: showId)

        urlSession.getTasksWithCompletionHandler { [weak self] _, _, downloadTasks in
            guard let self else { return }
            for task in downloadTasks {
                if let identifier = self.taskToIdentifier[task.taskIdentifier],
                   let downloadId = DownloadIdentifier(from: identifier),
                   downloadId.showId == showId {
                    task.cancel { resumeData in
                        Task { @MainActor in
                            self.handlePause(identifier: identifier, resumeData: resumeData)
                        }
                    }
                }
            }
        }
    }

    func resumeShow(_ showId: String) {
        logger.debug("Resuming downloads for show: \(showId)")
        resumeShowDownloads(showId)
    }

    func cancelShow(_ showId: String) {
        logger.debug("Cancelling downloads for show: \(showId)")

        // Clear pending queue
        pendingDownloads.removeValue(forKey: showId)

        // Cancel active tasks
        urlSession.getTasksWithCompletionHandler { [weak self] _, _, downloadTasks in
            guard let self else { return }
            for task in downloadTasks {
                if let identifier = self.taskToIdentifier[task.taskIdentifier],
                   let downloadId = DownloadIdentifier(from: identifier),
                   downloadId.showId == showId {
                    task.cancel()
                    self.taskToIdentifier[task.taskIdentifier] = nil
                }
            }
        }

        // Delete database records
        do {
            try downloadTaskDAO.deleteForShow(showId)
        } catch {
            logger.error("Failed to delete download tasks for show: \(error.localizedDescription)")
        }

        // Delete files
        do {
            try storageManager.deleteShow(showId)
        } catch {
            logger.error("Failed to delete files for show: \(error.localizedDescription)")
        }

        // Clear library download info
        do {
            try libraryDAO.clearDownloadedRecording(showId)
        } catch {
            logger.error("Failed to clear library download info: \(error.localizedDescription)")
        }

        // Update progress
        allProgress.removeValue(forKey: showId)
    }

    func removeShow(_ showId: String) {
        logger.debug("Removing downloads for show: \(showId)")
        cancelShow(showId)
    }

    func removeAll() {
        logger.debug("Removing all downloads")

        urlSession.invalidateAndCancel()
        taskToIdentifier.removeAll()
        pendingDownloads.removeAll()

        do {
            try downloadTaskDAO.deleteAll()
        } catch {
            logger.error("Failed to delete all download tasks: \(error.localizedDescription)")
        }
        do {
            try storageManager.deleteAll()
        } catch {
            logger.error("Failed to delete all downloaded files: \(error.localizedDescription)")
        }

        for showId in allProgress.keys {
            do {
                try libraryDAO.clearDownloadedRecording(showId)
            } catch {
                logger.error("Failed to clear library record for \(showId): \(error.localizedDescription)")
            }
        }

        allProgress.removeAll()
    }

    func totalStorageUsed() -> Int64 {
        storageManager.totalStorageUsed()
    }

    func showStorageUsed(_ showId: String) -> Int64 {
        storageManager.showStorageUsed(showId)
    }

    func allDownloadedShowIds() -> [String] {
        storageManager.downloadedShowIds()
    }

    func handleBackgroundSessionCompletion(_ completionHandler: @escaping () -> Void) {
        sessionDelegate.backgroundCompletionHandler = completionHandler
    }

    // MARK: - Private

    private func startDownloadTask(_ record: DownloadTaskRecord) {
        guard let url = URL(string: record.remoteURL) else { return }

        let task: URLSessionDownloadTask
        if let resumeData = record.resumeData {
            task = urlSession.downloadTask(withResumeData: resumeData)
        } else {
            task = urlSession.downloadTask(with: url)
        }

        task.taskDescription = record.identifier
        taskToIdentifier[task.taskIdentifier] = record.identifier

        task.resume()

        do {
            try downloadTaskDAO.updateState(record.identifier, state: .downloading)
        } catch {
            logger.error("Failed to update state to downloading: \(error.localizedDescription)")
        }
    }

    private func startNextDownloads(for showId: String) {
        // Count currently active downloads for this show
        var activeCount = 0
        for (_, identifier) in taskToIdentifier {
            if let downloadId = DownloadIdentifier(from: identifier),
               downloadId.showId == showId {
                activeCount += 1
            }
        }

        // Start pending downloads up to the concurrency limit
        while activeCount < maxConcurrentDownloads,
              var queue = pendingDownloads[showId],
              !queue.isEmpty {
            let record = queue.removeFirst()
            pendingDownloads[showId] = queue
            startDownloadTask(record)
            activeCount += 1
        }

        // Clean up empty queue entry
        if pendingDownloads[showId]?.isEmpty == true {
            pendingDownloads.removeValue(forKey: showId)
        }
    }

    private func resumeShowDownloads(_ showId: String) {
        do {
            let tasks = try downloadTaskDAO.fetchForShow(showId)
            let pending = tasks.filter { $0.downloadState != .completed }
            pendingDownloads[showId] = pending
            startNextDownloads(for: showId)
            updateProgress(for: showId)
        } catch {
            logger.error("Failed to resume downloads: \(error.localizedDescription)")
        }
    }

    private func handleDownloadComplete(task: URLSessionDownloadTask, location: URL) {
        guard let identifierString = task.taskDescription,
              let identifier = DownloadIdentifier(from: identifierString) else {
            logger.error("Unknown download completed")
            return
        }

        do {
            try storageManager.moveToStorage(from: location, identifier: identifier)
            try downloadTaskDAO.updateState(identifierString, state: .completed)
            logger.debug("Download completed: \(identifier.trackFilename)")
        } catch {
            logger.error("Failed to save download: \(error.localizedDescription)")
            do {
                try downloadTaskDAO.updateState(identifierString, state: .failed, errorMessage: error.localizedDescription)
            } catch let dbError {
                logger.error("Failed to update state to failed: \(dbError.localizedDescription)")
            }
        }

        taskToIdentifier[task.taskIdentifier] = nil
        updateProgress(for: identifier.showId)
        startNextDownloads(for: identifier.showId)
    }

    private func handleProgress(task: URLSessionDownloadTask, bytesWritten: Int64, totalBytes: Int64) {
        guard let identifierString = task.taskDescription,
              let identifier = DownloadIdentifier(from: identifierString) else { return }

        do {
            try downloadTaskDAO.updateState(
                identifierString,
                state: .downloading,
                bytesDownloaded: bytesWritten,
                totalBytes: totalBytes
            )
        } catch {
            logger.error("Failed to update download progress: \(error.localizedDescription)")
        }

        updateProgress(for: identifier.showId)
    }

    private func handleError(task: URLSessionDownloadTask, error: Error) {
        guard let identifierString = task.taskDescription,
              let identifier = DownloadIdentifier(from: identifierString) else { return }

        logger.error("Download failed for \(identifier.trackFilename): \(error.localizedDescription)")

        // Check for resume data in the error
        let resumeData = (error as NSError).userInfo[NSURLSessionDownloadTaskResumeData] as? Data

        do {
            try downloadTaskDAO.updateState(
                identifierString,
                state: .failed,
                resumeData: resumeData,
                errorMessage: error.localizedDescription
            )
        } catch let dbError {
            logger.error("Failed to update state to failed: \(dbError.localizedDescription)")
        }

        taskToIdentifier[task.taskIdentifier] = nil
        updateProgress(for: identifier.showId)
        startNextDownloads(for: identifier.showId)
    }

    private func handlePause(identifier: String, resumeData: Data?) {
        do {
            try downloadTaskDAO.updateState(identifier, state: .paused, resumeData: resumeData)
        } catch {
            logger.error("Failed to update state to paused: \(error.localizedDescription)")
        }

        if let downloadId = DownloadIdentifier(from: identifier) {
            updateProgress(for: downloadId.showId)
        }
    }

    private func updateProgress(for showId: String) {
        do {
            let tasks = try downloadTaskDAO.fetchForShow(showId)
            if tasks.isEmpty {
                allProgress.removeValue(forKey: showId)
            } else {
                allProgress[showId] = computeProgress(showId: showId, tasks: tasks)
            }
        } catch {
            logger.error("Failed to update progress: \(error.localizedDescription)")
        }
    }

    private func computeProgress(showId: String, tasks: [DownloadTaskRecord]) -> ShowDownloadProgress {
        guard !tasks.isEmpty else {
            return ShowDownloadProgress(
                showId: showId,
                status: .notDownloaded,
                overallProgress: 0,
                downloadedBytes: 0,
                totalBytes: 0,
                tracksCompleted: 0,
                tracksTotal: 0
            )
        }

        let downloadedBytes = tasks.reduce(0) { $0 + $1.bytesDownloaded }
        let totalBytes = tasks.reduce(0) { $0 + $1.totalBytes }
        let tracksCompleted = tasks.filter { $0.downloadState == .completed }.count
        let overallProgress: Float = tasks.isEmpty ? 0 : Float(tracksCompleted) / Float(tasks.count)

        let states = tasks.map { $0.downloadState }
        let status: LibraryDownloadStatus
        if states.allSatisfy({ $0 == .completed }) {
            status = .completed
        } else if states.contains(.failed) {
            status = .failed
        } else if states.contains(.downloading) {
            status = .downloading
        } else if states.contains(.pending) {
            status = .queued
        } else if states.contains(.paused) {
            status = .paused
        } else {
            status = .notDownloaded
        }

        return ShowDownloadProgress(
            showId: showId,
            status: status,
            overallProgress: overallProgress,
            downloadedBytes: downloadedBytes,
            totalBytes: totalBytes,
            tracksCompleted: tracksCompleted,
            tracksTotal: tasks.count
        )
    }
}

// MARK: - Errors

enum DownloadError: LocalizedError {
    case noRecordingFound
    case noTracksFound
    case noSupportedFormat

    var errorDescription: String? {
        switch self {
        case .noRecordingFound:
            return "No recording found for this show"
        case .noTracksFound:
            return "No tracks found for this recording"
        case .noSupportedFormat:
            return "No supported audio format found"
        }
    }
}
