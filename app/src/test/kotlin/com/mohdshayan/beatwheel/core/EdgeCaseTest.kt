package com.mohdshayan.beatwheel.core

import com.mohdshayan.beatwheel.core.drift.BackupCodec
import com.mohdshayan.beatwheel.core.drift.BackupParse
import com.mohdshayan.beatwheel.core.drift.BackupProfile
import com.mohdshayan.beatwheel.core.drift.BackupReading
import com.mohdshayan.beatwheel.core.drift.BackupSession
import com.mohdshayan.beatwheel.core.drift.BackupTemperament
import com.mohdshayan.beatwheel.core.drift.DriftPoint
import com.mohdshayan.beatwheel.core.drift.DriftSummary
import com.mohdshayan.beatwheel.core.drift.ImportSanitizer
import com.mohdshayan.beatwheel.core.drift.SessionCsv
import com.mohdshayan.beatwheel.core.drift.WeekGrouping
import com.mohdshayan.beatwheel.core.drone.BeatMeter
import com.mohdshayan.beatwheel.core.drone.DroneCanceller
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.core.drone.Wavetables
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.Temperaments
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.core.music.TuningMath
import com.mohdshayan.beatwheel.core.pitch.EstimatorConfig
import com.mohdshayan.beatwheel.core.pitch.NoiseGate
import com.mohdshayan.beatwheel.core.pitch.PhaseDemodulator
import com.mohdshayan.beatwheel.core.pitch.PitchEstimator
import com.mohdshayan.beatwheel.core.pitch.YinDetector
import com.mohdshayan.beatwheel.ui.sessions.formatDuration
import com.mohdshayan.beatwheel.ui.tuner.formatElapsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class MusicEdgeCaseTest {
    @Test
    fun midiBoundariesSpellAndParse() {
        assertEquals("C-1", NoteNames.spell(0).ascii)
        assertEquals("G9", NoteNames.spell(127).ascii)
        assertEquals(11, TuningMath.pitchClass(-1))
        assertEquals(-2, TuningMath.octave(-1))
        assertEquals(0, NoteNames.parse("C-1"))
        assertEquals(127, NoteNames.parse("G9"))
        assertNull("above MIDI 127", NoteNames.parse("G#9"))
        assertNull("below MIDI 0", NoteNames.parse("Cb-1"))
        assertEquals(59, NoteNames.parse("Cb4"))
        assertNull(NoteNames.parse(""))
        assertNull(NoteNames.parse("4"))
        assertEquals("", NoteNames.parseNoteList(""))
        assertEquals(emptyList<Int>(), NoteNames.parseMidiCsv(""))
    }

    @Test
    fun referenceRangeIsInclusiveAtBothEnds() {
        assertTrue(TuningMath.isValidReference(415.0))
        assertTrue(TuningMath.isValidReference(466.0))
        assertFalse(TuningMath.isValidReference(414.99))
        assertFalse(TuningMath.isValidReference(466.01))
        assertFalse(TuningMath.isValidReference(0.0))
        assertFalse(TuningMath.isValidReference(-440.0))
        assertFalse(TuningMath.isValidReference(Double.NaN))
    }

    @Test
    fun centsAreZeroAtTargetAndOctavesAre1200() {
        assertEquals(0.0, TuningMath.cents(123.4, 123.4), 1e-12)
        assertEquals(1200.0, TuningMath.cents(880.0, 440.0), 1e-9)
        assertEquals(-1200.0, TuningMath.cents(220.0, 440.0), 1e-9)
        // Unit boundary: half a semitone either side of A4 picks the nearer note.
        assertEquals(69, TuningMath.nearestMidi(TuningMath.applyCents(440.0, 49.9), 440.0, DoubleArray(12)))
        assertEquals(70, TuningMath.nearestMidi(TuningMath.applyCents(440.0, 50.1), 440.0, DoubleArray(12)))
        assertEquals(88, TuningMath.nearestMidi(1318.5, 440.0, DoubleArray(12)))
    }

    @Test
    fun extremeCustomOffsetsStillFindTheClosestTemperedNote() {
        // A sits 50 cents sharp and A sharp 50 cents flat, so both targets coincide in the middle.
        val offsets = DoubleArray(12).also { it[9] = 50.0; it[10] = -50.0; it[11] = 50.0 }
        val between = TuningMath.equalHz(69.5, 440.0)
        val midi = TuningMath.nearestMidi(between, 440.0, offsets)
        assertTrue(midi == 69 || midi == 70)
        assertEquals(0.0, TuningMath.cents(between, TuningMath.targetHz(midi, 440.0, offsets)), 1e-6)
    }

    @Test
    fun tonicWrapsForNegativeAndLargeValues() {
        val just = Temperaments.preset("just")!!.offsetsCents
        assertTrue(Temperaments.offsetsFor(just, -1, false).contentEquals(Temperaments.offsetsFor(just, 11, false)))
        assertTrue(Temperaments.offsetsFor(just, 25, false).contentEquals(Temperaments.offsetsFor(just, 1, false)))
        // Equal temperament stays all zero on any tonic, with or without keeping A.
        assertTrue(Temperaments.offsetsFor(DoubleArray(12), 5, true).all { it == 0.0 })
    }

    @Test
    fun customOffsetsRejectNonNumbersAndAcceptTheExactLimits() {
        assertEquals(12, Temperaments.parseOffsets("50,-50,0,0,0,0,0,0,0,0,0,0")!!.size)
        assertNull(Temperaments.parseOffsets("50.1,0,0,0,0,0,0,0,0,0,0,0"))
        assertNull(Temperaments.parseOffsets(""))
        assertNull(Temperaments.parseOffsets("NaN,0,0,0,0,0,0,0,0,0,0,0"))
        assertNull(Temperaments.parseOffsets("Infinity,0,0,0,0,0,0,0,0,0,0,0"))
        assertNull(Temperaments.parseOffsets("0,0,0,0,0,0,0,0,0,0,0,0,0"))
        assertNull(Temperaments.customId("custom:abc"))
        assertNull(Temperaments.customId("equal"))
    }

    @Test
    fun signedCentsNeverShowMinusZero() {
        val t = com.mohdshayan.beatwheel.core.music.CentsText
        assertEquals("0.0", t.signed(-0.04))
        assertEquals("0.0", t.signed(0.049))
        assertEquals("0.0", t.signed(-0.0))
        assertEquals("-0.1", t.signed(-0.05))
        assertEquals("+3.4", t.signed(3.44))
        assertEquals("-10.0", t.signed(-10.0))
        assertEquals("+10.0", t.signed(10.0))
        assertEquals("0.00", t.signed(-0.001f, 2))
        assertEquals("-0.29", t.signed(-0.29f, 2))
    }

    @Test
    fun transpositionRoundTripsAndNamesUnknownIntervals() {
        for (semis in listOf(0, -2, -9, -14, -21, 7, 36)) {
            assertEquals(60, Transposition.written(Transposition.sounding(60, semis), semis))
        }
        assertEquals("3 semitones", Transposition.label(3))
        assertEquals("Concert", Transposition.label(0))
    }
}

