package com.mohdshayan.beatwheel.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/** Synthetic test signals. */
object Signals {
    fun tone(hz: Double, seconds: Double, rate: Int = 48_000, harmonics: DoubleArray = doubleArrayOf(1.0), amp: Double = 0.5, noise: Double = 0.0, seed: Long = 7): FloatArray {
        val n = (seconds * rate).toInt()
        val rnd = Random(seed)
        val norm = harmonics.sum()
        return FloatArray(n) { i ->
            var v = 0.0
            for (k in harmonics.indices) v += harmonics[k] * sin(2 * PI * hz * (k + 1) * i / rate)
            (amp * v / norm + noise * rnd.nextGaussian()).toFloat()
        }
    }

    fun mix(a: FloatArray, b: FloatArray): FloatArray = FloatArray(minOf(a.size, b.size)) { a[it] + b[it] }

    inline fun blocks(signal: FloatArray, size: Int = 480, body: (FloatArray, Int) -> Unit) {
        val block = FloatArray(size)
        var i = 0
        while (i + size <= signal.size) {
            System.arraycopy(signal, i, block, 0, size)
            body(block, size)
            i += size
        }
    }
}
