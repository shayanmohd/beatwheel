package com.mohdshayan.beatwheel.core.music

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Frequency and cents arithmetic. MIDI 69 is A4. Offsets are cents from equal temperament, indexed by pitch class C=0. */
object TuningMath {
    const val MIN_REFERENCE_HZ = 415.0
    const val MAX_REFERENCE_HZ = 466.0

    private val LN2 = ln(2.0)

    fun log2(x: Double): Double = ln(x) / LN2

    fun equalHz(midi: Double, referenceAHz: Double): Double =
        referenceAHz * 2.0.pow((midi - 69.0) / 12.0)

    fun hzToMidi(hz: Double, referenceAHz: Double): Double =
        69.0 + 12.0 * log2(hz / referenceAHz)

    fun cents(hz: Double, targetHz: Double): Double = 1200.0 * log2(hz / targetHz)

    fun applyCents(hz: Double, cents: Double): Double = hz * 2.0.pow(cents / 1200.0)

    fun pitchClass(midi: Int): Int = ((midi % 12) + 12) % 12

    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    /** The frequency a note should sound at, with the temperament offset for its pitch class. */
    fun targetHz(midi: Int, referenceAHz: Double, offsets: DoubleArray): Double =
        applyCents(equalHz(midi.toDouble(), referenceAHz), offsets[pitchClass(midi)])

    /** The note whose tempered target is closest to [hz] in cents. */
    fun nearestMidi(hz: Double, referenceAHz: Double, offsets: DoubleArray): Int {
        val guess = hzToMidi(hz, referenceAHz).roundToInt()
        var best = guess
        var bestDist = Double.MAX_VALUE
        for (m in guess - 1..guess + 1) {
            val d = abs(cents(hz, targetHz(m, referenceAHz, offsets)))
            if (d < bestDist) {
                bestDist = d
                best = m
            }
        }
        return best
    }

    fun isValidReference(hz: Double): Boolean = hz in MIN_REFERENCE_HZ..MAX_REFERENCE_HZ
}
