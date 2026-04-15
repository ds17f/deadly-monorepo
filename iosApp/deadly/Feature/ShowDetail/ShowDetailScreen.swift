import SwiftUI
import SwiftAudioStreamEx

struct ShowDetailScreen: View {
    let showId: String

    @Environment(\.appContainer) private var container

    private var playlistService: PlaylistServiceImpl { container.playlistService }
    private var streamPlayer: StreamPlayer { container.streamPlayer }
    private var downloadService: DownloadServiceImpl { container.downloadService }
    private var networkMonitor: NetworkMonitor { container.networkMonitor }
    private var connectService: ConnectService { container.connectService }

    /// Current show ID - uses the navigated show if available, falls back to initial showId
    private var currentShowId: String {
        playlistService.currentShow?.id ?? showId
    }

    @State private var showMenuSheet = false
    @State private var showQRShareSheet = false
    @State private var showShareChooser = false
    @State private var showMessageShare = false
    @State private var showRecordingPicker = false
    @State private var showEqualizerSheet = false
    @State private var isFavorite = false
    @State private var isDownloading = false
    @State private var showRemoveDownloadAlert = false
    @State private var showCancelDownloadAlert = false
    @State private var showReviewSheet = false
    @State private var showSetlistSheet = false
    @State private var showWriteReviewSheet = false
    @State private var favoriteTracks: Set<String> = []
    @State private var userReview: ShowReview?

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
            isFavorite = (try? container.favoritesService.isFavorite(showId: showId)) ?? false
            let review = try? container.reviewService.getShowReview(showId)
            userReview = review?.hasContent == true ? review : nil
        }
        .task(id: playlistService.currentShow?.id ?? showId) {
            let activeShowId = playlistService.currentShow?.id ?? showId
            do {
                for try await titles in container.reviewService.observeFavoriteTitles(showId: activeShowId) {
                    favoriteTracks = titles
                }
            } catch {}
        }
        .onChange(of: playlistService.currentShow?.id) { _, newId in
            if let newId {
                isFavorite = (try? container.favoritesService.isFavorite(showId: newId)) ?? false
                let review = try? container.reviewService.getShowReview(newId)
                userReview = review?.hasContent == true ? review : nil
            }
        }
    }

    // MARK: - Show content

    private func showContent(_ show: Show) -> some View {
        List {
            // Header
            Section {
                VStack(spacing: 12) {
                    ShowArtwork(
                        recordingId: playlistService.currentRecording?.identifier,
                        imageUrl: show.coverImageUrl,
                        size: 220,
                        cornerRadius: DeadlySize.cardCornerRadius
                    )
                    .frame(maxWidth: .infinity)

                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(DateFormatting.formatShowDate(show.date))
                                .font(.headline)
                                .bold()

                            Text("\(show.venue.name), \(show.location.displayText)")
                                .font(.body)
                                .foregroundStyle(.secondary)
                                .lineLimit(3)
                        }

                        Spacer()

                        HStack(spacing: 8) {
                            Button {
                                Task { await playlistService.navigateToPreviousShow() }
                            } label: {
                                Image(systemName: "chevron.left")
                                    .font(.title2)
                                    .foregroundColor(playlistService.hasPreviousShow ? .secondary : .secondary.opacity(0.3))
                                    .frame(width: 40, height: 40)
                            }
                            .disabled(!playlistService.hasPreviousShow)
                            .buttonStyle(.plain)

                            Button {
                                Task { await playlistService.navigateToNextShow() }
                            } label: {
                                Image(systemName: "chevron.right")
                                    .font(.title2)
                                    .foregroundColor(playlistService.hasNextShow ? .secondary : .secondary.opacity(0.3))
                                    .frame(width: 40, height: 40)
                            }
                            .disabled(!playlistService.hasNextShow)
                            .buttonStyle(.plain)
                        }
                    }

                    ratingCard(show)
                }
                .padding(.vertical, 8)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

            // Action row
            Section {
                HStack(spacing: 8) {
                    Button {
                        if isFavorite {
                            try? container.favoritesService.removeFromFavorites(showId: currentShowId)
                        } else {
                            try? container.favoritesService.addToFavorites(showId: currentShowId)
                        }
                        isFavorite.toggle()
                    } label: {
                        Image(systemName: isFavorite ? "heart.fill" : "heart")
                            .font(.title2)
                            .foregroundStyle(isFavorite ? DeadlyColors.primary : .secondary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)

                    downloadButton

                    // Setlist button
                    Button {
                        showSetlistSheet = true
                    } label: {
                        Image(systemName: "list.bullet.rectangle")
                            .font(.title2)
                            .foregroundStyle(.secondary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)

                    // Collections button (placeholder)
                    Button {
                        // TODO: Phase 5 — show collections sheet
                    } label: {
                        Image(systemName: "rectangle.stack")
                            .font(.title2)
                            .foregroundStyle(.secondary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)

                    // Menu button
                    Button {
                        showMenuSheet = true
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.title2)
                            .foregroundStyle(.secondary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    playButton
                }
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

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
                            isPlaying: isCurrentTrack(track) && streamPlayer.playbackState.isPlaying,
                            isLoading: isCurrentTrack(track) && (streamPlayer.playbackState == .loading || streamPlayer.playbackState == .buffering),
                            downloadState: trackStates[track.name],
                            isFavorite: favoriteTracks.contains(track.title)
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
        .sheet(isPresented: $showMenuSheet) {
            menuSheet(show)
        }
        .sheet(isPresented: $showReviewSheet) {
            ReviewDetailsSheet(show: show, playlistService: playlistService, userReview: userReview) {
                showWriteReviewSheet = true
            }
        }
        .sheet(isPresented: $showWriteReviewSheet) {
            if let show = playlistService.currentShow {
                let freshReview = (try? container.reviewService.getShowReview(show.id)) ?? ShowReview(showId: show.id)
                ShowReviewSheet(
                    showDate: DateFormatting.formatShowDate(show.date),
                    venue: show.venue.name,
                    location: show.location.displayText,
                    review: freshReview,
                    lineupMembers: show.lineup?.members.map(\.name) ?? [],
                    currentRecordingId: playlistService.currentRecording?.identifier,
                    bestRecordingId: show.bestRecordingId
                ) { notes, rating, recQuality, playQuality, standouts in
                    let showId = show.id
                    let recordingId = playlistService.currentRecording?.identifier
                    try? container.reviewService.updateShowNotes(showId, notes: notes)
                    try? container.reviewService.updateShowRating(showId, rating: rating)
                    try? container.reviewService.updateRecordingQuality(showId, quality: recQuality, recordingId: recordingId)
                    try? container.reviewService.updatePlayingQuality(showId, quality: playQuality)
                    let existingTags = (try? container.reviewService.getPlayerTags(showId)) ?? []
                    let existingNames = Set(existingTags.map(\.playerName))
                    let newNames = Set(standouts)
                    for name in existingNames.subtracting(newNames) {
                        try? container.reviewService.removePlayerTag(showId: showId, playerName: name)
                    }
                    for name in newNames.subtracting(existingNames) {
                        try? container.reviewService.upsertPlayerTag(showId: showId, playerName: name)
                    }
                    // Refresh user review state
                    let updatedReview = try? container.reviewService.getShowReview(showId)
                    userReview = updatedReview?.hasContent == true ? updatedReview : nil
                } onDelete: {
                    let showId = show.id
                    try? container.reviewService.deleteShowReview(showId)
                    userReview = nil
                }
            }
        }
        .sheet(isPresented: $showSetlistSheet) {
            SetlistSheet(show: show)
        }
        .sheet(isPresented: $showEqualizerSheet) {
            EqualizerSheet()
                .presentationDetents([.medium, .large])
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
                        showQRShareSheet = true
                    }
                }
            )
        }
        .sheet(isPresented: $showMessageShare) {
            if let recording = playlistService.currentRecording {
                let url = "\(container.appPreferences.shareBaseUrl)/shows/\(currentShowId)/recording/\(recording.identifier)"
                let items = MessageShareService.shareItems(url: url)
                ShareActivityView(items: items)
            }
        }
        .sheet(isPresented: $showQRShareSheet) {
            if let recording = playlistService.currentRecording {
                QRShareSheet(
                    showId: currentShowId,
                    recordingId: recording.identifier,
                    showDate: DateFormatting.formatShowDate(show.date),
                    venue: show.venue.name,
                    location: show.venue.displayLocation,
                    coverImageUrl: show.coverImageUrl,
                    trackNumber: nil,
                    songTitle: nil
                )
            }
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

    // MARK: - Rating card

    private func ratingCard(_ show: Show) -> some View {
        Button {
            showReviewSheet = true
            Task { await playlistService.loadReviews() }
        } label: {
            HStack {
                HStack(spacing: 8) {
                    CompactStarRating(
                        rating: show.averageRating,
                        color: userReview != nil ? DeadlyColors.secondary : DeadlyColors.primary
                    )
                    Text(show.hasRating
                        ? String(format: "%.1f", show.averageRating!)
                        : "N/A")
                        .font(.headline)
                        .fontWeight(.semibold)
                        .foregroundStyle(show.hasRating ? .primary : .secondary)
                }

                Spacer()

                HStack(spacing: 4) {
                    if userReview != nil {
                        Image(systemName: "square.and.pencil")
                            .font(.caption)
                            .foregroundStyle(DeadlyColors.secondary)
                    }
                    Text(show.totalReviews > 0
                        ? "(\(show.totalReviews))"
                        : "No reviews")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(12)
            .background(Color(.systemGray6).opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Menu sheet

    private func menuSheet(_ show: Show) -> some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        if isFavorite {
                            try? container.favoritesService.removeFromFavorites(showId: currentShowId)
                        } else {
                            try? container.favoritesService.addToFavorites(showId: currentShowId)
                        }
                        isFavorite.toggle()
                        showMenuSheet = false
                    } label: {
                        Label(
                            isFavorite ? "Remove from Favorites" : "Add to Favorites",
                            systemImage: isFavorite ? "heart.fill" : "heart"
                        )
                    }

                    Button {
                        showMenuSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            showSetlistSheet = true
                        }
                    } label: {
                        Label("Setlist", systemImage: "list.bullet.rectangle")
                    }

                    Button {
                        // TODO: Phase 5 — show collections sheet
                        showMenuSheet = false
                    } label: {
                        Label("Collections", systemImage: "rectangle.stack")
                    }
                }

                Section {
                    if playlistService.currentRecording != nil {
                        Button {
                            showMenuSheet = false
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                showShareChooser = true
                            }
                        } label: {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                    }

                    if show.hasMultipleRecordings {
                        Button {
                            showMenuSheet = false
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                showRecordingPicker = true
                            }
                        } label: {
                            Label("Choose Recording", systemImage: "waveform.circle")
                        }
                    }

                    Button {
                        showMenuSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            showEqualizerSheet = true
                        }
                    } label: {
                        Label("Equalizer", systemImage: "slider.vertical.3")
                    }
                }
            }
            .tint(.primary)
            .navigationTitle("Options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") {
                        showMenuSheet = false
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Play button

    private var playButton: some View {
        Button {
            handlePlayToggle()
        } label: {
            ZStack {
                Circle()
                    .fill(DeadlyColors.primary.opacity(0.1))
                    .frame(width: 56, height: 56)

                if isCurrentShowLoading {
                    ProgressView()
                        .tint(DeadlyColors.primary)
                } else {
                    Image(systemName: isCurrentShowPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.largeTitle)
                        .foregroundStyle(DeadlyColors.primary)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private var isCurrentShowPlaying: Bool {
        // Remote-controlling: use server state
        if connectService.isRemoteControlling,
           let state = connectService.connectState,
           let recording = playlistService.currentRecording,
           state.recordingId == recording.identifier {
            return state.playing
        }
        // Local/active device: use local player state
        guard let currentTrack = streamPlayer.currentTrack,
              let recording = playlistService.currentRecording else { return false }
        let isThisShow = currentTrack.metadata["recordingId"] == recording.identifier
        return isThisShow && streamPlayer.playbackState.isPlaying
    }

    private var isCurrentShowLoading: Bool {
        guard let currentTrack = streamPlayer.currentTrack,
              let recording = playlistService.currentRecording else { return false }
        let isThisShow = currentTrack.metadata["recordingId"] == recording.identifier
        let state = streamPlayer.playbackState
        return isThisShow && (state == .loading || state == .buffering)
    }

    private var isCurrentShowActive: Bool {
        // Remote-controlling: check server state
        if connectService.isRemoteControlling,
           let state = connectService.connectState,
           let recording = playlistService.currentRecording,
           state.recordingId == recording.identifier {
            return true
        }
        // Local/active device: check local player
        guard let currentTrack = streamPlayer.currentTrack,
              let recording = playlistService.currentRecording else { return false }
        return currentTrack.metadata["recordingId"] == recording.identifier
    }

    private func handlePlayToggle() {
        if isCurrentShowLoading {
            // Do nothing while loading
        } else if connectService.isRemoteControlling {
            // Remote control: send commands only, no local audio
            if isCurrentShowActive {
                if connectService.connectState?.playing == true {
                    connectService.sendPause()
                } else {
                    connectService.sendPlay()
                }
            } else {
                // Different show — playTrack already sends sendLoad with autoplay
                playlistService.playTrack(at: 0)
                playlistService.recordRecentPlay()
            }
        } else if isCurrentShowActive {
            // Local/active device: toggle local + send connect command optimistically
            let wasPlaying = streamPlayer.playbackState.isPlaying
            streamPlayer.togglePlayPause()
            if wasPlaying {
                connectService.sendPause()
            } else {
                connectService.sendPlay()
            }
        } else {
            // New show — playTrack handles local audio + sendLoad
            playlistService.playTrack(at: 0)
            playlistService.recordRecentPlay()
        }
    }

    // MARK: - Helpers

    private func isCurrentTrack(_ track: ArchiveTrack) -> Bool {
        guard let recording = playlistService.currentRecording,
              let currentTrack = streamPlayer.currentTrack else { return false }
        return currentTrack.metadata["recordingId"] == recording.identifier
            && currentTrack.title == track.title
    }

    // MARK: - Download button

    private var downloadStatus: FavoritesDownloadStatus {
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
    private func downloadContextMenu(for status: FavoritesDownloadStatus) -> some View {
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

    private func downloadIcon(for status: FavoritesDownloadStatus) -> Image {
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

    private func downloadIconColor(for status: FavoritesDownloadStatus) -> Color {
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

    private func handleDownloadAction(status: FavoritesDownloadStatus) {
        switch status {
        case .notDownloaded, .cancelled, .failed:
            isDownloading = true
            Task {
                do {
                    // Auto-add to favorites when downloading
                    if !isFavorite {
                        try? container.favoritesService.addToFavorites(showId: currentShowId)
                        isFavorite = true
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

}
