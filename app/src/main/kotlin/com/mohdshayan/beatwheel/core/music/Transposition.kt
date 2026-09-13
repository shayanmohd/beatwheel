package com.mohdshayan.beatwheel.core.music

data class TranspositionPreset(val semitones: Int, val label: String)

/**
 * Written and sounding pitch. [semitones] is how far the sounding note lies from the written one:
 * a B flat clarinet's written C sounds a whole tone lower, so its value is -2.
 */
object Transposition {
    val presets: List<TranspositionPreset> = listOf(
        TranspositionPreset(0, "Concert"),
        TranspositionPreset(-2, "B flat"),
        TranspositionPreset(-14, "B flat, octave lower"),
        TranspositionPreset(-9, "E flat"),
        TranspositionPreset(-21, "E flat, octave lower"),
        TranspositionPreset(-7, "F"),
        TranspositionPreset(-5, "G"),
    )

    fun sounding(writtenMidi: Int, semitones: Int): Int = writtenMidi + semitones

    fun written(soundingMidi: Int, semitones: Int): Int = soundingMidi - semitones

    fun label(semitones: Int): String =
        presets.firstOrNull { it.semitones == semitones }?.label ?: "$semitones semitones"
}
