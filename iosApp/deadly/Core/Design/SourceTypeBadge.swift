import SwiftUI

/// Compact badge showing the best available source type for a show.
///
/// Uses two icon categories for simplicity:
/// - Radio waves icon for high-fidelity sources (SBD, FM, Matrix, Remaster)
/// - Mic icon for audience recordings
/// - Question mark for unknown
///
/// The text label shows the specific type (SBD, FM, Matrix, etc.).
struct SourceTypeBadge: View {
    let sourceType: RecordingSourceType

    var body: some View {
        Label(label, systemImage: iconName)
            .font(.caption2)
            .fontWeight(.medium)
            .foregroundStyle(.secondary)
            .labelStyle(.titleAndIcon)
    }

    private var iconName: String {
        switch sourceType {
        case .soundboard, .fm, .matrix, .remaster:
            return "dot.radiowaves.left.and.right"
        case .audience:
            return "mic.fill"
        case .unknown:
            return "questionmark.circle"
        }
    }

    private var label: String {
        sourceType == .unknown ? "?" : sourceType.displayName
    }
}
