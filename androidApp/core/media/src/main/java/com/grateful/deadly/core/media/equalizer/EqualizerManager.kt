package com.grateful.deadly.core.media.equalizer

import android.media.audiofx.Equalizer
import android.util.Log

/**
 * Thin wrapper around [android.media.audiofx.Equalizer].
 *
 * Handles the mismatch between our canonical 10-band presets and the
 * device's actual band count (typically 5) by mapping each device band
 * to the nearest canonical frequency.
 */
class EqualizerManager {

    companion object {
        private const val TAG = "EqualizerManager"
        private const val MIN_LEVEL_DB = -12f
        private const val MAX_LEVEL_DB = 12f
    }

    private var equalizer: Equalizer? = null
    private var deviceBandCount: Int = 0
    private var deviceBandFreqs: List<Int> = emptyList()

    /** Mapping from device band index → canonical 10-band index (nearest frequency). */
    private var deviceToCanonicalMap: List<Int> = emptyList()

    val isInitialized: Boolean get() = equalizer != null

    fun init(audioSessionId: Int) {
        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            deviceBandCount = eq.numberOfBands.toInt()
            deviceBandFreqs = (0 until deviceBandCount).map { band ->
                eq.getCenterFreq(band.toShort()) / 1000 // milliHz → Hz
            }
            deviceToCanonicalMap = deviceBandFreqs.map { freq ->
                EqPresets.BAND_FREQUENCIES.indices.minByOrNull { i ->
                    kotlin.math.abs(EqPresets.BAND_FREQUENCIES[i] - freq)
                } ?: 0
            }
            Log.d(TAG, "Initialized: $deviceBandCount bands, freqs=$deviceBandFreqs, map=$deviceToCanonicalMap")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Equalizer", e)
            equalizer = null
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing equalizer", e)
        }
        equalizer = null
        deviceBandCount = 0
        deviceBandFreqs = emptyList()
        deviceToCanonicalMap = emptyList()
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set enabled=$enabled", e)
        }
    }

    /**
     * Apply a 10-band gain array to the device EQ.
     * Maps canonical bands to device bands by nearest frequency.
     */
    fun applyCanonicalGains(gains: List<Float>) {
        val eq = equalizer ?: return
        try {
            for (deviceBand in 0 until deviceBandCount) {
                val canonicalIndex = deviceToCanonicalMap[deviceBand]
                val gainDb = gains.getOrElse(canonicalIndex) { 0f }
                val levelRange = eq.bandLevelRange
                val minMb = levelRange[0].toInt()
                val maxMb = levelRange[1].toInt()
                val millibels = (gainDb * 100).toInt().coerceIn(minMb, maxMb)
                eq.setBandLevel(deviceBand.toShort(), millibels.toShort())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply gains", e)
        }
    }

    /**
     * Set a single canonical band's gain.
     * Finds any device bands mapped to that canonical index and updates them.
     */
    fun setCanonicalBandGain(canonicalIndex: Int, gainDb: Float) {
        val eq = equalizer ?: return
        try {
            for (deviceBand in 0 until deviceBandCount) {
                if (deviceToCanonicalMap[deviceBand] == canonicalIndex) {
                    val levelRange = eq.bandLevelRange
                    val minMb = levelRange[0].toInt()
                    val maxMb = levelRange[1].toInt()
                    val millibels = (gainDb * 100).toInt().coerceIn(minMb, maxMb)
                    eq.setBandLevel(deviceBand.toShort(), millibels.toShort())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set band $canonicalIndex gain", e)
        }
    }

    fun getBandLevelRange(): Pair<Float, Float> {
        return MIN_LEVEL_DB to MAX_LEVEL_DB
    }
}
