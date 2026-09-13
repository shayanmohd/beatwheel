package com.mohdshayan.beatwheel.ui.tuner

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.beatwheel.audio.ListenStatus
import com.mohdshayan.beatwheel.audio.Reading
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.core.pitch.NoiseGate
import com.mohdshayan.beatwheel.data.prefs.DisplayMode
import com.mohdshayan.beatwheel.ui.components.DiscMode
import com.mohdshayan.beatwheel.ui.components.EmptyState
import com.mohdshayan.beatwheel.ui.components.Glyphs
import com.mohdshayan.beatwheel.ui.components.LevelMeter
import com.mohdshayan.beatwheel.ui.components.NeedleGauge
import com.mohdshayan.beatwheel.ui.components.NoteName
import com.mohdshayan.beatwheel.ui.components.PrimaryButton
import com.mohdshayan.beatwheel.ui.components.SecondaryButton
import com.mohdshayan.beatwheel.ui.components.StrobeDisc
import com.mohdshayan.beatwheel.ui.components.canAskForMic
import com.mohdshayan.beatwheel.ui.components.hasMicPermission
import com.mohdshayan.beatwheel.ui.components.openAppSettings
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.LocalReducedMotion
import com.mohdshayan.beatwheel.ui.theme.NoteLetterStyle
import com.mohdshayan.beatwheel.ui.theme.NumeralStyle
import com.mohdshayan.beatwheel.ui.theme.RadiusSm
import java.util.Locale
import kotlin.math.abs

/** What the display area shows, decided from permission, microphone status and the live reading. */
enum class MeterState { RATIONALE, DENIED, BUSY, LOADING, EMPTY, LIVE }

