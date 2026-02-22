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

    @State private var uiImage: UIImage?

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
        .task(id: resolvedUrl?.absoluteString) {
            uiImage = nil
            guard let url = resolvedUrl,
                  let (data, _) = try? await URLSession.shared.data(from: url),
                  let img = UIImage(data: data),
                  !isWaveform(img) else { return }
            uiImage = img
        }
    }

    private var placeholder: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(DeadlyColors.darkSurface)
            .frame(width: size, height: size)
            .overlay {
                Image("deadly_logo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .padding(size * 0.15)
                    .opacity(0.6)
            }
    }
}
