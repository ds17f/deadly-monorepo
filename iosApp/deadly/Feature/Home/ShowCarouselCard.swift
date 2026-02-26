import SwiftUI

struct ShowCarouselCard: View {
    let imageRecordingId: String?
    let imageUrl: String?
    let lines: [String]
    var recordingCount: Int? = nil

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
                if let count = recordingCount, count == 0 {
                    Text("No recordings")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .frame(width: DeadlySize.carouselCard)
        .opacity(recordingCount == 0 ? 0.5 : 1.0)
    }
}