class DspEdgeCaseTest {
    @Test
    fun levelOfEmptySilentAndFullScaleBlocks() {
        assertEquals(-120f, NoiseGate.rmsDb(FloatArray(10), 0))
        assertEquals(-120f, NoiseGate.rmsDb(FloatArray(480), 480))
        assertEquals(0f, NoiseGate.rmsDb(FloatArray(480) { 1f }, 480), 1e-4f)
        assertEquals(-6.02f, NoiseGate.rmsDb(FloatArray(480) { if (it % 2 == 0) 0.5f else -0.5f }, 480), 0.01f)
    }

    @Test
    fun gateOpensAtThresholdHoldsThenCloses() {
        val gate = NoiseGate(thresholdDb = -52f, sensitivity = 0.5f)
        assertFalse(gate.update(-52.1f, 480, 48_000))
        assertTrue(gate.update(-52f, 480, 48_000))
        // Inside the 4 dB hysteresis nothing changes.
        assertTrue(gate.update(-55f, 480, 48_000))
        // Below it, the 150 ms hold runs out after 15 blocks of 10 ms.
        repeat(14) { assertTrue(gate.update(-80f, 480, 48_000)) }
        assertFalse(gate.update(-80f, 480, 48_000))
        // Sensitivity moves the threshold 15 dB either way.
        assertEquals(-37f, NoiseGate(-52f, 0f).effectiveThresholdDb, 1e-4f)
        assertEquals(-67f, NoiseGate(-52f, 1f).effectiveThresholdDb, 1e-4f)
    }

