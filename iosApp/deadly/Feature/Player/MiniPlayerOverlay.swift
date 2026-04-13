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
    @Environment(\.appContainer) private var container
    @State private var showConnectSheet = false
    @State private var showPlayingOnTooltip = false

    var body: some View {
        if service.isVisible {
            miniPlayerBar
                .overlay(alignment: .topTrailing) {
                    if showPlayingOnTooltip, let deviceName = container.connectService.connectState?.activeDeviceName {
                        PlayingOnBubble(deviceName: deviceName)
                            .onTapGesture {
                                showPlayingOnTooltip = false
                                showConnectSheet = true
                            }
                            .padding(.trailing, 46)
                            .offset(y: -48)
                            .transition(.opacity.combined(with: .scale(scale: 0.9, anchor: .bottomTrailing)))
                    }
                }
            .sheet(isPresented: $showConnectSheet) {
                ConnectSheet()
            }
            .contentShape(Rectangle())
            .onTapGesture {
                showFullPlayer = true
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .animation(.spring(), value: service.isVisible)
            .animation(.easeInOut(duration: 0.3), value: showPlayingOnTooltip)
            .task {
                guard container.isColdLaunch,
                      container.connectService.isRemoteControlling,
                      container.connectService.connectState?.activeDeviceName != nil else { return }
                showPlayingOnTooltip = true
                try? await Task.sleep(for: .seconds(4))
                showPlayingOnTooltip = false
            }
        }
    }

    private var miniPlayerBar: some View {
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
                            showConnectSheet = true
                        } label: {
                            Image(systemName: "airplayaudio")
                                .font(.title3)
                                .foregroundStyle(container.connectService.isRemoteControlling ? DeadlyColors.primary : .secondary)
                                .frame(width: 32, height: 32)
                        }
                        .buttonStyle(.plain)

                        Button {
                            service.togglePlayPause()
                        } label: {
                            if service.isPendingCommand {
                                ProgressView()
                                    .frame(width: 24, height: 24)
                            } else {
                                Image(systemName: service.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.title3)
                                    .foregroundStyle(.primary)
                            }
                        }
                        .buttonStyle(.plain)
                        .disabled(service.isPendingCommand)
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
    }
}

// MARK: - Playing On Speech Bubble

private struct PlayingOnBubble: View {
    let deviceName: String
    private let arrowSize: CGFloat = 8
    private let cornerRadius: CGFloat = 12

    var body: some View {
        VStack(alignment: .trailing, spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: "airplayaudio")
                    .font(.caption)
                Text("Playing on \(deviceName)")
                    .font(.subheadline)
                    .fontWeight(.medium)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(DeadlyColors.primary)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))

            // Downward-pointing arrow aligned to the right
            HStack {
                Spacer()
                Triangle()
                    .fill(DeadlyColors.primary)
                    .frame(width: 14, height: arrowSize)
                    .padding(.trailing, 12)
            }
        }
        .shadow(color: .black.opacity(0.2), radius: 6, y: 3)
    }
}

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
