package com.mohdshayan.beatwheel.core

import com.mohdshayan.beatwheel.core.drift.BackupCodec
import com.mohdshayan.beatwheel.core.drift.BackupFile
import com.mohdshayan.beatwheel.core.drift.BackupParse
import com.mohdshayan.beatwheel.core.drift.BackupReading
import com.mohdshayan.beatwheel.core.drift.BackupSession
import com.mohdshayan.beatwheel.core.drift.SessionCsv
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.core.drone.Wavetables
import com.mohdshayan.beatwheel.core.music.Temperaments
import com.mohdshayan.beatwheel.core.music.TuningMath
import com.mohdshayan.beatwheel.core.pitch.EstimatorConfig
import com.mohdshayan.beatwheel.core.pitch.PitchEstimator
import com.mohdshayan.beatwheel.data.db.InstrumentProfile
import com.mohdshayan.beatwheel.data.prefs.DroneSettings
import com.mohdshayan.beatwheel.data.repo.ActiveTuning
import com.mohdshayan.beatwheel.ui.profiles.ProfileForm
import com.mohdshayan.beatwheel.ui.profiles.TemperamentForm
import com.mohdshayan.beatwheel.ui.tuner.TunerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Edge cases added in the independent review: editor limits, corrupt real backups, and the tempered chain end to end. */
class ReviewPassTest {
    private val werckmeister = Temperaments.preset("werckmeister3")!!.offsetsCents

    @Test
    fun referenceFieldAcceptsExactlyTheEditorRange() {
        val base = ProfileForm(name = "Oboe", minHz = "220", maxHz = "1800")
        for (ok in listOf("415", "415.0", "440", "466.0", "442.5")) assertNull(ok, base.copy(reference = ok).referenceError)
        for (bad in listOf("", "414.9", "466.1", "-440", "0", "NaN", "Infinity", "4150", "abc")) {
            assertNotNull(bad, base.copy(reference = bad).referenceError)
        }
        assertTrue(base.copy(reference = "440").valid)
        assertFalse("a blank name never saves", base.copy(name = "   ", reference = "440").valid)
    }

    @Test
    fun rangeFieldsRejectInvertedTinyHugeAndNonNumbers() {
        val base = ProfileForm(name = "Bass", reference = "440.0")
        assertNull(base.copy(minHz = "20", maxHz = "30").rangeError)
        assertNull(base.copy(minHz = "3000", maxHz = "5000").rangeError)
        for ((lo, hi) in listOf("19.9" to "100", "100" to "5000.1", "100" to "149.9", "800" to "400", "" to "100", "100" to "NaN", "-5" to "100", "20" to "9999999")) {
            assertNotNull("$lo..$hi", base.copy(minHz = lo, maxHz = hi).rangeError)
        }
    }

    @Test
    fun temperamentOffsetsAcceptTheLimitsAndNothingBeyond() {
        val form = TemperamentForm(name = "Mine")
        assertTrue(form.valid)
        for (ok in listOf("-50", "-50.0", "50.0", "+50", "0", "-0.0", "13.7")) {
            assertNull(ok, form.copy(offsets = List(12) { ok }).offsetError(0))
        }
        for (bad in listOf("", "-", "-50.1", "50.01", "-99.5", "NaN", "Infinity", "1,5")) {
            val f = form.copy(offsets = listOf(bad) + List(11) { "0.0" })
            assertNotNull(bad, f.offsetError(0))
            assertFalse(bad, f.valid)
        }
    }

    @Test
    fun truncatedAndNewerBackupsAreRejectedButTheWholeOneReads() {
        val session = BackupSession(
            name = "Tuesday brass", profileName = "Trumpet in B flat", startedAt = 1_789_000_000_000, durationMs = 2_000,
            readings = List(20) { BackupReading(it * 100L, 70, 466.16f, -3.2f) },
        )
        val text = BackupCodec.encode(BackupFile(exportedAt = 1, sessions = listOf(session)))
        assertTrue(BackupCodec.decode(text) is BackupParse.Ok)
        // A download or copy cut short anywhere is not a backup, and never a crash.
        for (cut in listOf(1, 10, text.length / 3, text.length / 2, text.length - 2, text.length - 1)) {
            assertEquals("cut at $cut", BackupParse.NotABackup, BackupCodec.decode(text.take(cut)))
        }
        assertEquals(BackupParse.NotABackup, BackupCodec.decode(text.replaceFirst("\"format\":1", "\"format\":2")))
        assertEquals(BackupParse.NotABackup, BackupCodec.decode(text.byteInputStream().readBytes().copyOf(text.length / 2).inputStream()))
    }

