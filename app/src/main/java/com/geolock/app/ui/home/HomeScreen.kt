package com.geolock.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geolock.app.domain.ProtectionStatus
import com.geolock.app.ui.components.HorizontalDivider
import com.geolock.app.ui.components.SectionCard
import com.geolock.app.ui.components.KeyGateDialog
import com.geolock.app.ui.components.StatusBanner
import com.geolock.app.ui.components.WarningCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenZone: (String) -> Unit,
    onAddZone: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val runAction: (GateAction) -> Unit = { action ->
        when (action) {
            GateAction.SETTINGS -> onOpenSettings()
            GateAction.ADD_ZONE -> onAddZone()
            GateAction.EDIT_ZONE -> Unit
            GateAction.LOGS -> onOpenLogs()
        }
    }

    if (gate.visible) {
        KeyGateDialog(
            value = gate.key,
            error = gate.error,
            onValueChange = viewModel::updateGateKey,
            onConfirm = { viewModel.confirmGate(runAction) },
            onDismiss = viewModel::dismissGate
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoLock") },
                actions = {
                    IconButton(onClick = { viewModel.requestProtected(GateAction.LOGS, runAction) }) {
                        Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "Activity log")
                    }
                    IconButton(onClick = { viewModel.requestProtected(GateAction.SETTINGS, runAction) }) {
                        Icon(Icons.Outlined.Lock, contentDescription = "Unlock to manage")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.requestProtected(GateAction.ADD_ZONE, runAction) }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add zone")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SectionCard {
                StatusBanner(snapshot.status, snapshot.reasons.firstOrNull())
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                LabelBlock(
                    label = "Current location",
                    value = snapshot.activeZones.joinToString(", ") { it.name }.ifBlank { "Outside all zones" }
                )
                Spacer(Modifier.height(14.dp))
                LabelBlock(label = "Locked apps", value = snapshot.lockedApps.size.toString())
            }

            if (snapshot.status == ProtectionStatus.DEGRADED) {
                Spacer(Modifier.height(16.dp))
                WarningCard(
                    buildString {
                        append("GeoLock protection is inactive.\n\n")
                        snapshot.reasons.forEach { append(it).append('\n') }
                        append("\nEnable the missing permission or service to restore app blocking.")
                    }.trim()
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("Zones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (state.zones.isEmpty()) {
                Text("No zones yet. Add the first place you want to lock apps.")
            } else {
                state.zones.forEach { zone ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.requestProtected(GateAction.EDIT_ZONE) { onOpenZone(zone.id) }
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(zone.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                buildString {
                                    append(if (zone.enabled) "Enabled" else "Disabled")
                                    append(" · ")
                                    append("${zone.radius.toInt()} m")
                                    append(" · ")
                                    append("${zone.blockedApplications.size} apps")
                                    if (zone.blockedDomains.isNotEmpty()) {
                                        append(" · ${zone.blockedDomains.size} sites")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun LabelBlock(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
