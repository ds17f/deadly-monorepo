import SwiftUI

extension View {
    func miniPlayer(miniPlayerService: MiniPlayerServiceImpl, showFullPlayer: Binding<Bool>) -> some View {
        self
            .contentMargins(.bottom, miniPlayerService.isVisible ? 80 : 0, for: .scrollContent)
            .overlay(alignment: .bottom) {
                MiniPlayerOverlay(service: miniPlayerService, showFullPlayer: showFullPlayer)
            }
    }
}

struct MiniPlayerOverlay: View {
    let service: MiniPlayerServiceImpl
    @Binding var showFullPlayer: Bool

    var body: some View {
        if service.isVisible {
            HStack(spacing: 12) {
                // Artwork — ticket art from show if available, archive.org img as fallback
                ShowArtwork(
                    recordingId: service.artworkRecordingId,
                    imageUrl: service.artworkURL,
                    size: 44,
                    cornerRadius: DeadlySize.artworkCornerRadius
                )

                // Track info or error message
                VStack(alignment: .leading, spacing: 2) {
                    if service.hasError {
                        Text("Playback Error")
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundStyle(.red)
                            .lineLimit(1)

                        Text("Tap for details")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    } else {
                        Text(service.trackTitle ?? "")
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .lineLimit(1)

                        Text(service.albumTitle ?? "")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }

                Spacer()

                // Controls
                if service.hasError {
                    // Show error icon instead of play controls
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.title3)
                        .foregroundStyle(.red)
                } else {
                    Button {
                        service.togglePlayPause()
                    } label: {
                        Image(systemName: service.isPlaying ? "pause.fill" : "play.fill")
                            .font(.title3)
                            .foregroundStyle(.primary)
                    }
                    .buttonStyle(.plain)

                    Button {
                        service.skipNext()
                    } label: {
                        Image(systemName: "forward.fill")
                            .font(.title3)
                            .foregroundStyle(service.hasNext ? .primary : .tertiary)
                    }
                    .buttonStyle(.plain)
                    .disabled(!service.hasNext)
                }
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
            .animation(.spring(), value: service.isVisible)
        }
    }
}
