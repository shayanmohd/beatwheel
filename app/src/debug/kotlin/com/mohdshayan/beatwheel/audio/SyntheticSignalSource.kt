package com.mohdshayan.beatwheel.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** A reedy tone at a set pitch with optional slow drift and a little room noise, paced to real time. */
class SyntheticSignalSource(private val baseHz: Double, private val centsPerMinute: Double) : SignalSource {
    override val sampleRate = 48_000
    private val harmonics = doubleArrayOf(1.0, 0.7, 0.5, 0.3, 0.2)
    private val random = Random(11)
    private var phase = 0.0
    private var samples = 0L
    private var startNanos = 0L

    override fun start(): StartResult {
        startNanos = System.nanoTime()
        return StartResult.OK
    }

    override fun read(buffer: FloatArray, len: Int): Int {
        val due = startNanos + (samples + len) * 1_000_000_000L / sampleRate
        val wait = due - System.nanoTime()
        if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
        val minutes = samples / sampleRate / 60.0
        val hz = baseHz * 2.0.pow(centsPerMinute * minutes / 1200.0)
        val inc = 2 * PI * hz / sampleRate
        for (i in 0 until len) {
            var v = 0.0
            for (k in harmonics.indices) v += harmonics[k] * sin((k + 1) * phase)
            buffer[i] = (0.12 * v + random.nextGaussian() * 0.002).toFloat()
            phase += inc
            if (phase > 2 * PI * 1000) phase -= 2 * PI * 1000
        }
        samples += len
        return len
    }

    override fun isSilencedByOtherApp() = false

    override fun stop() {}
}
