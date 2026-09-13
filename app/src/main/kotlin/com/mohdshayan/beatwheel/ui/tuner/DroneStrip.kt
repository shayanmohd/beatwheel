package com.mohdshayan.beatwheel.ui.tuner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.ui.components.Glyphs
import com.mohdshayan.beatwheel.ui.components.NoteName
import com.mohdshayan.beatwheel.ui.components.PrimaryButton
import com.mohdshayan.beatwheel.ui.theme.Barlow
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.LocalReducedMotion
import com.mohdshayan.beatwheel.ui.theme.RadiusMd
import com.mohdshayan.beatwheel.ui.theme.RadiusSm
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DroneStrip(ui: TunerUi, vm: TunerViewModel, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val reduced = LocalReducedMotion.current
    val colors = Beatwheel.colors
    val d = ui.droneSettings
    val written = NoteNames.spell(TunerViewModel.droneWrittenMidi(d))

    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            // Wait until the settings are laid out: with animations off they appear on the next frame,
            // and asking earlier brings only the collapsed strip into view.
            if (!reduced) delay(230)
            withFrameNanos { }
            withFrameNanos { }
            bringIntoView.bringIntoView()
        }
    }

    Surface(
        color = colors.case,
        shape = RoundedCornerShape(RadiusMd),
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.TuningFork, contentDescription = null, tint = colors.graphite, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .weight(1f)
                        .clickable { onExpandedChange(!expanded) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Drone", style = MaterialTheme.typography.labelMedium, color = colors.graphite)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NoteName(written, androidx.compose.ui.text.TextStyle(fontFamily = Barlow, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 28.sp))
                            Spacer(Modifier.width(8.dp))
                            Text(Waveform.fromKey(d.waveform).label, style = MaterialTheme.typography.bodyMedium, color = colors.graphite)
                        }
                    }
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Hide drone settings" else "Show drone settings",
                        tint = colors.graphite,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                if (ui.drone.playing) {
                    PrimaryButton("Stop drone", vm::stopDrone)
                } else {
                    PrimaryButton("Start drone", { vm.startDrone() }, enabled = ui.tuning != null)
                }
            }

            if (ui.drone.playing && ui.drone.onSpeaker && !ui.droneHintShown) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Headphones, contentDescription = null, tint = colors.graphite, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "On the speaker, Beatwheel removes its drone from what it hears. Headphones keep it out entirely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.dismissDroneHint() }) { Text("Got it") }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(if (reduced) snap() else tween(200)),
                exit = shrinkVertically(if (reduced) snap() else tween(200)),
            ) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Note", style = MaterialTheme.typography.labelMedium, color = colors.graphite)
                    // Two rows of six so the twelve notes span the strip at any width.
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0 until 2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (pc in row * 6 until row * 6 + 6) {
                                    val selected = pc == d.pitchClass
                                    Surface(
                                        shape = RoundedCornerShape(RadiusSm),
                                        color = if (selected) colors.stand else colors.case,
                                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.neon else colors.rule),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clickable { vm.setDronePitchClass(pc) }
                                            .semantics {
                                                this.selected = selected
                                                contentDescription = "Drone on " + NoteNames.pitchClassSpoken(pc)
                                            },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            NoteName(
                                                NoteNames.spell(60 + pc),
                                                MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 20.sp),
                                                showOctave = false,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Octave", style = MaterialTheme.typography.labelMedium, color = colors.graphite, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.setDroneOctave(d.octave - 1) }, enabled = d.octave > 2) {
                            Icon(Icons.Outlined.Remove, contentDescription = "Octave down")
                        }
                        Text(d.octave.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp))
                        IconButton(onClick = { vm.setDroneOctave(d.octave + 1) }, enabled = d.octave < 5) {
                            Icon(Icons.Outlined.Add, contentDescription = "Octave up")
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Waveform.entries.forEachIndexed { i, w ->
                            SegmentedButton(
                                selected = w.key == d.waveform,
                                onClick = { vm.setDroneWaveform(w) },
                                shape = SegmentedButtonDefaults.itemShape(i, Waveform.entries.size, RoundedCornerShape(RadiusSm)),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = colors.stand,
                                    activeContentColor = colors.ink,
                                    activeBorderColor = colors.rule,
                                    inactiveContainerColor = colors.case,
                                    inactiveContentColor = colors.graphite,
                                    inactiveBorderColor = colors.rule,
                                ),
                                label = { Text(w.label) },
                            )
                        }
                    }
                    var volume by remember(d.volume) { mutableFloatStateOf(d.volume) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Volume", style = MaterialTheme.typography.labelMedium, color = colors.graphite, modifier = Modifier.width(64.dp))
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            onValueChangeFinished = { vm.setDroneVolume(volume) },
                            colors = beatwheelSliderColors(),
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Drone volume" },
                        )
                        Text(String.format(Locale.US, "%d%%", (volume * 100).toInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
fun beatwheelSliderColors() = SliderDefaults.colors(
    thumbColor = Beatwheel.colors.neon,
    activeTrackColor = Beatwheel.colors.ink,
    inactiveTrackColor = Beatwheel.colors.rule,
    activeTickColor = Beatwheel.colors.stand,
    inactiveTickColor = Beatwheel.colors.graphite,
)
