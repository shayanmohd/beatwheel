package com.mohdshayan.beatwheel.core.music

data class TemperamentPreset(val key: String, val name: String, val offsetsCents: DoubleArray)

/**
 * Temperament presets as cents from equal temperament, C to B, with C as tonic. The tables are the
 * published values rounded to a tenth of a cent. [offsetsFor] rotates them to any tonic and, when
 * asked, shifts the whole set so A reads zero and the reference pitch keeps its meaning.
 */
object Temperaments {
    const val EQUAL = "equal"
    const val CUSTOM_PREFIX = "custom:"

    val presets: List<TemperamentPreset> = listOf(
        TemperamentPreset(EQUAL, "Equal", DoubleArray(12)),
        TemperamentPreset(
            "just", "Just",
            doubleArrayOf(0.0, 11.7, 3.9, 15.6, -13.7, -2.0, -9.8, 2.0, 13.7, -15.6, 17.6, -11.7),
        ),
        TemperamentPreset(
            "pythagorean", "Pythagorean",
            doubleArrayOf(0.0, 13.7, 3.9, -5.9, 7.8, -2.0, 11.7, 2.0, 15.6, 5.9, -3.9, 9.8),
        ),
        TemperamentPreset(
            "meantone_qc", "Quarter-comma meantone",
            doubleArrayOf(0.0, -24.0, -6.8, 10.3, -13.7, 3.4, -20.5, -3.4, -27.4, -10.3, 6.8, -17.1),
        ),
        TemperamentPreset(
            "werckmeister3", "Werckmeister III",
            doubleArrayOf(0.0, -9.8, -7.8, -5.9, -9.8, -2.0, -11.7, -3.9, -7.8, -11.7, -3.9, -7.8),
        ),
        TemperamentPreset(
            "kirnberger3", "Kirnberger III",
            doubleArrayOf(0.0, -9.8, -6.8, -5.9, -13.7, -2.0, -9.8, -3.4, -7.8, -10.3, -3.9, -11.7),
        ),
    )

    fun preset(key: String): TemperamentPreset? = presets.firstOrNull { it.key == key }

    fun customKey(id: Long): String = CUSTOM_PREFIX + id

    fun customId(key: String): Long? =
        if (key.startsWith(CUSTOM_PREFIX)) key.removePrefix(CUSTOM_PREFIX).toLongOrNull() else null

    /**
     * Offsets by absolute pitch class. [base] is defined with C as tonic; the pattern moves so that
     * [tonicPitchClass] takes C's place. With [keepReferenceA] every value shifts so A is exactly 0.
     */
    fun offsetsFor(base: DoubleArray, tonicPitchClass: Int, keepReferenceA: Boolean): DoubleArray {
        require(base.size == 12)
        val tonic = ((tonicPitchClass % 12) + 12) % 12
        val out = DoubleArray(12) { pc -> base[(pc - tonic + 12) % 12] }
        if (keepReferenceA) {
            val a = out[9]
            for (i in 0 until 12) out[i] -= a
        }
        return out
    }

    /** Parses twelve comma-separated cents values, or null when malformed or out of range. */
    fun parseOffsets(csv: String): DoubleArray? {
        val parts = csv.split(',').map { it.trim().toDoubleOrNull() ?: return null }
        // toDoubleOrNull accepts "NaN" and "Infinity"; neither is a pitch offset.
        if (parts.size != 12 || parts.any { !it.isFinite() || it < -50.0 || it > 50.0 }) return null
        return parts.toDoubleArray()
    }

    fun formatOffsets(offsets: DoubleArray): String =
        offsets.joinToString(",") { String.format(java.util.Locale.US, "%.1f", it) }
}
