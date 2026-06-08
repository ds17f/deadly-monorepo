import SwiftUI

/// Transient "new message" pill shown above the tab bar (decision C). Tapping
/// opens the inbox. Auto-dismissed by the presenter after a few seconds.
struct NotificationToastView: View {
    let arrival: NewArrival
    let onTap: () -> Void

    private var message: String {
        arrival.count > 1 ? "\(arrival.count) new messages" : "New: \(arrival.title)"
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                Image(systemName: "bell.fill")
                Text(message)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.regularMaterial, in: Capsule())
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.12)))
            .shadow(radius: 8, y: 2)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
    }
}
