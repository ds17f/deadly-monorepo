import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

enum ShareCardGenerator {

    // MARK: - QR Code

    /// Generates a QR code image at the given size.
    static func generateQRCodeWithLogo(url: String, size: CGFloat) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(url.utf8)
        filter.correctionLevel = "M"

        guard let outputImage = filter.outputImage else { return nil }

        let scale = size / outputImage.extent.width
        let scaledImage = outputImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        let ciContext = CIContext()
        guard let cgImage = ciContext.createCGImage(scaledImage, from: scaledImage.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    // MARK: - Share Card

    /// Builds a 1080×1920 branded concert poster with the given QR code and metadata.
    static func buildShareCard(
        qrImage: UIImage,
        coverImage: UIImage?,
        showDate: String,
        venue: String,
        location: String,
        songTitle: String? = nil
    ) -> UIImage {
        let W: CGFloat = 1080
        let H: CGFloat = 1920
        let topH = H * 0.48
        let bg = UIColor(red: 0x0D / 255.0, green: 0x0D / 255.0, blue: 0x0D / 255.0, alpha: 1)

        let renderer = UIGraphicsImageRenderer(size: CGSize(width: W, height: H))
        return renderer.image { ctx in
            let cgCtx = ctx.cgContext

            // Dark background
            bg.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: W, height: H))

            // Top section: cover art or symbol placeholder
            let validCover = coverImage.flatMap { isValidCover($0) ? $0 : nil }
            if let cover = validCover {
                drawCenterCropped(image: cover, in: CGRect(x: 0, y: 0, width: W, height: topH), context: cgCtx)
            } else {
                let config = UIImage.SymbolConfiguration(pointSize: 160, weight: .light)
                if let symbol = UIImage(systemName: "music.note", withConfiguration: config) {
                    let tint = UIColor(white: 1, alpha: 0.3)
                    let tinted = symbol.withTintColor(tint, renderingMode: .alwaysOriginal)
                    let symSize = tinted.size
                    tinted.draw(in: CGRect(
                        x: (W - symSize.width) / 2,
                        y: (topH - symSize.height) / 2,
                        width: symSize.width,
                        height: symSize.height
                    ))
                }
            }

            // Gradient scrim over bottom 300pt of top section
            drawGradientScrim(in: CGRect(x: 0, y: topH - 300, width: W, height: 300), context: cgCtx)

            // Text overlays on the art
            let white = UIColor.white
            var textY = topH - 290

            if let song = songTitle, !song.isEmpty {
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.italicSystemFont(ofSize: 44),
                    .foregroundColor: white.withAlphaComponent(0.8)
                ]
                let str = song as NSString
                let bounds = str.boundingRect(
                    with: CGSize(width: W - 80, height: 200),
                    options: .usesLineFragmentOrigin,
                    attributes: attrs,
                    context: nil
                )
                str.draw(in: CGRect(x: 40, y: textY, width: W - 80, height: bounds.height), withAttributes: attrs)
                textY += bounds.height + 8
            }

            let dateAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: 72),
                .foregroundColor: white
            ]
            (showDate as NSString).draw(
                in: CGRect(x: 40, y: textY, width: W - 80, height: 90),
                withAttributes: dateAttrs
            )
            textY += 86

            let venueAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 46, weight: .regular),
                .foregroundColor: white.withAlphaComponent(0.85)
            ]
            let venueStr = venue as NSString
            let venueBounds = venueStr.boundingRect(
                with: CGSize(width: W - 80, height: 200),
                options: .usesLineFragmentOrigin,
                attributes: venueAttrs,
                context: nil
            )
            venueStr.draw(
                in: CGRect(x: 40, y: textY, width: W - 80, height: venueBounds.height),
                withAttributes: venueAttrs
            )

            // QR code in bottom section
            let qrSize: CGFloat = 620
            let qrPad: CGFloat = 28
            let qrX = (W - qrSize - qrPad * 2) / 2
            let qrY = topH + 70

            UIColor.white.setFill()
            ctx.fill(CGRect(x: qrX, y: qrY, width: qrSize + qrPad * 2, height: qrSize + qrPad * 2))
            qrImage.draw(in: CGRect(x: qrX + qrPad, y: qrY + qrPad, width: qrSize, height: qrSize))

            // Location text below QR
            if !location.isEmpty {
                let locAttrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 40),
                    .foregroundColor: white.withAlphaComponent(0.67)
                ]
                let locStr = location as NSString
                let locW = locStr.size(withAttributes: locAttrs).width
                locStr.draw(
                    at: CGPoint(x: (W - locW) / 2, y: qrY + qrSize + qrPad * 2 + 36),
                    withAttributes: locAttrs
                )
            }

            // Branding at bottom
            let brandAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 32),
                .foregroundColor: white.withAlphaComponent(0.33)
            ]
            let brandStr = "thedeadly.app" as NSString
            let brandW = brandStr.size(withAttributes: brandAttrs).width
            brandStr.draw(at: CGPoint(x: (W - brandW) / 2, y: H - 72), withAttributes: brandAttrs)
        }
    }

    // MARK: - Private Helpers

    private static func isValidCover(_ image: UIImage) -> Bool {
        guard image.size.height > 50 else { return false }
        return (image.size.width / image.size.height) <= 3.0
    }

    private static func drawCenterCropped(image: UIImage, in rect: CGRect, context: CGContext) {
        let imageAspect = image.size.width / image.size.height
        let rectAspect = rect.width / rect.height

        let drawRect: CGRect
        if imageAspect > rectAspect {
            let drawW = rect.height * imageAspect
            drawRect = CGRect(x: rect.minX - (drawW - rect.width) / 2, y: rect.minY, width: drawW, height: rect.height)
        } else {
            let drawH = rect.width / imageAspect
            drawRect = CGRect(x: rect.minX, y: rect.minY - (drawH - rect.height) / 2, width: rect.width, height: drawH)
        }

        context.saveGState()
        context.clip(to: rect)
        image.draw(in: drawRect)
        context.restoreGState()
    }

    private static func drawGradientScrim(in rect: CGRect, context: CGContext) {
        let colors = [
            UIColor.black.withAlphaComponent(0).cgColor,
            UIColor.black.withAlphaComponent(0.85).cgColor
        ]
        guard let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: colors as CFArray,
            locations: [0, 1]
        ) else { return }

        context.saveGState()
        context.clip(to: rect)
        context.drawLinearGradient(
            gradient,
            start: CGPoint(x: 0, y: rect.minY),
            end: CGPoint(x: 0, y: rect.maxY),
            options: []
        )
        context.restoreGState()
    }
}
