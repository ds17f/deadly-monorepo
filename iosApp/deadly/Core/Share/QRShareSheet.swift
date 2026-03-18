import LinkPresentation
import SwiftUI

struct QRShareSheet: View {
    let showId: String
    let recordingId: String?
    let showDate: String
    let venue: String
    let location: String
    let coverImageUrl: String?
    let trackNumber: String?
    let songTitle: String?

    @State private var qrImage: UIImage?
    @State private var shareCard: UIImage?
    @State private var isSharing = false
    @Environment(\.dismiss) private var dismiss

    @Environment(\.appContainer) private var container

    private var shareUrl: String {
        var url = "\(container.appPreferences.shareBaseUrl)/shows/\(showId)"
        if let rid = recordingId { url += "/recording/\(rid)" }
        if let track = trackNumber { url += "/track/\(track)" }
        return url
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer(minLength: 8)

                if let card = shareCard {
                    Image(uiImage: card)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, 24)
                        .transition(.opacity.animation(.easeInOut(duration: 0.3)))
                } else if let qr = qrImage {
                    VStack(spacing: 16) {
                        Image(uiImage: qr)
                            .resizable()
                            .interpolation(.none)
                            .frame(width: 240, height: 240)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        ProgressView("Building share card…")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 24)
                } else {
                    ProgressView("Generating QR code…")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                Spacer()

                Button {
                    isSharing = true
                } label: {
                    Label("Share", systemImage: "square.and.arrow.up")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .tint(DeadlyColors.primary)
                .disabled(qrImage == nil)
                .padding(.horizontal, 24)
                .padding(.bottom, 16)
            }
            .navigationTitle("Share")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await generate() }
            .sheet(isPresented: $isSharing) {
                if let image = shareCard ?? qrImage {
                    ActivityView(items: shareItems(image))
                }
            }
        }
        .presentationDetents([.large])
    }

    // MARK: - Sharing

    private func shareItems(_ image: UIImage) -> [Any] {
        let title = [showDate, venue].filter { !$0.isEmpty }.joined(separator: " — ")
        return [
            ShareImageSource(image: image, url: URL(string: shareUrl), title: title),
            ShareURLSource(urlString: shareUrl)
        ]
    }

    // MARK: - Generation

    private func generate() async {
        let url = shareUrl
        let qr = await Task.detached(priority: .userInitiated) {
            ShareCardGenerator.generateQRCodeWithLogo(url: url, size: 600)
        }.value

        await MainActor.run { qrImage = qr }
        guard let qr else { return }

        let cover = await loadCoverImage()

        let date = showDate
        let ven = venue
        let loc = location
        let song = songTitle
        let card = await Task.detached(priority: .userInitiated) {
            ShareCardGenerator.buildShareCard(
                qrImage: qr,
                coverImage: cover,
                showDate: date,
                venue: ven,
                location: loc,
                songTitle: song
            )
        }.value

        await MainActor.run { shareCard = card }
    }

    private func loadCoverImage() async -> UIImage? {
        let candidates: [String?] = [
            coverImageUrl,
            recordingId.map { "https://archive.org/services/img/\($0)" }
        ]
        for urlString in candidates.compactMap({ $0 }) {
            guard let url = URL(string: urlString) else { continue }
            if let image = try? await fetchImage(from: url) {
                return image
            }
        }
        return nil
    }

    private func fetchImage(from url: URL) async throws -> UIImage? {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 12
        config.timeoutIntervalForResource = 18
        let (data, _) = try await URLSession(configuration: config).data(from: url)
        return UIImage(data: data)
    }
}

// MARK: - UIActivityItemSource Providers

private final class ShareImageSource: NSObject, UIActivityItemSource {
    let image: UIImage
    let url: URL?
    let title: String

    init(image: UIImage, url: URL?, title: String) {
        self.image = image
        self.url = url
        self.title = title
    }

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        image
    }

    func activityViewController(_ activityViewController: UIActivityViewController, itemForActivityType activityType: UIActivity.ActivityType?) -> Any? {
        image
    }

    func activityViewControllerLinkMetadata(_ activityViewController: UIActivityViewController) -> LPLinkMetadata? {
        let metadata = LPLinkMetadata()
        metadata.title = title
        metadata.url = url
        metadata.imageProvider = NSItemProvider(object: image)
        return metadata
    }
}

private final class ShareURLSource: NSObject, UIActivityItemSource {
    let urlString: String

    init(urlString: String) {
        self.urlString = urlString
    }

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        urlString
    }

    func activityViewController(_ activityViewController: UIActivityViewController, itemForActivityType activityType: UIActivity.ActivityType?) -> Any? {
        urlString
    }
}

// MARK: - Activity View

private struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
