import SwiftUI
import CoreImage.CIFilterBuiltins

struct QRCodeView: View {
    @Environment(\.dismiss) private var dismiss
    let show: Show

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                if let image = generateQRCode() {
                    Image(uiImage: image)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)
                }
                Text(show.date)
                    .font(.headline)
                Text(show.venue.name)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .navigationTitle("QR Code")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func generateQRCode() -> UIImage? {
        guard let recordingId = show.bestRecordingId else { return nil }
        let url = "https://share.thedeadly.app/show/\(show.id)/recording/\(recordingId)"
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(url.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
