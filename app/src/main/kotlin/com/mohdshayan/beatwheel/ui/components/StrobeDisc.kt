package com.mohdshayan.beatwheel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.mohdshayan.beatwheel.audio.Reading
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.min

enum class DiscMode { SKELETON, IDLE, LIVE }

/**
 * The disc that stops. Four rings of tangerine segments on the Stand ground turn at the measured
 * difference frequency: clockwise when sharp, anticlockwise when flat, still at pitch. Angle is
 * integrated per frame from the live rate, never from an animate call. Near a drone partial it turns
 * at the beat rate instead. With [rotate] false (reduced motion) the rings stay still and a Neon rim
 * arc shows the offset, so the reading survives animations being off.
 */
@Composable
fun StrobeDisc(
    readings: StateFlow<Reading>,
    mode: DiscMode,
    rotate: Boolean,
    firstRunReveal: Boolean,
    modifier: Modifier = Modifier,
    onRevealed: () -> Unit = {},
) {
    val colors = Beatwheel.colors
    val frame = remember { mutableLongStateOf(0L) }
    val angle = remember { FloatArray(1) }
    val lastNanos = remember { LongArray(1) }
    val reveal = remember { Animatable(if (firstRunReveal) 0f else 1f) }

    LaunchedEffect(firstRunReveal, mode) {
        if (firstRunReveal && mode == DiscMode.LIVE || firstRunReveal && mode == DiscMode.IDLE) {
            reveal.animateTo(1f, tween(400))
            onRevealed()
        } else if (!firstRunReveal) {
            reveal.snapTo(1f)
        }
    }
    LaunchedEffect(mode, rotate) {
        if (mode == DiscMode.LIVE && rotate) {
            while (true) withFrameNanos { frame.longValue = it }
        } else if (mode == DiscMode.LIVE) {
            // Still disc: nothing turns, but the rim arc must follow the reading, at the readout's ten updates a second.
            while (true) {
                frame.longValue = System.nanoTime()
                kotlinx.coroutines.delay(100)
            }
        }
    }

    Spacer(
        modifier.drawWithCache {
            val radius = min(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val rimGap = radius * 0.05f
            val outer = radius - rimGap * 1.4f
            val ringWidth = outer * 0.1f
            val ringGap = outer * 0.025f
            // Each ring halves the one outside it, like the octave rings of a bench strobe, so every edge lines up.
            val segments = intArrayOf(64, 32, 16, 8)
            // Each ring is one filled path of annular sectors, built once per size. Sectors are plain
            // polygons (a point every 1.5 degrees along each edge, straight lines only): on the software
            // renderer, arc-based contours this large lost every other sector in landscape Stand mode.
            val ringPaths = Array(4) { ring ->
                val mid = outer - ringWidth / 2f - ring * (ringWidth + ringGap)
                val ro = mid + ringWidth / 2f
                val ri = mid - ringWidth / 2f
                val count = segments[ring]
                val sweep = 360.0 / count
                val steps = max(2, ceil(sweep / 2.0 / 1.5).toInt())
                Path().apply {
                    for (seg in 0 until count) {
                        val a0 = Math.toRadians(seg * sweep - 90.0)
                        val a1 = Math.toRadians(seg * sweep - 90.0 + sweep / 2.0)
                        for (i in 0..steps) {
                            val t = a0 + (a1 - a0) * i / steps
                            val x = c.x + ro * cos(t).toFloat()
                            val y = c.y + ro * sin(t).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        for (i in steps downTo 0) {
                            val t = a0 + (a1 - a0) * i / steps
                            lineTo(c.x + ri * cos(t).toFloat(), c.y + ri * sin(t).toFloat())
                        }
                        close()
                    }
                }
            }
            val ringEdges = Array(4) { ring -> outer - ringWidth / 2f - ring * (ringWidth + ringGap) }
            val rimRadius = radius - rimGap * 0.5f

            onDrawBehind {
                frame.longValue
                val reading = readings.value
                val e = reading.estimate
                val live = mode == DiscMode.LIVE && e.hasPitch
                val rateHz = when {
                    !live -> 0.0
                    reading.beatsPerSecond != null -> reading.beatsPerSecond
                    e.strobeLocked -> e.phaseRateHz
                    else -> 0.0
                }.coerceIn(-30.0, 30.0)
                val now = System.nanoTime()
                if (rotate && live && lastNanos[0] != 0L) {
                    val dt = ((now - lastNanos[0]) / 1e9).coerceAtMost(0.1)
                    angle[0] = ((angle[0] + 2 * PI * rateHz * dt) % (2 * PI * 1920)).toFloat()
                }
                lastNanos[0] = now

                for (ring in 0 until 4) {
                    val ringAlpha = ((reveal.value * 4f) - (3 - ring)).coerceIn(0f, 1f)
                    if (ringAlpha <= 0f) continue
                    if (mode == DiscMode.SKELETON) {
                        val mid = ringEdges[ring]
                        drawCircle(colors.rule, radius = mid + ringWidth / 2f, center = c, style = Stroke(1.5f), alpha = ringAlpha)
                        drawCircle(colors.rule, radius = mid - ringWidth / 2f, center = c, style = Stroke(1.5f), alpha = ringAlpha)
                        continue
                    }
                    // One rigid disc: the whole wheel turns one inner-ring period per beat, so the edges stay aligned
                    // and the outer rings smear into a blur when the note is far off, as on a bench strobe.
                    val rotation = if (rotate && live) (angle[0] / segments[3] * 180f / PI.toFloat()) else 0f
                    // Idle is the same Neon disc standing still at half strength, so it reads as an instrument, not a skeleton.
                    val strength = if (live) 1f else 0.5f
                    rotate(rotation, pivot = c) {
                        drawPath(ringPaths[ring], color = colors.neon, alpha = ringAlpha * strength)
                    }
                }

                // The rim: a Rule circle always, and when the disc cannot turn, a Neon arc for the offset.
                drawCircle(colors.rule, radius = rimRadius, center = c, style = Stroke(2f), alpha = reveal.value)
                if (live && (!rotate || !e.strobeLocked && reading.beatsPerSecond == null)) {
                    val cents = e.cents.toFloat().coerceIn(-50f, 50f)
                    val sweep = if (abs(cents) < 0.5f) 2f else cents * 3f
                    drawArc(
                        color = colors.neon,
                        startAngle = -90f - if (abs(cents) < 0.5f) 1f else 0f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - rimRadius, c.y - rimRadius),
                        size = Size(rimRadius * 2, rimRadius * 2),
                        style = Stroke(width = rimGap * 0.9f),
                    )
                }
            }
        },
    )
}
