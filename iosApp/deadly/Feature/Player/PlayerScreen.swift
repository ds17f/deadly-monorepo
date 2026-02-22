import SwiftUI
import SwiftAudioStreamEx

struct PlayerScreen: View {
    let streamPlayer: StreamPlayer
    @Binding var isPresented: Bool
    @State private var sliderValue: Double?
    @GestureState private var dragOffset: CGFloat = 0

    /// Extract the archive.org recording ID from a stream URL.
    /// URL format: https://archive.org/download/{recordingId}/{filename}
    private var artworkRecordingId: String? {
        guard let url = streamPlayer.currentTrack?.url else { return nil }
        let parts = url.pathComponents
        guard parts.count >= 3, parts[1] == "download" else { return nil }
        return parts[2]
    }

    var body: some View {
        ZStack {
            DeadlyColors.darkBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Spotify-style header: down chevron | playing from | spacer
                HStack {
                    Button {
                        isPresented = false
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.title2)
                            .foregroundStyle(.white)
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    VStack(spacing: 2) {
                        Text("Now Playing")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.5))
                            .textCase(.uppercase)
                        Text(streamPlayer.currentTrack?.albumTitle ?? "")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundStyle(.white)
                            .lineLimit(1)
                    }

                    Spacer()

                    // Invisible spacer to balance the chevron
                    Image(systemName: "chevron.down")
                        .font(.title2)
                        .foregroundStyle(.clear)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 8)

                Spacer()

                // Artwork — ticket art from show if available, archive.org img as fallback
                ShowArtwork(
                    recordingId: artworkRecordingId,
                    imageUrl: streamPlayer.currentTrack?.artworkURL?.absoluteString,
                    size: 300,
                    cornerRadius: DeadlySize.carouselCornerRadius
                )
                .shadow(color: .black.opacity(0.4), radius: 20, y: 10)

                Spacer()

                // Track info
                VStack(spacing: 6) {
                    Text(streamPlayer.currentTrack?.title ?? "")
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)

                    Text(streamPlayer.currentTrack?.albumTitle ?? "")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.6))
                        .lineLimit(1)
                }
                .padding(.horizontal, 24)

                Spacer().frame(height: 32)

                // Progress slider
                VStack(spacing: 6) {
                    Slider(
                        value: Binding(
                            get: { sliderValue ?? streamPlayer.progress.progress },
                            set: { sliderValue = $0 }
                        ),
                        in: 0...1
                    ) { editing in
                        if !editing, let value = sliderValue {
                            let target = value * streamPlayer.progress.duration
                            streamPlayer.seek(to: target)
                            sliderValue = nil
                        }
                    }
                    .tint(DeadlyColors.primary)
                    .padding(.horizontal, 24)

                    HStack {
                        Text(formatTime(sliderValue.map { $0 * streamPlayer.progress.duration }
                                        ?? streamPlayer.progress.currentTime))
                        Spacer()
                        Text("-\(formatTime(streamPlayer.progress.remaining))")
                    }
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.5))
                    .padding(.horizontal, 28)
                }

                Spacer().frame(height: 12)

                // Queue position
                if streamPlayer.queueState.totalTracks > 0 {
                    Text("Track \(streamPlayer.queueState.currentIndex + 1) of \(streamPlayer.queueState.totalTracks)")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.4))
                }

                Spacer().frame(height: 28)

                // Playback controls
                HStack(spacing: 52) {
                    Button {
                        streamPlayer.previous()
                    } label: {
                        Image(systemName: "backward.fill")
                            .font(.title)
                            .foregroundStyle(.white)
                    }

                    Button {
                        streamPlayer.togglePlayPause()
                    } label: {
                        Image(systemName: streamPlayer.playbackState.isPlaying
                              ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 70))
                            .foregroundStyle(DeadlyColors.primary)
                    }

                    Button {
                        streamPlayer.next()
                    } label: {
                        Image(systemName: "forward.fill")
                            .font(.title)
                            .foregroundStyle(streamPlayer.queueState.hasNext
                                             ? .white : .white.opacity(0.3))
                    }
                    .disabled(!streamPlayer.queueState.hasNext)
                }

                Spacer()
            }
        }
        .offset(y: max(0, dragOffset))
        .gesture(
            DragGesture()
                .updating($dragOffset) { value, state, _ in
                    if value.translation.height > 0 {
                        state = value.translation.height
                    }
                }
                .onEnded { value in
                    if value.translation.height > 120 {
                        isPresented = false
                    }
                }
        )
        .preferredColorScheme(.dark)
    }

    private func formatTime(_ seconds: TimeInterval) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
