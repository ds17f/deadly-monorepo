import SwiftUI

/// Slim banner displayed above the tab bar communicating playback/network status.
struct OfflineBanner: View {
    enum Style {
        case offline
        case retrying
        case error(String)
    }
    let style: Style

    init(style: Style = .offline) {
        self.style = style
    }

    var body: some View {
        HStack(spacing: 6) {
            switch style {
            case .offline:
                Image(systemName: "wifi.slash")
                    .font(.subheadline).fontWeight(.medium)
                Text("Offline")
                    .font(.caption)
                    .fontWeight(.medium)
            case .retrying:
                ProgressView().controlSize(.mini)
                Text("Network trouble — retrying…")
                    .font(.caption)
                    .fontWeight(.medium)
            case .error(let message):
                Image(systemName: "wifi.exclamationmark")
                    .font(.subheadline).fontWeight(.medium)
                    .foregroundStyle(.orange)
                Text(message)
                    .font(.caption)
                    .fontWeight(.medium)
                    .lineLimit(1)
            }
        }
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(Color(.secondarySystemBackground))
    }
}

/// View modifier that shows the status banner above the tab bar.
/// Priority: error > retrying > offline.
struct OfflineBannerModifier: ViewModifier {
    let isConnected: Bool
    let isRetrying: Bool
    let errorMessage: String?

    private var style: OfflineBanner.Style? {
        if let msg = errorMessage { return .error(msg) }
        if isRetrying { return .retrying }
        if !isConnected { return .offline }
        return nil
    }

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            if let style = style {
                OfflineBanner(style: style)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: style)
    }
}

extension OfflineBanner.Style: Equatable {
    static func == (lhs: OfflineBanner.Style, rhs: OfflineBanner.Style) -> Bool {
        switch (lhs, rhs) {
        case (.offline, .offline), (.retrying, .retrying): return true
        case (.error(let a), .error(let b)): return a == b
        default: return false
        }
    }
}

extension View {
    /// Status banner at the bottom: error (highest priority), retrying, or offline.
    func offlineBanner(
        isConnected: Bool,
        isRetrying: Bool = false,
        errorMessage: String? = nil
    ) -> some View {
        modifier(OfflineBannerModifier(
            isConnected: isConnected,
            isRetrying: isRetrying,
            errorMessage: errorMessage
        ))
    }
}

#Preview("Offline") {
    NavigationStack {
        Text("Content")
            .navigationTitle("Home")
    }
    .offlineBanner(isConnected: false)
}

#Preview("Retrying") {
    NavigationStack {
        Text("Content")
            .navigationTitle("Home")
    }
    .offlineBanner(isConnected: true, isRetrying: true)
}

#Preview("Online") {
    NavigationStack {
        Text("Content")
            .navigationTitle("Home")
    }
    .offlineBanner(isConnected: true)
}