    @Test
    fun yinRefusesSilenceShortBuffersAndEmptyRanges() {
        val yin = YinDetector(12_000, 1024, 480)
        assertNull(yin.detect(FloatArray(1504), 1504, 27.5, 2500.0))
        assertNull(yin.detect(FloatArray(1000), 1000, 27.5, 2500.0))
        val tone = Signals.tone(440.0, 0.2, rate = 12_000)
        assertNull("lowest above highest", yin.detect(tone, 1504, 900.0, 800.0))
        assertEquals(440.0, yin.detect(tone, 1504, 27.5, 2500.0)!!, 0.5)
    }

    @Test
    fun demodulatorNeedsHistoryAndIgnoresAMissingTarget() {
        val demod = PhaseDemodulator(48_000)
        assertNull(demod.offsetCents())
        val s = Signals.tone(440.0, 0.5)
        demod.process(s, 0, s.size) // no target yet: nothing happens
        assertNull(demod.offsetHz())
        demod.setTarget(440.0)
        demod.process(s, 0, 48) // one millisecond is not enough
        assertNull(demod.offsetHz())
        demod.setTarget(2500.0)
        val high = Signals.tone(TuningMath.applyCents(2500.0, 3.0), 1.0)
        demod.process(high, 0, high.size)
        assertEquals(3.0, demod.offsetCents()!!, 0.2)
    }

    @Test
    fun estimatorSurvivesEmptyBlocksAndSilence() {
        val est = PitchEstimator()
        est.configure(EstimatorConfig())
        assertFalse(est.process(FloatArray(480), 0).hasPitch)
        repeat(200) { assertFalse(est.process(FloatArray(480), 480).hasPitch) }
        // A clipped square wave at full scale still reads as a note, not a crash.
        val square = Signals.tone(220.0, 1.0).map { if (it >= 0) 1f else -1f }.toFloatArray()
        var last = est.process(FloatArray(480), 480)
        Signals.blocks(square) { b, n -> last = est.process(b, n) }
        assertTrue(last.hasPitch)
        assertEquals(57, last.midi)
    }

    @Test
    fun slowAndFastBeatsNearTheWindowEdges() {
        // With the drone removed from the input (headphones, or cancelled on the speaker) only the player remains.
        for (offset in listOf(0.4, -0.7, 1.4)) {
            val s = Signals.tone(440.0 + offset, 3.0, amp = 0.5, noise = 0.002)
            val meter = BeatMeter(48_000)
            meter.setReference(440.0)
            meter.process(s, s.size)
            assertEquals("offset $offset", offset, meter.beatsPerSecond()!!, 0.1)
        }
    }

    @Test
    fun beatMeterNeedsAReferenceAndAFullWindow() {
        val meter = BeatMeter(48_000)
        assertNull(meter.beatsPerSecond())
        meter.setReference(0.0)
        val s = Signals.tone(440.0, 3.0)
        meter.process(s, s.size)
        assertNull(meter.beatsPerSecond())
        meter.setReference(440.0)
        meter.process(s, 48_000) // one second of a two-second window
        assertNull(meter.beatsPerSecond())
    }

    @Test
    fun cancellerPassesAudioThroughWithoutADroneOrAboveNyquist() {
        val input = Signals.tone(300.0, 0.1)
        val out = FloatArray(input.size)
        DroneCanceller(48_000).apply { setDrone(0.0) }.process(input, out, input.size)
        assertTrue(input.contentEquals(out))
        val out2 = FloatArray(input.size)
        DroneCanceller(48_000).apply { setDrone(30_000.0) }.process(input, out2, input.size)
        assertTrue(input.contentEquals(out2))
    }

    @Test
    fun wavetablesPeakBelowFullScale() {
        for (w in Waveform.entries) {
            val t = Wavetables.build(w, 1024)
            assertEquals(w.name, 0.9f, t.maxOf { kotlin.math.abs(it) }, 1e-4f)
        }
        assertEquals(Waveform.REED, Waveform.fromKey(null))
        assertEquals(Waveform.REED, Waveform.fromKey("banjo"))
    }
}

