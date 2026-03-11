package com.grateful.deadly.core.media.visualizer

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.sqrt

/**
 * Thin wrapper around [android.media.audiofx.Visualizer] that extracts
 * per-band magnitude data from the audio pipeline via waveform capture.
 *
 * Uses audio session 0 (global mix output) because per-session capture
 * returns all zeros on many emulators and some devices.
 *
 * Computes RMS amplitude over fixed bands from the waveform rather than
 * relying on FFT callbacks, which don't fire reliably on all devices.
 *
 * Requires RECORD_AUDIO permission.
 */
class AudioVisualizerCapture {

    companion object {
        private const val TAG = "AudioVisualizerCapture"
        private const val NUM_BANDS = 64
    }

    private var visualizer: Visualizer? = null

    var onMagnitudes: ((FloatArray) -> Unit)? = null

    fun init(audioSessionId: Int) {
        release()
        // Use session 0 (global mix output) — per-session capture returns
        // all zeros on many emulators and some devices.
        tryInit(0)
    }

    private fun tryInit(sessionId: Int): Boolean {
        return try {
            val viz = Visualizer(sessionId)
            viz.captureSize = Visualizer.getCaptureSizeRange()[1] // max capture size
            viz.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) {
                    val samplesPerBand = waveform.size / NUM_BANDS
                    val magnitudes = FloatArray(NUM_BANDS)

                    for (band in 0 until NUM_BANDS) {
                        var sumSquares = 0f
                        val offset = band * samplesPerBand
                        for (j in 0 until samplesPerBand) {
                            // Unsigned byte (0–255) centered at 128 → signed range -128..127
                            val sample = (waveform[offset + j].toInt() and 0xFF) - 128
                            sumSquares += sample * sample
                        }
                        // RMS normalized to 0–1 (max amplitude is 128)
                        magnitudes[band] = sqrt(sumSquares / samplesPerBand) / 128f
                    }

                    onMagnitudes?.invoke(magnitudes)
                }

                override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) {
                    // Not used — FFT callbacks don't fire reliably on all devices.
                }
            }, Visualizer.getMaxCaptureRate(), true, false)
            viz.enabled = true
            visualizer = viz
            Log.d(TAG, "Initialized: session=$sessionId, captureSize=${viz.captureSize}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Visualizer on session $sessionId", e)
            false
        }
    }

    fun release() {
        try {
            visualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing visualizer", e)
        }
        visualizer = null
    }
}
