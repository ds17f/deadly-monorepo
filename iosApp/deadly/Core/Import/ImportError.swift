import Foundation

/// Errors produced by the data import pipeline.
enum ImportError: Error, LocalizedError {
    case networkError(Error)
    case downloadFailed(statusCode: Int)
    case noDataAsset
    case extractionFailed(Error)
    case parseError(file: String, underlying: Error)
    case databaseError(Error)

    var errorDescription: String? {
        switch self {
        case .networkError(let e):
            return "Network error: \(e.localizedDescription)"
        case .downloadFailed(let code):
            return "Download failed with HTTP \(code)"
        case .noDataAsset:
            return "No data ZIP asset found in latest GitHub release"
        case .extractionFailed(let e):
            return "ZIP extraction failed: \(e.localizedDescription)"
        case .parseError(let file, let e):
            return "Failed to parse \(file): \(e.localizedDescription)"
        case .databaseError(let e):
            return "Database error: \(e.localizedDescription)"
        }
    }
}