class DriftEdgeCaseTest {
    @Test
    fun summaryOfEmptySingleAndSimultaneousReadings() {
        assertNull(DriftSummary.summarize(emptyList()))
        val one = DriftSummary.summarize(listOf(DriftPoint(500, -3.5f)))!!
        assertEquals(0f, one.centsPerMinute)
        assertEquals(-3.5f, one.startCents)
        assertEquals(-3.5f, one.endCents)
        assertEquals(1, one.readingCount)
        // Every reading at the same instant has no slope rather than a division by zero.
        val same = DriftSummary.summarize(listOf(DriftPoint(0, 1f), DriftPoint(0, 5f)))!!
        assertEquals(0f, same.centsPerMinute)
        assertEquals(3f, same.startCents, 1e-4f)
    }

    @Test
    fun anHourAtTenReadingsASecondSummarisesExactly() {
        val points = (0 until 36_000).map { DriftPoint(it * 100L, 20f - 30f * it / 36_000f) }
        val stats = DriftSummary.summarize(points)!!
        assertEquals(-0.5f, stats.centsPerMinute, 0.001f)
        assertEquals(20f, stats.maxCents, 1e-3f)
        assertEquals(-10f, stats.minCents, 0.01f)
        assertEquals(36_000, stats.readingCount)
    }

    @Test
    fun durationsAndElapsedTimesAtUnitBoundaries() {
        assertEquals("0 s", formatDuration(0))
        assertEquals("59 s", formatDuration(59_999))
        assertEquals("1 min", formatDuration(60_000))
        assertEquals("59 min", formatDuration(3_599_999))
        assertEquals("1 h 00 min", formatDuration(3_600_000))
        assertEquals("0:00", formatElapsed(999))
        assertEquals("1:00", formatElapsed(60_000))
        assertEquals("60:00", formatElapsed(3_600_000))
    }

    @Test
    fun csvKeepsFormulaLikeNamesAsTextAndEscapesQuotes() {
        val s = BackupSession(
            name = "=HYPERLINK(\"x\")", profileName = "+Oboe", temperamentName = "@Just",
            readings = listOf(BackupReading(0, 69, 440f, 0f)),
        )
        val row = SessionCsv.write(s, ZoneOffset.UTC).lines()[1]
        assertTrue(row, row.startsWith("\"'=HYPERLINK(\"\"x\"\")\","))
        assertTrue(row, row.contains(",'@Just,'+Oboe,0"))
        assertEquals(SessionCsv.HEADER + "\n", SessionCsv.write(s.copy(readings = emptyList()), ZoneOffset.UTC))
        assertEquals("line\none".let { "\"$it\"" }, SessionCsv.escape("line\none"))
        assertEquals("session.csv", SessionCsv.fileName("!!!"))
        assertEquals("session.csv", SessionCsv.fileName(""))
    }

    @Test
    fun csvStartTimeIsWrittenInTheGivenZone() {
        val s = BackupSession(name = "x", startedAt = Instant.parse("2026-03-29T00:30:00Z").toEpochMilli(), readings = listOf(BackupReading(0, 69, 440f, 0f)))
        assertTrue(SessionCsv.write(s, ZoneId.of("Europe/London")).lines()[1].contains(",2026-03-29T00:30:00Z,"))
        val afterShift = s.copy(startedAt = Instant.parse("2026-03-29T01:30:00Z").toEpochMilli())
        assertTrue(SessionCsv.write(afterShift, ZoneId.of("Europe/London")).lines()[1].contains(",2026-03-29T02:30:00+01:00,"))
        assertTrue(SessionCsv.write(s, ZoneId.of("Asia/Kolkata")).lines()[1].contains(",2026-03-29T06:00:00+05:30,"))
    }
}

class WeekGroupingEdgeCaseTest {
    private fun ms(local: String, zone: ZoneId) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun emptyListHasNoGroups() {
        assertTrue(WeekGrouping.group(emptyList<Long>(), Instant.now(), ZoneId.of("UTC")) { it }.isEmpty())
    }

