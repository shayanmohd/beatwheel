package com.mohdshayan.beatwheel.core.pitch

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Level gate with hysteresis and a short hold, so the display stays calm between notes.
 * [sensitivity] runs 0 to 1: higher opens the gate for quieter sounds.
 */
class NoiseGate(var thresholdDb: Float = -52f, var sensitivity: Float = 0.5f) {
    var isOpen = false
        private set
    private var holdSamples = 0

    val effectiveThresholdDb: Float get() = thresholdDb + (0.5f - sensitivity) * 30f

    fun update(levelDb: Float, blockSamples: Int, sampleRate: Int): Boolean {
        val open = effectiveThresholdDb
        val close = open - 4f
        if (levelDb >= open) {
            isOpen = true
            holdSamples = (sampleRate * 0.15).toInt()
        } else if (levelDb < close) {
            holdSamples -= blockSamples
            if (holdSamples <= 0) isOpen = false
        }
        return isOpen
    }

    fun reset() {
        isOpen = false
        holdSamples = 0
    }

    companion object {
        fun rmsDb(buffer: FloatArray, len: Int): Float {
            if (len <= 0) return -120f
            var sum = 0.0
            for (i in 0 until len) sum += buffer[i] * buffer[i]
            val rms = sqrt(sum / len)
            return if (rms <= 1e-6) -120f else (20 * log10(rms)).toFloat()
        }
    }
}
