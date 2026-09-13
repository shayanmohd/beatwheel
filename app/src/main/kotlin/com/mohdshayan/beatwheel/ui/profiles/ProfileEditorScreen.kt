package com.mohdshayan.beatwheel.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.ui.components.EmptyState
import com.mohdshayan.beatwheel.ui.components.GroupLabel
import com.mohdshayan.beatwheel.ui.components.PrimaryButton
import com.mohdshayan.beatwheel.ui.components.SkeletonBar
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.RadiusSm
import com.mohdshayan.beatwheel.ui.tuner.beatwheelSliderColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorScreen(onBack: () -> Unit, vm: ProfileEditorViewModel = viewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val temperaments by vm.temperaments.collectAsStateWithLifecycle()
    val colors = Beatwheel.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (f.original?.builtInKey != null) "Edit instrument" else "Edit profile", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!f.loaded) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(6) { SkeletonBar(null, 56.dp) }
            }
            return@Scaffold
        }
        if (f.missing) {
            EmptyState("This profile was deleted.", null, Modifier.fillMaxSize().padding(padding), actionLabel = "Back to profiles", onAction = onBack)
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val err = f.showErrors
            OutlinedTextField(
                value = f.name,
                onValueChange = { v -> vm.update { it.copy(name = v.take(40)) } },
                label = { Text("Name") },
                singleLine = true,
                isError = err && f.nameError != null,
                supportingText = (if (err) f.nameError else null)?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            PickerField(
                "Transposition",
                Transposition.label(f.transposition),
                Transposition.presets.map { it.semitones to it.label },
            ) { v -> vm.update { it.copy(transposition = v) } }

            GroupLabel("Reference", start = 0.dp)
            OutlinedTextField(
                value = f.reference,
                onValueChange = { v -> vm.update { it.copy(reference = v.take(6)) } },
                label = { Text("A4 in Hz") },
                singleLine = true,
                isError = f.referenceError != null && (err || f.reference.length >= 3),
                supportingText = { Text(f.referenceError ?: "0.1 Hz steps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(415.0, 430.0, 440.0, 442.0, 466.0).forEach { hz ->
                    val text = String.format(Locale.US, "%.1f", hz)
                    val selected = f.reference.toDoubleOrNull() == hz
                    FilterChip(
                        selected = selected,
                        onClick = { vm.update { it.copy(reference = text) } },
                        label = { Text(hz.toInt().toString()) },
                        shape = RoundedCornerShape(RadiusSm),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = selected,
                            borderColor = colors.rule, selectedBorderColor = colors.neon, selectedBorderWidth = 2.dp,
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.stand,
                            selectedLabelColor = colors.ink,
                            labelColor = colors.ink,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "A $text" },
                    )
                }
            }

            GroupLabel("Tuning", start = 0.dp)
            PickerField(
                "Temperament",
                temperaments.firstOrNull { it.key == f.temperamentKey }?.name ?: "Equal",
                temperaments.map { it.key to it.name },
            ) { v -> vm.update { it.copy(temperamentKey = v) } }
            PickerField(
                "Tonic",
                NoteNames.pitchClassSpoken(f.tonic),
                (0 until 12).map { it to NoteNames.pitchClassSpoken(it) },
            ) { v -> vm.update { it.copy(tonic = v) } }
            SwitchRow("Keep A at the reference", "Shifts the temperament so A reads exactly the reference pitch.", f.keepReferenceA) { v ->
                vm.update { it.copy(keepReferenceA = v) }
            }

            GroupLabel("Range and strings", start = 0.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = f.minHz,
                    onValueChange = { v -> vm.update { it.copy(minHz = v.take(7)) } },
                    label = { Text("Lowest Hz") },
                    singleLine = true,
                    isError = err && f.rangeError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f.maxHz,
                    onValueChange = { v -> vm.update { it.copy(maxHz = v.take(7)) } },
                    label = { Text("Highest Hz") },
                    singleLine = true,
                    isError = err && f.rangeError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                )
            }
            if (err && f.rangeError != null) {
                Text(f.rangeError!!, style = MaterialTheme.typography.bodySmall, color = colors.ink, modifier = Modifier.padding(top = 4.dp, start = 16.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = f.strings,
                onValueChange = { v -> vm.update { it.copy(strings = v.take(60)) } },
                label = { Text("Open strings, written") },
                singleLine = true,
                isError = f.stringsError != null && err,
                supportingText = { Text(f.stringsError ?: "Leave empty for wind instruments") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            GroupLabel("Listening", start = 0.dp)
            SwitchRow("Held-tone mode", "A longer averaging window for sustained reeds and bows.", f.heldTone) { v ->
                vm.update { it.copy(heldTone = v) }
            }
            Text("Noise gate", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = f.gateDb,
                    onValueChange = { v -> vm.update { it.copy(gateDb = v) } },
                    valueRange = -70f..-30f,
                    colors = beatwheelSliderColors(),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Noise gate" },
                )
                Text(String.format(Locale.US, "%d dB", f.gateDb.toInt()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 56.dp).padding(start = 8.dp))
            }
            Text("Raise it in a loud room so other players do not open the meter.", style = MaterialTheme.typography.bodyMedium, color = colors.graphite)

            Spacer(Modifier.height(24.dp))
            PrimaryButton("Save profile", { vm.save(onBack) }, modifier = Modifier.fillMaxWidth())
            if (err && !f.valid) {
                Text("Fix the fields marked above, then save.", style = MaterialTheme.typography.bodyMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

