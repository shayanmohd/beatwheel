package com.mohdshayan.beatwheel.core.music

enum class Accidental { NATURAL, SHARP, FLAT }

/** A spelled note: letter, accidental and octave. */
data class SpelledNote(val letter: Char, val accidental: Accidental, val octave: Int) {
    /** Plain text with ASCII accidentals, used in CSV and file names: "Bb4", "F#3". */
    val ascii: String
        get() = letter + when (accidental) {
            Accidental.NATURAL -> ""
            Accidental.SHARP -> "#"
            Accidental.FLAT -> "b"
        } + octave

    /** What a screen reader says: "B flat 4". */
    val spoken: String
        get() = letter + when (accidental) {
            Accidental.NATURAL -> ""
            Accidental.SHARP -> " sharp"
            Accidental.FLAT -> " flat"
        } + " " + octave
}

object NoteNames {
    // The spelling most tuners use: sharps for C, F and G, flats for E, A and B.
    private val LETTERS = charArrayOf('C', 'C', 'D', 'E', 'E', 'F', 'F', 'G', 'A', 'A', 'B', 'B')
    private val ACCIDENTALS = arrayOf(
        Accidental.NATURAL, Accidental.SHARP, Accidental.NATURAL, Accidental.FLAT,
        Accidental.NATURAL, Accidental.NATURAL, Accidental.SHARP, Accidental.NATURAL,
        Accidental.FLAT, Accidental.NATURAL, Accidental.FLAT, Accidental.NATURAL,
    )

    fun spell(midi: Int): SpelledNote {
        val pc = TuningMath.pitchClass(midi)
        return SpelledNote(LETTERS[pc], ACCIDENTALS[pc], TuningMath.octave(midi))
    }

    fun pitchClassName(pc: Int): String = spell(60 + pc).ascii.dropLast(1)

    fun pitchClassSpoken(pc: Int): String = spell(60 + pc).spoken.substringBeforeLast(' ')

    private val SEMITONE = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** Parses "E2", "Bb3", "F#4" or "C-1" into MIDI, or null. After the letter, "B" is a flat too, so "BB3" from a capitalising keyboard reads. */
    fun parse(text: String): Int? {
        val t = text.trim()
        if (t.length < 2) return null
        val base = SEMITONE[t[0].uppercaseChar()] ?: return null
        var i = 1
        var shift = 0
        while (i < t.length && t[i] in "#bB♯♭") {
            shift += if (t[i] == '#' || t[i] == '♯') 1 else -1
            i++
        }
        val octave = t.substring(i).toIntOrNull() ?: return null
        val midi = (octave + 1) * 12 + base + shift
        return if (midi in 0..127) midi else null
    }

    /** "40,45,50" to "E2 A2 D3". */
    fun formatMidiList(csv: String): String =
        parseMidiCsv(csv).joinToString(" ") { spell(it).ascii }

    fun parseMidiCsv(csv: String): List<Int> =
        csv.split(',').mapNotNull { it.trim().toIntOrNull() }

    /** "E2 A2 D3" to "40,45,50", or null when any token is not a note. */
    fun parseNoteList(text: String): String? {
        val tokens = text.split(' ', ',').filter { it.isNotBlank() }
        val midis = tokens.map { parse(it) ?: return null }
        return midis.joinToString(",")
    }
}
