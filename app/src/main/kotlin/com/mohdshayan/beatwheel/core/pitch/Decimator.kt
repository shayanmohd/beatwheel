package com.mohdshayan.beatwheel.core.pitch

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Windowed-sinc low-pass followed by keeping every [factor]th sample. */
class Decimator(private val factor: Int, inputRate: Int, cutoffHz: Double, taps: Int = 63) {
    private val coeffs = DoubleArray(taps)
    private val history = DoubleArray(taps)
    private var pos = 0
    private var phase = 0

    init {
        val fc = cutoffHz / inputRate
        val m = taps - 1
        var sum = 0.0
        for (i in 0 until taps) {
            val x = i - m / 2.0
            val sinc = if (x == 0.0) 2 * PI * fc else sin(2 * PI * fc * x) / x
            val window = 0.42 - 0.5 * cos(2 * PI * i / m) + 0.08 * cos(4 * PI * i / m)
            coeffs[i] = sinc * window
            sum += coeffs[i]
        }
        for (i in 0 until taps) coeffs[i] /= sum
    }

    /** Filters [len] samples of [input]; emits decimated samples through [emit]. */
    inline fun process(input: FloatArray, len: Int, emit: (Float) -> Unit) {
        for (i in 0 until len) {
            if (push(input[i])) emit(output())
        }
    }

    fun push(sample: Float): Boolean {
        history[pos] = sample.toDouble()
        pos = (pos + 1) % history.size
        phase = (phase + 1) % factor
        return phase == 0
    }

    fun output(): Float {
        var acc = 0.0
        val n = history.size
        for (i in 0 until n) acc += coeffs[i] * history[(pos + i) % n]
        return acc.toFloat()
    }

    fun reset() {
        history.fill(0.0)
        pos = 0
        phase = 0
    }
}
