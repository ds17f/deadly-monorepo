import UIKit

enum MessageShareService {

    static func buildShareMessage(
        showDate: String,
        venue: String,
        location: String,
        songTitle: String? = nil,
        shareUrl: String
    ) -> String {
        var lines: [String] = []
        lines.append("Grateful Dead")
        lines.append("")
        if let songTitle, !songTitle.isEmpty {
            lines.append(songTitle)
            lines.append("")
        }
        lines.append(showDate)
        lines.append(venue)
        if !location.isEmpty {
            lines.append(location)
        }
        lines.append("")
        lines.append("Listen in The Deadly app:")
        lines.append(shareUrl)
        return lines.joined(separator: "\n")
    }

    static func shareItems(text: String, image: UIImage?, url: URL?) -> [Any] {
        var items: [Any] = [text]
        if let image { items.append(image) }
        if let url { items.append(url) }
        return items
    }
}
