package com.mohdshayan.beatwheel.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.LocalReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A needle over a plus or minus 50 cent scale. The needle is the Neon mark; the scale is Rule and Graphite. */
@Composable
fun NeedleGauge(cents: Float?, skeleton: Boolean, modifier: Modifier = Modifier) {
    val colors = Beatwheel.colors
    val reduced = LocalReducedMotion.current
    val target = (cents ?: 0f).coerceIn(-50f, 50f)
    val animated by animateFloatAsState(target, if (reduced) snap() else tween(90), label = "needle")

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pivot = Offset(w / 2f, h * 0.62f)
        val r = min(w * 0.46f, h * 0.56f)
        val span = 60.0
        for (tick in -50..50 step 5) {
            val a = Math.toRadians(-90.0 + tick / 50.0 * span)
            val major = tick % 10 == 0
            val len = when {
                tick == 0 -> r * 0.16f
                major -> r * 0.1f
                else -> r * 0.05f
            }
            val outer = Offset(pivot.x + r * cos(a).toFloat(), pivot.y + r * sin(a).toFloat())
            val inner = Offset(pivot.x + (r - len) * cos(a).toFloat(), pivot.y + (r - len) * sin(a).toFloat())
            drawLine(
                if (tick == 0) colors.ink else if (major) colors.graphite else colors.rule,
                inner, outer,
                strokeWidth = if (tick == 0) 5f else if (major) 3f else 2f,
                cap = StrokeCap.Round,
            )
        }
        if (skeleton || cents == null) {
            drawCircle(colors.rule, radius = 7f, center = pivot)
            return@Canvas
        }
        val a = -PI / 2 + animated / 50.0 * span * PI / 180.0
        val tip = Offset(pivot.x + r * 0.94f * cos(a).toFloat(), pivot.y + r * 0.94f * sin(a).toFloat())
        drawLine(colors.neon, pivot, tip, strokeWidth = 8f, cap = StrokeCap.Round)
        drawCircle(colors.neon, radius = 12f, center = pivot)
    }
}
