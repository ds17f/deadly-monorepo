import SwiftUI

struct CompactStarRating: View {
    let rating: Float?
    var starSize: CGFloat = 14

    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<5, id: \.self) { index in
                starImage(for: index)
                    .resizable()
                    .scaledToFit()
                    .frame(width: starSize, height: starSize)
                    .foregroundStyle(starColor)
            }
        }
    }

    private var starColor: Color {
        rating != nil ? DeadlyColors.secondary : Color(.systemGray3)
    }

    private func starImage(for index: Int) -> Image {
        guard let rating else {
            return Image(systemName: "star")
        }
        let threshold = Float(index) + 1.0
        if rating >= threshold {
            return Image(systemName: "star.fill")
        } else if rating >= threshold - 0.5 {
            return Image(systemName: "star.leadinghalf.filled")
        } else {
            return Image(systemName: "star")
        }
    }
}
