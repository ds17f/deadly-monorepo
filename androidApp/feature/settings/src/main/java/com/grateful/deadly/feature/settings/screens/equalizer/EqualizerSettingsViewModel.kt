package com.grateful.deadly.feature.settings.screens.equalizer

import androidx.lifecycle.ViewModel
import com.grateful.deadly.core.media.equalizer.EqualizerRepository
import com.grateful.deadly.core.media.equalizer.EqualizerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class EqualizerSettingsViewModel @Inject constructor(
    private val equalizerRepository: EqualizerRepository
) : ViewModel() {

    val equalizerState: StateFlow<EqualizerState> = equalizerRepository.state

    fun setEnabled(enabled: Boolean) {
        equalizerRepository.setEnabled(enabled)
    }

    fun selectPreset(preset: String) {
        equalizerRepository.selectPreset(preset)
    }

    fun setBandLevel(index: Int, gainDb: Float) {
        equalizerRepository.setBandLevel(index, gainDb)
    }

    fun resetToFlat() {
        equalizerRepository.resetToFlat()
    }
}
