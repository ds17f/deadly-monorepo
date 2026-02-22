import Foundation

// MARK: - Protocol

protocol GitHubReleasesClient: Sendable {
    /// Fetch the latest release metadata from GitHub.
    func fetchLatestRelease() async throws -> GitHubRelease
    /// Stream-download a ZIP asset to a temporary file, returning its URL.
    func downloadZIP(from url: URL) async throws -> URL
}

// MARK: - URLSession implementation

struct URLSessionGitHubReleasesClient: GitHubReleasesClient {
    private static let releasesURL = URL(string: "https://api.github.com/repos/ds17f/dead-metadata/releases/latest")!

    func fetchLatestRelease() async throws -> GitHubRelease {
        var request = URLRequest(url: Self.releasesURL)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("2022-11-28", forHTTPHeaderField: "X-GitHub-Api-Version")
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                throw ImportError.downloadFailed(statusCode: http.statusCode)
            }
            let decoder = JSONDecoder()
            return try decoder.decode(GitHubRelease.self, from: data)
        } catch let error as ImportError {
            throw error
        } catch {
            throw ImportError.networkError(error)
        }
    }

    func downloadZIP(from url: URL) async throws -> URL {
        let destURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("zip")
        do {
            let (tempURL, response) = try await URLSession.shared.download(from: url)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                throw ImportError.downloadFailed(statusCode: http.statusCode)
            }
            try FileManager.default.moveItem(at: tempURL, to: destURL)
            return destURL
        } catch let error as ImportError {
            throw error
        } catch {
            throw ImportError.networkError(error)
        }
    }
}
