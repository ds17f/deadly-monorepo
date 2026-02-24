import SwiftUI

struct TrackListRow: View {
    let track: ArchiveTrack
    let index: Int
    let isPlaying: Bool
    var downloadState: TrackDownloadState?

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

            // Download state indicator
            if let state = downloadState {
                downloadIndicator(for: state)
            }

            if let duration = track.displayDuration {
                Text(duration)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func downloadIndicator(for state: TrackDownloadState) -> some View {
        switch state {
        case .downloading, .pending:
            Image(systemName: "arrow.down.circle")
                .font(.caption)
                .foregroundStyle(DeadlyColors.primary)
                .symbolEffect(.pulse.byLayer, options: .repeating)
        case .completed:
            Image(systemName: "checkmark.circle.fill")
                .font(.caption)
                .foregroundStyle(.green)
        case .failed:
            Image(systemName: "exclamationmark.circle")
                .font(.caption)
                .foregroundStyle(.red)
        case .paused:
            Image(systemName: "pause.circle")
                .font(.caption)
                .foregroundStyle(.orange)
        }
    }
}
