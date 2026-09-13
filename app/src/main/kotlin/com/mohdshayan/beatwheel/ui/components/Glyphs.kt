package com.mohdshayan.beatwheel.ui.components

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Drawn glyphs the bundled fonts lack: accidentals, the tuning fork and the strobe wheel. Tinted by the caller. */
object Glyphs {
    val Sharp: ImageVector by lazy {
        ImageVector.Builder("sharp", 20.dp, 30.dp, 20f, 30f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5.6f, 3f); lineTo(7.6f, 2.4f); lineTo(7.6f, 28f); lineTo(5.6f, 28.6f); close()
                moveTo(12.4f, 1.4f); lineTo(14.4f, 0.8f); lineTo(14.4f, 26.4f); lineTo(12.4f, 27f); close()
                moveTo(2f, 11.6f); lineTo(18f, 7.6f); lineTo(18f, 11.2f); lineTo(2f, 15.2f); close()
                moveTo(2f, 20.4f); lineTo(18f, 16.4f); lineTo(18f, 20f); lineTo(2f, 24f); close()
            }
        }.build()
    }

    val Flat: ImageVector by lazy {
        ImageVector.Builder("flat", 16.dp, 30.dp, 16f, 30f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(2.6f, 1f); lineTo(4.8f, 1f); lineTo(4.8f, 16.6f)
                curveTo(8.2f, 13.4f, 13.8f, 13.2f, 13.8f, 17.6f)
                curveTo(13.8f, 21.8f, 9.4f, 25.4f, 2.6f, 29f)
                close()
                moveTo(4.8f, 19.6f)
                curveTo(7.2f, 17.2f, 11f, 16.8f, 11f, 18.9f)
                curveTo(11f, 21.4f, 8.2f, 23.9f, 4.8f, 26f)
                close()
            }
        }.build()
    }

    val TuningFork: ImageVector by lazy {
        ImageVector.Builder("tuning_fork", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6.5f, 2f); lineTo(8.5f, 2f); lineTo(8.5f, 9f)
                arcTo(3.5f, 3.5f, 0f, false, false, 15.5f, 9f)
                lineTo(15.5f, 2f); lineTo(17.5f, 2f); lineTo(17.5f, 9f)
                arcTo(5.5f, 5.5f, 0f, false, true, 13f, 14.4f)
                lineTo(13f, 21f)
                arcTo(1f, 1f, 0f, false, true, 11f, 21f)
                lineTo(11f, 14.4f)
                arcTo(5.5f, 5.5f, 0f, false, true, 6.5f, 9f)
                close()
            }
        }.build()
    }

    /** A strobe disc: a thin rim, six segments on one ring and a hub, so it never reads as a spinner. */
    val Strobe: ImageVector by lazy {
        ImageVector.Builder("strobe", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(2f, 12f); arcTo(10f, 10f, 0f, true, true, 22f, 12f); arcTo(10f, 10f, 0f, true, true, 2f, 12f); close()
                moveTo(3.6f, 12f); arcTo(8.4f, 8.4f, 0f, true, true, 20.4f, 12f); arcTo(8.4f, 8.4f, 0f, true, true, 3.6f, 12f); close()
            }
            path(fill = SolidColor(Color.Black)) {
                val cx = 12f
                val cy = 12f
                for (i in 0 until 6) {
                    val a0 = Math.toRadians(i * 60.0 - 90).toFloat()
                    val a1 = Math.toRadians(i * 60.0 - 90 + 30).toFloat()
                    val ro = 7f
                    val ri = 3.6f
                    moveTo(cx + ri * kotlin.math.cos(a0), cy + ri * kotlin.math.sin(a0))
                    lineTo(cx + ro * kotlin.math.cos(a0), cy + ro * kotlin.math.sin(a0))
                    lineTo(cx + ro * kotlin.math.cos(a1), cy + ro * kotlin.math.sin(a1))
                    lineTo(cx + ri * kotlin.math.cos(a1), cy + ri * kotlin.math.sin(a1))
                    close()
                }
                moveTo(10.6f, 12f); arcTo(1.4f, 1.4f, 0f, true, true, 13.4f, 12f); arcTo(1.4f, 1.4f, 0f, true, true, 10.6f, 12f); close()
            }
        }.build()
    }
}
