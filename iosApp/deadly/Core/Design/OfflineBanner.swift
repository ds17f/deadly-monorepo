import SwiftUI

/// Slim banner displayed above the tab bar when network is unavailable.
struct OfflineBanner: View {
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 14, weight: .medium))
            Text("Offline")
                .font(.caption)
                .fontWeight(.medium)
        }
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(Color(.secondarySystemBackground))
    }
}

/// View modifier that shows the offline banner above tab bar when not connected.
struct OfflineBannerModifier: ViewModifier {
    let isConnected: Bool

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            if !isConnected {
                OfflineBanner()
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: isConnected)
    }
}

extension View {
    /// Shows an offline banner at the bottom when `isConnected` is false.
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
