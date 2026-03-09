import AVFoundation
import SwiftAudioStreamEx

/// Manages a 10-band parametric EQ attached to the StreamPlayer's audio engine.
/// Persists settings via AppPreferences and applies them to an AVAudioUnitEQ node.
@Observable
@MainActor
final class EqualizerService {
    private let streamPlayer: StreamPlayer
    private let preferences: AppPreferences
    private let eqNode: AVAudioUnitEQ

    private(set) var bands: [EQBand]
    var enabled: Bool { preferences.eqEnabled }
    var currentPreset: EQPreset? {
        EQDefaults.presets.first { $0.id == preferences.eqPreset }
    }

    init(streamPlayer: StreamPlayer, preferences: AppPreferences) {
        self.streamPlayer = streamPlayer
        self.preferences = preferences
        self.eqNode = AVAudioUnitEQ(numberOfBands: EQDefaults.frequencies.count)

        // Build bands from saved preferences
        var initialBands: [EQBand] = []
        for (i, freq) in EQDefaults.frequencies.enumerated() {
            initialBands.append(EQBand(id: i, frequency: freq, gain: preferences.eqBandGains[i]))
        }
        self.bands = initialBands

        // Configure EQ node bands as parametric
        for (i, freq) in EQDefaults.frequencies.enumerated() {
            let param = eqNode.bands[i]
            param.filterType = .parametric
            param.frequency = freq
            param.bandwidth = 1.0 // octave
            param.gain = preferences.eqBandGains[i]
            param.bypass = !preferences.eqEnabled
        }

        // Attach to audio engine
        streamPlayer.attachAudioNode(eqNode)
    }

    func setEnabled(_ enabled: Bool) {
        preferences.eqEnabled = enabled
        for band in eqNode.bands {
            band.bypass = !enabled
        }
    }

    func setBandGain(index: Int, gain: Float) {
        guard index >= 0, index < bands.count else { return }
        let clampedGain = min(max(gain, EQDefaults.minGain), EQDefaults.maxGain)
        bands[index].gain = clampedGain
        eqNode.bands[index].gain = clampedGain

        // Save and clear preset (user is customizing)
        preferences.eqBandGains[index] = clampedGain
        preferences.eqPreset = ""
    }

    func selectPreset(_ preset: EQPreset) {
        preferences.eqPreset = preset.id
        for (i, gain) in preset.gains.enumerated() where i < bands.count {
            bands[i].gain = gain
            eqNode.bands[i].gain = gain
        }
        preferences.eqBandGains = preset.gains
    }

    func resetToFlat() {
        selectPreset(EQDefaults.presets[0]) // "Flat" is the first preset
    }

    deinit {
        // Note: Cannot call MainActor-isolated method from deinit.
        // The node will be cleaned up when the audio engine is torn down.
    }
}
