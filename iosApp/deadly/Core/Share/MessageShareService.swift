import UIKit

enum MessageShareService {

    static func shareItems(url: String) -> [Any] {
        var items: [Any] = [url]
        if let parsed = URL(string: url) { items.append(parsed) }
        return items
    }
}
