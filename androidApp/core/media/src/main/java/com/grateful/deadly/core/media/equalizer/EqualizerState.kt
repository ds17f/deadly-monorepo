package com.grateful.deadly.core.media.equalizer

/**
 * Snapshot of the equalizer's current configuration, exposed to UI.
 */
data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<BandInfo> = emptyList(),
    val currentPreset: String? = null,
    val presets: List<String> = EqPresets.ALL.map { it.name }
)

data class BandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val minLevel: Float,
    val maxLevel: Float,
    val currentLevel: Float
) {
    /** Human-readable label like "1k" or "250". */
    val label: String
        get() = if (centerFreqHz >= 1000) "${centerFreqHz / 1000}k" else "$centerFreqHz"
}

/**
 * A named set of 10-band gains (dB).
 * Frequencies: 32, 64, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz
 */
data class EqPreset(val name: String, val gains: List<Float>)

object EqPresets {
    val BAND_FREQUENCIES = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val Flat         = EqPreset("Flat",          listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    val BassBoost    = EqPreset("Bass Boost",    listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f))
    val TrebleBoost  = EqPreset("Treble Boost",  listOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f))
    val Vocal        = EqPreset("Vocal",         listOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -2f))
    val Rock         = EqPreset("Rock",          listOf(4f, 3f, 1f, 0f, -1f, 0f, 2f, 3f, 4f, 4f))
    val Classical    = EqPreset("Classical",     listOf(0f, 0f, 0f, 0f, 0f, 0f, -2f, -2f, -2f, -4f))
    val Jazz         = EqPreset("Jazz",          listOf(3f, 2f, 0f, 1f, -1f, -1f, 0f, 1f, 2f, 3f))
    val Electronic   = EqPreset("Electronic",   listOf(4f, 3f, 0f, -1f, -2f, 0f, 1f, 3f, 4f, 3f))
    val Acoustic     = EqPreset("Acoustic",      listOf(3f, 2f, 1f, 0f, 1f, 1f, 2f, 2f, 2f, 1f))
    val Live         = EqPreset("Live",          listOf(-1f, 0f, 2f, 3f, 3f, 3f, 2f, 1f, 0f, -1f))

    val ALL = listOf(Flat, BassBoost, TrebleBoost, Vocal, Rock, Classical, Jazz, Electronic, Acoustic, Live)

    fun findByName(name: String): EqPreset? = ALL.find { it.name == name }
}
