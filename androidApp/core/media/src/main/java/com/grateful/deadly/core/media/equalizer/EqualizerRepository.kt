package com.grateful.deadly.core.media.equalizer

import android.util.Log
import com.grateful.deadly.core.database.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerRepository @Inject constructor(
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "EqualizerRepository"
    }

    private val manager = EqualizerManager()

    private val _state = MutableStateFlow(buildStateFromPreferences())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    /**
     * Called after ExoPlayer is built with its audio session ID.
     * Initializes the system Equalizer and applies the already-loaded state.
     */
    fun onAudioSessionReady(audioSessionId: Int) {
        manager.init(audioSessionId)
        val current = _state.value
        manager.setEnabled(current.enabled)
        if (current.enabled) {
            manager.applyCanonicalGains(current.bands.map { it.currentLevel })
        }
    }

    fun release() {
        manager.release()
    }

    fun setEnabled(enabled: Boolean) {
        manager.setEnabled(enabled)
        appPreferences.setEqEnabled(enabled)
        _state.value = _state.value.copy(enabled = enabled)
        if (enabled) {
            // Re-apply current gains when enabling
            val gains = _state.value.bands.map { it.currentLevel }
            if (gains.isNotEmpty()) {
                manager.applyCanonicalGains(gains)
            }
        }
    }

    fun setBandLevel(index: Int, gainDb: Float) {
        manager.setCanonicalBandGain(index, gainDb)
        val updatedBands = _state.value.bands.toMutableList()
        if (index in updatedBands.indices) {
            updatedBands[index] = updatedBands[index].copy(currentLevel = gainDb)
        }
        _state.value = _state.value.copy(
            bands = updatedBands,
            currentPreset = null // manual adjustment clears preset
        )
        persistBandLevels(updatedBands)
        appPreferences.setEqPreset(null)
    }

    fun selectPreset(presetName: String) {
        val preset = EqPresets.findByName(presetName) ?: return
        manager.applyCanonicalGains(preset.gains)
        val updatedBands = _state.value.bands.mapIndexed { i, band ->
            band.copy(currentLevel = preset.gains.getOrElse(i) { 0f })
        }
        _state.value = _state.value.copy(
            bands = updatedBands,
            currentPreset = presetName
        )
        persistBandLevels(updatedBands)
        appPreferences.setEqPreset(presetName)
    }

    fun resetToFlat() {
        selectPreset("Flat")
    }

    /**
     * Build the UI state from persisted preferences.
     * Called at construction so the UI always has band data, even before ExoPlayer exists.
     */
    private fun buildStateFromPreferences(): EqualizerState {
        val enabled = appPreferences.eqEnabled.value
        val presetName = appPreferences.eqPreset.value
        val savedLevels = appPreferences.eqBandLevels.value
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }

        val (minLevel, maxLevel) = manager.getBandLevelRange()

        val gains = when {
            savedLevels != null && savedLevels.size == 10 -> savedLevels
            presetName != null -> EqPresets.findByName(presetName)?.gains ?: EqPresets.Flat.gains
            else -> EqPresets.Flat.gains
        }

        val bands = EqPresets.BAND_FREQUENCIES.mapIndexed { i, freq ->
            BandInfo(
                index = i,
                centerFreqHz = freq,
                minLevel = minLevel,
                maxLevel = maxLevel,
                currentLevel = gains.getOrElse(i) { 0f }
            )
        }

        Log.d(TAG, "Loaded from prefs: enabled=$enabled, preset=$presetName, gains=$gains")

        return EqualizerState(
            enabled = enabled,
            bands = bands,
            currentPreset = presetName
        )
    }

    private fun persistBandLevels(bands: List<BandInfo>) {
        val csv = bands.joinToString(",") { it.currentLevel.toString() }
        appPreferences.setEqBandLevels(csv)
    }
}
