import Foundation

/// Equalizer band definition with frequency and gain.
struct EQBand: Identifiable {
    let id: Int
    let frequency: Float // Hz
    var gain: Float       // dB (-12 to +12)

    var label: String {
        if frequency >= 1000 {
            return "\(Int(frequency / 1000))k"
        }
        return "\(Int(frequency))"
    }
}

/// Predefined EQ preset with gains for 10 canonical bands.
struct EQPreset: Identifiable, Hashable {
    let id: String
    let name: String
    let gains: [Float] // 10 values, one per canonical band

    static func == (lhs: EQPreset, rhs: EQPreset) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

/// Canonical 10-band frequencies matching Android implementation.
enum EQDefaults {
    static let frequencies: [Float] = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]
    static let minGain: Float = -12
    static let maxGain: Float = 12

    static let presets: [EQPreset] = [
        EQPreset(id: "flat", name: "Flat", gains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]),
        EQPreset(id: "bass_boost", name: "Bass Boost", gains: [6, 5, 4, 2, 0, 0, 0, 0, 0, 0]),
        EQPreset(id: "treble_boost", name: "Treble Boost", gains: [0, 0, 0, 0, 0, 0, 2, 4, 5, 6]),
        EQPreset(id: "vocal", name: "Vocal", gains: [-2, -1, 0, 2, 4, 4, 2, 0, -1, -2]),
        EQPreset(id: "rock", name: "Rock", gains: [4, 3, 1, 0, -1, -1, 0, 2, 3, 4]),
        EQPreset(id: "classical", name: "Classical", gains: [3, 2, 1, 0, 0, 0, 0, 1, 2, 3]),
        EQPreset(id: "jazz", name: "Jazz", gains: [3, 2, 1, 2, -1, -1, 0, 1, 2, 3]),
        EQPreset(id: "electronic", name: "Electronic", gains: [4, 3, 1, 0, -1, 0, 1, 3, 4, 3]),
        EQPreset(id: "acoustic", name: "Acoustic", gains: [3, 2, 1, 0, 1, 1, 2, 2, 3, 2]),
        EQPreset(id: "live", name: "Live", gains: [-1, 0, 2, 3, 3, 3, 2, 1, 0, -1]),
    ]
}
