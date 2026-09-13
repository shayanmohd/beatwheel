package com.mohdshayan.beatwheel.core

import com.mohdshayan.beatwheel.core.drone.BeatMeter
import com.mohdshayan.beatwheel.core.drone.DroneCanceller
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.core.drone.WavetableOscillator
import com.mohdshayan.beatwheel.core.drone.Wavetables
import com.mohdshayan.beatwheel.core.music.TuningMath
import com.mohdshayan.beatwheel.core.pitch.EstimatorConfig
import com.mohdshayan.beatwheel.core.pitch.Estimate
import com.mohdshayan.beatwheel.core.pitch.PhaseDemodulator
import com.mohdshayan.beatwheel.core.pitch.PitchEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PitchEstimatorTest {
    private fun run(signal: FloatArray, config: EstimatorConfig): Estimate {
        val est = PitchEstimator()
        est.configure(config)
        var last = Estimate.SILENT
        Signals.blocks(signal) { b, n -> last = est.process(b, n) }
        return last
    }

    @Test
    fun yinFindsLowAndHighNotesWithoutOctaveErrors() {
        // Strong second and third harmonics are what tempt a detector an octave up.
        val rich = doubleArrayOf(0.6, 1.0, 0.8, 0.4, 0.2)
        for ((hz, midi) in listOf(41.2 to 28, 110.0 to 45, 440.0 to 69, 1318.5 to 88)) {
            val e = run(Signals.tone(hz, 1.2, harmonics = rich, noise = 0.02), EstimatorConfig(minHz = 27.5, maxHz = 2500.0))
            assertTrue("$hz has pitch", e.hasPitch)
            assertEquals("$hz Hz", midi, e.midi)
            assertEquals("$hz Hz cents", TuningMath.cents(hz, TuningMath.equalHz(midi.toDouble(), 440.0)), e.cents, 1.0)
        }
    }

    @Test
    fun gateKeepsSilenceAndHissFromReadingANote() {
        val hiss = FloatArray(48_000).also { val r = Random(3); for (i in it.indices) it[i] = (r.nextGaussian() * 0.0005).toFloat() }
        assertTrue(!run(hiss, EstimatorConfig()).hasPitch)
    }
}

class PhaseDemodulatorTest {
    @Test
    fun halfCentSharpReadsWithinATenth() {
        val demod = PhaseDemodulator(48_000)
        demod.setTarget(440.0)
        val s = Signals.tone(440.127, 2.0, noise = 0.01)
        demod.process(s, 0, s.size)
        assertEquals(0.5, demod.offsetCents()!!, 0.1)
    }

    @Test
    fun flatTurnsTheOtherWay() {
        val demod = PhaseDemodulator(48_000)
        demod.setTarget(110.0)
        val s = Signals.tone(TuningMath.applyCents(110.0, -7.0), 2.0)
        demod.process(s, 0, s.size)
        assertEquals(-7.0, demod.offsetCents()!!, 0.1)
        assertTrue(demod.offsetHz()!! < 0)
    }
}

class DroneCancellerTest {
    @Test
    fun reedDroneOnTheSpeakerLeavesThePlayerMeasurable() {
        val rate = 48_000
        val seconds = 4.0
        val n = (rate * seconds).toInt()
        val drone = FloatArray(n)
        WavetableOscillator(rate, Wavetables.build(Waveform.REED)).apply { frequencyHz = 440.0 }.render(drone, n, 1f)
        // Room: direct path delayed 37 samples at 0.9, one reflection at 211 samples and 0.3.
        val mic = FloatArray(n)
        for (i in 0 until n) {
            val direct = if (i >= 37) drone[i - 37] * 0.9f else 0f
            val reflection = if (i >= 211) drone[i - 211] * 0.3f else 0f
            mic[i] = direct + reflection
        }
        val player = Signals.tone(446.0, seconds, amp = 0.5, harmonics = doubleArrayOf(1.0, 0.5, 0.25), noise = 0.003)
        val input = Signals.mix(mic, player)

        fun measure(cancel: Boolean): Estimate {
            val canceller = DroneCanceller(rate)
            canceller.setDrone(if (cancel) 440.0 else 0.0)
            val est = PitchEstimator()
            est.configure(EstimatorConfig())
            val out = FloatArray(480)
            var last = Estimate.SILENT
            Signals.blocks(input) { b, len ->
                canceller.process(b, out, len)
                last = est.process(out, len)
            }
            return last
        }
        val cancelled = measure(cancel = true)
        assertEquals(69, cancelled.midi)
        assertTrue(cancelled.strobeLocked)
        assertEquals(23.4, cancelled.cents, 0.5)
        // Without cancellation the drone drags the reading toward its own pitch.
        val raw = measure(cancel = false)
        assertTrue("raw reads ${raw.cents}", !raw.hasPitch || kotlin.math.abs(raw.cents - 23.4) > 3.0)
    }
}

class BeatMeterTest {
    private fun beats(playerHz: Double): Double {
        val signal = Signals.mix(Signals.tone(440.0, 3.0, amp = 0.5), Signals.tone(playerHz, 3.0, amp = 0.5, seed = 9))
        val meter = BeatMeter(48_000)
        meter.setReference(440.0)
        meter.process(signal, signal.size)
        return assertNotNullValue(meter.beatsPerSecond())
    }

    private fun assertNotNullValue(v: Double?): Double {
        assertNotNull(v)
        return v!!
    }

    @Test
    fun oneBeatPerSecondSignedByDirection() {
        assertEquals(1.0, beats(441.0), 0.05)
        assertEquals(-1.0, beats(439.0), 0.05)
    }
}
