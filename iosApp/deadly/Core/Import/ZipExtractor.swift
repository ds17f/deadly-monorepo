import Foundation
import ZIPFoundation

// MARK: - Protocol

protocol ZipExtracting: Sendable {
    /// Extract a ZIP file, returning the URL of the directory containing the extracted contents.
    func extract(from zipURL: URL) throws -> URL
}

// MARK: - ZIPFoundation implementation

struct ZipExtractor: ZipExtracting {
    func extract(from zipURL: URL) throws -> URL {
        let destURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: destURL, withIntermediateDirectories: true)
            try FileManager.default.unzipItem(at: zipURL, to: destURL)
        } catch {
            throw ImportError.extractionFailed(error)
        }
        return destURL
    }
}