    @Test
    fun weeksFollowTheLocalCalendarAcrossDaylightSaving() {
        val london = ZoneId.of("Europe/London")
        // Clocks went forward at 01:00 on Sunday 29 March 2026. Sunday 23:30 and Monday 00:30 are different weeks.
        val now = Instant.ofEpochMilli(ms("2026-03-31T12:00:00", london))
        val sunday = ms("2026-03-29T23:30:00", london)
        val monday = ms("2026-03-30T00:30:00", london)
        val groups = WeekGrouping.group(listOf(sunday, monday), now, london) { it }
        assertEquals(listOf("This week", "Last week"), groups.map { it.label })
        // Autumn: clocks went back on Sunday 25 October 2026, so 01:30 happens twice and both stay in one week.
        val autumnNow = Instant.ofEpochMilli(ms("2026-10-25T12:00:00", london))
        val firstPass = Instant.parse("2026-10-25T00:30:00Z").toEpochMilli()
        val secondPass = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli()
        assertEquals(listOf("This week"), WeekGrouping.group(listOf(firstPass, secondPass), autumnNow, london) { it }.map { it.label })
    }

    @Test
    fun theSameInstantFallsInTheWeekOfTheViewersZone() {
        // Monday 07:00 in Auckland is still Sunday afternoon in Los Angeles.
        val instant = Instant.parse("2026-09-13T19:00:00Z")
        val now = Instant.parse("2026-09-14T20:00:00Z")
        assertEquals("This week", WeekGrouping.group(listOf(instant.toEpochMilli()), now, ZoneId.of("Pacific/Auckland")) { it }[0].label)
        assertEquals("Last week", WeekGrouping.group(listOf(instant.toEpochMilli()), now, ZoneId.of("America/Los_Angeles")) { it }[0].label)
    }

    @Test
    fun olderYearsCarryTheYearAndFutureSessionsDoNotBreakGrouping() {
        val utc = ZoneId.of("UTC")
        val now = Instant.parse("2026-01-08T10:00:00Z")
        val groups = WeekGrouping.group(
            listOf(Instant.parse("2025-12-01T10:00:00Z").toEpochMilli(), Instant.parse("2026-02-01T10:00:00Z").toEpochMilli()),
            now, utc,
        ) { it }
        assertEquals(listOf("Week of 26 January", "Week of 1 December 2025"), groups.map { it.label })
    }
}

class ImportEdgeCaseTest {
    @Test
    fun garbageFilesAreNotBackups() {
        val garbage = listOf(
            "", " ", "null", "[]", "42", "\"format\"", "{", "{\"format\":}", "{\"format\":\"one\"}",
            "{\"format\":0}", "{\"format\":-1}", "{\"format\":1.5}", "{\"format\":NaN}",
            "{\"format\":1,\"profiles\":[{\"id\":1}]}", // a profile without a name
            "{\"format\":1,\"sessions\":[{\"name\":\"x\",\"readings\":[{\"offsetMs\":0,\"midi\":69,\"hz\":NaN,\"cents\":0}]}]}",
            String(ByteArray(2048) { (it * 37 % 256).toByte() }, Charsets.ISO_8859_1),
            "[".repeat(100_000),
            "{\"a\":".repeat(50_000) + "1" + "}".repeat(50_000),
        )
        for (g in garbage) assertEquals(g.take(40), BackupParse.NotABackup, BackupCodec.decode(g))
    }

    @Test
    fun unknownKeysAndMissingOptionalListsStillImport() {
        val parsed = BackupCodec.decode("{\"format\":1,\"app\":\"beatwheel\",\"future\":{\"x\":[1,2]}}")
        assertTrue(parsed is BackupParse.Ok)
        val file = (parsed as BackupParse.Ok).file
        assertTrue(file.profiles.isEmpty() && file.sessions.isEmpty() && file.customTemperaments.isEmpty())
    }

    @Test
    fun hostileProfileValuesArePulledIntoRange() {
        val p = ImportSanitizer.profile(
            BackupProfile(
                id = 9, name = "   ", referenceAHz = -440.0, tonicPitchClass = -13, transpositionSemitones = 9_999,
                minHz = 0.0, maxHz = 1e9, stringTargetsMidi = "40,abc,-5,200,64", noiseGateDb = Float.NEGATIVE_INFINITY,
            ),
        )
        assertEquals("Imported profile", p.name)
        assertEquals(440.0, p.referenceAHz, 0.0)
        assertEquals(11, p.tonicPitchClass)
        assertEquals(36, p.transpositionSemitones)
        assertEquals(27.5, p.minHz, 0.0)
        assertEquals(2500.0, p.maxHz, 0.0)
        assertEquals("40,64", p.stringTargetsMidi)
        assertEquals(-52f, p.noiseGateDb)
        // Valid values pass untouched, except the reference snaps to the 0.1 Hz grid.
        val ok = BackupProfile(id = 1, name = "Oboe", referenceAHz = 415.04, minHz = 220.0, maxHz = 1800.0, noiseGateDb = -45f)
        assertEquals(ok.copy(referenceAHz = 415.0), ImportSanitizer.profile(ok))
        assertEquals(466.0, ImportSanitizer.profile(ok.copy(referenceAHz = 466.0)).referenceAHz, 0.0)
        assertEquals(440.0, ImportSanitizer.profile(ok.copy(referenceAHz = 466.1)).referenceAHz, 0.0)
    }

