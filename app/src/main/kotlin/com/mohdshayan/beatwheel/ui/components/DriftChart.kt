package com.mohdshayan.beatwheel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdshayan.beatwheel.core.drift.DriftPoint
import com.mohdshayan.beatwheel.ui.theme.Atkinson
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Cents against minutes. Zero line in Ink, the trace in Neon, axis labels in Graphite, and a dashed
 * Rule marker wherever the recording paused. Long sessions are reduced to a min and max per pixel
 * column so an hour draws as fast as a minute.
 */
@Composable
fun DriftChart(points: List<DriftPoint>, modifier: Modifier = Modifier, minMinutes: Double = 1.0) {
    val colors = Beatwheel.colors
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val labelStyle = androidx.compose.ui.text.TextStyle(fontFamily = Atkinson, fontSize = 12.sp, color = colors.graphite, fontFeatureSettings = "tnum")
        val left = 44.dp.toPx()
        val bottom = 24.dp.toPx()
        val top = 8.dp.toPx()
        val right = 8.dp.toPx()
        val plotW = size.width - left - right
        val plotH = size.height - top - bottom
        val real = points.filter { !it.gap }
        val spanMin = max(minMinutes, (points.lastOrNull()?.offsetMs ?: 0L) / 60_000.0)
        // Multiples of ten keep the half-scale grid lines on whole cents.
        val maxAbs = max(10f, ceil((real.maxOfOrNull { abs(it.cents) } ?: 0f) / 10f) * 10f)
        fun px(ms: Long) = left + (ms / 60_000.0 / spanMin * plotW).toFloat()
        fun py(c: Float) = top + plotH / 2f - (c / maxAbs) * plotH / 2f

        // Grid and labels.
        for (v in listOf(maxAbs, maxAbs / 2, -maxAbs / 2, -maxAbs)) {
            drawLine(colors.rule, Offset(left, py(v)), Offset(left + plotW, py(v)), strokeWidth = 1f)
            label(measurer, labelStyle, formatCents(v), Offset(0f, py(v)), left - 6.dp.toPx())
        }
        label(measurer, labelStyle, "0", Offset(0f, py(0f)), left - 6.dp.toPx())
        val stepMin = chartStepMinutes(spanMin)
        var m = 0.0
        while (m <= spanMin + 1e-9) {
            val x = px((m * 60_000).toLong())
            val text = minuteLabel(m)
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset((x - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width), size.height - layout.size.height))
            m += stepMin
        }
        drawLine(colors.ink, Offset(left, py(0f)), Offset(left + plotW, py(0f)), strokeWidth = 2f)

        // Gap markers.
        for (g in points.filter { it.gap }) {
            val x = px(g.offsetMs)
            drawLine(colors.graphite, Offset(x, top), Offset(x, top + plotH), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }

        // Trace, bucketed by pixel column.
        if (real.isEmpty()) return@Canvas
        val path = Path()
        var penDown = false
        var bucketX = -1
        var bMin = 0f
        var bMax = 0f
        var lastOffset = -1L
        fun emit() {
            if (bucketX < 0) return
            val x = bucketX.toFloat()
            if (!penDown) {
                path.moveTo(x, py(bMax))
                penDown = true
            } else {
                path.lineTo(x, py(bMax))
            }
            if (bMin != bMax) path.lineTo(x, py(bMin))
        }
        for (p in points) {
            if (p.gap) {
                emit()
                bucketX = -1
                penDown = false
                continue
            }
            // A jump of more than two seconds without readings breaks the line too.
            if (lastOffset >= 0 && p.offsetMs - lastOffset > 2_000) {
                emit()
                bucketX = -1
                penDown = false
            }
            lastOffset = p.offsetMs
            val col = px(p.offsetMs).toInt()
            if (col != bucketX) {
                emit()
                bucketX = col
                bMin = p.cents
                bMax = p.cents
            } else {
                if (p.cents < bMin) bMin = p.cents
                if (p.cents > bMax) bMax = p.cents
            }
        }
        emit()
        drawPath(path, colors.neon, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Minutes between axis labels: at most six steps, on a round value, for any span an imported file can carry. */
fun chartStepMinutes(spanMinutes: Double): Double =
    listOf(0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0).firstOrNull { spanMinutes / it <= 6 }
        ?: (ceil(spanMinutes / 6 / 60.0) * 60.0)

/** "0", "0.25", "0.5", "12": no trailing zeros on the minute axis. */
fun minuteLabel(minutes: Double): String =
    String.format(java.util.Locale.US, "%.2f", minutes).trimEnd('0').trimEnd('.')

private fun formatCents(v: Float): String =
    if (v > 0) "+" + v.toInt() else v.toInt().toString()

private fun androidx.compose.ui.graphics.drawscope.DrawScope.label(
    measurer: TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
    text: String,
    anchor: Offset,
    rightEdge: Float,
) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(rightEdge - layout.size.width, anchor.y - layout.size.height / 2f))
}
