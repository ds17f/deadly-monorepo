import SwiftUI
import SwiftAudioStreamEx
import Combine

struct PlayerScreen: View {
    let streamPlayer: StreamPlayer
    @Binding var isPresented: Bool
    var onViewShow: ((String) -> Void)? = nil

    @State private var sliderValue: Double?
    @State private var showErrorAlert = false
    @State private var showQRShare = false
    @State private var showShareChooser = false
    @State private var showMessageShare = false
    @State private var showEqualizerSheet = false
    @State private var showPlayerMenuSheet = false
    @State private var showConnectSheet = false
    @State private var isCurrentTrackFavorite = false
    @State private var remoteProgressTick = Date()
    @Environment(\.appContainer) private var container

    private var playbackError: StreamPlayerError? {
        if case .error(let error) = streamPlayer.playbackState {
            return error
        }
        return nil
    }

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

    // MARK: - Remote mode

    private var connectService: ConnectService {
        container.connectService
    }

    private var isRemoteMode: Bool {
        guard let state = connectService.userState else { return false }
        return state.activeDeviceId != nil && !connectService.isActiveDevice
    }

    private var displayProgress: Double {
        if isRemoteMode {
            let _ = remoteProgressTick
            guard let state = connectService.userState, state.durationMs > 0 else { return 0 }
            let posMs = Double(connectService.interpolatedPositionMs)
            return min(posMs / Double(state.durationMs), 1.0)
        }
        return streamPlayer.progress.progress
    }

    private var displayCurrentTime: TimeInterval {
        if isRemoteMode {
            let _ = remoteProgressTick
            return Double(connectService.interpolatedPositionMs) / 1000.0
        }
        return streamPlayer.progress.currentTime
    }

    private var displayDuration: TimeInterval {
        if isRemoteMode, let state = connectService.userState {
            return Double(state.durationMs) / 1000.0
        }
        return streamPlayer.progress.duration
    }

    private var displayIsPlaying: Bool {
        if isRemoteMode, let state = connectService.userState {
            return state.isPlaying
        }
        return streamPlayer.playbackState.isPlaying
    }

    private var headerContextText: String {
        if isRemoteMode, let name = connectService.userState?.activeDeviceName {
            return "Playing on \(name)"
        }
        return "Now Playing"
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()

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
                                .foregroundStyle(.primary)
                                .lineLimit(2)
                                .multilineTextAlignment(.center)

                            Text(streamPlayer.currentTrack?.albumTitle ?? "")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 24)

                        Spacer().frame(height: 32)

                        // Progress slider
                        VStack(spacing: 6) {
                            Slider(
                                value: Binding(
                                    get: { sliderValue ?? displayProgress },
                                    set: { sliderValue = $0 }
                                ),
                                in: 0...1
                            ) { editing in
                                if !editing, let value = sliderValue {
                                    if isRemoteMode {
                                        remoteSeek(fraction: value)
                                    } else {
                                        let target = value * streamPlayer.progress.duration
                                        streamPlayer.seek(to: target)
                                    }
                                    sliderValue = nil
                                }
                            }
                            .tint(DeadlyColors.primary)
                            .padding(.horizontal, 24)

                            HStack {
                                Text(formatTime(sliderValue.map { $0 * displayDuration }
                                                ?? displayCurrentTime))
                                Spacer()
                                Text("-\(formatTime(displayDuration - (sliderValue.map { $0 * displayDuration } ?? displayCurrentTime)))")
                            }
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                            .padding(.horizontal, 28)
                        }

                        Spacer().frame(height: 12)

                        // Queue position
                        if !isRemoteMode && streamPlayer.queueState.totalTracks > 0 {
                            Text("Track \(streamPlayer.queueState.currentIndex + 1) of \(streamPlayer.queueState.totalTracks)")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }

                        Spacer().frame(height: 28)

