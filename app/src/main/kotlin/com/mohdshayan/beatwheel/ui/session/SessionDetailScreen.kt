package com.mohdshayan.beatwheel.ui.session

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.beatwheel.core.music.Transposition
import com.mohdshayan.beatwheel.ui.components.DriftChart
import com.mohdshayan.beatwheel.ui.components.EmptyState
import com.mohdshayan.beatwheel.ui.components.PrimaryButton
import com.mohdshayan.beatwheel.ui.components.SecondaryButton
import com.mohdshayan.beatwheel.ui.components.SkeletonBar
import com.mohdshayan.beatwheel.ui.sessions.formatDuration
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.NumeralStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(onBack: () -> Unit, vm: SessionDetailViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val fileError by vm.fileError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val loaded = ui as? SessionDetailUi.Loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(loaded?.session?.name ?: "Session", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (val state = ui) {
            SessionDetailUi.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBar(null, 240.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    repeat(3) { SkeletonBar(72.dp, 40.dp) }
                }
                SkeletonBar(220.dp, 16.dp)
            }
            SessionDetailUi.Missing -> EmptyState(
                title = "This session was deleted.",
                body = null,
                actionLabel = "Back to sessions",
                onAction = onBack,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is SessionDetailUi.Loaded -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Detail(state, vm)
                Spacer(Modifier.height(20.dp))
                FlowRowActions(
                    onExport = { exporter.launch(vm.csvFileName()) },
                    onShare = { vm.share { context.startActivity(Intent.createChooser(it, "Share CSV")) } },
                    onDelete = { confirmDelete = true },
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("Its readings are removed from this device. An exported CSV or backup stays as it is.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text("Delete session") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    if (fileError) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }, // errors are Ink with an outlined icon, never the accent
            title = { Text("Could not write the file. Choose another location.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearError()
                    exporter.launch(vm.csvFileName())
                }) { Text("Choose location") }
            },
            dismissButton = { TextButton(onClick = vm::clearError) { Text("Close") } },
        )
    }
}

private val STARTED = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.getDefault())

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Detail(state: SessionDetailUi.Loaded, vm: SessionDetailViewModel) {
    val s = state.session
    val colors = Beatwheel.colors
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
        if (state.points.none { !it.gap }) {
            EmptyState(title = "No readings in this session", body = "Readings are taken while a note sounds and Tune or Stand is on screen.")
        } else {
            DriftChart(state.points, Modifier.fillMaxSize())
        }
    }
    Text("minutes", style = MaterialTheme.typography.labelSmall, color = colors.graphite, modifier = Modifier.align(Alignment.End))
    Spacer(Modifier.height(16.dp))

    if (s.startCents != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Start, cents", cents(s.startCents))
            Stat("End, cents", cents(s.endCents))
            Stat("Range, cents", String.format(Locale.US, "%.1f", (s.maxCents ?: 0f) - (s.minCents ?: 0f)))
            Stat("Cents a minute", cents(s.driftCentsPerMinute, 2))
        }
        Spacer(Modifier.height(20.dp))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Line(STARTED.format(Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())))
        Line(formatDuration(s.durationMs) + ", " + s.readingCount + " readings")
        val transposition = Transposition.label(s.transpositionSemitones)
        Line(if (s.transpositionSemitones == 0 || s.profileName.contains(transposition)) s.profileName else s.profileName + ", " + transposition)
        Line("A " + String.format(Locale.US, "%.1f", s.referenceAHz) + " Hz, " + s.temperamentName)
    }
    Spacer(Modifier.height(20.dp))

    var name by rememberSaveable(s.id) { mutableStateOf(s.name) }
    var note by rememberSaveable(s.id) { mutableStateOf(s.note) }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(60) },
        label = { Text("Name") },
        singleLine = true,
        isError = name.isBlank(),
        supportingText = if (name.isBlank()) ({ Text("Give the session a name") }) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = com.mohdshayan.beatwheel.ui.profiles.fieldColors(),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) vm.rename(name, note) },
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = note,
        onValueChange = { note = it.take(500) },
        label = { Text("Note") },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = com.mohdshayan.beatwheel.ui.profiles.fieldColors(),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) vm.rename(name, note) },
    )
    androidx.compose.runtime.DisposableEffect(s.id) {
        onDispose { vm.rename(name, note) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowActions(onExport: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Export CSV", onExport, icon = Icons.Outlined.FileDownload)
        SecondaryButton("Share", onShare, icon = Icons.Outlined.Share)
        SecondaryButton("Delete session", onDelete, icon = Icons.Outlined.DeleteOutline)
    }
}

private fun cents(v: Float?, decimals: Int = 1): String =
    v?.let { com.mohdshayan.beatwheel.core.music.CentsText.signed(it, decimals) } ?: "none"

@Composable
private fun Stat(label: String, value: String) {
    Column(Modifier.widthIn(min = 64.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Beatwheel.colors.graphite)
        Text(value, style = NumeralStyle.copy(fontSize = 24.sp, lineHeight = 30.sp), color = Beatwheel.colors.ink)
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
}

