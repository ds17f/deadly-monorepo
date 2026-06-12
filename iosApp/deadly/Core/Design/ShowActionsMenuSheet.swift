import SwiftUI

/// Which "This Show" sheet to open when the player deep-links into the show
/// detail screen (ADR-0014).
enum ShowDetailSheet {
    case setlist
    case collections
    case recording
}

/// ShowActionsMenuSheet — the single unified "⋯" overflow shared by the full
/// player and the playlist (ADR-0014).
///
/// One taxonomy, learned once and reused on both surfaces:
///   - Playback   — Choose Recording · Equalizer · Autoplay Next Show
///   - This Show  — Setlist · Collections (only when in ≥1) · Download
///   - Share      — Share
///
/// Each surface passes `nil` for the actions it already shows inline, so a
/// control is never one-tap-inline *and* in the menu. When a group collapses to
/// a single item on a surface, the group header is dropped (a lone item under a
/// bold header reads heavier than it is); SwiftUI's section divider remains.
struct ShowActionsMenuSheet: View {
    let isAutoplayEnabled: Bool
    let collectionsCount: Int

    // Playback — pass nil to hide (shown inline on this surface)
    var onChooseRecording: (() -> Void)? = nil
    var onEqualizer: (() -> Void)? = nil
    var onAutoplay: (() -> Void)? = nil
    // This Show
    var onSetlist: (() -> Void)? = nil
    var onCollections: (() -> Void)? = nil
    var onDownload: (() -> Void)? = nil
    // Share
    var onShare: (() -> Void)? = nil

    var onDone: () -> Void

    private var hasCollections: Bool { collectionsCount > 0 && onCollections != nil }

    private var playbackCount: Int {
        [onChooseRecording, onEqualizer, onAutoplay].compactMap { $0 }.count
    }
    private var thisShowCount: Int {
        [onSetlist, hasCollections ? onCollections : nil, onDownload].compactMap { $0 }.count
    }
    private var shareCount: Int {
        [onShare].compactMap { $0 }.count
    }

    var body: some View {
        NavigationStack {
            List {
                if playbackCount > 0 {
                    section(header: playbackCount >= 2 ? "Playback" : nil) {
                        playbackRows
                    }
                }
                if thisShowCount > 0 {
                    section(header: thisShowCount >= 2 ? "This Show" : nil) {
                        thisShowRows
                    }
                }
                if shareCount > 0 {
                    section(header: shareCount >= 2 ? "Share" : nil) {
                        shareRows
                    }
                }
            }
            .tint(.primary)
            .navigationTitle("Options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onDone)
                }
            }
        }
        .presentationDetents([.medium])
    }

    @ViewBuilder
    private func section<Content: View>(header: String?, @ViewBuilder content: () -> Content) -> some View {
        if let header {
            Section(header: Text(header)) { content() }
        } else {
            Section { content() }
        }
    }

    @ViewBuilder
    private var playbackRows: some View {
        if let onChooseRecording {
            row("Choose Recording", systemImage: "waveform.circle", action: onChooseRecording)
        }
        if let onEqualizer {
            row("Equalizer", systemImage: "slider.vertical.3", action: onEqualizer)
        }
        if let onAutoplay {
            Button(action: onAutoplay) {
                HStack {
                    Label("Autoplay Next Show", systemImage: "infinity")
                    Spacer()
                    if isAutoplayEnabled {
                        Image(systemName: "checkmark")
                            .foregroundStyle(DeadlyColors.primary)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var thisShowRows: some View {
        if let onSetlist {
            row("Setlist", systemImage: "list.bullet.rectangle", action: onSetlist)
        }
        if hasCollections, let onCollections {
            row("Collections", systemImage: "rectangle.stack", action: onCollections)
        }
        if let onDownload {
            row("Download", systemImage: "arrow.down.circle", action: onDownload)
        }
    }

    @ViewBuilder
    private var shareRows: some View {
        if let onShare {
            row("Share", systemImage: "square.and.arrow.up", action: onShare)
        }
    }

    private func row(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
        }
    }
}
