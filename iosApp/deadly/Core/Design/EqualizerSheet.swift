import SwiftUI

/// Modal sheet version — wraps EqualizerView in a NavigationStack with Done button.
/// Use this for .sheet presentations from PlayerScreen and ShowDetailScreen.
struct EqualizerSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            EqualizerView()
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Done") { dismiss() }
                    }
                }
        }
    }
}

/// Standalone EQ view — can be pushed via NavigationLink or wrapped in a sheet.
struct EqualizerView: View {
    @Environment(\.appContainer) private var container

    var body: some View {
        let eq = container.equalizerService
        ScrollView {
            VStack(spacing: 16) {
                // Enable toggle
                Toggle("Enabled", isOn: Binding(
                    get: { eq.enabled },
                    set: { eq.setEnabled($0) }
                ))
                .padding(.horizontal)

                // Preset chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(EQDefaults.presets) { preset in
                            Button {
                                eq.selectPreset(preset)
                            } label: {
                                Text(preset.name)
                                    .font(.subheadline)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(
                                        eq.currentPreset == preset
                                            ? DeadlyColors.primary.opacity(0.2)
                                            : Color(.systemGray5)
                                    )
                                    .foregroundStyle(
                                        eq.currentPreset == preset
                                            ? DeadlyColors.primary
                                            : .primary
                                    )
                                    .clipShape(Capsule())
                            }
                            .disabled(!eq.enabled)
                        }
                    }
                    .padding(.horizontal)
                }

                // Band sliders
                HStack(alignment: .bottom, spacing: 0) {
                    ForEach(eq.bands) { band in
                        VStack(spacing: 4) {
                            Text("\(Int(band.gain))")
                                .font(.caption2)
                                .foregroundStyle(eq.enabled ? .primary : .secondary)
                                .frame(height: 16)

                            VerticalSlider(
                                value: Binding(
                                    get: { band.gain },
                                    set: { eq.setBandGain(index: band.id, gain: $0) }
                                ),
                                range: EQDefaults.minGain...EQDefaults.maxGain,
                                enabled: eq.enabled
                            )
                            .frame(height: 180)

                            Text(band.label)
                                .font(.caption2)
                                .foregroundStyle(eq.enabled ? .secondary : .tertiary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 8)

                // Reset button
                Button("Reset to Flat") {
                    eq.resetToFlat()
                }
                .font(.subheadline)
                .disabled(!eq.enabled)
            }
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
        .navigationTitle("Equalizer")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// A vertical slider built by rotating a standard Slider.
private struct VerticalSlider: View {
    @Binding var value: Float
    let range: ClosedRange<Float>
    let enabled: Bool

    var body: some View {
        GeometryReader { geo in
            Slider(
                value: $value,
                in: range
            )
            .disabled(!enabled)
            .tint(DeadlyColors.primary)
            .frame(width: geo.size.height)
            .rotationEffect(.degrees(-90))
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}
