package com.mohdshayan.beatwheel.ui.sessions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.beatwheel.data.db.DriftSession
import com.mohdshayan.beatwheel.ui.components.EmptyState

import com.mohdshayan.beatwheel.ui.components.GroupLabel
import com.mohdshayan.beatwheel.ui.components.Hairline
import com.mohdshayan.beatwheel.ui.components.SkeletonBar
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.NumeralStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onOpenSession: (Long) -> Unit,
    onRecordDrift: () -> Unit,
    vm: SessionsViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val error by vm.importError.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import backup") },
                            onClick = {
                                menu = false
                                importer.launch(IMPORT_MIME_TYPES)
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (val state = ui) {
            SessionsUi.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SkeletonBar(96.dp, 14.dp)
                repeat(5) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBar(200.dp, 18.dp)
                        SkeletonBar(260.dp, 14.dp)
                    }
                }
            }
            is SessionsUi.Loaded -> if (state.groups.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = "No sessions yet",
                    body = "Record a held note from Tune. A session charts how far it drifts over a rehearsal, for up to an hour.",
                    actionLabel = "Record drift",
                    onAction = onRecordDrift,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    state.groups.forEach { group ->
                        item(key = "h" + group.label) { GroupLabel(group.label) }
                        items(group.items, key = { it.id }) { s ->
                            SessionRow(s) { onOpenSession(s.id) }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }, // errors are Ink with an outlined icon, never the accent
            title = { Text(error!!) },
            text = { Text("Choose a file exported from Beatwheel's Settings, named like beatwheel-backup-2026-09-13.json.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearError()
                    importer.launch(IMPORT_MIME_TYPES)
                }) { Text("Choose another file") }
            },
            dismissButton = { TextButton(onClick = vm::clearError) { Text("Close") } },
        )
    }
}

private val DATE = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

@Composable
private fun SessionRow(s: DriftSession, onClick: () -> Unit) {
    val colors = Beatwheel.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                DATE.format(Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())) + ", " +
                    formatDuration(s.durationMs) + ", " + s.profileName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.graphite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val sag = if (s.startCents != null && s.endCents != null) s.endCents - s.startCents else null
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp).widthIn(min = 64.dp)) {
            Text(
                sag?.let { com.mohdshayan.beatwheel.core.music.CentsText.signed(it) } ?: "No data",
                style = if (sag != null) NumeralStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize) else MaterialTheme.typography.bodyMedium,
                color = if (sag != null) colors.ink else colors.graphite,
            )
            if (sag != null) Text("cents", style = MaterialTheme.typography.labelSmall, color = colors.graphite)
        }
    }
    Hairline(Modifier.padding(horizontal = 16.dp))
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    return when {
        totalSec < 60 -> "$totalSec s"
        totalSec < 3600 -> "${totalSec / 60} min"
        else -> String.format(Locale.US, "%d h %02d min", totalSec / 3600, (totalSec % 3600) / 60)
    }
}
