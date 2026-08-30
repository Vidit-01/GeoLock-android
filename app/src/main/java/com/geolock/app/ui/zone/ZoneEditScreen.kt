package com.geolock.app.ui.zone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geolock.app.domain.UnlockDurations
import com.geolock.app.ui.apps.AppList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneEditScreen(
    viewModel: ZoneEditViewModel,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { if (state.isNew) "New zone" else "Zone" }) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.enabled) "Enabled" else "Disabled", fontWeight = FontWeight.Medium)
                    Text("Turn this zone on or off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.enabled, onCheckedChange = viewModel::updateEnabled)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text("Location", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search a place") },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { viewModel.searchLocation(query) }) { Text("Search") }
            OutlinedButton(onClick = viewModel::useCurrentLocation, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.locating) "Finding location…" else "Use current location")
            }
            if (state.latitude != null && state.longitude != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.locationLabel.ifBlank { "Selected location" }, fontWeight = FontWeight.Medium)
                Text(
                    "Lat ${"%.5f".format(state.latitude)}  ·  Lng ${"%.5f".format(state.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Radius  ${state.radius.toInt()} m", style = MaterialTheme.typography.titleSmall)
            Slider(value = state.radius, onValueChange = viewModel::updateRadius, valueRange = 50f..2000f)
            Spacer(Modifier.height(20.dp))
            Text("Blocked applications", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            AppList(
                apps = state.apps,
                selected = state.selectedPackages,
                onToggle = viewModel::toggleApp,
                modifier = Modifier.height(360.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text("Blocked domains", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sites in a browser, like youtube.com. The app lock still covers Instagram and YouTube themselves.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.domainDraft,
                onValueChange = viewModel::updateDomainDraft,
                label = { Text("example.com") },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = viewModel::addDomain) { Text("Add domain") }
            state.domains.forEach { domain ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(domain, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.removeDomain(domain) }) { Text("Remove") }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Unlock behavior", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            val options = UnlockDurations.OPTIONS + UnlockDurations.UNTIL_LEAVE
            options.forEach { minutes ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.unlockDurationMinutes == minutes,
                            onClick = { viewModel.updateUnlock(minutes) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.unlockDurationMinutes == minutes,
                        onClick = { viewModel.updateUnlock(minutes) }
                    )
                    Text(UnlockDurations.label(minutes), modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = !state.saving && state.name.isNotBlank() && state.latitude != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
            if (!state.isNew) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.delete(onDone) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete zone", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
