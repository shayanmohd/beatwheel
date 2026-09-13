package com.mohdshayan.beatwheel.core

import com.mohdshayan.beatwheel.core.drift.BackupCodec
import com.mohdshayan.beatwheel.core.drift.BackupFile
import com.mohdshayan.beatwheel.core.drift.BackupParse
import com.mohdshayan.beatwheel.core.drift.BackupProfile
import com.mohdshayan.beatwheel.core.drift.BackupReading
import com.mohdshayan.beatwheel.core.drift.BackupSession
import com.mohdshayan.beatwheel.core.drift.BackupTemperament
import com.mohdshayan.beatwheel.core.drift.DriftPoint
import com.mohdshayan.beatwheel.core.drift.DriftSummary
import com.mohdshayan.beatwheel.core.drift.ImportNames
import com.mohdshayan.beatwheel.core.drift.ReviewPolicy
import com.mohdshayan.beatwheel.core.drift.SessionCsv
import com.mohdshayan.beatwheel.core.drift.WeekGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DriftSummaryTest {
    @Test
    fun linearSagOfTwelveCentsOverTenMinutes() {
        val points = (0..6000).map { i -> DriftPoint(i * 100L, (-12.0 * i / 6000).toFloat()) } +
            DriftPoint(300_000L, 99f, gap = true)
        val stats = DriftSummary.summarize(points.sortedBy { it.offsetMs })!!
        assertEquals(-1.2f, stats.centsPerMinute, 0.005f)
        assertEquals(0f, stats.startCents, 0.1f)
        assertEquals(-12f, stats.endCents, 0.1f)
        assertEquals(-12f, stats.minCents, 1e-4f)
        assertEquals(0f, stats.maxCents, 1e-4f)
        assertEquals(6001, stats.readingCount)
    }

    @Test
    fun noReadingsGivesNoSummary() {
        assertEquals(null, DriftSummary.summarize(listOf(DriftPoint(0, 0f, gap = true))))
    }
}

class SessionFilesTest {
    private val session = BackupSession(
        id = 3, name = "Tuesday brass, section B", profileName = "Trumpet in B flat", referenceAHz = 442.0,
        temperamentName = "Equal", transpositionSemitones = -2, startedAt = 1_757_430_000_000L,
        readings = listOf(
            BackupReading(0, 70, 466.16f, 0f, -20f),
            BackupReading(100, 70, 465.0f, -4.3f, -21f),
            BackupReading(900, 0, 0f, 0f, 0f, gap = true),
        ),
    )

    @Test
    fun csvHasTheHeaderWrittenAndSoundingNotesAndGaps() {
        val lines = SessionCsv.write(session, ZoneOffset.UTC).trimEnd().lines()
        assertEquals(SessionCsv.HEADER, lines[0])
        assertEquals(4, lines.size)
        val row = lines[2]
        assertTrue(row, row.startsWith("\"Tuesday brass, section B\",2025-09-09T15:00:00Z,0.1,C5,Bb4,"))
        assertTrue(row, row.endsWith(",-4.3,442.0,Equal,Trumpet in B flat,0"))
        assertTrue(lines[3].endsWith(",1"))
        assertEquals("tuesday-brass-section-b.csv", SessionCsv.fileName(session.name))
    }

    @Test
    fun backupRoundTripsAndRejectsFilesWithoutFormat() {
        val file = BackupFile(
            exportedAt = 5,
            profiles = listOf(BackupProfile(id = 1, name = "Oboe", builtInKey = "oboe", referenceAHz = 415.0, temperamentKey = "custom:4")),
            customTemperaments = listOf(BackupTemperament(4, "Vallotti", "0,-5.9,-3.9,-2.0,-7.8,2.0,-7.8,-2.0,-3.9,-5.9,0.0,-9.8")),
            sessions = listOf(session),
        )
        val parsed = BackupCodec.decode(BackupCodec.encode(file))
        assertTrue(parsed is BackupParse.Ok)
        assertEquals(file.copy(format = 1), (parsed as BackupParse.Ok).file)
        assertEquals(BackupParse.NotABackup, BackupCodec.decode("""{"profiles":[]}"""))
        assertEquals(BackupParse.NotABackup, BackupCodec.decode("name,cents\nx,1"))
        assertEquals(BackupParse.NotABackup, BackupCodec.decode("""{"format":7}"""))
    }

    @Test
    fun importedNamesNeverOverwrite() {
        assertEquals("Oboe (imported)", ImportNames.uniqueName("Oboe", setOf("Oboe")))
        assertEquals("Oboe (imported 2)", ImportNames.uniqueName("Oboe", setOf("Oboe", "Oboe (imported)")))
        assertEquals("Shawm", ImportNames.uniqueName("Shawm", setOf("Oboe")))
    }
}

class GroupingAndReviewTest {
    @Test
    fun sessionsGroupByMondayWeeks() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-09-13T10:00:00Z") // a Sunday
        val times = listOf("2026-09-07T08:00:00Z", "2026-09-13T09:00:00Z", "2026-09-06T20:00:00Z", "2026-08-19T12:00:00Z")
            .map { Instant.parse(it).toEpochMilli() }
        val groups = WeekGrouping.group(times, now, zone) { it }
        assertEquals(listOf("This week", "Last week", "Week of 17 August"), groups.map { it.label })
        assertEquals(2, groups[0].items.size)
        assertTrue(groups[0].items[0] > groups[0].items[1])
    }

    @Test
    fun reviewPromptWaitsForThreeUsesOnSeparateDays() {
        var s = ReviewPolicy.State(0, -1, false)
        s = ReviewPolicy.afterHeldNote(s, 100)
        s = ReviewPolicy.afterHeldNote(s, 100)
        s = ReviewPolicy.afterSessionSaved(s)
        assertFalse(ReviewPolicy.shouldPrompt(s, audioRunning = false))
        s = ReviewPolicy.afterHeldNote(s, 101)
        assertFalse(ReviewPolicy.shouldPrompt(s, audioRunning = true))
        assertTrue(ReviewPolicy.shouldPrompt(s, audioRunning = false))
        assertFalse(ReviewPolicy.shouldPrompt(s.copy(prompted = true), audioRunning = false))
    }
}
