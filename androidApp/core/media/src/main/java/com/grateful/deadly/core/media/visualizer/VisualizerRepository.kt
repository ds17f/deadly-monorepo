package com.grateful.deadly.core.media.visualizer

import android.util.Log
import com.grateful.deadly.core.database.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisualizerRepository @Inject constructor(
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "VisualizerRepository"
    }

    private val capture = AudioVisualizerCapture()

    // Don't auto-enable from prefs — the UI must request RECORD_AUDIO
    // permission first, then call setEnabled(true) explicitly.
    private val _state = MutableStateFlow(VisualizerState(enabled = false))
    val state: StateFlow<VisualizerState> = _state.asStateFlow()

    private var audioSessionId: Int = 0

    fun onAudioSessionReady(audioSessionId: Int) {
        this.audioSessionId = audioSessionId
        Log.d(TAG, "Audio session ready: $audioSessionId")
    }

    fun setEnabled(enabled: Boolean) {
        appPreferences.setVisualizerEnabled(enabled)
        _state.value = _state.value.copy(enabled = enabled)
        if (enabled) startCapture() else stopCapture()
        Log.d(TAG, "Visualizer enabled=$enabled")
    }

    fun release() {
        stopCapture()
    }

    private fun startCapture() {
        capture.onMagnitudes = { magnitudes ->
            _state.value = _state.value.copy(fftMagnitudes = magnitudes)
        }
        capture.init(audioSessionId)
    }

    private fun stopCapture() {
        capture.release()
        _state.value = _state.value.copy(fftMagnitudes = FloatArray(0))
    }
}
