package com.mohdshayan.beatwheel.core.pitch

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * YIN fundamental estimator (de Cheveigne and Kawahara 2002): squared difference, cumulative mean
 * normalisation, absolute threshold, then parabolic interpolation around the chosen lag.
 * Allocation free after construction; not thread safe.
 */
class YinDetector(
    private val sampleRate: Int,
    private val windowSize: Int,
    private val maxLag: Int,
    private val threshold: Double = 0.15,
) {
    private val diff = DoubleArray(maxLag + 2)
    private val cmnd = DoubleArray(maxLag + 2)

    /** Last clarity, 1 minus the normalised difference at the chosen lag. */
    var clarity: Double = 0.0
        private set

    /** Needs at least windowSize + maxLag samples ending at [end]. Returns Hz or null. */
    fun detect(buffer: FloatArray, end: Int, minHz: Double, maxHz: Double): Double? {
        val start = end - windowSize - maxLag
        if (start < 0) return null
        val tauMin = max(2, floor(sampleRate / maxHz).toInt())
        val tauMax = min(maxLag, ceil(sampleRate / minHz).toInt())
        if (tauMax <= tauMin + 1) return null

        for (tau in 1..tauMax) {
            var sum = 0.0
            var j = start
            val stop = start + windowSize
            while (j < stop) {
                val d = (buffer[j] - buffer[j + tau]).toDouble()
                sum += d * d
                j++
            }
            diff[tau] = sum
        }
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax) {
            running += diff[tau]
            cmnd[tau] = if (running > 0.0) diff[tau] * tau / running else 1.0
        }

        var chosen = -1
        var tau = tauMin
        while (tau <= tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                chosen = tau
                break
            }
            tau++
        }
        if (chosen < 0) {
            var best = tauMin
            for (t in tauMin..tauMax) if (cmnd[t] < cmnd[best]) best = t
            if (cmnd[best] > 0.35) {
                clarity = 0.0
                return null
            }
            chosen = best
        }
        clarity = 1.0 - cmnd[chosen]

        val refined = if (chosen > 1 && chosen < tauMax) {
            val a = cmnd[chosen - 1]
            val b = cmnd[chosen]
            val c = cmnd[chosen + 1]
            val denom = a - 2 * b + c
            if (denom != 0.0) chosen + 0.5 * (a - c) / denom else chosen.toDouble()
        } else {
            chosen.toDouble()
        }
        val hz = sampleRate / refined
        return if (hz in minHz * 0.97..maxHz * 1.03) hz else null
    }
}
