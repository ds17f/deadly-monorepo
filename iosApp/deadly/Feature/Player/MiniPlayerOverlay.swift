import SwiftUI
import SwiftAudioStreamEx

extension View {
    func miniPlayer(streamPlayer: StreamPlayer, showFullPlayer: Binding<Bool>) -> some View {
        self.safeAreaInset(edge: .bottom) {
            MiniPlayerOverlay(streamPlayer: streamPlayer, showFullPlayer: showFullPlayer)
        }
    }
}

struct MiniPlayerOverlay: View {
    let streamPlayer: StreamPlayer
    @Binding var showFullPlayer: Bool

    /// Extract the archive.org recording ID from a stream URL.
    /// URL format: https://archive.org/download/{recordingId}/{filename}
    private var artworkRecordingId: String? {
        guard let url = streamPlayer.currentTrack?.url else { return nil }
        let parts = url.pathComponents
        guard parts.count >= 3, parts[1] == "download" else { return nil }
        return parts[2]
    }

    var body: some View {
        if streamPlayer.playbackState.isActive {
            HStack(spacing: 12) {
                // Artwork — ticket art from show if available, archive.org img as fallback
                ShowArtwork(
                    recordingId: artworkRecordingId,
                    imageUrl: streamPlayer.currentTrack?.artworkURL?.absoluteString,
                    size: 44,
                    cornerRadius: DeadlySize.artworkCornerRadius
                )

                // Track info
                VStack(alignment: .leading, spacing: 2) {
                    Text(streamPlayer.currentTrack?.title ?? "")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .lineLimit(1)

                    Text(streamPlayer.currentTrack?.albumTitle ?? "")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()

                // Controls
                Button {
                    streamPlayer.togglePlayPause()
                } label: {
                    Image(systemName: streamPlayer.playbackState.isPlaying ? "pause.fill" : "play.fill")
                        .font(.title3)
                        .foregroundStyle(.primary)
                }
                .buttonStyle(.plain)

                Button {
                    streamPlayer.next()
                } label: {
                    Image(systemName: "forward.fill")
                        .font(.title3)
                        .foregroundStyle(streamPlayer.queueState.hasNext ? .primary : .tertiary)
                }
                .buttonStyle(.plain)
                .disabled(!streamPlayer.queueState.hasNext)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal, 12)
            .padding(.bottom, 8)
            .contentShape(Rectangle())
            .onTapGesture {
                showFullPlayer = true
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .animation(.spring(), value: streamPlayer.playbackState.isActive)
        }
    }
}
