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
            VStack(spacing: 0) {
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

                            Text(service.displaySubtitle ?? "")
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
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                // Progress bar
                GeometryReader { geo in
                    Rectangle()
                        .fill(DeadlyColors.primary)
                        .frame(width: geo.size.width * service.playbackProgress, height: 3)
                }
                .frame(height: 3)
            }
            .background(Color(.secondarySystemBackground))
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 12, topTrailingRadius: 12))
            .padding(.horizontal, 12)
            .contentShape(Rectangle())
            .onTapGesture {
                showFullPlayer = true
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .animation(.spring(), value: service.isVisible)
        }
    }
}
