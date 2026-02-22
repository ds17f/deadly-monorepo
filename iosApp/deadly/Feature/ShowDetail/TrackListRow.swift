import SwiftUI

struct TrackListRow: View {
    let track: ArchiveTrack
    let index: Int
    let isPlaying: Bool

    var body: some View {
        HStack(spacing: 12) {
            Group {
                if isPlaying {
                    Image(systemName: "speaker.wave.2.fill")
                        .foregroundStyle(DeadlyColors.primary)
                } else {
                    Text("\(track.trackNumber > 0 ? track.trackNumber : index + 1)")
                        .foregroundStyle(.secondary)
                }
            }
            .font(.caption)
            .frame(width: 28, alignment: .center)

            Text(track.title)
                .foregroundStyle(isPlaying ? DeadlyColors.primary : .primary)
                .lineLimit(1)

            Spacer()

            if let duration = track.displayDuration {
                Text(duration)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
        .padding(.vertical, 2)
    }
}