@Composable
fun rememberMicGate(ui: TunerUi, vm: TunerViewModel): Triple<MeterState, () -> Unit, Boolean> {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasMicPermission()) }
    // Whether the system will still show its prompt. Held as state so a second refusal turns the button into "Open settings" at once.
    var canAsk by remember { mutableStateOf(context.canAskForMic()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        canAsk = context.canAskForMic()
        vm.markMicAsked()
        if (ok) vm.retryMic()
    }
    LifecycleResumeEffect(Unit) {
        val now = context.hasMicPermission()
        if (now && (!granted || vm.engine.status.value == ListenStatus.NO_PERMISSION)) vm.retryMic()
        granted = now
        canAsk = context.canAskForMic()
        onPauseOrDispose { }
    }
    val state = when {
        !granted && !ui.micAsked -> MeterState.RATIONALE
        !granted -> MeterState.DENIED
        ui.status == ListenStatus.BUSY -> MeterState.BUSY
        ui.status == ListenStatus.NO_PERMISSION -> MeterState.DENIED
        ui.status != ListenStatus.LISTENING -> MeterState.LOADING
        else -> MeterState.EMPTY
    }
    return Triple(state, { launcher.launch(Manifest.permission.RECORD_AUDIO) }, state == MeterState.RATIONALE || canAsk)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(
    onOpenSettings: () -> Unit,
    onOpenStand: () -> Unit,
    onManageProfiles: () -> Unit,
    onSessionSaved: (Long) -> Unit,
    openRecorder: Boolean = false,
    vm: TunerViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val readout by vm.readout.collectAsStateWithLifecycle()
    val (meter, requestMic, canAskMic) = rememberMicGate(ui, vm)
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var recorderHandled by rememberSaveable { mutableStateOf(false) }
    // Held here rather than in the strip: rotating between the one-column and two-pane layouts recomposes the strip elsewhere.
    var droneExpanded by rememberSaveable { mutableStateOf(false) }
    // Arriving from the Sessions empty state starts a recording once the microphone is live.
    androidx.compose.runtime.LaunchedEffect(openRecorder, ui.tuning != null, meter == MeterState.EMPTY) {
        if (openRecorder && !recorderHandled && ui.tuning != null && meter == MeterState.EMPTY) {
            recorderHandled = true
            vm.startRecording()
            sheetOpen = true
        }
    }

    LifecycleStartEffect(Unit) {
        vm.engine.acquire()
        onStopOrDispose { vm.engine.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ProfilePicker(ui, vm::selectProfile, onManageProfiles) },
                actions = {
                    val needle = ui.displayMode == DisplayMode.NEEDLE
                    IconButton(onClick = { vm.setDisplayMode(if (needle) DisplayMode.STROBE else DisplayMode.NEEDLE) }) {
                        Icon(
                            if (needle) Glyphs.Strobe else Icons.Outlined.Speed,
                            contentDescription = if (needle) "Show strobe disc" else "Show needle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenStand) {
                        Icon(Icons.Outlined.Fullscreen, contentDescription = "Stand mode", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val wide = LocalConfiguration.current.screenWidthDp >= 600
        val onRecord = {
            if (!ui.recorder.active) vm.startRecording()
            sheetOpen = true
        }
        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    val size = minOf(maxWidth, maxHeight - 96.dp).coerceAtLeast(160.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MeterDisplay(ui, vm, readout, meter, requestMic, canAskMic, size)
                        Spacer(Modifier.height(12.dp))
                        Readout(ui, readout, meter)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Controls(ui, vm, readout, meter, onRecord, droneExpanded) { droneExpanded = it }
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val size = minOf(maxWidth - 48.dp, maxHeight - 300.dp).coerceIn(220.dp, 360.dp)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(4.dp))
                    MeterDisplay(ui, vm, readout, meter, requestMic, canAskMic, size)
                    Spacer(Modifier.height(8.dp))
                    Readout(ui, readout, meter)
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Controls(ui, vm, readout, meter, onRecord, droneExpanded) { droneExpanded = it }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (sheetOpen && ui.recorder.active) {
        RecordDriftSheet(
            state = ui.recorder,
            onDismiss = { sheetOpen = false },
            onSave = { name ->
                sheetOpen = false
                vm.stopAndSave(name, onSessionSaved)
            },
            onDiscard = {
                sheetOpen = false
                vm.discardRecording()
            },
        )
    }
}

@Composable
private fun ProfilePicker(ui: TunerUi, onSelect: (Long) -> Unit, onManage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val tuning = ui.tuning
    Box {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(enabled = tuning != null) { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.widthIn(max = 220.dp)) {
                Text(
                    tuning?.profile?.name ?: "Beatwheel",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tuning != null) {
                    Text(
                        "A " + String.format(Locale.US, "%.1f", tuning.profile.referenceAHz) + ", " + tuning.temperamentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choose profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ui.profiles.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        open = false
                        onSelect(p.id)
                    },
                    trailingIcon = {
                        if (p.id == tuning?.profile?.id) Icon(Icons.Outlined.Check, contentDescription = "Selected")
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Manage profiles", style = MaterialTheme.typography.labelLarge) },
                onClick = {
                    open = false
                    onManage()
                },
            )
        }
    }
}

@Composable
private fun MeterDisplay(
    ui: TunerUi,
    vm: TunerViewModel,
    readout: Reading,
    meter: MeterState,
    requestMic: () -> Unit,
    canAsk: Boolean,
    size: Dp,
) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when (meter) {
            MeterState.RATIONALE -> EmptyState(
                icon = Icons.Outlined.Mic,
                title = "Beatwheel listens to your instrument to measure pitch.",
                body = "No audio is saved.",
                actionLabel = "Allow microphone",
                onAction = requestMic,
                modifier = Modifier.fillMaxSize(),
            )
            MeterState.DENIED -> {
                EmptyState(
                    icon = Icons.Outlined.MicOff,
                    title = "Beatwheel needs the microphone to measure pitch.",
                    body = "The drone, profiles and sessions still work.",
                    actionLabel = if (canAsk) "Allow microphone" else "Open settings",
                    onAction = if (canAsk) requestMic else ({ context.openAppSettings() }),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MeterState.BUSY -> EmptyState(
                icon = Icons.Outlined.MicOff,
                title = "Another app is using the microphone.",
                body = "Close it, then try again.",
                actionLabel = "Try again",
                onAction = vm::retryMic,
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                val live = readout.estimate.hasPitch
                val discMode = when {
                    meter == MeterState.LOADING -> DiscMode.SKELETON
                    live -> DiscMode.LIVE
                    else -> DiscMode.IDLE
                }
                Crossfade(ui.displayMode, animationSpec = if (reduced) snap() else tween(150), label = "display") { mode ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (mode == DisplayMode.STROBE) {
                            StrobeDisc(
                                readings = vm.readings,
                                mode = discMode,
                                rotate = !reduced || ui.rotateReduced,
                                firstRunReveal = !ui.firstRunDiscShown && !reduced,
                                onRevealed = vm::markFirstRunDiscShown,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (reduced && !ui.firstRunDiscShown && discMode != DiscMode.SKELETON) {
                                androidx.compose.runtime.LaunchedEffect(Unit) { vm.markFirstRunDiscShown() }
                            }
                            CenterNote(ui, readout, meter, size, Modifier.align(Alignment.Center))
                        } else {
                            NeedleGauge(
                                cents = if (live) readout.estimate.cents.toFloat() else null,
                                skeleton = meter == MeterState.LOADING,
                                modifier = Modifier.fillMaxSize(),
                            )
                            CenterNote(ui, readout, meter, size, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterNote(ui: TunerUi, readout: Reading, meter: MeterState, discSize: Dp, modifier: Modifier) {
    val e = readout.estimate
    val transposition = ui.tuning?.profile?.transpositionSemitones ?: 0
    // The letter and the prompt sit inside the innermost ring, so they scale with the disc on short screens.
    // Sized in dp through the font scale: the letter belongs to the disc graphic, so a large system font
    // must not push it over the inner ring (the readout below the disc does scale).
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val letterSp = minOf(112f, discSize.value * 0.34f) / fontScale
    if (e.hasPitch) {
        val written = Transposition.written(e.midi, transposition)
        NoteName(NoteNames.spell(written), NoteLetterStyle.copy(fontSize = letterSp.sp, lineHeight = letterSp.sp), modifier)
    } else if (meter == MeterState.EMPTY) {
        val promptSp = minOf(22f, discSize.value * 0.075f) / fontScale
        Text(
            "Play a note",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = promptSp.sp, lineHeight = (promptSp * 1.25f).sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.widthIn(max = discSize * 0.4f),
        )
    }
}

@Composable
private fun Readout(ui: TunerUi, readout: Reading, meter: MeterState) {
    val e = readout.estimate
    val transposition = ui.tuning?.profile?.transpositionSemitones ?: 0
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!e.hasPitch || meter != MeterState.EMPTY) {
            Text(
                if (meter == MeterState.EMPTY) "Cents and hertz appear when a note sounds" else " ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
            return@Column
        }
        val beats = readout.beatsPerSecond
        Row(verticalAlignment = Alignment.Bottom) {
            if (beats != null) {
                Text(
                    String.format(Locale.US, "%.1f", abs(beats)),
                    style = NumeralStyle.copy(fontSize = 36.sp, lineHeight = 40.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    " beats per second, " + if (beats >= 0) "sharp" else "flat",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            } else {
                Text(
                    com.mohdshayan.beatwheel.core.music.CentsText.signed(e.cents),
                    style = NumeralStyle.copy(fontSize = 36.sp, lineHeight = 40.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    " cents",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format(Locale.US, "%.1f Hz", e.hz),
                style = NumeralStyle.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (transposition != 0) {
                Text(
                    "   sounds ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NoteName(
                    NoteNames.spell(e.midi),
                    MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    ui: TunerUi,
    vm: TunerViewModel,
    readout: Reading,
    meter: MeterState,
    onRecord: () -> Unit,
    droneExpanded: Boolean,
    onDroneExpandedChange: (Boolean) -> Unit,
) {
    val tuning = ui.tuning
    val e = readout.estimate
    if (meter == MeterState.EMPTY && tuning != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Input", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            LevelMeter(
                levelDb = e.levelDb,
                gateDb = NoiseGate(tuning.profile.noiseGateDb, ui.sensitivity).effectiveThresholdDb,
                gateOpen = e.gateOpen,
                modifier = Modifier.weight(1f).height(16.dp),
            )
        }
    }
    val strings = tuning?.profile?.stringTargetsMidi?.let { NoteNames.parseMidiCsv(it) }.orEmpty()
    if (strings.isNotEmpty()) {
        val transposition = tuning?.profile?.transpositionSemitones ?: 0
        val playing = if (e.hasPitch) Transposition.written(e.midi, transposition) else null
        val nearest = playing?.let { p -> strings.minByOrNull { abs(it - p) }?.takeIf { abs(it - p) <= 1 } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            strings.forEach { midi ->
                val active = midi == nearest
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(RadiusSm),
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Beatwheel.colors.neon else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        NoteName(NoteNames.spell(midi), MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp))
                    }
                }
            }
        }
    }
    DroneStrip(ui, vm, droneExpanded, onDroneExpandedChange)
    if (ui.recorder.active) {
        PrimaryButton(
            "Recording " + formatElapsed(ui.recorder.elapsedMs),
            onRecord,
            modifier = Modifier.fillMaxWidth(),
            icon = Glyphs.Strobe,
        )
    } else {
        SecondaryButton("Record drift", onRecord, modifier = Modifier.fillMaxWidth(), enabled = tuning != null && meter == MeterState.EMPTY)
    }
}

fun formatElapsed(ms: Long): String {
    val total = ms / 1000
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
