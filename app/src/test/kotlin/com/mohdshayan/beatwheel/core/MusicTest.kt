package com.mohdshayan.beatwheel.core

import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.Temperaments
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.core.music.TuningMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.ln

class TuningMathTest {
    @Test
    fun a440IsMidi69And415IsAboutASemitoneLow() {
        assertEquals(69.0, TuningMath.hzToMidi(440.0, 440.0), 1e-9)
        assertEquals(-101.3, TuningMath.cents(415.0, 440.0), 0.05)
        assertEquals(261.63, TuningMath.equalHz(60.0, 440.0), 0.01)
        assertEquals(415.0 * 2, TuningMath.targetHz(81, 415.0, DoubleArray(12)), 1e-9)
    }

    @Test
    fun nearestNoteRespectsTemperamentOffsets() {
        // In quarter-comma meantone E sits 13.7 cents low, so 327.0 Hz at A 440 is an in-tune E4, not a sharp one.
        val offsets = Temperaments.offsetsFor(Temperaments.preset("meantone_qc")!!.offsetsCents, 0, keepReferenceA = false)
        val e4 = TuningMath.targetHz(64, 440.0, offsets)
        assertEquals(64, TuningMath.nearestMidi(e4, 440.0, offsets))
        assertEquals(0.0, TuningMath.cents(e4, TuningMath.targetHz(64, 440.0, offsets)), 1e-9)
        assertEquals(-13.7, TuningMath.cents(e4, TuningMath.equalHz(64.0, 440.0)), 0.05)
    }
}

class TemperamentsTest {
    private fun ratioCents(num: Double, den: Double, semis: Int) = 1200 * ln(num / den) / ln(2.0) - semis * 100

    /** Cents from equal temperament for each pitch class, built by stacking fifths of the given sizes. */
    private fun chain(fifthUp: (Int) -> Double, fifthDown: (Int) -> Double, up: Int, down: Int): DoubleArray {
        val out = DoubleArray(12)
        var c = 0.0
        var pc = 0
        for (i in 0 until up) {
            c += fifthUp(pc)
            pc = (pc + 7) % 12
            out[pc] = ((c % 1200) + 1200) % 1200 - pc * 100
        }
        c = 0.0
        pc = 0
        for (i in 0 until down) {
            pc = (pc + 5) % 12
            c -= fifthDown(pc)
            out[pc] = ((c % 1200) + 1200) % 1200 - pc * 100
        }
        return out
    }

    private fun assertTable(expected: DoubleArray, key: String) {
        val table = Temperaments.preset(key)!!.offsetsCents
        for (pc in 0 until 12) assertEquals("$key pc $pc", expected[pc], table[pc], 0.05)
    }

    @Test
    fun justAndPythagoreanTablesMatchTheirRatios() {
        val just = doubleArrayOf(
            0.0, ratioCents(16.0, 15.0, 1), ratioCents(9.0, 8.0, 2), ratioCents(6.0, 5.0, 3),
            ratioCents(5.0, 4.0, 4), ratioCents(4.0, 3.0, 5), ratioCents(45.0, 32.0, 6), ratioCents(3.0, 2.0, 7),
            ratioCents(8.0, 5.0, 8), ratioCents(5.0, 3.0, 9), ratioCents(9.0, 5.0, 10), ratioCents(15.0, 8.0, 11),
        )
        assertTable(just, "just")
        val pure = 1200 * ln(1.5) / ln(2.0)
        assertTable(chain({ pure }, { pure }, 8, 3), "pythagorean")
    }

    @Test
    fun meantoneWerckmeisterAndKirnbergerTablesMatchTheirFifths() {
        val pure = 1200 * ln(1.5) / ln(2.0)
        val syntonic = 1200 * ln(81.0 / 80.0) / ln(2.0)
        val pythagoreanComma = 1200 * ln(531441.0 / 524288.0) / ln(2.0)
        val schisma = pythagoreanComma - syntonic
        assertTable(chain({ pure - syntonic / 4 }, { pure - syntonic / 4 }, 8, 3), "meantone_qc")
        // Werckmeister III: C-G, G-D, D-A and B-F sharp narrowed by a quarter Pythagorean comma.
        val w3Tempered = setOf(0, 7, 2, 11)
        assertTable(chain({ if (it in w3Tempered) pure - pythagoreanComma / 4 else pure }, { pure }, 8, 3), "werckmeister3")
        // Kirnberger III: C-G-D-A-E narrowed by a quarter syntonic comma, F sharp-C sharp by a schisma.
        val k3 = mapOf(0 to syntonic / 4, 7 to syntonic / 4, 2 to syntonic / 4, 9 to syntonic / 4, 6 to schisma)
        assertTable(chain({ pure - (k3[it] ?: 0.0) }, { pure }, 8, 3), "kirnberger3")
    }

    @Test
    fun rotationMovesThePatternToTheTonicAndKeepReferenceZeroesA() {
        val just = Temperaments.preset("just")!!.offsetsCents
        val onG = Temperaments.offsetsFor(just, tonicPitchClass = 7, keepReferenceA = false)
        assertEquals(0.0, onG[7], 1e-9)
        assertEquals(just[4], onG[11], 1e-9) // B is the major third of G
        val keepA = Temperaments.offsetsFor(just, tonicPitchClass = 7, keepReferenceA = true)
        assertEquals(0.0, keepA[9], 1e-9)
        assertEquals(onG[11] - onG[9], keepA[11], 1e-9)
    }

    @Test
    fun customOffsetsParseOnlyTwelveValuesInRange() {
        assertEquals(12, Temperaments.parseOffsets("0,1,2,3,4,5,6,7,8,9,10,-11.5")!!.size)
        assertNull(Temperaments.parseOffsets("0,1,2"))
        assertNull(Temperaments.parseOffsets("0,1,2,3,4,5,6,7,8,9,10,60"))
        assertEquals(42L, Temperaments.customId(Temperaments.customKey(42)))
    }
}

class TranspositionTest {
    @Test
    fun writtenAndSoundingNotes() {
        val c5 = 72
        assertEquals("Bb4", NoteNames.spell(Transposition.sounding(c5, -2)).ascii)
        assertEquals("Eb4", NoteNames.spell(Transposition.sounding(c5, -9)).ascii)
        assertEquals("F4", NoteNames.spell(Transposition.sounding(c5, -7)).ascii)
        // A clarinet sounding concert C reads a written D.
        assertEquals("D5", NoteNames.spell(Transposition.written(70 + 2, -2)).ascii)
        assertEquals("B flat 4", NoteNames.spell(70).spoken)
    }

    @Test
    fun noteListsParseBothWays() {
        assertEquals("40,45,50,55,59,64", NoteNames.parseNoteList("E2 A2 D3 G3 B3 E4"))
        assertEquals("E2 A2 D3", NoteNames.formatMidiList("40,45,50"))
        assertEquals(70, NoteNames.parse("Bb4"))
        assertEquals(66, NoteNames.parse("F#4"))
        assertNull(NoteNames.parseNoteList("E2 H9"))
    }
}
