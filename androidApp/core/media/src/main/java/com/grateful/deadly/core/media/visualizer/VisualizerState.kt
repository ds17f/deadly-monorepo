package com.grateful.deadly.core.media.visualizer

data class VisualizerState(
    val enabled: Boolean = false,
    val fftMagnitudes: FloatArray = FloatArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisualizerState) return false
        return enabled == other.enabled && fftMagnitudes.contentEquals(other.fftMagnitudes)
    }

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + fftMagnitudes.contentHashCode()
        return result
    }
}
