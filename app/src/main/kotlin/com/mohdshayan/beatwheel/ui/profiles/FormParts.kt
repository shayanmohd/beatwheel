package com.mohdshayan.beatwheel.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mohdshayan.beatwheel.ui.theme.Beatwheel

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Beatwheel.colors.neon,
    // Graphite, not Rule: an input's edge must reach 3:1 against the ground (Rule is 1.6:1).
    unfocusedBorderColor = Beatwheel.colors.graphite,
    focusedLabelColor = Beatwheel.colors.ink,
    unfocusedLabelColor = Beatwheel.colors.graphite,
    cursorColor = Beatwheel.colors.ink,
    errorBorderColor = Beatwheel.colors.ink,
    errorLabelColor = Beatwheel.colors.ink,
    errorSupportingTextColor = Beatwheel.colors.ink,
)

/** A labelled value that opens a menu. Used for transposition, temperament and tonic. */
@Composable
fun <T> PickerField(label: String, valueLabel: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.DropdownList) { open = true }
                .semantics { contentDescription = "$label, $valueLabel" }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Beatwheel.colors.graphite)
                Text(valueLabel, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = Beatwheel.colors.graphite)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    onPick(value)
                })
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = Beatwheel.colors.graphite)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Beatwheel.colors.stand,
                checkedTrackColor = Beatwheel.colors.ink,
                checkedBorderColor = Beatwheel.colors.ink,
                uncheckedThumbColor = Beatwheel.colors.graphite,
                uncheckedTrackColor = Beatwheel.colors.case,
                uncheckedBorderColor = Beatwheel.colors.graphite,
            ),
        )
    }
}
