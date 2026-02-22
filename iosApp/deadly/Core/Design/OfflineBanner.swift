import SwiftUI

/// Slim banner displayed at top of content when network is unavailable.
/// Animates in/out with slide and opacity transitions.
struct OfflineBanner: View {
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 14, weight: .medium))
            Text("No internet connection")
                .font(.subheadline)
                .fontWeight(.medium)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .frame(height: 44)
        .background(Color.orange.opacity(0.9))
    }
}

/// View modifier that overlays the offline banner at the top when not connected.
struct OfflineBannerModifier: ViewModifier {
    let isConnected: Bool

    func body(content: Content) -> some View {
        content.overlay(alignment: .top) {
            if !isConnected {
                OfflineBanner()
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: isConnected)
    }
}

extension View {
    /// Shows an offline banner at the top when `isConnected` is false.
    func offlineBanner(isConnected: Bool) -> some View {
        modifier(OfflineBannerModifier(isConnected: isConnected))
    }
}

#Preview("Offline") {
    NavigationStack {
        Text("Content")
            .navigationTitle("Home")
    }
    .offlineBanner(isConnected: false)
}

#Preview("Online") {
    NavigationStack {
        Text("Content")
            .navigationTitle("Home")
    }
    .offlineBanner(isConnected: true)
}
