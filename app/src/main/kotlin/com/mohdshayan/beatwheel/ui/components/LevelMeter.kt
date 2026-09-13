package com.mohdshayan.beatwheel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.mohdshayan.beatwheel.ui.theme.Beatwheel

/** Input level from -80 to 0 dBFS, Neon once the gate is open, with an Ink tick where the noise gate opens. */
@Composable
fun LevelMeter(levelDb: Float, gateDb: Float, gateOpen: Boolean, modifier: Modifier = Modifier) {
    val colors = Beatwheel.colors
    Canvas(modifier) {
        fun x(db: Float) = ((db + 80f) / 80f).coerceIn(0f, 1f) * size.width
        val h = size.height
        val barH = h * 0.5f
        val top = (h - barH) / 2f
        drawRoundRect(colors.rule, Offset(0f, top), Size(size.width, barH), CornerRadius(barH / 2))
        drawRoundRect(
            if (gateOpen) colors.neon else colors.graphite,
            Offset(0f, top), Size(x(levelDb), barH), CornerRadius(barH / 2),
        )
        val gx = x(gateDb)
        drawLine(colors.ink, Offset(gx, 0f), Offset(gx, h), strokeWidth = 4f)
    }
}
