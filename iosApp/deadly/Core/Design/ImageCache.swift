import UIKit

actor ImageCache {
    static let shared = ImageCache()

    private let cache = NSCache<NSString, UIImage>()
    private var inFlightTasks: [String: Task<UIImage?, Never>] = [:]

    private init() {
        cache.countLimit = 100
    }

    func image(for url: URL) async -> UIImage? {
        let key = url.absoluteString as NSString

        // Return cached image immediately
        if let cached = cache.object(forKey: key) {
            return cached
        }

        // Check if already fetching this URL
        if let existingTask = inFlightTasks[url.absoluteString] {
            return await existingTask.value
        }

        // Start new fetch
        let task = Task<UIImage?, Never> {
            guard let (data, _) = try? await URLSession.shared.data(from: url),
                  let image = UIImage(data: data) else {
                return nil
            }
            cache.setObject(image, forKey: key)
            return image
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
}
