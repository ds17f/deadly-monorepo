import SwiftUI

/// Docked side player for the wide layout (iPad, any phone in landscape) —
/// the "more than mini, less than full" player from the tablet/landscape
/// design. Sized to fit a single landscape screen without scrolling.
///
/// Layout (top → bottom): a header card — album art with the show date + venue
/// stacked beside it — then the song title and a seekable scrubber. The art
/// scales with the column height so the upper block fills the available space
/// instead of leaving a gap. A secondary action row (Connect · Share ·
/// Equalizer · ⋯ overflow) sits above the primary transport controls, which
/// stay pinned at the bottom.
///
/// Contextual: the caller (`MainNavigation.rootLayout`) only mounts this column
/// while a track is loaded (`service.isVisible`), so the content pane reclaims
/// the full width when playback is idle. Reads the shared `MiniPlayerServiceImpl`
/// (the same source the mini and full players use), so it stays in sync.
struct SidePlayerView: View {
    let service: MiniPlayerServiceImpl
    @Binding var showFullPlayer: Bool
    /// Navigates to a show's playlist (its home for Setlist / Collections /
    /// Choose Recording from the "⋯" menu). Mirrors `PlayerScreen.onViewShow`.
    var onViewShow: ((String, ShowDetailSheet?) -> Void)? = nil
    @Environment(\.appContainer) private var container
    /// Wider on iPad/large tablets (more room, keeps the date on one line beside
    /// the big cover); stays tight on landscape phones where width is scarce.
    @Environment(\.horizontalSizeClass) private var hSizeClass

    @State private var sliderValue: Double?
    @State private var isCurrentTrackFavorite = false
    @State private var showCollectionsCount = 0
    @State private var showConnectSheet = false
    @State private var showEqualizerSheet = false
    @State private var showMenuSheet = false
    @State private var showShareChooser = false
    @State private var showMessageShare = false
    @State private var showQRShare = false

    private var panelWidth: CGFloat { hSizeClass == .regular ? 400 : 320 }

    private var currentShowId: String? {
        container.streamPlayer.currentTrack?.metadata["showId"]
    }
    private var currentRecordingId: String? {
        container.streamPlayer.currentTrack?.metadata["recordingId"]
    }
    private var currentTrackNumber: String? {
        container.streamPlayer.currentTrack?.metadata["trackNumber"]
    }

