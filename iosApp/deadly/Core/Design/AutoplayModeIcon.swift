import SwiftUI

/// The "Autoplay" control glyph (ADR-0010 Amendment). A constant ∞ anchor — so
/// users learn "∞ = autoplay" — with a small corner badge naming the active
/// mode (list = Show Queue, calendar = Chronological). `none` dims the ∞ and
/// drops the badge, reading as "autoplay, idle".
struct AutoplayModeIcon: View {
    let mode: AdvanceMode
    var font: Font = .title2

    var body: some View {
        Image(systemName: "infinity")
            .font(font)
            .foregroundStyle(mode == .none ? AnyShapeStyle(.secondary) : AnyShapeStyle(DeadlyColors.primary))
            .overlay(alignment: .bottomTrailing) {
                if let badge = mode.badgeSystemName {
                    Image(systemName: badge)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(DeadlyColors.primary)
                        .padding(2)
                        .background(Circle().fill(Color(.systemBackground)))
                        .offset(x: 5, y: 4)
                }
            }
    }
}
