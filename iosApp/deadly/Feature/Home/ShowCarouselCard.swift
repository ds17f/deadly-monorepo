import SwiftUI

struct ShowCarouselCard: View {
    let imageRecordingId: String?
    let imageUrl: String?
    let lines: [String]
    var recordingCount: Int? = nil
    var size: CGFloat = DeadlySize.carouselCard

    private var isCompact: Bool { size <= DeadlySize.carouselCardSmall }

    var body: some View {
        VStack(alignment: .leading, spacing: isCompact ? 6 : 12) {
            ShowArtwork(
                recordingId: imageRecordingId,
                imageUrl: imageUrl,
                size: size,
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
        .frame(width: size)
        .opacity(recordingCount == 0 ? 0.5 : 1.0)
        .accessibilityElement(children: .combine)
    }
}
