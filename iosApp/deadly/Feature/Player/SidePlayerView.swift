import SwiftUI

/// Docked side player for the wide layout (iPad, any phone in landscape) —
/// the "more than mini, less than full" player from the tablet/landscape
/// design. A compact vertical arrangement reusing the full player's building
/// blocks: cover art, track info (+ favorite), a seekable scrubber, and
/// transport controls.
///
/// Contextual: the caller (`MainNavigation.rootLayout`) only mounts this column
/// while a track is loaded (`service.isVisible`), so the content pane reclaims
/// the full width when playback is idle. Reads the shared `MiniPlayerServiceImpl`
/// (the same source the mini and full players use), so it stays in sync.
struct SidePlayerView: View {
    let service: MiniPlayerServiceImpl
    @Binding var showFullPlayer: Bool
    @Environment(\.appContainer) private var container

    @State private var sliderValue: Double?
    @State private var isCurrentTrackFavorite = false

    private static let panelWidth: CGFloat = 320

    private var currentShowId: String? {
        container.streamPlayer.currentTrack?.metadata["showId"]
    }
    private var currentRecordingId: String? {
        container.streamPlayer.currentTrack?.metadata["recordingId"]
    }
    private var currentTrackNumber: String? {
        container.streamPlayer.currentTrack?.metadata["trackNumber"]
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // Cover art — tap to open the full player.
                ShowArtwork(
                    recordingId: service.artworkRecordingId,
                    imageUrl: service.artworkURL,
                    size: Self.panelWidth - 48,
                    cornerRadius: DeadlySize.carouselCornerRadius
                )
                .shadow(color: .black.opacity(0.3), radius: 12, y: 6)
                .contentShape(Rectangle())
                .onTapGesture { showFullPlayer = true }

                Spacer().frame(height: 24)

                // Track info with the prominent Favorite action (ADR-0014).
                HStack(alignment: .center, spacing: 8) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(service.trackTitle ?? "")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)

                        Text(service.displaySubtitle ?? "")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Button {
                        toggleFavoriteSong()
                    } label: {
                        Image(systemName: isCurrentTrackFavorite ? "heart.fill" : "heart")
                            .font(.title3)
                            .foregroundStyle(isCurrentTrackFavorite ? DeadlyColors.primary : .secondary)
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(.plain)
                    .disabled(currentShowId == nil)
                }

                Spacer().frame(height: 16)

                // Progress slider
                Slider(
                    value: Binding(
                        get: { sliderValue ?? service.playbackProgress },
                        set: { sliderValue = $0 }
                    ),
                    in: 0...1
                ) { editing in
                    if !editing, let value = sliderValue {
                        service.seek(fraction: value)
                        sliderValue = nil
                    }
                }
                .tint(DeadlyColors.primary)

                HStack {
                    let durSec = Double(service.durationMs) / 1000.0
                    let posSec = sliderValue.map { $0 * durSec }
                        ?? Double(service.positionMs) / 1000.0
                    Text(formatTime(posSec))
                    Spacer()
                    Text("-\(formatTime(max(0, durSec - posSec)))")
                }
                .font(.caption2)
                .foregroundStyle(.tertiary)

                Spacer().frame(height: 16)

                // Transport controls
                HStack(spacing: 36) {
                    Button {
                        container.playlistService.noteUserSkip(forward: false)
                        service.skipPrev()
                    } label: {
                        Image(systemName: "backward.fill")
                            .font(.title2)
                            .foregroundStyle(.primary)
                    }

                    if service.isPendingCommand || service.isPreparing || service.isRetrying
                        || (service.isBuffering && !service.isPlaying) {
                        ProgressView()
                            .controlSize(.large)
                            .frame(width: 56, height: 56)
                    } else {
                        Button {
                            service.togglePlayPause()
                        } label: {
                            Image(systemName: service.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                                .font(.system(size: 56))
                                .foregroundStyle(DeadlyColors.primary)
                        }
                    }

                    Button {
                        container.playlistService.noteUserSkip(forward: true)
                        service.skipNext()
                    } label: {
                        Image(systemName: "forward.fill")
                            .font(.title2)
                            .foregroundStyle(service.hasNext ? .primary : .tertiary)
                    }
                    .disabled(!service.hasNext)
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 24)
        }
        .frame(width: Self.panelWidth)
        .frame(maxHeight: .infinity)
        .background(Color(.secondarySystemBackground))
        .task(id: container.streamPlayer.currentTrack?.id) {
            loadFavoriteState()
        }
    }

    private func toggleFavoriteSong() {
        guard let showId = currentShowId,
              let trackTitle = container.streamPlayer.currentTrack?.title else { return }
        try? container.reviewService.toggleFavoriteSong(
            showId: showId,
            trackTitle: trackTitle,
            trackNumber: currentTrackNumber.flatMap { Int($0) },
            recordingId: currentRecordingId
        )
        isCurrentTrackFavorite.toggle()
    }

    private func loadFavoriteState() {
        guard let showId = currentShowId,
              let trackTitle = container.streamPlayer.currentTrack?.title else {
            isCurrentTrackFavorite = false
            return
        }
        isCurrentTrackFavorite = (try? container.reviewService.isSongFavorite(
            showId: showId, trackTitle: trackTitle
        )) ?? false
    }

    private func formatTime(_ seconds: TimeInterval) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
