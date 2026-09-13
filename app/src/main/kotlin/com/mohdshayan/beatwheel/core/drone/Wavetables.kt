package com.mohdshayan.beatwheel.core.drone

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class Waveform(val key: String, val label: String) {
    SINE("sine", "Sine"),
    REED("reed", "Reed"),
    SAW("saw", "Saw");

    companion object {
        fun fromKey(key: String?): Waveform = entries.firstOrNull { it.key == key } ?: REED
    }
}

/** Band-limited single-cycle tables built from harmonic amplitudes, normalised to a peak of 0.9. */
object Wavetables {
    const val SIZE = 4096

    /**
     * Amplitude of harmonic k+1 before normalisation, keeping at most [maxHarmonics]. Harmonics stay under 24
     * so octave 5 at A 440 does not alias at 48 kHz; higher drones (A 466, a transposed profile) pass a lower limit.
     */
    fun harmonics(waveform: Waveform, maxHarmonics: Int = Int.MAX_VALUE): DoubleArray {
        val all = when (waveform) {
            Waveform.SINE -> doubleArrayOf(1.0)
            Waveform.REED -> doubleArrayOf(1.0, 0.72, 0.55, 0.42, 0.30, 0.21, 0.14, 0.09, 0.06)
            Waveform.SAW -> DoubleArray(23) { k -> 1.0 / (k + 1) }
        }
        return all.copyOf(all.size.coerceAtMost(maxHarmonics.coerceAtLeast(1)))
    }

    /** How many harmonics of [frequencyHz] fit below 45 percent of the sample rate, never fewer than the fundamental. */
    fun harmonicLimit(frequencyHz: Double, sampleRate: Int): Int =
        if (frequencyHz <= 0) Int.MAX_VALUE else (sampleRate * 0.45 / frequencyHz).toInt().coerceAtLeast(1)

    fun build(waveform: Waveform, size: Int = SIZE, maxHarmonics: Int = Int.MAX_VALUE): FloatArray {
        val h = harmonics(waveform, maxHarmonics)
        val raw = DoubleArray(size)
        for (i in 0 until size) {
            var v = 0.0
            val x = 2 * PI * i / size
            for (k in h.indices) v += h[k] * sin((k + 1) * x)
            raw[i] = v
        }
        val peak = raw.maxOf { abs(it) }.coerceAtLeast(1e-9)
        return FloatArray(size) { (raw[it] / peak * 0.9).toFloat() }
    }
}

/** Phase-accumulator wavetable oscillator with linear interpolation. Plain Kotlin so the drone can be simulated in tests. */
class WavetableOscillator(private val sampleRate: Int, var table: FloatArray) {
    private var phase = 0.0
    var frequencyHz = 440.0

    fun render(out: FloatArray, len: Int, gain: Float) {
        val size = table.size
        val inc = frequencyHz * size / sampleRate
        for (i in 0 until len) {
            val idx = phase.toInt()
            val frac = (phase - idx).toFloat()
            val a = table[idx % size]
            val b = table[(idx + 1) % size]
            out[i] = (a + (b - a) * frac) * gain
            phase += inc
            if (phase >= size) phase -= size
        }
    }
}
