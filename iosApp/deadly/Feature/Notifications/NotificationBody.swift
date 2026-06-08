import SwiftUI

// Render a notification body with tappable links (decision D). Supports
// markdown links `[label](url)` AND bare http(s) URLs, restricted to the
// http/https schemes. Built as an AttributedString with `.link` runs so
// SwiftUI Text opens them in Safari on tap — no HTML, no manual gestures.

private let notificationLinkRegex = try! NSRegularExpression(
    pattern: #"\[([^\]]+)\]\((https?://[^\s)]+)\)|(https?://[^\s]+)"#
)

private let trailingPunctuation = CharacterSet(charactersIn: ").,!?;:")

func notificationBody(_ body: String, linkColor: Color) -> AttributedString {
    var result = AttributedString("")
    let ns = body as NSString
    var last = 0

    func link(_ urlString: String, label: String) -> AttributedString {
        var run = AttributedString(label)
        if let url = URL(string: urlString), url.scheme == "http" || url.scheme == "https" {
            run.link = url
            run.foregroundColor = linkColor
            run.underlineStyle = .single
        }
        return run
    }

    for m in notificationLinkRegex.matches(in: body, range: NSRange(location: 0, length: ns.length)) {
        if m.range.location > last {
            result += AttributedString(ns.substring(with: NSRange(location: last, length: m.range.location - last)))
        }
        let mdUrlRange = m.range(at: 2)
        let bareRange = m.range(at: 3)
        if mdUrlRange.location != NSNotFound {
            result += link(ns.substring(with: mdUrlRange), label: ns.substring(with: m.range(at: 1)))
        } else if bareRange.location != NSNotFound {
            var urlStr = ns.substring(with: bareRange)
            var trailing = ""
            while let lastChar = urlStr.unicodeScalars.last, trailingPunctuation.contains(lastChar) {
                trailing = String(lastChar) + trailing
                urlStr.removeLast()
            }
            result += link(urlStr, label: urlStr)
            if !trailing.isEmpty { result += AttributedString(trailing) }
        }
        last = m.range.location + m.range.length
    }
    if last < ns.length {
        result += AttributedString(ns.substring(from: last))
    }
    return result
}
