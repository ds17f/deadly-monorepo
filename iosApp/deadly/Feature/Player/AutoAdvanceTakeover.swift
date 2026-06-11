import SwiftUI

/// ADR-0010 — full-screen "Up Next" end-of-show takeover: the full-player
/// counterpart to the docked `AutoAdvanceOverlay`. While the countdown runs and
/// the player is open, the player is replaced by a preview of the NEXT show —
/// the same large artwork / date / venue layout as the now-playing view, under
/// an "Up Next in Ns" header, with Play now / Cancel. Mirrors web's HeaderPlayer
/// takeover. Fed by `AutoAdvanceCoordinator.countdown`; renders nothing when
/// there's no countdown.
struct AutoAdvanceTakeover: View {
    @Environment(\.appContainer) private var container

    var body: some View {
        if let countdown = container.autoAdvanceCoordinator.countdown {
            let show = countdown.nextShow
            ZStack {
                Color(.systemBackground).ignoresSafeArea()

                VStack(spacing: 0) {
                    // "Up Next in Ns" header — the countdown the user watches tick.
                    Text("UP NEXT IN \(countdown.secondsRemaining)s")
                        .font(.subheadline.weight(.semibold))
                        .tracking(2)
                        .foregroundStyle(DeadlyColors.primary)

                    Spacer().frame(height: 24)

                    // Large cover art of the next show — same prominence (300pt)
                    // and shadow as the now-playing player's artwork.
                    ShowArtwork(
                        recordingId: show.bestRecordingId,
                        imageUrl: show.coverImageUrl,
                        size: 300,
                        cornerRadius: DeadlySize.carouselCornerRadius
                    )
                    .shadow(color: .black.opacity(0.4), radius: 20, y: 10)

                    Spacer().frame(height: 32)

                    // Next show date / venue / location.
                    VStack(spacing: 6) {
                        Text(show.date)
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                        if !show.venue.name.isEmpty {
                            Text(show.venue.name)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        if !show.location.displayText.isEmpty {
                            Text(show.location.displayText)
                                .font(.footnote)
                                .foregroundStyle(.secondary.opacity(0.8))
                                .lineLimit(1)
                        }
                    }

                    Spacer().frame(height: 32)

                    // Actions in the transport's place: start now, or stay put.
                    Button {
                        container.autoAdvanceCoordinator.playNow()
                    } label: {
                        Text("Play now")
                            .font(.subheadline.weight(.bold))
                            .padding(.horizontal, 32)
                            .padding(.vertical, 12)
                            .background(DeadlyColors.primary)
                            .foregroundStyle(.black)
                            .clipShape(Capsule())
                    }

                    Spacer().frame(height: 12)

                    Button {
                        container.autoAdvanceCoordinator.cancel()
                    } label: {
                        Text("Cancel")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 10)
                            .overlay(Capsule().stroke(Color.secondary.opacity(0.4)))
                    }
                }
                .padding(.horizontal, 24)
            }
            .transition(.opacity)
        }
    }
}
