package com.mohdshayan.beatwheel.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.beatwheel.core.music.NoteNames
import com.mohdshayan.beatwheel.core.music.Temperaments
import com.mohdshayan.beatwheel.ui.components.GroupLabel
import com.mohdshayan.beatwheel.ui.components.NoteName
import com.mohdshayan.beatwheel.ui.components.PrimaryButton
import com.mohdshayan.beatwheel.ui.components.SkeletonBar
import com.mohdshayan.beatwheel.ui.theme.Beatwheel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemperamentEditorScreen(onBack: () -> Unit, vm: TemperamentEditorViewModel = viewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val colors = Beatwheel.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (f.id == 0L) "New temperament" else "Edit temperament", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!f.loaded) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(8) { SkeletonBar(null, 48.dp) }
            }
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
            OutlinedTextField(
                value = f.name,
                onValueChange = vm::setName,
                label = { Text("Name") },
                singleLine = true,
                isError = f.showErrors && f.nameError != null,
                supportingText = (if (f.showErrors) f.nameError else null)?.let { { Text(it) } },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            PickerField(
                "Start from",
                "Choose a built-in table",
                Temperaments.presets.map { it.key to it.name },
                vm::startFrom,
            )
            GroupLabel("Cents from equal temperament, with C as tonic", start = 0.dp)
            for (pc in 0 until 12) {
                val error = f.offsetError(pc)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    NoteName(
                        NoteNames.spell(60 + pc),
                        MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 24.sp),
                        showOctave = false,
                        modifier = Modifier.width(48.dp),
                    )
                    OutlinedTextField(
                        value = f.offsets[pc],
                        onValueChange = { vm.setOffset(pc, it) },
                        singleLine = true,
                        suffix = { Text("cents", color = colors.graphite) },
                        isError = error != null && (f.showErrors || f.offsets[pc].isNotEmpty()),
                        supportingText = if (error != null && (f.showErrors || f.offsets[pc].isNotEmpty())) ({ Text(error) }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                        modifier = Modifier.weight(1f).semantics { contentDescription = NoteNames.pitchClassSpoken(pc) + ", cents from equal" },
                    )
                    IconButton(onClick = { vm.play(pc) }) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Play " + NoteNames.pitchClassSpoken(pc), tint = colors.ink)
                    }
                }
            }
            Text(
                "Play sounds each note at octave 4 on the reference of the profile in use.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.graphite,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Save temperament", { vm.save(onBack) }, modifier = Modifier.fillMaxWidth())
            // The first bad field can be a screen above the button, so the tap always answers here.
            if (f.showErrors && !f.valid) {
                Text("Fix the fields marked above, then save.", style = MaterialTheme.typography.bodyMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
