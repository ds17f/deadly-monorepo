import Foundation
import os

/// URLSession delegate that handles background download events.
/// This class bridges URLSession callbacks to the DownloadService.
final class DownloadSessionDelegate: NSObject, URLSessionDownloadDelegate {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "DownloadSession")

    /// Callback for when a download completes successfully.
    var onDownloadComplete: ((URLSessionDownloadTask, URL) -> Void)?

    /// Callback for progress updates.
    var onProgress: ((URLSessionDownloadTask, Int64, Int64, Int64) -> Void)?

    /// Callback for errors.
    var onError: ((URLSessionDownloadTask, Error) -> Void)?

    /// Completion handler for background session events.
    /// Must be called when all background tasks are processed.
    var backgroundCompletionHandler: (() -> Void)?

    // MARK: - URLSessionDownloadDelegate

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        logger.debug("Download finished: \(downloadTask.taskDescription ?? "unknown")")

        // CRITICAL: iOS deletes the temp file immediately when this delegate method returns.
        // We MUST copy the file synchronously before returning, then pass the copy to our handler.
        let tempCopy = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)

        do {
            try FileManager.default.copyItem(at: location, to: tempCopy)
            onDownloadComplete?(downloadTask, tempCopy)
        } catch {
            logger.error("Failed to copy temp file: \(error.localizedDescription)")
        }
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        onProgress?(downloadTask, bytesWritten, totalBytesWritten, totalBytesExpectedToWrite)
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didResumeAtOffset fileOffset: Int64,
        expectedTotalBytes: Int64
    ) {
        logger.debug("Download resumed at offset \(fileOffset)")
    }

    // MARK: - URLSessionTaskDelegate

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        if let error = error {
            // Check if this is a cancellation
            let nsError = error as NSError
            if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled {
                logger.debug("Download cancelled: \(task.taskDescription ?? "unknown")")
            } else {
                logger.error("Download error: \(error.localizedDescription)")
                if let downloadTask = task as? URLSessionDownloadTask {
                    onError?(downloadTask, error)
                }
            }
        }
    }

    // MARK: - URLSessionDelegate

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        logger.debug("Background session finished events")
        DispatchQueue.main.async { [weak self] in
            self?.backgroundCompletionHandler?()
            self?.backgroundCompletionHandler = nil
        }
    }
}
