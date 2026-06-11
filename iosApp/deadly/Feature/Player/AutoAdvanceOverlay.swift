import SwiftUI

/// ADR-0010 — end-of-show "Next up in Ns" card. Shows the next show's cover +
/// details while the countdown runs, with Play now / Cancel. Fed by
/// `AutoAdvanceCoordinator.countdown`; rendered above the mini player (active
/// device and remotes alike). Renders nothing when there's no countdown.
struct AutoAdvanceOverlay: View {
    @Environment(\.appContainer) private var container

    var body: some View {
        if let countdown = container.autoAdvanceCoordinator.countdown {
            let show = countdown.nextShow
            HStack(spacing: 12) {
                ShowArtwork(
                    recordingId: show.bestRecordingId,
                    imageUrl: show.coverImageUrl,
                    size: 44,
                    cornerRadius: DeadlySize.artworkCornerRadius
                )

                VStack(alignment: .leading, spacing: 2) {
                    Text("Next up in \(countdown.secondsRemaining)s")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(DeadlyColors.primary)
                    Text(show.date)
                        .font(.subheadline)
                        .lineLimit(1)
                    Text(show.venue.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 4)

                Button("Play now") { container.autoAdvanceCoordinator.playNow() }
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(DeadlyColors.primary)
                    .foregroundStyle(.black)
                    .clipShape(Capsule())

                Button("Cancel") { container.autoAdvanceCoordinator.cancel() }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .overlay(Capsule().stroke(Color.secondary.opacity(0.4)))
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal, 8)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}
