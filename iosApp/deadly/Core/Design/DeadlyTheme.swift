import SwiftUI

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

enum DeadlyColors {
    static let primary = Color(red: 0xDC / 255, green: 0x14 / 255, blue: 0x3C / 255)   // Crimson
    static let secondary = Color(red: 0xFF / 255, green: 0xD7 / 255, blue: 0x00 / 255) // Gold
    static let tertiary = Color(red: 0x22 / 255, green: 0x8B / 255, blue: 0x22 / 255)  // Forest green
}

enum DeadlySpacing {
    static let screenPadding: CGFloat = 16
    static let sectionSpacing: CGFloat = 24
    static let itemSpacing: CGFloat = 16
    static let gridSpacing: CGFloat = 8
    static let gridVerticalSpacing: CGFloat = 4
}

enum DeadlySize {
    static let carouselCard: CGFloat = 160
    static let recentCardHeight: CGFloat = 64
    static let recentArtwork: CGFloat = 56
    static let artworkCornerRadius: CGFloat = 6
    static let cardCornerRadius: CGFloat = 8
    static let carouselCornerRadius: CGFloat = 12
}
