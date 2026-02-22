import SwiftUI
import SwiftAudioStreamEx

struct PlayerScreen: View {
    let streamPlayer: StreamPlayer
    @Binding var isPresented: Bool
    var onViewShow: ((String) -> Void)? = nil

    @State private var sliderValue: Double?
    @Environment(\.appContainer) private var container

    /// Extract the archive.org recording ID from a stream URL.
    /// URL format: https://archive.org/download/{recordingId}/{filename}
    private var artworkRecordingId: String? {
        guard let url = streamPlayer.currentTrack?.url else { return nil }
        let parts = url.pathComponents
        guard parts.count >= 3, parts[1] == "download" else { return nil }
        return parts[2]
    }

    private var currentShowId: String? {
        streamPlayer.currentTrack?.metadata["showId"]
    }

    private var currentRecordingId: String? {
        streamPlayer.currentTrack?.metadata["recordingId"]
    }

    private var currentTrackNumber: String? {
        streamPlayer.currentTrack?.metadata["trackNumber"]
    }

    private var shareText: String? {
        guard let show = container.playlistService.currentShow,
              let recording = container.playlistService.currentRecording else { return nil }

        let showId = show.id
        let recordingId = recording.identifier
        var url = "https://share.thedeadly.app/show/\(showId)/recording/\(recordingId)"
        if let trackNum = currentTrackNumber { url += "/track/\(trackNum)" }

        let trackTitle = streamPlayer.currentTrack?.title

        var lines: [String] = []
        lines.append("🌹⚡💀 Grateful Dead 💀⚡🌹")
        lines.append("")
        if let title = trackTitle { lines.append("🎵 \(title)") ; lines.append("") }
        lines.append("📅 \(show.date)")
        lines.append("📍 \(show.venue.name)")
        let loc = show.venue.displayLocation
        if !loc.isEmpty { lines.append("🌎 \(loc)") }
        lines.append("")
        lines.append("🎧 Source: \(recording.sourceType.displayName)")
        if show.hasRating { lines.append("⭐ Rating: \(show.displayRating)") }
        lines.append("")
        lines.append("🔗 Listen in The Deadly app:")
        lines.append(url)

        return lines.joined(separator: "\n")
    }

    var body: some View {
        ZStack {
            DeadlyColors.darkBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Sticky header — drag here to dismiss
                header
                    .gesture(
                        DragGesture()
                            .onEnded { value in
                                if value.translation.height > 80 {
                                    isPresented = false
                                }
                            }
                    )

                ScrollView {
                    VStack(spacing: 0) {
                        Spacer().frame(height: 24)

                        // Artwork
                        ShowArtwork(
                            recordingId: artworkRecordingId,
                            imageUrl: streamPlayer.currentTrack?.artworkURL?.absoluteString,
                            size: 300,
                            cornerRadius: DeadlySize.carouselCornerRadius
                        )
                        .shadow(color: .black.opacity(0.4), radius: 20, y: 10)

                        Spacer().frame(height: 32)

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

                        Spacer().frame(height: 24)

                        // Action buttons row
                        actionButtons

                        Spacer().frame(height: 32)

                        // Info panels
                        infoPanels

                        Spacer().frame(height: 40)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .task(id: streamPlayer.currentTrack?.id) {
            let show = container.playlistService.currentShow
            let title = streamPlayer.currentTrack?.title
            await container.panelContentService.loadContent(show: show, songTitle: title)
        }
    }

    // MARK: - Subviews

    private var header: some View {
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
    }

    @ViewBuilder
    private var actionButtons: some View {
        HStack(spacing: 20) {
            if let text = shareText {
                ShareLink(item: text) {
                    Label("Share", systemImage: "square.and.arrow.up")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(DeadlyColors.darkSurface)
                        .clipShape(Capsule())
                }
            }

            if let showId = currentShowId, !showId.isEmpty {
                Button {
                    onViewShow?(showId)
                } label: {
                    Label("View Show", systemImage: "calendar")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                        .background(DeadlyColors.darkSurface)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 24)
    }

    @ViewBuilder
    private var infoPanels: some View {
        let panels = container.panelContentService
        if panels.isLoading {
            ProgressView()
                .tint(.white.opacity(0.5))
                .padding(.vertical, 16)
        } else {
            VStack(spacing: 16) {
                if let lyrics = panels.lyrics {
                    InfoPanelCard(title: "Lyrics", content: lyrics)
                }
                if let venue = panels.venueInfo {
                    InfoPanelCard(title: "About the Venue", content: venue)
                }
                if let members = panels.credits, !members.isEmpty {
                    CreditsPanelCard(members: members)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func formatTime(_ seconds: TimeInterval) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
