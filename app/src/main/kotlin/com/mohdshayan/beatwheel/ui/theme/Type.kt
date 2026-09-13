package com.mohdshayan.beatwheel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mohdshayan.beatwheel.R

/*
 * Barlow Semi Condensed carries the note letter and the titles: narrow, engineered, legible at a
 * distance. Atkinson Hyperlegible Next carries everything read up close, and its bold tabular figures
 * carry cents and hertz so the numbers do not jitter sideways as they change.
 * Both are bundled under res/font with their OFL licences in docs/.
 */
val Barlow = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
)

@OptIn(ExperimentalTextApi::class)
private fun atkinson(weight: Int) = Font(
    R.font.atkinson_hyperlegible_next,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Atkinson = FontFamily(atkinson(400), atkinson(500), atkinson(700))

/** Cents, hertz and every other changing number. */
val NumeralStyle = TextStyle(
    fontFamily = Atkinson,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)

/** The note letter: 112sp on Tune, 180sp in Stand. */
val NoteLetterStyle = TextStyle(
    fontFamily = Barlow,
    fontWeight = FontWeight.SemiBold,
    fontSize = 112.sp,
    lineHeight = 112.sp,
    fontFeatureSettings = "tnum",
)

val AppTypography = Typography(
    displaySmall = NumeralStyle.copy(fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
