package com.mohdshayan.beatwheel.core.drone

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Removes the app's own drone from the microphone signal when it plays through the speaker. For the
 * fundamental and the first eight harmonics it tracks the room's gain and phase with a two-tap
 * recursive least squares filter on a quadrature reference, forgetting over about two seconds, and
 * subtracts the estimate. Each partial becomes a notch well under 1 Hz wide, so a player a few hertz
 * away passes through untouched. The room path is assumed steady, which is what makes the notches narrow.
 */
class DroneCanceller(
    private val sampleRate: Int,
    private val partials: Int = 9,
    forgettingSeconds: Double = 2.0,
) {
    private val lambda = 1.0 - 1.0 / (forgettingSeconds * sampleRate)
    private val omega = DoubleArray(partials)
    private val theta = DoubleArray(partials)
    private val wa = DoubleArray(partials)
    private val wb = DoubleArray(partials)
    private val p11 = DoubleArray(partials)
    private val p12 = DoubleArray(partials)
    private val p22 = DoubleArray(partials)
    private val cosT = DoubleArray(partials)
    private val sinT = DoubleArray(partials)
    private var active = 0

    var droneHz: Double = 0.0
        private set

    fun setDrone(fundamentalHz: Double) {
        if (fundamentalHz == droneHz) return
        droneHz = fundamentalHz
        active = 0
        for (k in 0 until partials) {
            val f = fundamentalHz * (k + 1)
            if (fundamentalHz > 0 && f < sampleRate * 0.45) active = k + 1
            omega[k] = 2 * PI * f / sampleRate
            theta[k] = 0.0
            wa[k] = 0.0
            wb[k] = 0.0
            p11[k] = 10.0
            p12[k] = 0.0
            p22[k] = 10.0
        }
    }

    fun clear() = setDrone(0.0)

    /** Cancels in place from [input] into [output]; both may be the same array. */
    fun process(input: FloatArray, output: FloatArray, len: Int) {
        if (active == 0) {
            if (input !== output) System.arraycopy(input, 0, output, 0, len)
            return
        }
        for (i in 0 until len) {
            var estimate = 0.0
            for (k in 0 until active) {
                cosT[k] = cos(theta[k])
                sinT[k] = sin(theta[k])
                estimate += wa[k] * cosT[k] + wb[k] * sinT[k]
            }
            val e = input[i] - estimate
            for (k in 0 until active) {
                val c = cosT[k]
                val s = sinT[k]
                val pc1 = p11[k] * c + p12[k] * s
                val pc2 = p12[k] * c + p22[k] * s
                val denom = lambda + c * pc1 + s * pc2
                val g1 = pc1 / denom
                val g2 = pc2 / denom
                wa[k] += g1 * e
                wb[k] += g2 * e
                val n11 = (p11[k] - g1 * pc1) / lambda
                val n12 = (p12[k] - g1 * pc2) / lambda
                val n22 = (p22[k] - g2 * pc2) / lambda
                p11[k] = n11.coerceAtMost(1e3)
                p12[k] = n12
                p22[k] = n22.coerceAtMost(1e3)
                theta[k] += omega[k]
                if (theta[k] > 2 * PI) theta[k] -= 2 * PI
            }
            output[i] = e.toFloat()
        }
    }
}
