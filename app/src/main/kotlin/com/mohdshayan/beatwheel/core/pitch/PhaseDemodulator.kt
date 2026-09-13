package com.mohdshayan.beatwheel.core.pitch

import com.mohdshayan.beatwheel.core.music.TuningMath
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What a mechanical strobe does, in software. The input is mixed against a quadrature oscillator at
 * the target frequency and averaged over blocks a whole number of periods long, which cancels the
 * double-frequency product and every harmonic. The resulting phasor turns at the difference frequency: its angle drives the
 * disc and the slope of the unwrapped angle over time gives the offset in cents.
 */
class PhaseDemodulator(private val sampleRate: Int, windowSeconds: Double = 0.5) {
    var targetHz: Double = 0.0
        private set

    var windowSeconds: Double = windowSeconds

    private var omega = 0.0
    private var oscPhase = 0.0
    private var blockLen = 1
    private var inBlock = 0
    private var accI = 0.0
    private var accQ = 0.0
    private var accEnergy = 0.0
    private var samplesSeen = 0L

    private val capacity = 512
    private val times = DoubleArray(capacity)
    private val phases = DoubleArray(capacity)
    private var head = 0
    private var count = 0
    private var lastRaw = 0.0
    private var unwrapped = 0.0

    /** Latest unwrapped phase in radians. */
    val phase: Double get() = unwrapped

    fun setTarget(hz: Double) {
        targetHz = hz
        omega = 2 * PI * hz / sampleRate
        // Nyquist of the block rate must cover about 50 cents either side, and blocks stay under 40 ms.
        // Whole periods, so every harmonic of the target mixes to a multiple of it and averages out.
        val maxBlockSeconds = min(0.04, 13.0 / hz)
        val periods = max(1, (maxBlockSeconds * hz).toInt())
        blockLen = max(8, (periods * sampleRate / hz).roundToInt())
        reset()
    }

    fun reset() {
        oscPhase = 0.0
        inBlock = 0
        accI = 0.0
        accQ = 0.0
        accEnergy = 0.0
        samplesSeen = 0
        head = 0
        count = 0
        lastRaw = 0.0
        unwrapped = 0.0
    }

    fun process(input: FloatArray, offset: Int, len: Int) {
        if (targetHz <= 0.0) return
        for (i in offset until offset + len) {
            val x = input[i].toDouble()
            accI += x * cos(oscPhase)
            accQ -= x * sin(oscPhase)
            accEnergy += x * x
            oscPhase += omega
            if (oscPhase > 2 * PI) oscPhase -= 2 * PI
            inBlock++
            samplesSeen++
            if (inBlock == blockLen) closeBlock()
        }
    }

    private fun closeBlock() {
        val magnitude = hypot(accI, accQ)
        val rms = kotlin.math.sqrt(accEnergy / blockLen)
        if (rms > 1e-6 && magnitude / blockLen > rms * 0.05) {
            val raw = atan2(accQ, accI)
            if (count == 0) {
                unwrapped = raw
            } else {
                var d = raw - lastRaw
                while (d > PI) d -= 2 * PI
                while (d < -PI) d += 2 * PI
                unwrapped += d
            }
            lastRaw = raw
            val t = (samplesSeen - blockLen / 2.0) / sampleRate
            times[head] = t
            phases[head] = unwrapped
            head = (head + 1) % capacity
            if (count < capacity) count++
        }
        inBlock = 0
        accI = 0.0
        accQ = 0.0
        accEnergy = 0.0
    }

    /** Seconds of phase history the current estimate rests on. */
    fun spanSeconds(): Double {
        if (count < 2) return 0.0
        val newest = times[(head - 1 + capacity) % capacity]
        return newest - oldestInWindow(newest)
    }

    private fun oldestInWindow(newest: Double): Double {
        var oldest = newest
        for (k in 0 until count) {
            val idx = (head - 1 - k + capacity) % capacity
            if (newest - times[idx] > windowSeconds) break
            oldest = times[idx]
        }
        return oldest
    }

    /** Difference frequency in Hz from a least-squares line over the window, or null with too little history. */
    fun offsetHz(minBlocks: Int = 5): Double? {
        if (count < minBlocks) return null
        val newest = times[(head - 1 + capacity) % capacity]
        var n = 0
        var st = 0.0
        var sp = 0.0
        var stt = 0.0
        var stp = 0.0
        for (k in 0 until count) {
            val idx = (head - 1 - k + capacity) % capacity
            val t = times[idx] - newest
            if (-t > windowSeconds) break
            val p = phases[idx]
            n++
            st += t
            sp += p
            stt += t * t
            stp += t * p
        }
        if (n < minBlocks) return null
        val denom = n * stt - st * st
        if (denom <= 0.0) return null
        val slope = (n * stp - st * sp) / denom
        return slope / (2 * PI)
    }

    fun offsetCents(minBlocks: Int = 5): Double? {
        val df = offsetHz(minBlocks) ?: return null
        val measured = targetHz + df
        return if (measured > 0) TuningMath.cents(measured, targetHz) else null
    }
}