    @Test
    fun csvOfAnEmptySessionIsJustTheHeaderAndNamesWithoutLatinLettersStillGetAFile() {
        val empty = BackupSession(name = "Empty", startedAt = 0)
        assertEquals(SessionCsv.HEADER + "\n", SessionCsv.write(empty, ZoneId.of("UTC")))
        assertEquals("session.csv", SessionCsv.fileName("रियाज़"))
        assertEquals("session.csv", SessionCsv.fileName("  ...  "))
        assertEquals("sa-drone-2.csv", SessionCsv.fileName("Sa drone #2"))
    }

    @Test
    fun aNoteAtItsWerckmeisterTargetAtA415ReadsZeroCents() {
        // Margriet's setup: A 415, Werckmeister III, C tonic, A kept at the reference, so E sits 1.9 cents above equal.
        val offsets = Temperaments.offsetsFor(werckmeister, 0, keepReferenceA = true)
        val e4 = TuningMath.targetHz(64, 415.0, offsets)
        assertEquals(1.9, TuningMath.cents(e4, TuningMath.equalHz(64.0, 415.0)), 0.05)
        val est = PitchEstimator()
        est.configure(EstimatorConfig(referenceAHz = 415.0, offsets = offsets, minHz = 220.0, maxHz = 1800.0, heldTone = true))
        var last = est.process(FloatArray(480), 480)
        Signals.blocks(Signals.tone(e4, 3.0, harmonics = doubleArrayOf(1.0, 0.6, 0.4), noise = 0.003)) { b, n -> last = est.process(b, n) }
        assertTrue(last.hasPitch && last.strobeLocked)
        assertEquals(64, last.midi)
        assertEquals(0.0, last.cents, 0.2)
    }

    @Test
    fun aNoteOutsideTheProfileRangeNeverReads() {
        val est = PitchEstimator()
        est.configure(EstimatorConfig(minHz = 220.0, maxHz = 1800.0))
        var any = false
        Signals.blocks(Signals.tone(110.0, 2.0)) { b, n -> if (est.process(b, n).hasPitch) any = true }
        assertFalse("A2 is below an oboe profile", any)
    }

    @Test
    fun droneSoundsTheWrittenNoteThroughTranspositionAndTemperament() {
        val clarinet = InstrumentProfile(name = "Clarinet in B flat", transpositionSemitones = -2, referenceAHz = 440.0)
        val equal = ActiveTuning(clarinet, "Equal", DoubleArray(12), 15)
        // Written D4 on a B flat clarinet sounds C4.
        val d = DroneSettings("reed", 0.6f, 4, 2)
        assertEquals(TuningMath.equalHz(60.0, 440.0), TunerViewModel.droneHz(equal, d), 1e-9)
        val concert = ActiveTuning(InstrumentProfile(name = "Chromatic", referenceAHz = 466.0), "Equal", DoubleArray(12), 15)
        assertEquals(TuningMath.equalHz(36.0, 466.0), TunerViewModel.droneHz(concert, DroneSettings("saw", 1f, 2, 0)), 1e-9)
    }

    @Test
    fun theHighestDroneNeverAliases() {
        // B5 at A 466 is 1046.5 Hz: a full 23-harmonic saw would put its top partial past 24 kHz.
        val concert = ActiveTuning(InstrumentProfile(name = "Chromatic", referenceAHz = 466.0), "Equal", DoubleArray(12), 15)
        val top = TunerViewModel.droneHz(concert, DroneSettings("saw", 1f, 5, 11))
        for (w in Waveform.entries) {
            val h = Wavetables.harmonics(w, Wavetables.harmonicLimit(top, 48_000))
            assertTrue(w.key, h.isNotEmpty() && h.size * top < 48_000 * 0.45)
        }
        // A transposed import can push the drone past 7 kHz; it still gets at least its fundamental.
        assertEquals(1, Wavetables.harmonics(Waveform.SAW, Wavetables.harmonicLimit(20_000.0, 48_000)).size)
        assertEquals(Wavetables.harmonics(Waveform.SAW).size, Wavetables.harmonics(Waveform.SAW, Wavetables.harmonicLimit(220.0, 48_000)).size)
    }
}
