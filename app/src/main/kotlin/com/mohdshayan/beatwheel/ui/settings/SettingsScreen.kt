package com.mohdshayan.beatwheel.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.beatwheel.R
import com.mohdshayan.beatwheel.core.drift.BackupCodec
import com.mohdshayan.beatwheel.core.drone.Waveform
import com.mohdshayan.beatwheel.data.prefs.Counter
import com.mohdshayan.beatwheel.data.prefs.DisplayMode
import com.mohdshayan.beatwheel.data.prefs.ThemeChoice
import com.mohdshayan.beatwheel.ui.components.GroupLabel
import com.mohdshayan.beatwheel.ui.components.PrimaryButton
import com.mohdshayan.beatwheel.ui.components.SecondaryButton
import com.mohdshayan.beatwheel.ui.components.SkeletonBar
import com.mohdshayan.beatwheel.ui.profiles.SwitchRow
import com.mohdshayan.beatwheel.ui.sessions.IMPORT_MIME_TYPES
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.RadiusSm
import com.mohdshayan.beatwheel.ui.tuner.beatwheelSliderColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = Beatwheel.colors
    var licences by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }
    val policyUrl = stringResource(R.string.privacy_policy_url)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!ui.loaded) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(6) { SkeletonBar(null, 48.dp) }
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            GroupLabel("Display", start = 0.dp)
            Segmented(
                listOf(DisplayMode.STROBE to "Strobe disc", DisplayMode.NEEDLE to "Needle"),
                ui.displayMode,
                vm::setDisplayMode,
            )
            SwitchRow(
                "Turn the disc with animations off",
                "When system animations are off, the disc stays still and a rim arc shows the offset.",
                ui.rotateReduced,
            ) { vm.setRotateReduced(it) }

            GroupLabel("Theme", start = 0.dp)
            Segmented(
                listOf(ThemeChoice.SYSTEM to "System", ThemeChoice.LIGHT to "Light", ThemeChoice.DARK to "Dark"),
                ui.theme,
                vm::setTheme,
            )

            GroupLabel("Sensitivity", start = 0.dp)
            var sensitivity by remember(ui.sensitivity) { mutableFloatStateOf(ui.sensitivity) }
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                onValueChangeFinished = { vm.setSensitivity(sensitivity) },
                colors = beatwheelSliderColors(),
                modifier = Modifier.semantics { contentDescription = "Sensitivity" },
            )
            Row {
                Text("Loud rooms", style = MaterialTheme.typography.bodySmall, color = colors.graphite, modifier = Modifier.weight(1f))
                Text("Quiet instruments", style = MaterialTheme.typography.bodySmall, color = colors.graphite)
            }
            Text(
                "Tune shows the input level with a mark where the meter starts listening.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.graphite,
                modifier = Modifier.padding(top = 8.dp),
            )

            GroupLabel("Drone waveform", start = 0.dp)
            Segmented(Waveform.entries.map { it to it.label }, ui.waveform, vm::setWaveform)

            GroupLabel("Your data", start = 0.dp)
            Text(
                "${ui.profileCount} profiles and ${ui.sessionCount} sessions on this device. A backup is one JSON file with everything, which Import backup reads back.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.graphite,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Export backup", { exporter.launch(BackupCodec.fileName(LocalDate.now())) })
                SecondaryButton("Import backup", { importer.launch(IMPORT_MIME_TYPES) })
            }

            GroupLabel("Local counts", start = 0.dp)
            SwitchRow(
                "Keep local counts",
                "Counts stay on this device. Beatwheel has no internet permission.",
                ui.countsEnabled,
            ) { vm.setCountsEnabled(it) }
            if (ui.countsEnabled) {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CountLine("Tuning sessions", ui.counts[Counter.TUNING_SESSIONS] ?: 0)
                    CountLine("Drone starts", ui.counts[Counter.DRONE_STARTS] ?: 0)
                    CountLine("Sessions saved", ui.counts[Counter.SESSIONS_SAVED] ?: 0)
                    CountLine("Exports", ui.counts[Counter.EXPORTS] ?: 0)
                }
            }

            GroupLabel("About", start = 0.dp)
            LinkRow("Privacy policy") {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)))
                } catch (_: Exception) {
                    vm.showMessage("No browser is installed. The policy is at shayanmohd.github.io/beatwheel")
                }
            }
            LinkRow("Licences") { licences = true }
            Text(
                "Microphone audio is measured in memory and discarded. Nothing is recorded to a file or sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.graphite,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (licences) {
        AlertDialog(
            onDismissRequest = { licences = false },
            title = { Text("Licences") },
            text = {
                Text(
                    "Barlow Semi Condensed, copyright 2017 The Barlow Project Authors.\n\n" +
                        "Atkinson Hyperlegible Next, copyright 2020 to 2024 The Atkinson Hyperlegible Next Project Authors.\n\n" +
                        "Both fonts are licensed under the SIL Open Font License, Version 1.1.\n\n" +
                        "Android Jetpack libraries and Kotlin are licensed under the Apache License, Version 2.0.",
                )
            },
            confirmButton = { TextButton(onClick = { licences = false }) { Text("Close") } },
        )
    }
    if (error != null) {
        val writeFailed = error == SettingsViewModel.WRITE_FAILED
        AlertDialog(
            onDismissRequest = vm::clearError,
            icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }, // errors are Ink with an outlined icon, never the accent
            title = { Text(error!!) },
            // Same shape as the Sessions import error: what happened, how to fix it, and the fix one tap away.
            text = if (writeFailed) null else ({ Text("Choose a file exported from Beatwheel's Settings, named like beatwheel-backup-2026-09-13.json.") }),
            confirmButton = {
                TextButton(onClick = {
                    vm.clearError()
                    if (writeFailed) exporter.launch(BackupCodec.fileName(LocalDate.now())) else importer.launch(IMPORT_MIME_TYPES)
                }) { Text(if (writeFailed) "Choose location" else "Choose another file") }
            },
            dismissButton = { TextButton(onClick = vm::clearError) { Text("Close") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = Beatwheel.colors
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size, RoundedCornerShape(RadiusSm)),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.case,
                    activeContentColor = colors.ink,
                    activeBorderColor = colors.rule,
                    inactiveContainerColor = colors.stand,
                    inactiveContentColor = colors.graphite,
                    inactiveBorderColor = colors.rule,
                ),
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun CountLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.widthIn(min = 40.dp),
        )
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
    )
}