    @Test
    fun overflowingNumbersRejectTheFileAndBadReadingsAreDropped() {
        // 1e39 does not fit a Float, so the parser refuses the whole file rather than store infinity.
        assertEquals(BackupParse.NotABackup, BackupCodec.decode("{\"format\":1,\"sessions\":[{\"name\":\"x\",\"startCents\":1e39}]}"))
        val s = ImportSanitizer.session(
            BackupSession(
                name = "", durationMs = -5, readingCount = 999, startCents = Float.POSITIVE_INFINITY, referenceAHz = 0.0,
                readings = listOf(
                    BackupReading(200, 69, Float.POSITIVE_INFINITY, 0f),
                    BackupReading(100, 69, 440f, 1.5f),
                    BackupReading(-1, 69, 440f, 0f),
                    BackupReading(150, 69, 0f, 0f),
                    BackupReading(300, 0, 0f, 0f, gap = true),
                ),
            ),
        )
        assertEquals("Imported session", s.name)
        assertEquals(listOf(100L, 300L), s.readings.map { it.offsetMs })
        assertEquals(1, s.readingCount)
        assertEquals(300L, s.durationMs)
        assertEquals(440.0, s.referenceAHz, 0.0)
        // The infinite start was dropped and a summary rebuilt from the one good reading.
        assertEquals(1.5f, s.startCents)
        assertEquals(0f, s.driftCentsPerMinute)
        assertNull(ImportSanitizer.session(BackupSession(name = "empty")).startCents)
        assertEquals("Unknown profile", s.profileName)
        assertEquals(1_700_000_000_000L, ImportSanitizer.session(BackupSession(name = "x"), 1_700_000_000_000L).startedAt)
        assertEquals(5L, ImportSanitizer.session(BackupSession(name = "x", startedAt = 5), 1L).startedAt)
    }

    @Test
    fun streamedExportIsTheSameDocumentAndReadsBackFromAStream() {
        val sessions = listOf(
            BackupSession(id = 1, name = "Tuesday brass", startedAt = 5, durationMs = 2_000, readings = listOf(BackupReading(0, 70, 466.2f, 1.5f))),
            BackupSession(id = 2, name = "Reeds, \"warm\"", readings = emptyList()),
        )
        val head = com.mohdshayan.beatwheel.core.drift.BackupFile(
            exportedAt = 42,
            profiles = listOf(BackupProfile(id = 1, name = "Oboe", referenceAHz = 415.0)),
            customTemperaments = listOf(BackupTemperament(4, "Vallotti", "0,0,0,0,0,0,0,0,0,0,0,0")),
        )
        for (list in listOf(sessions, emptyList())) {
            val out = java.io.StringWriter()
            val it = list.iterator()
            BackupCodec.write(out, head) { if (it.hasNext()) it.next() else null }
            assertEquals(BackupCodec.encode(head.copy(sessions = list)), out.toString())
            val parsed = BackupCodec.decode(out.toString().byteInputStream())
            assertEquals(head.copy(format = 1, sessions = list), (parsed as BackupParse.Ok).file)
        }
        assertEquals(BackupParse.NotABackup, BackupCodec.decode("PK\u0003\u0004 not json".byteInputStream()))
        assertEquals(BackupParse.NotABackup, BackupCodec.decode(ByteArray(0).inputStream()))
    }

    @Test
    fun temperamentNamesAreNeverBlank() {
        assertEquals("Imported temperament", ImportSanitizer.temperament(BackupTemperament(1, "", "0,0,0,0,0,0,0,0,0,0,0,0")).name)
    }
}
