package com.mohdshayan.beatwheel.core.drift

import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.core.music.TuningMath
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One drift session as a spreadsheet: one row per reading, one gap row wherever the app was in the background. */
object SessionCsv {
    const val HEADER =
        "session,started_at,elapsed_s,written_note,sounding_note,target_hz,measured_hz,cents,reference_a_hz,temperament,profile,gap"

    fun write(session: BackupSession, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder(HEADER.length + session.readings.size * 80)
        sb.append(HEADER).append('\n')
        val started = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(session.startedAt).atZone(zone),
        )
        for (r in session.readings) {
            val cells = if (r.gap) {
                listOf(
                    text(session.name), started, seconds(r.offsetMs), "", "", "", "", "",
                    num(session.referenceAHz, 1), text(session.temperamentName), text(session.profileName), "1",
                )
            } else {
                val written = Transposition.written(r.midi, session.transpositionSemitones)
                val target = TuningMath.applyCents(r.hz.toDouble(), -r.cents.toDouble())
                listOf(
                    text(session.name), started, seconds(r.offsetMs),
                    NoteNames.spell(written).ascii, NoteNames.spell(r.midi).ascii,
                    num(target, 2), num(r.hz.toDouble(), 2), num(r.cents.toDouble(), 1),
                    num(session.referenceAHz, 1), text(session.temperamentName), text(session.profileName), "0",
                )
            }
            cells.joinTo(sb, ",") { escape(it) }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun seconds(ms: Long) = String.format(Locale.US, "%.1f", ms / 1000.0)

    private fun num(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

    /** A typed name that starts like a formula is kept as text, so a spreadsheet never evaluates it. */
    fun text(cell: String): String =
        if (cell.isNotEmpty() && cell[0] in "=+-@\t\r") "'$cell" else cell

    fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }

    /** A file name safe on every storage provider: "tuesday-brass.csv". */
    fun fileName(sessionName: String): String {
        val slug = sessionName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return (slug.ifEmpty { "session" }) + ".csv"
    }
}
