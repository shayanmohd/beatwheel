package com.mohdshayan.beatwheel.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Slate and neon tangerine. Six tokens per mode, named for the instrument:
 *   Stand     background and surface, also the gaps between strobe segments
 *   Case      drone strip, sheets, fields, menus
 *   Ink       text, primary buttons, the chart's zero line, errors
 *   Graphite  secondary text, axis labels, icons
 *   Rule      dividers, the disc skeleton, slider tracks
 *   Neon      the one accent: strobe segments, the drift trace, slider thumbs, selected outlines
 * Neon never carries body text; it marks strokes and numerals of 24sp and larger.
 */

// Light mode
val LightBackground = Color(0xFFEFF2F4) // Stand
val LightCase = Color(0xFFDFE4E8)
val LightInk = Color(0xFF18212A)
val LightGraphite = Color(0xFF4F5B66)
val LightRule = Color(0xFFB9C2CA)
val LightNeon = Color(0xFFC85A26)

// Dark mode
val DarkBackground = Color(0xFF12171C) // Stand
val DarkCase = Color(0xFF1C232A)
val DarkInk = Color(0xFFE3E8EC)
val DarkGraphite = Color(0xFF9AA6B1)
val DarkRule = Color(0xFF34404A)
val DarkNeon = Color(0xFFE8844A)

@Immutable
data class BeatwheelColors(
    val stand: Color,
    val case: Color,
    val ink: Color,
    val graphite: Color,
    val rule: Color,
    val neon: Color,
)

val LightTokens = BeatwheelColors(LightBackground, LightCase, LightInk, LightGraphite, LightRule, LightNeon)
val DarkTokens = BeatwheelColors(DarkBackground, DarkCase, DarkInk, DarkGraphite, DarkRule, DarkNeon)

val LocalBeatwheelColors = staticCompositionLocalOf { LightTokens }
