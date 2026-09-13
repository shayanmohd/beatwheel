package com.mohdshayan.beatwheel.core.music

import java.util.Locale

/** Signed numbers as a musician reads them: "+3.4", "-12.0", and plain "0.0" rather than "-0.0" for a value that rounds to zero. */
object CentsText {
    fun signed(value: Double, decimals: Int = 1): String {
        val text = String.format(Locale.US, "%+.${decimals}f", value)
        return if (text.drop(1).all { it == '0' || it == '.' }) text.drop(1) else text
    }

    fun signed(value: Float, decimals: Int = 1): String = signed(value.toDouble(), decimals)
}