    var body: some View {
        GeometryReader { geo in
            // Branch on available HEIGHT, not horizontalSizeClass (which is
            // unreliable here — an iPad can report .compact). Tall columns
            // (tablet, any orientation) stack the show text under the cover
            // like the full player; short columns (landscape phone) keep the
            // cover + text side-by-side so the title, scrubber, and bottom-
            // pinned transport still fit without scrolling.
            let isTall = geo.size.height >= 620
            let coverSize: CGFloat = {
                if isTall {
                    return max(120, min(geo.size.width - 48, geo.size.height * 0.40, 360))
                }
                let cap = min(220, geo.size.height - 348)
                return max(56, min(geo.size.height * 0.30, cap))
            }()

            VStack(spacing: 0) {
                header(coverSize: coverSize, isTall: isTall)

                Spacer().frame(height: 8)

                titleRow

                Spacer().frame(height: 12)

                scrubber

                // Absorbs any residual height above the bottom-pinned controls.
                Spacer(minLength: 12)

                secondaryActions
                    .padding(.vertical, 18)

                transport
                    .padding(.vertical, 18)
            }
            // Pad inside the full-height frame so the bottom controls stay
            // within bounds with clear spacing beneath them (padding applied
            // after the frame would spill the bottom row off-screen).
            .padding(.horizontal, 16)
            .padding(.top, 30)
            .padding(.bottom, 24)
            .frame(width: geo.size.width, height: geo.size.height, alignment: .top)
        }
        .frame(width: panelWidth)
        .background(Color(.secondarySystemBackground))
        .sheet(isPresented: $showConnectSheet) {
            ConnectSheet()
                .environment(\.appContainer, container)
        }
        .sheet(isPresented: $showEqualizerSheet) {
            EqualizerSheet()
                .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showMenuSheet) {
            menuSheet
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
                ShareActivityView(items: MessageShareService.shareItems(url: url))
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
                    coverImageUrl: container.streamPlayer.currentTrack?.artworkURL?.absoluteString,
                    trackNumber: currentTrackNumber,
                    songTitle: container.streamPlayer.currentTrack?.title
                )
            }
        }
        .task(id: container.streamPlayer.currentTrack?.id) {
            loadFavoriteState()
            showCollectionsCount = currentShowId.map { container.collectionsService.collectionsContaining(showId: $0).count } ?? 0
        }
    }

    // MARK: - Header (album art + date / venue beside it)

    @ViewBuilder
    private func header(coverSize: CGFloat, isTall: Bool) -> some View {
        let cover = ShowArtwork(
            recordingId: service.artworkRecordingId,
            imageUrl: service.artworkURL,
            size: coverSize,
            cornerRadius: DeadlySize.carouselCornerRadius
        )
        .shadow(color: .black.opacity(0.25), radius: 8, y: 4)
        .contentShape(Rectangle())
        .onTapGesture { showFullPlayer = true }

        if isTall {
            // Tablet: cover on top, show date + venue centered beneath it —
            // mirrors the full mobile player, filling the room under the art.
            VStack(spacing: 16) {
                cover

                VStack(spacing: 6) {
                    Text(service.showDate ?? "")
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)

                    Text(service.venue ?? "")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
            }
            .frame(maxWidth: .infinity)
        } else {
            // Landscape phone: side-by-side to stay compact against the short height.
            HStack(alignment: .top, spacing: 14) {
                cover

                VStack(alignment: .leading, spacing: 6) {
                    Text(service.showDate ?? "")
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    Text(service.venue ?? "")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                        .multilineTextAlignment(.leading)

                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: - Title + favorite

    private var titleRow: some View {
        HStack(alignment: .center, spacing: 8) {
            Text(service.trackTitle ?? "")
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundStyle(.primary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                toggleFavoriteSong()
            } label: {
                Image(systemName: isCurrentTrackFavorite ? "heart.fill" : "heart")
                    .font(.title3)
                    .foregroundStyle(isCurrentTrackFavorite ? DeadlyColors.primary : .secondary)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            .disabled(currentShowId == nil)
        }
    }

    // MARK: - Scrubber

    private var scrubber: some View {
        VStack(spacing: 4) {
            Slider(
                value: Binding(
                    get: { sliderValue ?? service.playbackProgress },
                    set: { sliderValue = $0 }
                ),
                in: 0...1
            ) { editing in
                if !editing, let value = sliderValue {
                    service.seek(fraction: value)
                    sliderValue = nil
                }
            }
            .tint(DeadlyColors.primary)

            HStack {
                let durSec = Double(service.durationMs) / 1000.0
                let posSec = sliderValue.map { $0 * durSec }
                    ?? Double(service.positionMs) / 1000.0
                Text(formatTime(posSec))
                Spacer()
                Text("-\(formatTime(max(0, durSec - posSec)))")
            }
            .font(.caption2)
            .foregroundStyle(.tertiary)
        }
    }

    // MARK: - Secondary actions (Connect · Share · Equalizer · ⋯)

    private var secondaryActions: some View {
        HStack(spacing: 0) {
            // Connect / AirPlay — left, with optional active-device label.
            Button {
                showConnectSheet = true
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "airplayaudio")
                        .font(.title3)
                    if let name = container.connectService.connectState?.activeDeviceName,
                       container.connectService.isRemoteControlling {
                        Text(name)
                            .font(.caption2)
                            .lineLimit(1)
                            .frame(maxWidth: 80, alignment: .leading)
                    }
                }
                .foregroundStyle(container.connectService.isRemoteControlling ? DeadlyColors.primary : .secondary)
            }
            .buttonStyle(.plain)

            Spacer()

            // Share
            Button {
                showShareChooser = true
            } label: {
                Image(systemName: "square.and.arrow.up")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            .disabled(currentShowId == nil)

            // Equalizer
            Button {
                showEqualizerSheet = true
            } label: {
                Image(systemName: "slider.vertical.3")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)

            // Overflow "⋯" — the full menu (Choose Recording · Autoplay ·
            // Setlist · Collections · Download).
            Button {
                showMenuSheet = true
            } label: {
                Image(systemName: "ellipsis")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Transport (pinned at the bottom)

    private var transport: some View {
        HStack(spacing: 36) {
            Button {
                container.playlistService.noteUserSkip(forward: false)
                service.skipPrev()
            } label: {
                Image(systemName: "backward.fill")
                    .font(.title2)
                    .foregroundStyle(.primary)
            }

            if service.isPendingCommand || service.isPreparing || service.isRetrying
                || (service.isBuffering && !service.isPlaying) {
                ProgressView()
                    .controlSize(.large)
                    .frame(width: 56, height: 56)
            } else {
                Button {
                    service.togglePlayPause()
                } label: {
                    Image(systemName: service.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 56))
                        .foregroundStyle(DeadlyColors.primary)
                }
            }

            Button {
                container.playlistService.noteUserSkip(forward: true)
                service.skipNext()
            } label: {
                Image(systemName: "forward.fill")
                    .font(.title2)
                    .foregroundStyle(service.hasNext ? .primary : .tertiary)
            }
            .disabled(!service.hasNext)
        }
    }

    // MARK: - Overflow menu

    private var menuSheet: some View {
        ShowActionsMenuSheet(
            isAutoplayEnabled: container.appPreferences.autoAdvanceEnabled,
            collectionsCount: showCollectionsCount,
            onChooseRecording: {
                showMenuSheet = false
                if let sid = currentShowId { onViewShow?(sid, .recording) }
            },
            onAutoplay: { toggleAutoAdvance() },
            onAddToUpNext: {
                showMenuSheet = false
                addToUpNext()
            },
            onSetlist: {
                showMenuSheet = false
                if let sid = currentShowId { onViewShow?(sid, .setlist) }
            },
            onCollections: {
                showMenuSheet = false
                if let sid = currentShowId { onViewShow?(sid, .collections) }
            },
            onDownload: {
                showMenuSheet = false
                downloadCurrentShow()
            },
            onDone: { showMenuSheet = false }
        )
    }

    // MARK: - Actions

    private func addToUpNext() {
        guard let showId = currentShowId else { return }
        if container.backlogService.contains(showId) {
            container.toastPresenter.show("Already in Up Next")
        } else {
            container.backlogService.addToBottom(showId)
            container.toastPresenter.show("Added to Up Next")
        }
    }

    private func toggleFavoriteSong() {
        guard let showId = currentShowId,
              let trackTitle = container.streamPlayer.currentTrack?.title else { return }
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
              let trackTitle = container.streamPlayer.currentTrack?.title else {
            isCurrentTrackFavorite = false
            return
        }
        isCurrentTrackFavorite = (try? container.reviewService.isSongFavorite(
            showId: showId, trackTitle: trackTitle
        )) ?? false
    }

    private func toggleAutoAdvance() {
        let newValue = !container.appPreferences.autoAdvanceEnabled
        container.appPreferences.autoAdvanceEnabled = newValue
        container.toastPresenter.show(autoplayToastMessage(newValue))
        container.analyticsService.track("feature_use", props: [
            "feature": "toggle_auto_advance",
            "category": "playback",
            "enabled": newValue,
        ])
    }

    private func downloadCurrentShow() {
        guard let showId = currentShowId else { return }
        Task {
            try? await container.downloadService.downloadShow(showId, recordingId: currentRecordingId)
        }
    }

    private func buildShareUrl(showId: String, recordingId: String?, trackNumber: String?) -> String {
        var url = "\(container.appPreferences.shareBaseUrl)/shows/\(showId)"
        if let rid = recordingId { url += "/recording/\(rid)" }
        if let track = trackNumber { url += "/track/\(track)" }
        return url
    }

    private func formatTime(_ seconds: TimeInterval) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

/// Idle-state stand-in for the wide layout's side player column. Keeps the
/// three-pane balance (rail · content · player) before anything is playing so
/// the screen doesn't look lopsided on first launch. Matches `SidePlayerView`'s
/// width and background so the live player drops straight in once a track loads.
struct SidePlayerPlaceholder: View {
    // Keep in lockstep with SidePlayerView.panelWidth so the column width
    // doesn't jump when the live player swaps in.
    @Environment(\.horizontalSizeClass) private var hSizeClass
    private var panelWidth: CGFloat { hSizeClass == .regular ? 400 : 320 }

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "play.circle")
                .font(.system(size: 48))
                .foregroundStyle(.tertiary)
            Text("Nothing playing")
                .font(.headline)
                .foregroundStyle(.secondary)
            Text("Pick a show to start listening")
                .font(.subheadline)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 24)
        .frame(width: panelWidth)
        .frame(maxHeight: .infinity)
        .background(Color(.secondarySystemBackground))
    }
}
