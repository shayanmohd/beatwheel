package com.mohdshayan.beatwheel.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun scheme(t: BeatwheelColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.ink,
        onPrimary = t.stand,
        primaryContainer = t.case,
        onPrimaryContainer = t.ink,
        inversePrimary = t.neon,
        secondary = t.neon,
        onSecondary = t.stand,
        secondaryContainer = t.case,
        onSecondaryContainer = t.ink,
        tertiary = t.neon,
        onTertiary = t.stand,
        tertiaryContainer = t.case,
        onTertiaryContainer = t.ink,
        background = t.stand,
        onBackground = t.ink,
        surface = t.stand,
        onSurface = t.ink,
        surfaceVariant = t.case,
        onSurfaceVariant = t.graphite,
        surfaceTint = t.stand,
        inverseSurface = t.ink,
        inverseOnSurface = t.stand,
        error = t.ink,
        onError = t.stand,
        errorContainer = t.case,
        onErrorContainer = t.ink,
        outline = t.rule,
        outlineVariant = t.rule,
        scrim = t.ink,
        surfaceBright = t.stand,
        surfaceDim = t.case,
        surfaceContainerLowest = t.stand,
        surfaceContainerLow = t.stand,
        surfaceContainer = t.case,
        surfaceContainerHigh = t.case,
        surfaceContainerHighest = t.case,
    )
}

private val LightColors = scheme(LightTokens, dark = false)
private val DarkColors = scheme(DarkTokens, dark = true)

/**
 * The app theme. Dynamic colour is off: the tangerine strobe is the product's identity. System bar
 * icons follow the mode, and LocalReducedMotion is provided for every animation to consult.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides rememberReducedMotion(),
        LocalBeatwheelColors provides if (darkTheme) DarkTokens else LightTokens,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object Beatwheel {
    val colors: BeatwheelColors
        @Composable @ReadOnlyComposable get() = LocalBeatwheelColors.current
}
