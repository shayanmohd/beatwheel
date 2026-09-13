package com.mohdshayan.beatwheel.core.pitch

import com.mohdshayan.beatwheel.core.music.TuningMath
import kotlin.math.abs

/** What the estimator needs to know about the instrument, already resolved to absolute pitch-class offsets. */
data class EstimatorConfig(
    val referenceAHz: Double = 440.0,
    val offsets: DoubleArray = DoubleArray(12),
    val minHz: Double = 27.5,
    val maxHz: Double = 2500.0,
    val heldTone: Boolean = false,
    val noiseGateDb: Float = -52f,
    val sensitivity: Float = 0.5f,
)

/** One measurement. Pitch fields are meaningful only when [hasPitch]. MIDI numbers are sounding pitch. */
data class Estimate(
    val hasPitch: Boolean,
    val midi: Int,
    val targetHz: Double,
    val hz: Double,
    val cents: Double,
    val phase: Double,
    val phaseRateHz: Double,
    val strobeLocked: Boolean,
    val levelDb: Float,
    val gateOpen: Boolean,
) {
    companion object {
        val SILENT = Estimate(false, -1, 0.0, 0.0, 0.0, 0.0, 0.0, false, -120f, false)
    }
}

/**
 * The measuring chain for 48 kHz mono float blocks: level and gate, a 4x decimated YIN pass every 40 ms
 * to choose the note (three agreeing frames before a change), and a [PhaseDemodulator] at full rate for
 * the fine offset and the strobe angle. Held-tone mode lengthens the phase window from 0.5 s to 1.5 s.
 */
class PitchEstimator(private val sampleRate: Int = 48_000) {
    private val decimation = 4
    private val lowRate = sampleRate / decimation
    private val yinWindow = 1024
    private val yinMaxLag = 480
    private val decimator = Decimator(decimation, sampleRate, 4_500.0)
    private val lowRing = FloatArray(yinWindow + yinMaxLag)
    private val lowLinear = FloatArray(yinWindow + yinMaxLag)
    private var lowWrite = 0
    private var lowCount = 0
    private var wasClosed = true
    private var hopCounter = 0
    private val hop = lowRate / 25
    private val yin = YinDetector(lowRate, yinWindow, yinMaxLag)
    private val demod = PhaseDemodulator(sampleRate)
    val gate = NoiseGate()

    var config = EstimatorConfig()
        private set

    private var currentMidi = -1
    private var pendingMidi = -1
    private var pendingCount = 0
    private var lastYinHz = 0.0
    private var silentSamples = 0
    private var last = Estimate.SILENT

    fun configure(newConfig: EstimatorConfig) {
        val retune = newConfig.referenceAHz != config.referenceAHz || !newConfig.offsets.contentEquals(config.offsets)
        config = newConfig
        gate.thresholdDb = newConfig.noiseGateDb
        gate.sensitivity = newConfig.sensitivity
        demod.windowSeconds = if (newConfig.heldTone) 1.5 else 0.5
        if (retune && currentMidi >= 0) {
            demod.setTarget(TuningMath.targetHz(currentMidi, config.referenceAHz, config.offsets))
        }
        if (currentMidi >= 0) {
            val hz = TuningMath.targetHz(currentMidi, config.referenceAHz, config.offsets)
            if (hz < newConfig.minHz * 0.97 || hz > newConfig.maxHz * 1.03) dropNote()
        }
    }

    fun reset() {
        decimator.reset()
        lowCount = 0
        lowWrite = 0
        hopCounter = 0
        wasClosed = true
        gate.reset()
        dropNote()
        last = Estimate.SILENT
    }

    private fun dropNote() {
        currentMidi = -1
        pendingMidi = -1
        pendingCount = 0
        demod.reset()
    }

    fun process(block: FloatArray, len: Int): Estimate {
        val levelDb = NoiseGate.rmsDb(block, len)
        val open = gate.update(levelDb, len, sampleRate)

        decimator.process(block, len) { s ->
            lowRing[lowWrite] = s
            lowWrite = (lowWrite + 1) % lowRing.size
            if (lowCount < lowRing.size) lowCount++
            hopCounter++
        }

        if (!open) {
            wasClosed = true
            silentSamples += len
            if (silentSamples > sampleRate) dropNote()
            last = last.copy(hasPitch = false, levelDb = levelDb, gateOpen = false, strobeLocked = false)
            return last
        }
        silentSamples = 0
        if (wasClosed) {
            wasClosed = false
            if (currentMidi >= 0) demod.setTarget(demod.targetHz)
        }

        if (hopCounter >= hop && lowCount == lowRing.size) {
            hopCounter = 0
            val n = lowRing.size
            System.arraycopy(lowRing, lowWrite, lowLinear, 0, n - lowWrite)
            System.arraycopy(lowRing, 0, lowLinear, n - lowWrite, lowWrite)
            val hz = yin.detect(lowLinear, n, config.minHz, config.maxHz)
            if (hz != null) {
                lastYinHz = hz
                val candidate = TuningMath.nearestMidi(hz, config.referenceAHz, config.offsets)
                if (candidate == pendingMidi) pendingCount++ else {
                    pendingMidi = candidate
                    pendingCount = 1
                }
                val needed = if (currentMidi < 0) 2 else 3
                if (candidate != currentMidi && pendingCount >= needed) {
                    currentMidi = candidate
                    demod.setTarget(TuningMath.targetHz(candidate, config.referenceAHz, config.offsets))
                }
            }
        }

        if (currentMidi < 0) {
            last = Estimate.SILENT.copy(levelDb = levelDb, gateOpen = true)
            return last
        }

        demod.process(block, 0, len)
        val target = demod.targetHz
        val yinCents = if (lastYinHz > 0) TuningMath.cents(lastYinHz, target) else 0.0
        val demodCents = if (demod.spanSeconds() >= 0.15) demod.offsetCents() else null
        val locked = demodCents != null && abs(demodCents - yinCents) < 20.0
        val cents = if (locked) demodCents!! else yinCents
        last = Estimate(
            hasPitch = true,
            midi = currentMidi,
            targetHz = target,
            hz = TuningMath.applyCents(target, cents),
            cents = cents,
            phase = demod.phase,
            phaseRateHz = if (locked) demod.offsetHz() ?: 0.0 else 0.0,
            strobeLocked = locked,
            levelDb = levelDb,
            gateOpen = true,
        )
        return last
    }
}
