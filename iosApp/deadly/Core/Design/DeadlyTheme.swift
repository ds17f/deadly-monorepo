import SwiftUI

enum DeadlyColors {
    static let primary = Color(red: 0xDC / 255, green: 0x14 / 255, blue: 0x3C / 255)   // Crimson
    static let secondary = Color(red: 0xFF / 255, green: 0xD7 / 255, blue: 0x00 / 255) // Gold
    static let tertiary = Color(red: 0x22 / 255, green: 0x8B / 255, blue: 0x22 / 255)  // Forest green

    static let darkBackground = Color(red: 0x12 / 255, green: 0x12 / 255, blue: 0x12 / 255)
    static let darkSurface = Color(red: 0x1E / 255, green: 0x1E / 255, blue: 0x1E / 255)
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
