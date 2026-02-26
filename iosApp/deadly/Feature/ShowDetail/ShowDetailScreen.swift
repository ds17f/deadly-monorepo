import SwiftUI
import SwiftAudioStreamEx

struct ShowDetailScreen: View {
    let showId: String

    @Environment(\.appContainer) private var container

    private var playlistService: PlaylistServiceImpl { container.playlistService }
    private var streamPlayer: StreamPlayer { container.streamPlayer }
    private var downloadService: DownloadServiceImpl { container.downloadService }
    private var networkMonitor: NetworkMonitor { container.networkMonitor }

    /// Current show ID - uses the navigated show if available, falls back to initial showId
    private var currentShowId: String {
        playlistService.currentShow?.id ?? showId
    }

    @State private var showRecordingPicker = false
    @State private var isInLibrary = false
    @State private var showShareSheet = false
    @State private var isDownloading = false
    @State private var showRemoveDownloadAlert = false
    @State private var showCancelDownloadAlert = false

    var body: some View {
        Group {
            if let show = playlistService.currentShow {
                showContent(show)
            } else if playlistService.trackLoadError != nil {
                errorState
            } else {
                ProgressView("Loading show…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await playlistService.loadShow(showId)
            isInLibrary = (try? container.libraryService.isInLibrary(showId: showId)) ?? false
        }
        .onChange(of: playlistService.currentShow?.id) { _, newId in
            if let newId {
                isInLibrary = (try? container.libraryService.isInLibrary(showId: newId)) ?? false
            }
        }
    }

    // MARK: - Show content

    private func showContent(_ show: Show) -> some View {
        List {
            // Header
            Section {
                VStack(alignment: .center, spacing: 12) {
                    ShowArtwork(
                        recordingId: playlistService.currentRecording?.identifier,
                        imageUrl: show.coverImageUrl,
                        size: 200,
                        cornerRadius: DeadlySize.carouselCornerRadius
                    )

                    Text(DateFormatting.formatShowDate(show.date))
                        .font(.headline)

                    Text(show.venue.name)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    Text(show.location.displayText)
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if show.hasRating {
                        Text(show.displayRating)
                            .font(.caption)
                            .foregroundStyle(DeadlyColors.secondary)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

            // Action row
            Section {
                HStack(spacing: 8) {
                    Button {
                        if isInLibrary {
                            try? container.libraryService.removeFromLibrary(showId: currentShowId)
                        } else {
                            try? container.libraryService.addToLibrary(showId: currentShowId)
                        }
                        isInLibrary.toggle()
                    } label: {
                        Image(systemName: isInLibrary ? "heart.fill" : "heart")
                            .font(.title2)
                            .foregroundStyle(isInLibrary ? DeadlyColors.primary : .secondary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)

                    downloadButton

                    if let text = shareText(show: show) {
                        ShareLink(item: text) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.title2)
                                .foregroundStyle(.secondary)
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                    }

                    Spacer()

                    // Prev/Next show navigation
                    HStack(spacing: 0) {
                        Button {
                            Task { await playlistService.navigateToPreviousShow() }
                        } label: {
                            Image(systemName: "chevron.left")
                                .font(.title2)
                                .foregroundColor(playlistService.hasPreviousShow ? .secondary : .secondary.opacity(0.3))
                                .frame(width: 44, height: 44)
                        }
                        .disabled(!playlistService.hasPreviousShow)
                        .buttonStyle(.plain)

                        Button {
                            Task { await playlistService.navigateToNextShow() }
                        } label: {
                            Image(systemName: "chevron.right")
                                .font(.title2)
                                .foregroundColor(playlistService.hasNextShow ? .secondary : .secondary.opacity(0.3))
                                .frame(width: 44, height: 44)
                        }
                        .disabled(!playlistService.hasNextShow)
                        .buttonStyle(.plain)
                    }
                }
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

            // Recording bar
            if let recording = playlistService.currentRecording {
                Section {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(recording.displayTitle)
                                .font(.subheadline)

                            if let taper = recording.taper {
                                Text("Taper: \(taper)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Spacer()

                        if show.hasMultipleRecordings {
                            Button("Switch") {
                                showRecordingPicker = true
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                    }
                }
            }

            // Track list
            Section("Tracks") {
                if playlistService.isLoadingTracks {
                    HStack {
                        Spacer()
                        ProgressView("Loading tracks…")
                        Spacer()
                    }
                    .listRowSeparator(.hidden)
                } else if !networkMonitor.isConnected && downloadStatus != .completed {
                    // Offline and show is not downloaded - show message
                    VStack(spacing: 12) {
                        Image(systemName: "wifi.slash")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text("Offline")
                            .font(.headline)
                        Text("Download this show to listen offline")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                    .listRowSeparator(.hidden)
                } else if let error = playlistService.trackLoadError {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                } else {
                    let trackStates = downloadService.trackDownloadStates(for: currentShowId)
                    ForEach(Array(playlistService.tracks.enumerated()), id: \.element.id) { index, track in
                        TrackListRow(
                            track: track,
                            index: index,
                            isPlaying: isCurrentTrack(track),
                            downloadState: trackStates[track.name]
                        )
                        .contentShape(Rectangle())
                        .onTapGesture {
                            playlistService.playTrack(at: index)
                            playlistService.recordRecentPlay()
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .sheet(isPresented: $showRecordingPicker) {
            RecordingPicker(show: show, playlistService: playlistService)
        }
        .alert("Remove Download?", isPresented: $showRemoveDownloadAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Remove", role: .destructive) {
                downloadService.removeShow(currentShowId)
            }
        } message: {
            Text("This will delete the downloaded files for this show. You can download it again later.")
        }
        .alert("Cancel Download?", isPresented: $showCancelDownloadAlert) {
            Button("Keep Downloading", role: .cancel) { }
            Button("Cancel Download", role: .destructive) {
                downloadService.cancelShow(currentShowId)
            }
        } message: {
            Text("This will stop the download and remove any partially downloaded files.")
        }
    }

    // MARK: - Error state

    private var errorState: some View {
        ContentUnavailableView(
            "Show Not Found",
            systemImage: "exclamationmark.triangle",
            description: Text(playlistService.trackLoadError ?? "")
        )
    }

    // MARK: - Helpers

    private func isCurrentTrack(_ track: ArchiveTrack) -> Bool {
        guard let recording = playlistService.currentRecording,
              let currentURL = streamPlayer.currentTrack?.url else { return false }
        return currentURL == track.streamURL(recordingId: recording.identifier)
    }

    // MARK: - Download button

    private var downloadStatus: LibraryDownloadStatus {
        downloadService.downloadStatus(for: currentShowId)
    }

    @ViewBuilder
    private var downloadButton: some View {
        let status = downloadStatus
        Button {
            handleDownloadAction(status: status)
        } label: {
            downloadIcon(for: status)
                .font(.title2)
                .foregroundStyle(downloadIconColor(for: status))
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .disabled(isDownloading)
        .contextMenu {
            downloadContextMenu(for: status)
        }
    }

    @ViewBuilder
    private func downloadContextMenu(for status: LibraryDownloadStatus) -> some View {
        switch status {
        case .notDownloaded, .cancelled, .failed:
            Button {
                handleDownloadAction(status: status)
            } label: {
                Label("Download", systemImage: "arrow.down.circle")
            }
        case .queued, .downloading:
            Button {
                downloadService.pauseShow(currentShowId)
            } label: {
                Label("Pause Download", systemImage: "pause.circle")
            }
            Button(role: .destructive) {
                showCancelDownloadAlert = true
            } label: {
                Label("Cancel Download", systemImage: "xmark.circle")
            }
        case .paused:
            Button {
                downloadService.resumeShow(currentShowId)
            } label: {
                Label("Resume Download", systemImage: "play.circle")
            }
            Button(role: .destructive) {
                showCancelDownloadAlert = true
            } label: {
                Label("Cancel Download", systemImage: "xmark.circle")
            }
        case .completed:
            Button(role: .destructive) {
                showRemoveDownloadAlert = true
            } label: {
                Label("Remove Download", systemImage: "trash")
            }
        }
    }

    private func downloadIcon(for status: LibraryDownloadStatus) -> Image {
        switch status {
        case .notDownloaded, .cancelled, .failed:
            return Image(systemName: "arrow.down.circle")
        case .queued, .downloading:
            return Image(systemName: "arrow.down.circle.fill")
        case .paused:
            return Image(systemName: "pause.circle.fill")
        case .completed:
            return Image(systemName: "checkmark.circle.fill")
        }
    }

    private func downloadIconColor(for status: LibraryDownloadStatus) -> Color {
        switch status {
        case .notDownloaded, .cancelled:
            return .secondary
        case .queued, .downloading:
            return DeadlyColors.primary
        case .paused:
            return .orange
        case .completed:
            return DeadlyColors.primary
        case .failed:
            return .red
        }
    }

    private func handleDownloadAction(status: LibraryDownloadStatus) {
        switch status {
        case .notDownloaded, .cancelled, .failed:
            isDownloading = true
            Task {
                do {
                    // Auto-add to library when downloading
                    if !isInLibrary {
                        try? container.libraryService.addToLibrary(showId: currentShowId)
                        isInLibrary = true
                    }
                    try await downloadService.downloadShow(currentShowId, recordingId: playlistService.currentRecording?.identifier)
                } catch {
                    // Error is handled by the service
                }
                isDownloading = false
            }
        case .queued, .downloading:
            // Pause the download (tap again to resume, or long-press for cancel option)
            downloadService.pauseShow(currentShowId)
        case .paused:
            downloadService.resumeShow(currentShowId)
        case .completed:
            // Show confirmation before removing
            showRemoveDownloadAlert = true
        }
    }

    private func shareText(show: Show) -> String? {
        guard let recording = playlistService.currentRecording else { return nil }
        var lines: [String] = []
        lines.append("🌹⚡💀 Grateful Dead 💀⚡🌹")
        lines.append("")
        lines.append("📅 \(show.date)")
        lines.append("📍 \(show.venue.name)")
        let loc = show.venue.displayLocation
        if !loc.isEmpty { lines.append("🌎 \(loc)") }
        lines.append("")
        lines.append("🎧 Source: \(recording.sourceType.displayName)")
        if show.hasRating { lines.append("⭐ Rating: \(show.displayRating)") }
        lines.append("")
        lines.append("🔗 Listen in The Deadly app:")
        lines.append("https://share.thedeadly.app/show/\(show.id)/recording/\(recording.identifier)")
        return lines.joined(separator: "\n")
    }
}
