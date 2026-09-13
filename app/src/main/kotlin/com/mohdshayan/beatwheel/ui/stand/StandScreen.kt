package com.mohdshayan.beatwheel.ui.stand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.data.prefs.DisplayMode
import com.mohdshayan.beatwheel.ui.components.DiscMode
import com.mohdshayan.beatwheel.ui.components.EmptyState
import com.mohdshayan.beatwheel.ui.components.Glyphs
import com.mohdshayan.beatwheel.ui.components.NeedleGauge
import com.mohdshayan.beatwheel.ui.components.NoteName
import com.mohdshayan.beatwheel.ui.components.StrobeDisc
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.LocalReducedMotion
import com.mohdshayan.beatwheel.ui.theme.NoteLetterStyle
import com.mohdshayan.beatwheel.ui.theme.NumeralStyle
import com.mohdshayan.beatwheel.ui.tuner.MeterState
import com.mohdshayan.beatwheel.ui.tuner.TunerViewModel
import com.mohdshayan.beatwheel.ui.tuner.formatElapsed
import com.mohdshayan.beatwheel.ui.tuner.rememberMicGate
import com.mohdshayan.beatwheel.ui.components.findActivity
import com.mohdshayan.beatwheel.ui.components.openAppSettings
import kotlinx.coroutines.delay
import java.util.Locale

/** Full screen, screen kept on: the disc and the note as large as the display allows. A tap shows the controls for a few seconds. */
@Composable
fun StandScreen(onClose: () -> Unit, vm: TunerViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val readout by vm.readout.collectAsStateWithLifecycle()
    val (meter, requestMic, canAsk) = rememberMicGate(ui, vm)
    val reduced = LocalReducedMotion.current
    val colors = Beatwheel.colors
    val context = LocalContext.current
    val view = LocalView.current
    var taps by remember { mutableIntStateOf(1) }
    var controlsVisible by remember { mutableIntStateOf(1) }

    LifecycleStartEffect(Unit) {
        vm.engine.acquire()
        onStopOrDispose { vm.engine.release() }
    }
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(taps) {
        controlsVisible = 1
        delay(3_000)
        controlsVisible = 0
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.stand)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { taps++ },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            val fullWidth = maxWidth
            val landscape = maxWidth > maxHeight
            val e = readout.estimate
            val transposition = ui.tuning?.profile?.transpositionSemitones ?: 0
            val discSize = if (landscape) minOf(maxHeight, maxWidth * 0.55f) else minOf(maxWidth, maxHeight * 0.58f)

            val display: @Composable () -> Unit = {
                Box(Modifier.size(discSize), contentAlignment = Alignment.Center) {
                    when (meter) {
                        MeterState.RATIONALE, MeterState.DENIED -> {
                            // Same rule as Tune: ask while the system still shows its prompt, otherwise send the player to settings.
                            EmptyState(
                                title = "Beatwheel needs the microphone to measure pitch.",
                                body = null,
                                actionLabel = if (canAsk) "Allow microphone" else "Open settings",
                                onAction = if (canAsk) requestMic else ({ context.openAppSettings() }),
                            )
                        }
                        MeterState.BUSY -> EmptyState(
                            title = "Another app is using the microphone.",
                            body = null,
                            actionLabel = "Try again",
                            onAction = vm::retryMic,
                        )
                        else -> {
                            val mode = when {
                                meter == MeterState.LOADING -> DiscMode.SKELETON
                                e.hasPitch -> DiscMode.LIVE
                                else -> DiscMode.IDLE
                            }
                            if (ui.displayMode == DisplayMode.STROBE) {
                                StrobeDisc(vm.readings, mode, rotate = !reduced || ui.rotateReduced, firstRunReveal = false, modifier = Modifier.fillMaxSize())
                            } else {
                                NeedleGauge(if (e.hasPitch) e.cents.toFloat() else null, meter == MeterState.LOADING, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            val note: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (e.hasPitch) {
                        NoteName(
                            NoteNames.spell(Transposition.written(e.midi, transposition)),
                            NoteLetterStyle.copy(fontSize = 180.sp, lineHeight = 180.sp),
                        )
                        val beats = readout.beatsPerSecond
                        // The number is read from the stand, so it alone is large; the unit sits under it and never wraps the number.
                        Text(
                            if (beats != null) String.format(Locale.US, "%.1f", kotlin.math.abs(beats))
                            else com.mohdshayan.beatwheel.core.music.CentsText.signed(e.cents),
                            style = NumeralStyle.copy(fontSize = 56.sp, lineHeight = 60.sp),
                            color = colors.ink,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            if (beats != null) "beats per second, " + if (beats >= 0) "sharp" else "flat" else "cents",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.graphite,
                            textAlign = TextAlign.Center,
                        )
                        Text(String.format(Locale.US, "%.1f Hz", e.hz), style = NumeralStyle.copy(fontSize = 20.sp), color = colors.graphite, textAlign = TextAlign.Center)
                    } else if (meter == MeterState.EMPTY) {
                        Text("Play a note", style = MaterialTheme.typography.headlineMedium, color = colors.graphite, textAlign = TextAlign.Center)
                    }
                }
            }
            if (landscape) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    display()
                    Box(Modifier.width(fullWidth - discSize - 32.dp), contentAlignment = Alignment.Center) { note() }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The note is measured first and the disc takes what height is left, so a 16:9 phone never clips the cents.
                    Box(Modifier.weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
                        display()
                    }
                    Spacer(Modifier.height(12.dp))
                    note()
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible == 1,
            enter = fadeIn(if (reduced) snap() else tween(150)),
            exit = fadeOut(if (reduced) snap() else tween(150)),
            modifier = Modifier.fillMaxWidth().safeDrawingPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Leave stand mode", tint = colors.ink)
                }
                Text(ui.tuning?.profile?.name ?: "", style = MaterialTheme.typography.titleLarge, color = colors.ink, modifier = Modifier.weight(1f))
                if (ui.recorder.active) {
                    Text("Recording " + formatElapsed(ui.recorder.elapsedMs), style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    Spacer(Modifier.width(8.dp))
                }
                val needle = ui.displayMode == DisplayMode.NEEDLE
                IconButton(onClick = {
                    vm.setDisplayMode(if (needle) DisplayMode.STROBE else DisplayMode.NEEDLE)
                    taps++
                }) {
                    Icon(
                        if (needle) Glyphs.Strobe else Icons.Outlined.Speed,
                        contentDescription = if (needle) "Show strobe disc" else "Show needle",
                        tint = colors.graphite,
                    )
                }
            }
        }
    }
}

