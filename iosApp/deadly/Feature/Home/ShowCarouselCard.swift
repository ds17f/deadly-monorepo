import SwiftUI

struct ShowCarouselCard: View {
    let imageRecordingId: String?
    let imageUrl: String?
    let lines: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ShowArtwork(
                recordingId: imageRecordingId,
                imageUrl: imageUrl,
                size: DeadlySize.carouselCard,
                cornerRadius: DeadlySize.carouselCornerRadius
            )
            VStack(alignment: .leading, spacing: 2) {
                ForEach(lines.prefix(3), id: \.self) { line in
                    Text(line)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .lineLimit(1)
                }
            }
        }
        .frame(width: DeadlySize.carouselCard)
    }
}
