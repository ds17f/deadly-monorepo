import SwiftUI

extension View {
    func miniPlayer(miniPlayerService: MiniPlayerServiceImpl, showFullPlayer: Binding<Bool>) -> some View {
        self
            .contentMargins(.bottom, miniPlayerService.isVisible ? 80 : 0, for: .scrollContent)
            .overlay(alignment: .bottom) {
                MiniPlayerOverlay(service: miniPlayerService, showFullPlayer: showFullPlayer)
            }
            .overlay(alignment: .bottom) {
                // End-of-show countdown + "Queue A" snackbar (ADR-0010), floated
                // just above the mini player bar.
                QueueAdvanceOverlay()
                    .padding(.bottom, miniPlayerService.isVisible ? 84 : 4)
            }
    }
}

/// Bottom-floating banner for end-of-show auto-advance: a cancelable countdown
/// and the interrupt "Queue A" snackbar (ADR-0010).
struct QueueAdvanceOverlay: View {
    @Environment(\.appContainer) private var container

    var body: some View {
        let coordinator = container.playbackAdvanceCoordinator
        VStack(spacing: 8) {
            if let remaining = coordinator.countdownRemaining {
                banner {
                    HStack(spacing: 12) {
                        Image(systemName: "forward.fill").font(.subheadline)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Next show in \(remaining)s")
                                .font(.subheadline).fontWeight(.semibold)
                            if let title = coordinator.countdownNextTitle {
                                Text(title).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                        }
                        Spacer()
                        Button("Cancel") { coordinator.cancelCountdown() }
                            .font(.subheadline.weight(.semibold))
                            .buttonStyle(.plain)
                            .foregroundStyle(DeadlyColors.primary)
                    }
                }
            }
            if let payload = coordinator.pendingRequeue {
                banner {
                    HStack(spacing: 12) {
                        Image(systemName: "play.fill").font(.subheadline)
                        Text("Now playing").font(.subheadline).fontWeight(.semibold)
                        Spacer()
                        Button("Queue \(payload.showTitle)") {
                            container.playQueueService.enqueueNext(
                                showId: payload.showId,
                                recordingId: payload.recordingId,
                                resumeTrackIndex: payload.resumeTrackIndex,
                                resumePositionMs: payload.resumePositionMs
                            )
                            coordinator.dismissRequeue()
                        }
                        .font(.subheadline.weight(.semibold))
                        .buttonStyle(.plain)
                        .foregroundStyle(DeadlyColors.primary)
                        .lineLimit(1)
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        .animation(.spring(response: 0.35), value: coordinator.countdownRemaining)
        .animation(.spring(response: 0.35), value: coordinator.pendingRequeue)
    }

    @ViewBuilder
    private func banner<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .shadow(color: .black.opacity(0.18), radius: 8, y: 2)
            .transition(.move(edge: .bottom).combined(with: .opacity))
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
                .background(Color(.secondarySystemBackground))
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 12, topTrailingRadius: 12))
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
                .contentShape(Rectangle())
                .onTapGesture {
                    showFullPlayer = true
                }
                .sheet(isPresented: $showConnectSheet) {
                    ConnectSheet()
                        .environment(\.appContainer, container)
                }
                .onChange(of: container.connectService.showVolumeUI) { _, show in
                    if show {
                        showConnectSheet = true
                        container.connectService.showVolumeUI = false
                    }
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.spring(), value: service.isVisible)
                .animation(.easeInOut(duration: 0.3), value: showPlayingOnTooltip)
                .task {
                    // On cold launch into a remote-controlled session, briefly
                    // surface a "Playing on <device>" bubble so the user knows
                    // why the bar reflects another device.
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
                    // Connect / AirPlay device picker
                    Button {
                        showConnectSheet = true
                    } label: {
                        Image(systemName: "airplayaudio")
                            .font(.title3)
                            .foregroundStyle(container.connectService.isRemoteControlling ? DeadlyColors.primary : .secondary)
                            .frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)

                    if service.isPendingCommand
                        || service.isSkeleton || service.isPreparing || service.isRetrying
                        || (service.isBuffering && !service.isPlaying) {
                        // Pending remote command, skeleton, first-play seek dance,
                        // or engine still buffering before audio actually starts.
                        ProgressView()
                            .controlSize(.regular)
                            .frame(width: 32, height: 32)
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
