package com.mohdshayan.beatwheel.core.drone

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Beats per second between a played note and a drone partial it sits within 1.5 Hz of, where the two
 * cannot be honestly separated. The signal is demodulated at the partial, the steady drone phasor is
 * removed by subtracting the mean over the window, and the slope of what remains is the signed beat
 * rate: positive when the player is sharp of the drone.
 */
class BeatMeter(private val sampleRate: Int, private val windowSeconds: Double = 2.0) {
    var referenceHz = 0.0
        private set
    private var omega = 0.0
    private var osc = 0.0
    private var blockLen = 1
    private var inBlock = 0
    private var accI = 0.0
    private var accQ = 0.0
    private val capacity = 256
    private val zi = DoubleArray(capacity)
    private val zq = DoubleArray(capacity)
    private var head = 0
    private var count = 0

    fun setReference(hz: Double) {
        if (hz == referenceHz) return
        referenceHz = hz
        head = 0
        count = 0
        if (hz <= 0) return
        omega = 2 * PI * hz / sampleRate
        val periods = max(1, (0.02 * hz).roundToInt())
        blockLen = max(8, (periods * sampleRate / hz).roundToInt())
        osc = 0.0
        inBlock = 0
        accI = 0.0
        accQ = 0.0
        head = 0
        count = 0
    }

    fun process(input: FloatArray, len: Int) {
        if (referenceHz <= 0) return
        for (i in 0 until len) {
            val x = input[i].toDouble()
            accI += x * cos(osc)
            accQ -= x * sin(osc)
            osc += omega
            if (osc > 2 * PI) osc -= 2 * PI
            if (++inBlock == blockLen) {
                zi[head] = accI / blockLen
                zq[head] = accQ / blockLen
                head = (head + 1) % capacity
                if (count < capacity) count++
                inBlock = 0
                accI = 0.0
                accQ = 0.0
            }
        }
    }

    /** Signed beats per second, or null until a full window has been heard or when nothing beats. */
    fun beatsPerSecond(): Double? {
        val blockSeconds = blockLen.toDouble() / sampleRate
        val n = minOf(count, (windowSeconds / blockSeconds).roundToInt())
        if (n < 8 || n * blockSeconds < windowSeconds * 0.95) return null
        var mi = 0.0
        var mq = 0.0
        for (k in 0 until n) {
            val idx = (head - n + k + capacity) % capacity
            mi += zi[idx]
            mq += zq[idx]
        }
        mi /= n
        mq /= n
        var unwrapped = 0.0
        var prev = 0.0
        var st = 0.0
        var sp = 0.0
        var stt = 0.0
        var stp = 0.0
        var energy = 0.0
        for (k in 0 until n) {
            val idx = (head - n + k + capacity) % capacity
            val i = zi[idx] - mi
            val q = zq[idx] - mq
            energy += hypot(i, q)
            val raw = atan2(q, i)
            if (k == 0) unwrapped = raw else {
                var d = raw - prev
                while (d > PI) d -= 2 * PI
                while (d < -PI) d += 2 * PI
                unwrapped += d
            }
            prev = raw
            val t = k * blockSeconds
            st += t
            sp += unwrapped
            stt += t * t
            stp += t * unwrapped
        }
        if (energy / n < 1e-5) return null
        val denom = n * stt - st * st
        if (abs(denom) < 1e-12) return null
        return (n * stp - st * sp) / denom / (2 * PI)
    }

    companion object {
        const val UNISON_WINDOW_HZ = 1.5
    }
}
