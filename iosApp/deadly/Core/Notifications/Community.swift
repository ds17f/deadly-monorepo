import Foundation

/// Single source of truth for the community subreddit link (decision I).
/// Rendered in the notifications inbox footer and the settings screen.
enum Community {
    static let subredditHandle = "r/thedeadlyapp"
    static let subredditURL = URL(string: "https://www.reddit.com/r/thedeadlyapp")!
}
