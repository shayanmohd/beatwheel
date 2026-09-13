package com.mohdshayan.beatwheel.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdshayan.beatwheel.data.repo.RecorderState
import com.mohdshayan.beatwheel.ui.components.DriftChart
import com.mohdshayan.beatwheel.ui.components.PrimaryButton
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.NumeralStyle
import com.mohdshayan.beatwheel.ui.theme.SheetShape
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDriftSheet(
    state: RecorderState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = Beatwheel.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable(state.sessionId) { mutableStateOf(state.defaultName) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = SheetShape,
        containerColor = colors.case,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // A phone in landscape is shorter than the sheet: without scrolling, Stop and save sits off screen.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Recording drift", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(formatElapsed(state.elapsedMs), style = NumeralStyle.copy(fontSize = 28.sp), color = colors.ink)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.readingCount == 0) {
                    Text("Hold a note to start the trace", style = MaterialTheme.typography.bodyLarge, color = colors.graphite)
                } else {
                    DriftChart(state.trace, Modifier.fillMaxWidth().height(180.dp))
                }
            }
            val gaps = state.trace.count { it.gap }
            Text(
                when {
                    state.paused -> "Paused in the background"
                    gaps > 0 -> String.format(Locale.US, "%d readings, paused %d %s in the background", state.readingCount, gaps, if (gaps == 1) "time" else "times")
                    else -> String.format(Locale.US, "%d readings, up to 60 minutes", state.readingCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.graphite,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Session name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.neon,
                    unfocusedBorderColor = colors.graphite,
                    focusedLabelColor = colors.ink,
                    unfocusedLabelColor = colors.graphite,
                    cursorColor = colors.ink,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            // The main action gets its own full-width line, so no label is cut short at large font sizes.
            PrimaryButton("Stop and save", { onSave(name) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A rehearsal's worth of readings is never one mistaken tap away from deletion.
                TextButton(
                    onClick = { if (state.readingCount == 0) onDiscard() else confirmDiscard = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Discard") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Keep recording") }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this recording?") },
            text = { Text(String.format(Locale.US, "Its %d readings are deleted and no session is saved.", state.readingCount)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDiscard()
                }) { Text("Discard recording") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep recording") } },
        )
    }
}
