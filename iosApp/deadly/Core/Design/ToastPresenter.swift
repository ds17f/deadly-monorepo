import SwiftUI

/// App-wide transient toast presenter.
///
/// Any surface can call `container.toastPresenter.show(...)`; the root view
/// renders the current `message` as a pill above the tab bar and it auto-
/// dismisses. Use for lightweight confirmations of actions with no other
/// on-screen feedback — e.g. the Autoplay toggle, whose only visible change is
/// an icon tint (ADR-0014). Deliberately generic so other surfaces can reuse it.
///
/// Convention: ALL transient, non-actionable confirmations should go through this
/// (not one-off `.alert`s). See `docs/docs/todo/transient-toasts.md`.
@Observable
@MainActor
final class ToastPresenter {
    private(set) var message: String?
    private var dismissTask: Task<Void, Never>?

    nonisolated init() {}

    func show(_ message: String) {
        self.message = message
        dismissTask?.cancel()
        dismissTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            self?.message = nil
        }
    }
}

/// Shared copy for the Autoplay toggle confirmation, matching Android so both
/// platforms say the same thing (ADR-0014).
func autoplayToastMessage(_ enabled: Bool) -> String {
    enabled ? "Autoplay on — the next show plays when this one ends" : "Autoplay off"
}

/// Transient pill used by the root view to render the current toast message.
struct ActionToastView: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.subheadline.weight(.semibold))
            .multilineTextAlignment(.center)
            .lineLimit(2)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.regularMaterial, in: Capsule())
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.12)))
            .shadow(radius: 8, y: 2)
            .padding(.horizontal, 24)
    }
}