                        // Playback controls
                        HStack(spacing: 52) {
                            Button {
                                if isRemoteMode { remotePrevious() } else { streamPlayer.previous() }
                            } label: {
                                Image(systemName: "backward.fill")
                                    .font(.title)
                                    .foregroundStyle(.primary)
                            }

                            Button {
                                if isRemoteMode { remotePlayPause() } else { streamPlayer.togglePlayPause() }
                            } label: {
                                Image(systemName: displayIsPlaying
                                      ? "pause.circle.fill" : "play.circle.fill")
                                    .font(.system(size: 70))
                                    .foregroundStyle(DeadlyColors.primary)
                            }

                            Button {
                                if isRemoteMode { remoteNext() } else { streamPlayer.next() }
                            } label: {
                                Image(systemName: "forward.fill")
                                    .font(.title)
                                    .foregroundStyle(isRemoteMode || streamPlayer.queueState.hasNext
                                                     ? .primary : .tertiary)
                            }
                            .disabled(!isRemoteMode && !streamPlayer.queueState.hasNext)
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
        .task(id: streamPlayer.currentTrack?.id) {
            let show = container.playlistService.currentShow
            let title = streamPlayer.currentTrack?.title
            await container.panelContentService.loadContent(show: show, songTitle: title)
            loadFavoriteState()
        }
        .onChange(of: playbackError) { _, newError in
            if newError != nil {
                showErrorAlert = true
            }
        }
        .sheet(isPresented: $showEqualizerSheet) {
            EqualizerSheet()
                .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showConnectSheet) {
            ConnectSheet(connectService: container.connectService)
        }
        .sheet(isPresented: $showPlayerMenuSheet) {
            playerMenuSheet
        }
        .sheet(isPresented: $showShareChooser) {
            ShareChooserSheet(
                onMessageShare: {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showMessageShare = true
                    }
                },
                onQrShare: {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showQRShare = true
                    }
                }
            )
        }
        .sheet(isPresented: $showMessageShare) {
            if let show = container.playlistService.currentShow,
               let recording = container.playlistService.currentRecording {
                let url = buildShareUrl(showId: show.id, recordingId: recording.identifier, trackNumber: currentTrackNumber)
                let items = MessageShareService.shareItems(url: url)
                ShareActivityView(items: items)
            }
        }
        .sheet(isPresented: $showQRShare) {
            if let show = container.playlistService.currentShow,
               let recording = container.playlistService.currentRecording {
                QRShareSheet(
                    showId: show.id,
                    recordingId: recording.identifier,
                    showDate: DateFormatting.formatShowDate(show.date),
                    venue: show.venue.name,
                    location: show.venue.displayLocation,
                    coverImageUrl: streamPlayer.currentTrack?.artworkURL?.absoluteString,
                    trackNumber: currentTrackNumber,
                    songTitle: streamPlayer.currentTrack?.title
                )
            }
        }
        .onReceive(Timer.publish(every: 0.5, on: .main, in: .common).autoconnect()) { date in
            if isRemoteMode {
                remoteProgressTick = date
            }
        }
        .alert("Playback Error", isPresented: $showErrorAlert) {
            Button("Retry") {
                streamPlayer.play()
            }
            Button("Dismiss", role: .cancel) {
                // Just dismiss
            }
        } message: {
            if let error = playbackError {
                Text(error.localizedDescription)
            } else {
                Text("An error occurred during playback. Please check your network connection and try again.")
            }
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
                    .foregroundStyle(.primary)
            }
            .buttonStyle(.plain)

            Spacer()

            Button {
                if let showId = currentShowId { onViewShow?(showId) }
            } label: {
                VStack(spacing: 2) {
                    Text(headerContextText)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .textCase(.uppercase)
                    Text(streamPlayer.currentTrack?.albumTitle ?? "")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
            }
            .buttonStyle(.plain)
            .disabled(currentShowId == nil)

            Spacer()

            Button {
                showPlayerMenuSheet = true
            } label: {
                Image(systemName: "ellipsis")
                    .font(.title2)
                    .foregroundStyle(.primary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 8)
    }

    @ViewBuilder
    private var actionButtons: some View {
        HStack(spacing: 32) {
            Spacer()

            // Favorite
            Button {
                toggleFavoriteSong()
            } label: {
                Image(systemName: isCurrentTrackFavorite ? "heart.fill" : "heart")
                    .font(.title2)
                    .foregroundStyle(isCurrentTrackFavorite ? DeadlyColors.primary : .secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .disabled(currentShowId == nil)

            // Equalizer
            Button {
                showEqualizerSheet = true
            } label: {
                Image(systemName: "slider.vertical.3")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)

            // Share
            Button {
                showShareChooser = true
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .disabled(currentShowId == nil)

            // Connect
            Button {
                showConnectSheet = true
            } label: {
                Image(systemName: "dot.radiowaves.left.and.right")
                    .font(.title2)
                    .foregroundStyle(
                        container.connectService.connectionState == .connected
                        ? Color.accentColor : .secondary
                    )
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)

            Spacer()
        }
        .padding(.horizontal, 24)
    }

    private var playerMenuSheet: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        toggleFavoriteSong()
                        showPlayerMenuSheet = false
                    } label: {
                        Label(
                            isCurrentTrackFavorite ? "Favorited" : "Favorite",
                            systemImage: isCurrentTrackFavorite ? "heart.fill" : "heart"
                        )
                    }

                    Button {
                        showPlayerMenuSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            showEqualizerSheet = true
                        }
                    } label: {
                        Label("Equalizer", systemImage: "slider.vertical.3")
                    }

                    Button {
                        showPlayerMenuSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            showShareChooser = true
                        }
                    } label: {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }
                }
            }
            .tint(.primary)
            .navigationTitle("Options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") {
                        showPlayerMenuSheet = false
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Remote commands

    private func remotePlayPause() {
        guard let activeId = connectService.userState?.activeDeviceId else { return }
        let action = connectService.userState?.isPlaying == true ? "pause" : "play"
        connectService.sendCommand(targetDeviceId: activeId, command: PlaybackCommand(action: action))
    }

    private func remoteNext() {
        guard let activeId = connectService.userState?.activeDeviceId else { return }
        connectService.sendCommand(targetDeviceId: activeId, command: PlaybackCommand(action: "next"))
    }

    private func remotePrevious() {
        guard let activeId = connectService.userState?.activeDeviceId else { return }
        connectService.sendCommand(targetDeviceId: activeId, command: PlaybackCommand(action: "prev"))
    }

    private func remoteSeek(fraction: Double) {
        guard let activeId = connectService.userState?.activeDeviceId,
              let state = connectService.userState else { return }
        let seekMs = Int(fraction * Double(state.durationMs))
        connectService.sendCommand(targetDeviceId: activeId, command: PlaybackCommand(action: "seek", seekMs: seekMs))
    }

    private func toggleFavoriteSong() {
        guard let showId = currentShowId,
              let trackTitle = streamPlayer.currentTrack?.title else { return }
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
              let trackTitle = streamPlayer.currentTrack?.title else {
            isCurrentTrackFavorite = false
            return
        }
        isCurrentTrackFavorite = (try? container.reviewService.isSongFavorite(
            showId: showId, trackTitle: trackTitle, recordingId: currentRecordingId
        )) ?? false
    }

    @ViewBuilder
    private var infoPanels: some View {
        let panels = container.panelContentService
        if panels.isLoading {
            ProgressView()
                .tint(.secondary)
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

    private func buildShareUrl(showId: String, recordingId: String?, trackNumber: String?) -> String {
        var url = "\(container.appPreferences.shareBaseUrl)/shows/\(showId)"
        if let rid = recordingId { url += "/recording/\(rid)" }
        if let track = trackNumber { url += "/track/\(track)" }
        return url
    }

}

// MARK: - Share Activity View

struct ShareActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
