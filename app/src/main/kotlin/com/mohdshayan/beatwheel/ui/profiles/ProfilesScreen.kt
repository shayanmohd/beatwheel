package com.mohdshayan.beatwheel.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.beatwheel.data.db.CustomTemperament
import com.mohdshayan.beatwheel.data.db.InstrumentProfile
import com.mohdshayan.beatwheel.ui.components.GroupLabel
import com.mohdshayan.beatwheel.ui.components.SecondaryButton
import com.mohdshayan.beatwheel.ui.components.SkeletonBar
import com.mohdshayan.beatwheel.ui.theme.Beatwheel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onEditProfile: (Long) -> Unit,
    onEditTemperament: (Long) -> Unit,
    vm: ProfilesViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<InstrumentProfile?>(null) }
    var pendingTempDelete by remember { mutableStateOf<CustomTemperament?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        when (val state = ui) {
            ProfilesUi.Loading -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(7) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBar(160.dp, 18.dp)
                        SkeletonBar(220.dp, 14.dp)
                    }
                }
            }
            is ProfilesUi.Loaded -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item { GroupLabel("Instruments") }
                items(state.builtIn, key = { it.profile.id }) { row ->
                    ProfileListRow(row, vm, onEditProfile, onDelete = null)
                }
                item { GroupLabel("Your profiles") }
                if (state.custom.isEmpty()) {
                    item {
                        Text(
                            "Copy any instrument from its menu to change its reference, temperament or range.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Beatwheel.colors.graphite,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                items(state.custom, key = { it.profile.id }) { row ->
                    ProfileListRow(row, vm, onEditProfile, onDelete = { pendingDelete = it })
                }
                item { GroupLabel("Temperaments") }
                items(state.temperaments, key = { "t" + it.id }) { t ->
                    TemperamentRow(t, onEdit = { onEditTemperament(t.id) }, onDelete = { pendingTempDelete = t })
                }
                item {
                    Text(
                        "Six historical temperaments are built in. Make your own from twelve cents offsets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Beatwheel.colors.graphite,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item {
                    SecondaryButton(
                        "New temperament",
                        { onEditTemperament(0L) },
                        icon = Icons.Outlined.Add,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Saved sessions keep their readings and the profile name they were recorded with.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.delete(p)
                }) { Text("Delete profile") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
    pendingTempDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingTempDelete = null },
            title = { Text("Delete ${t.name}?") },
            text = { Text("Profiles that use it switch to equal temperament.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingTempDelete = null
                    vm.deleteTemperament(t)
                }) { Text("Delete temperament") }
            },
            dismissButton = { TextButton(onClick = { pendingTempDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun ProfileListRow(
    row: ProfileRow,
    vm: ProfilesViewModel,
    onEdit: (Long) -> Unit,
    onDelete: ((InstrumentProfile) -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val colors = Beatwheel.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton) { vm.select(row.profile) }
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (row.active) Icon(Icons.Outlined.Check, contentDescription = "In use", tint = colors.neon)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.graphite, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More for ${row.profile.name}", tint = colors.graphite)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = {
                    menu = false
                    onEdit(row.profile.id)
                })
                DropdownMenuItem(text = { Text("Copy") }, onClick = {
                    menu = false
                    vm.copy(row.profile) { onEdit(it) }
                })
                if (onDelete != null) {
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        menu = false
                        onDelete(row.profile)
                    })
                }
            }
        }
    }
}

@Composable
private fun TemperamentRow(t: CustomTemperament, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .heightIn(min = 56.dp)
            .padding(start = 52.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More for ${t.name}", tint = Beatwheel.colors.graphite)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = {
                    menu = false
                    onEdit()
                })
                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                    menu = false
                    onDelete()
                })
            }
        }
    }
}
