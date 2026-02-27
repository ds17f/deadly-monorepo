import CryptoKit
import UIKit

actor ImageCache {
    static let shared = ImageCache()

    // NSCache is thread-safe, allowing nonisolated synchronous access
    private nonisolated(unsafe) let cache = NSCache<NSString, UIImage>()
    private var inFlightTasks: [String: Task<UIImage?, Never>] = [:]
    private let cacheExpiryDays: Int = 7  // Images rarely change

    private init() {
        cache.countLimit = 100
    }

    // MARK: - Disk cache helpers

    private var cacheDirectory: URL {
        let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let imageDir = cacheDir.appendingPathComponent("images", isDirectory: true)
        try? FileManager.default.createDirectory(at: imageDir, withIntermediateDirectories: true)
        return imageDir
    }

    private func cacheFile(for url: URL) -> URL {
        // Use SHA256 hash to avoid collisions from truncation
        let hash = SHA256.hash(data: Data(url.absoluteString.utf8))
        let filename = hash.compactMap { String(format: "%02x", $0) }.joined()
        return cacheDirectory.appendingPathComponent("\(filename).img")
    }

    private func isCacheExpired(_ modificationDate: Date) -> Bool {
        let expiryInterval = TimeInterval(cacheExpiryDays * 24 * 60 * 60)
        return Date().timeIntervalSince(modificationDate) > expiryInterval
    }

    // MARK: - Public API

    func image(for url: URL) async -> UIImage? {
        let key = url.absoluteString as NSString
        let diskFile = cacheFile(for: url)

        // 1. Check memory cache
        if let cached = cache.object(forKey: key) {
            return cached
        }

        // 2. Check disk cache (fresh)
        if let attrs = try? FileManager.default.attributesOfItem(atPath: diskFile.path),
           let modDate = attrs[.modificationDate] as? Date,
           !isCacheExpired(modDate),
           let data = try? Data(contentsOf: diskFile),
           let image = UIImage(data: data) {
            cache.setObject(image, forKey: key)  // Promote to memory
            return image
        }

        // 3. Check if already fetching
        if let existingTask = inFlightTasks[url.absoluteString] {
            return await existingTask.value
        }

        // 4. Fetch from network
        let task = Task<UIImage?, Never> { [diskFile] in
            if let (data, _) = try? await URLSession.shared.data(from: url),
               let image = UIImage(data: data) {
                // Save to disk
                try? data.write(to: diskFile)
                // Save to memory
                cache.setObject(image, forKey: key)
                return image
            }

            // 5. Network failed - fall back to expired cache (offline support)
            if let data = try? Data(contentsOf: diskFile),
               let image = UIImage(data: data) {
                cache.setObject(image, forKey: key)
                return image
            }

            return nil
        }

        inFlightTasks[url.absoluteString] = task
        let result = await task.value
        inFlightTasks.removeValue(forKey: url.absoluteString)

        return result
    }

    func prefetch(urls: [URL]) {
        for url in urls {
            Task {
                _ = await image(for: url)
            }
        }
    }

    /// Synchronous check for in-memory cached image (no disk/network).
    /// Use this to avoid placeholder flash when image was prefetched.
    nonisolated func cachedImage(for url: URL) -> UIImage? {
        cache.object(forKey: url.absoluteString as NSString)
    }
}
