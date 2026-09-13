package com.mohdshayan.beatwheel.core

import com.mohdshayan.beatwheel.core.drift.BackupReading
import com.mohdshayan.beatwheel.core.drift.BackupSession
import com.mohdshayan.beatwheel.core.drift.ImportSanitizer
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.ui.components.chartStepMinutes
import com.mohdshayan.beatwheel.ui.components.minuteLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewEdgeCaseTest {
    @Test
    fun noteListsTypedOnACapitalisingKeyboardStillParse() {
        // The strings field capitalises; "BB3" is B flat 3, not an error.
        assertEquals(58, NoteNames.parse("BB3"))
        assertEquals(58, NoteNames.parse("Bb3"))
        assertEquals(58, NoteNames.parse("b♭3"))
        assertEquals(66, NoteNames.parse("F♯4"))
        assertEquals("40,45,50,55,59,64", NoteNames.parseNoteList("E2 A2 D3 G3 B3 E4"))
        assertEquals("58,63", NoteNames.parseNoteList("BB3, EB4"))
        assertNull(NoteNames.parseNoteList("E2 H3"))
        assertNull(NoteNames.parse("B"))
        assertNull(NoteNames.parse("Bb"))
        // What the editor writes back reads back to the same notes.
        val csv = "40,58,66"
        assertEquals(csv, NoteNames.parseNoteList(NoteNames.formatMidiList(csv)))
    }

    @Test
    fun chartAxisHandlesEverySpanWithoutRunningOutOfSteps() {
        assertEquals(0.25, chartStepMinutes(1.0), 0.0)
        assertEquals(10.0, chartStepMinutes(42.0), 0.0)
        assertEquals(10.0, chartStepMinutes(60.0), 0.0)
        assertEquals(15.0, chartStepMinutes(61.0), 0.0)
        // An imported file can hold a reading hours or centuries in; the axis must still draw.
        for (span in listOf(181.0, 360.0, 10_000.0, Long.MAX_VALUE / 60_000.0)) {
            val step = chartStepMinutes(span)
            assertTrue("span $span", step > 0 && span / step <= 6.0)
        }
    }

    @Test
    fun minuteLabelsHaveNoTrailingZeros() {
        assertEquals(listOf("0", "0.25", "0.5", "0.75", "1", "10", "100"), listOf(0.0, 0.25, 0.5, 0.75, 1.0, 10.0, 100.0).map(::minuteLabel))
    }

    @Test
    fun zeroAndHugeOffsetsInAnImportedSessionKeepTheirOrderAndDuration() {
        val s = ImportSanitizer.session(
            BackupSession(
                name = "Late reading",
                startedAt = 1,
                readings = listOf(BackupReading(Long.MAX_VALUE / 2, 69, 440f, 0f), BackupReading(0, 69, 440f, -2f)),
            ),
        )
        assertEquals(listOf(0L, Long.MAX_VALUE / 2), s.readings.map { it.offsetMs })
        assertEquals(Long.MAX_VALUE / 2, s.durationMs)
        assertEquals(2, s.readingCount)
    }
}
