import SwiftUI
import UIKit

/// Archive.org auto-generates waveform spectrograms (180x45, 4:1 aspect) for audio
/// items that lack real artwork. Detect by pixel dimensions and show the logo instead.
private func isWaveform(_ image: UIImage) -> Bool {
    let w = image.size.width * image.scale
    let h = image.size.height * image.scale
    guard h > 0 else { return false }
    return h <= 50 || w / h > 3
}

struct ShowArtwork: View {
    let recordingId: String?
    let imageUrl: String?
    var size: CGFloat = DeadlySize.carouselCard
    var cornerRadius: CGFloat = DeadlySize.carouselCornerRadius
    var accessibilityDescription: String = "Show artwork"
    @State private var uiImage: UIImage?
    @State private var loadAttempted = false

    private var resolvedSourceType: RecordingSourceType? {
        recordingId.flatMap { ShowArtworkService.shared.sourceType(for: $0) }
    }

    private var resolvedUrl: URL? {
        if let imageUrl, let url = URL(string: imageUrl) {
            return url
        }
        if let recordingId {
            return URL(string: "https://archive.org/services/img/\(recordingId)")
        }
        return nil
    }

    var body: some View {
        ZStack {
            if let img = uiImage {
                Image(uiImage: img)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: size, height: size)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            } else {
                placeholder
            }
        }
        .overlay(alignment: .bottomTrailing) {
            if let resolvedSourceType, resolvedSourceType != .unknown {
                switch ShowArtworkService.shared.badgeStyle {
                case .none:
                    EmptyView()
                case .short, .long:
                    let label = ShowArtworkService.shared.badgeStyle == .short
                        ? resolvedSourceType.badgeLabel
                        : resolvedSourceType.displayName
                    Text(label)
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 2)
                        .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 4))
                        .padding(3)
                case .icon:
                    if let sfSymbol = resolvedSourceType.sfSymbolName {
                        Image(systemName: sfSymbol)
                            .font(.system(size: 8, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 16, height: 16)
                            .background(.black.opacity(0.6), in: Circle())
                            .padding(3)
                    }
                }
            }
        }
        .accessibilityLabel(accessibilityDescription)
        .task(id: resolvedUrl?.absoluteString) {
            loadAttempted = false
            guard let url = resolvedUrl else {
                uiImage = nil
                loadAttempted = true
                return
            }

            // Check memory cache synchronously first to avoid placeholder flash
            if let memCached = ImageCache.shared.cachedImage(for: url), !isWaveform(memCached) {
                uiImage = memCached
                loadAttempted = true
                return
            }

            // Not in memory - clear and load (may show placeholder briefly)
            uiImage = nil
            if let cached = await ImageCache.shared.image(for: url), !isWaveform(cached) {
                uiImage = cached
            }
            loadAttempted = true
        }
    }

    private var placeholder: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(Color(.secondarySystemBackground))
            Image(systemName: "music.note")
                .font(.system(size: size * 0.35))
                .foregroundStyle(.secondary)
        }
        .frame(width: size, height: size)
    }
}
